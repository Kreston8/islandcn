package com.kreston8.islandcn;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

/**
 * Owns the overlay window for the lifetime of the island.
 *
 * <p>A foreground service is required because the island has to stay on screen while other apps are
 * in front. The window is {@code TYPE_APPLICATION_OVERLAY}, so it draws above app windows but below
 * the status bar, which is why the pill is anchored just under the status bar instead of on top of
 * the clock.
 */
public final class IslandService extends Service implements IslandBridge.Sink {

    static final String ACTION_STOP = "com.kreston8.islandcn.STOP";

    private static final String CHANNEL_ID = "island_status";
    private static final int NOTIFICATION_ID = 42;
    private static final long AUTO_COLLAPSE_MS = 4200L;

    private static volatile boolean running;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private WindowManager windowManager;
    private IslandView island;

    private final Runnable collapseTask = () -> {
        if (island != null) {
            island.setExpanded(false, true);
        }
    };

    private PhoneWatcher phoneWatcher;
    private AlarmWatcher alarmWatcher;
    private boolean alarmRegistered;
    private MediaWatcher mediaWatcher;

    private IslandEvent current;
    private String currentKey;

    static boolean isRunning() {
        return running;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        IslandBridge.attach(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startInForeground();
        running = true;
        if (!Perms.canOverlay(this)) {
            Toast.makeText(this, R.string.need_overlay, Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }
        attachOverlay();
        startWatchers();
        return START_STICKY;
    }

    @Override
    public void onIslandEvent(IslandEvent event) {
        if (event == null) {
            return;
        }
        if (!Prefs.enabled(this, event.kind)) {
            return;
        }
        if (island == null) {
            attachOverlay();
        }
        if (island == null) {
            return;
        }
        current = event;
        currentKey = event.key;
        island.bind(event);
        island.setVisibility(View.VISIBLE);
        island.setExpanded(true, true);
        handler.removeCallbacks(collapseTask);
        if (event.kind != IslandEvent.KIND_MUSIC) {
            // The music card stays open, otherwise its lines would be clipped while rotating.
            handler.postDelayed(collapseTask, AUTO_COLLAPSE_MS);
        }
    }

    @Override
    public void onIslandRemoved(String key) {
        if (key == null || !key.equals(currentKey)) {
            return;
        }
        hideIsland();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(collapseTask);
        IslandBridge.detach(this);
        running = false;
        if (island != null && windowManager != null) {
            try {
                windowManager.removeView(island);
            } catch (Throwable ignored) {
                // The window is already gone with the process.
            }
        }
        stopWatchers();
        island = null;
        super.onDestroy();
    }

    private void attachOverlay() {
        if (island != null || windowManager == null) {
            return;
        }
        IslandView view = new IslandView(this);
        view.setActions(new IslandView.Actions() {
            @Override
            public void onOpen() {
                openCurrent();
            }

            @Override
            public void onDismiss() {
                dismissCurrent();
            }

            @Override
            public void onCopy(String value) {
                copyCode(value);
            }
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        params.format = PixelFormat.TRANSLUCENT;
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = statusBarHeight() + dp(4);
        params.setTitle(getString(R.string.app_name));

        try {
            windowManager.addView(view, params);
        } catch (Throwable failure) {
            Toast.makeText(this, R.string.need_overlay, Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        view.setVisibility(View.INVISIBLE);
        island = view;
    }

    /**
     * Starts the passive watchers: the call state, the next clock alarm, and the playing track.
     *
     * <p>All three are optional. Without {@code READ_PHONE_STATE} the phone half stays dormant, a
     * missing alarm clock provider is treated the same way, and the music half simply never sees a
     * session.
     */
    private void startWatchers() {
        if (phoneWatcher == null) {
            phoneWatcher = new PhoneWatcher(this);
        }
        phoneWatcher.start();

        if (alarmWatcher == null) {
            alarmWatcher = new AlarmWatcher();
        }
        if (!alarmRegistered) {
            try {
                IntentFilter filter = new IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(alarmWatcher, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    registerReceiver(alarmWatcher, filter);
                }
                alarmRegistered = true;
            } catch (Throwable ignored) {
                // Some ROMs reject the registration; the island keeps working without alarm cards.
            }
        }
        try {
            alarmWatcher.schedule(this);
        } catch (Throwable ignored) {
            // Nothing scheduled, or the clock service is unavailable.
        }

        // The music watcher is the only part that polls, so it is skipped entirely when off.
        if (!Prefs.enabled(this, IslandEvent.KIND_MUSIC)) {
            return;
        }
        if (mediaWatcher == null) {
            mediaWatcher = new MediaWatcher(this);
        }
        try {
            mediaWatcher.start();
        } catch (Throwable ignored) {
            // Without notification access there are no media sessions to read.
        }
    }

    private void stopWatchers() {
        if (phoneWatcher != null) {
            phoneWatcher.stop();
            phoneWatcher = null;
        }
        if (alarmRegistered && alarmWatcher != null) {
            try {
                unregisterReceiver(alarmWatcher);
            } catch (Throwable ignored) {
                // Already unregistered with the process.
            }
            alarmRegistered = false;
        }
        alarmWatcher = null;

        if (mediaWatcher != null) {
            mediaWatcher.stop();
            mediaWatcher = null;
        }
    }

    private void openCurrent() {
        handler.removeCallbacks(collapseTask);
        if (island != null) {
            island.setExpanded(false, true);
        }
        IslandEvent event = current;
        if (event != null && event.contentIntent != null) {
            try {
                event.contentIntent.send();
                return;
            } catch (Throwable ignored) {
                // The notification went stale; fall back to the app itself.
            }
        }
        // No content intent (SMS, or a notification without a tap target): open the app itself.
        if (event != null && event.pkg != null) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(event.pkg);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(launch);
                    return;
                } catch (Throwable ignored) {
                    // Fall through to our own screen.
                }
            }
        }
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(open);
        } catch (Throwable ignored) {
            // Nothing sensible left to do.
        }
    }

    private void copyCode(String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        try {
            ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager != null) {
                manager.setPrimaryClip(ClipData.newPlainText(getString(R.string.tag_otp), value));
            }
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
            // The clipboard can be unavailable in restricted profiles.
        }
        handler.removeCallbacks(collapseTask);
        if (island != null) {
            island.setExpanded(false, true);
        }
    }

    private void dismissCurrent() {
        handler.removeCallbacks(collapseTask);
        IslandBridge.cancel(currentKey);
        hideIsland();
    }

    private void hideIsland() {
        handler.removeCallbacks(collapseTask);
        current = null;
        currentKey = null;
        if (island != null) {
            island.stopTicker();
            island.setVisibility(View.INVISIBLE);
        }
    }

    private void startInForeground() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_status),
                    NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            channel.setSound(null, null);
            manager.createNotificationChannel(channel);
        }

        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openIntent = PendingIntent.getActivity(
                this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, IslandService.class).setAction(ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(
                this, 1, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_status)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.running_hint))
                .setContentIntent(openIntent)
                .setOngoing(true)
                .setShowWhen(false)
                .addAction(new Notification.Action.Builder(
                        (Icon) null, getString(R.string.stop_island), stopIntent).build());

        Notification notification = builder.build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (id > 0) {
            return getResources().getDimensionPixelSize(id);
        }
        return dp(28);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
