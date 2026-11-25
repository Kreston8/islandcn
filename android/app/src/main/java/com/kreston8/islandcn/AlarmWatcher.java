package com.kreston8.islandcn;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Turns the system clock alarm into an island card, about fifteen minutes early.
 *
 * <p>The island cannot show anything when the clock app actually rings: that is a full screen
 * intent owned by the clock, and it covers every overlay. So this receiver schedules its own
 * heads-up from the alarm time the AlarmManager exposes through {@code getNextAlarmClock()}.
 *
 * <p>{@link #schedule(Context)} is called by {@link IslandService} both at start-up and whenever the
 * system reports a changed alarm, because this broadcast is only delivered to receivers that are
 * registered at runtime.
 *
 * <p>A card that outlives its alarm is the failure people actually notice: a reminder kept sitting
 * on screen long after the alarm had rung, been moved or been switched off, which reads as a
 * phantom alarm. Three guards prevent it. Every call to {@link #schedule(Context)} reconciles the
 * card against the alarm the system currently reports and withdraws it as soon as the two disagree;
 * every announcement arms a second pending intent a minute past the alarm time that removes the card
 * even if nothing else ever runs; and one alarm is announced at most once, so a service restart
 * cannot produce a duplicate.
 */
public final class AlarmWatcher extends BroadcastReceiver {

    private static final String ACTION_ALARM_SOON = "com.kreston8.islandcn.ALARM_SOON";
    private static final String ACTION_ALARM_DONE = "com.kreston8.islandcn.ALARM_DONE";
    private static final String EXTRA_TRIGGER = "trigger";
    private static final long LEAD_MS = 15L * 60L * 1000L;
    private static final long DONE_DELAY_MS = 60L * 1000L;
    /** Must match the key {@link IslandEvent#alarm(Context)} builds, so the card can be withdrawn. */
    private static final String KEY_PREFIX = "alarm:";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (ACTION_ALARM_SOON.equals(action)) {
            announce(context, intent.getLongExtra(EXTRA_TRIGGER, 0L));
            return;
        }
        if (ACTION_ALARM_DONE.equals(action)) {
            withdraw(context, intent.getLongExtra(EXTRA_TRIGGER, 0L));
            return;
        }
        schedule(context);
    }

    /** Arms (or cancels) the single heads-up for whatever alarm the clock has queued next. */
    public void schedule(Context context) {
        AlarmManager manager = alarmManager(context);
        if (manager == null) {
            return;
        }
        cancel(context, ACTION_ALARM_SOON, REQUEST_SOON);
        long trigger = triggerOf(manager);
        long shown = Prefs.alarmShown(context);
        if (shown != 0L && shown != trigger) {
            // Moved, switched off or deleted since it was announced, so the card is no longer true.
            withdraw(context, shown);
        }
        long now = System.currentTimeMillis();
        if (trigger <= now) {
            return;
        }
        long warnAt = trigger - LEAD_MS;
        if (warnAt <= now) {
            // The alarm is closer than the warning window, so say it right away.
            announce(context, trigger);
            return;
        }
        try {
            manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, warnAt, pending(context, ACTION_ALARM_SOON, REQUEST_SOON, trigger));
        } catch (Throwable ignored) {
            // Inexact scheduling can be refused by aggressive power savers; nothing else to do.
        }
    }

    /** Posts the card once per alarm, and only while the system still agrees that alarm exists. */
    private void announce(Context context, long trigger) {
        long now = System.currentTimeMillis();
        if (trigger <= now) {
            return;
        }
        AlarmManager manager = alarmManager(context);
        if (manager == null) {
            return;
        }
        if (Prefs.alarmShown(context) == trigger) {
            // Already said once; a restart must not say it again.
            return;
        }
        if (triggerOf(manager) != trigger) {
            // Changed between arming and firing, so this warning belongs to an alarm that is gone.
            return;
        }
        IslandBridge.post(IslandEvent.alarm(context));
        Prefs.setAlarmShown(context, trigger);
        try {
            manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, trigger + DONE_DELAY_MS,
                    pending(context, ACTION_ALARM_DONE, REQUEST_DONE, trigger));
        } catch (Throwable ignored) {
            // Without the follow-up the schedule() reconciliation still removes the card.
        }
    }

    /** Removes an alarm card that no longer matches reality and forgets that it was shown. */
    private static void withdraw(Context context, long trigger) {
        if (trigger == 0L) {
            return;
        }
        cancel(context, ACTION_ALARM_DONE, REQUEST_DONE);
        if (Prefs.alarmShown(context) == trigger) {
            Prefs.setAlarmShown(context, 0L);
        }
        IslandBridge.removed(KEY_PREFIX + trigger);
    }

    private static final int REQUEST_SOON = 0;
    private static final int REQUEST_DONE = 1;

    private static AlarmManager alarmManager(Context context) {
        try {
            return (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        } catch (Throwable failure) {
            return null;
        }
    }

    private static long triggerOf(AlarmManager manager) {
        try {
            AlarmManager.AlarmClockInfo info = manager.getNextAlarmClock();
            return info == null ? 0L : info.getTriggerTime();
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static Intent build(Context context, String action, long trigger) {
        return new Intent(context, AlarmWatcher.class)
                .setAction(action)
                .putExtra(EXTRA_TRIGGER, trigger);
    }

    private static PendingIntent pending(Context context, String action, int request, long trigger) {
        return PendingIntent.getBroadcast(context, request, build(context, action, trigger),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void cancel(Context context, String action, int request) {
        PendingIntent pending = PendingIntent.getBroadcast(context, request,
                build(context, action, 0L),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE);
        if (pending == null) {
            return;
        }
        AlarmManager manager = alarmManager(context);
        if (manager != null) {
            try {
                manager.cancel(pending);
            } catch (Throwable ignored) {
                // Nothing scheduled is the same outcome as a failed cancel.
            }
        }
        pending.cancel();
    }
}
