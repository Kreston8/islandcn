package com.kreston8.islandcn;

import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;

import java.util.List;

/**
 * Shows a now-playing card while music plays in the background.
 *
 * <p>Cost discipline, because this runs for as long as the service does:
 * <ul>
 *   <li>Track changes arrive through {@link MediaController.Callback}; nothing is polled for them.
 *   <li>The only polling is "is the music app on screen yet", at one cheap query every 2.5 s, and
 *       only while a session is actually playing. It stops on pause, on screen off and when the
 *       usage-access permission is missing.
 *   <li>One card is posted per track, not per second.
 * </ul>
 */
final class MediaWatcher {

    private static final long POLL_MS = 2500L;
    /** Small window: we only need the most recent foreground change. */
    private static final long WINDOW_MS = 8000L;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ComponentName component;
    private final UsageEvents.Event event = new UsageEvents.Event();

    private MediaSessionManager sessions;
    private MediaController controller;
    private MediaSessionManager.OnActiveSessionsChangedListener sessionsListener;
    private MediaController.Callback controllerCallback;

    private android.content.BroadcastReceiver screenReceiver;
    private boolean registered;
    private boolean polling;
    private boolean shown;
    private boolean screenOn = true;
    private String shownKey;
    private String lastTrack;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            polling = false;
            refresh();
        }
    };

    MediaWatcher(Context context) {
        this.context = context.getApplicationContext();
        this.component = new ComponentName(context, IslandListener.class);
    }

    void start() {
        if (registered) {
            return;
        }
        try {
            sessions = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        } catch (Throwable failure) {
            sessions = null;
        }
        if (sessions == null) {
            return;
        }
        sessionsListener = this::onSessionsChanged;
        controllerCallback = new MediaController.Callback() {
            @Override
            public void onMetadataChanged(MediaMetadata metadata) {
                refresh();
            }

            @Override
            public void onPlaybackStateChanged(PlaybackState state) {
                refresh();
            }

            @Override
            public void onSessionDestroyed() {
                bind(null);
            }
        };
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsListener, component);
            registered = true;
        } catch (SecurityException denied) {
            // Notification access was revoked; media cards stay off until it comes back.
            return;
        } catch (Throwable ignored) {
            return;
        }
        registerScreenReceiver();
        onSessionsChanged(readActiveSessions());
    }

    void stop() {
        handler.removeCallbacks(poll);
        polling = false;
        hide();
        if (registered && sessions != null) {
            try {
                sessions.removeOnActiveSessionsChangedListener(sessionsListener);
            } catch (Throwable ignored) {
                // Already detached.
            }
        }
        registered = false;
        if (screenReceiver != null) {
            try {
                context.unregisterReceiver(screenReceiver);
            } catch (Throwable ignored) {
                // Not registered.
            }
            screenReceiver = null;
        }
        bind(null);
        sessions = null;
    }

    /** Drops the card and forgets the track, e.g. when the user switches the feature off. */
    void reset() {
        hide();
        lastTrack = null;
        refresh();
    }

    private List<MediaController> readActiveSessions() {
        try {
            return sessions == null ? null : sessions.getActiveSessions(component);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void onSessionsChanged(List<MediaController> list) {
        MediaController pick = null;
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                MediaController candidate = list.get(i);
                if (candidate == null) {
                    continue;
                }
                if (isPlaying(candidate.getPlaybackState())) {
                    pick = candidate;
                    break;
                }
                if (pick == null) {
                    pick = candidate;
                }
            }
        }
        bind(pick);
    }

    private void bind(MediaController next) {
        if (controller == next) {
            refresh();
            return;
        }
        if (controller != null) {
            try {
                controller.unregisterCallback(controllerCallback);
            } catch (Throwable ignored) {
                // The session died first.
            }
        }
        controller = next;
        if (controller == null) {
            hide();
            return;
        }
        try {
            controller.registerCallback(controllerCallback, handler);
        } catch (Throwable ignored) {
            // A vendor controller can refuse callbacks; the poll below still covers it.
        }
        refresh();
    }

    /** One decision point: post, keep, or hide. */
    private void refresh() {
        MediaController active = controller;
        boolean playing = active != null && isPlaying(active.getPlaybackState());
        if (!playing || !Prefs.enabled(context, IslandEvent.KIND_MUSIC)) {
            hide();
            return;
        }
        String pkg = active.getPackageName();
        if (pkg == null) {
            hide();
            return;
        }
        if (isMusicForeground(pkg)) {
            hide();
            schedulePoll();
            return;
        }
        MediaMetadata metadata = active.getMetadata();
        if (metadata == null) {
            hide();
            return;
        }
        CharSequence title = metadata.getText(MediaMetadata.METADATA_KEY_TITLE);
        CharSequence artist = metadata.getText(MediaMetadata.METADATA_KEY_ARTIST);
        String track = (title == null ? "" : title.toString()) + '|' + (artist == null ? "" : artist.toString());
        if (shown && track.equals(lastTrack)) {
            schedulePoll();
            return;
        }
        Bitmap art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null) {
            art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        }
        IslandEvent card = IslandEvent.music(
                context, pkg, title, artist, art, active.getSessionActivity());
        if (card == null) {
            hide();
            return;
        }
        shown = true;
        shownKey = card.key;
        lastTrack = track;
        IslandBridge.post(card);
        schedulePoll();
    }

    private void hide() {
        if (shown && shownKey != null) {
            IslandBridge.removed(shownKey);
        }
        shown = false;
        shownKey = null;
        lastTrack = null;
    }

    private void schedulePoll() {
        if (polling || !screenOn) {
            return;
        }
        if (!hasUsageAccess()) {
            // Without usage access the foreground check always says "not on screen", so there is
            // nothing to watch for and polling would only burn cycles.
            return;
        }
        polling = true;
        handler.postDelayed(poll, POLL_MS);
    }

    private boolean hasUsageAccess() {
        try {
            AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) {
                return false;
            }
            int mode = ops.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** True when the player itself is the app the user is looking at. */
    private boolean isMusicForeground(String pkg) {
        if (!hasUsageAccess()) {
            return false;
        }
        UsageStatsManager usage;
        try {
            usage = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        } catch (Throwable failure) {
            usage = null;
        }
        if (usage == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        UsageEvents events;
        try {
            events = usage.queryEvents(now - WINDOW_MS, now);
        } catch (Throwable ignored) {
            return false;
        }
        if (events == null) {
            return false;
        }
        String latest = null;
        try {
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                int type = event.getEventType();
                if (type == UsageEvents.Event.MOVE_TO_FOREGROUND
                        || type == UsageEvents.Event.ACTIVITY_RESUMED) {
                    latest = event.getPackageName();
                }
            }
        } catch (Throwable ignored) {
            return false;
        }
        return pkg.equals(latest);
    }

    private void registerScreenReceiver() {
        if (screenReceiver != null) {
            return;
        }
        screenReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                String action = intent == null ? null : intent.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    screenOn = false;
                    handler.removeCallbacks(poll);
                    polling = false;
                } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    screenOn = true;
                    refresh();
                }
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(screenReceiver, filter);
            }
        } catch (Throwable ignored) {
            screenReceiver = null;
        }
    }

    private static boolean isPlaying(PlaybackState state) {
        return state != null && state.getState() == PlaybackState.STATE_PLAYING;
    }
}
