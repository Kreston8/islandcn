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
 */
public final class AlarmWatcher extends BroadcastReceiver {

    private static final String ACTION_ALARM_SOON = "com.kreston8.islandcn.ALARM_SOON";
    private static final long LEAD_MS = 15L * 60L * 1000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        if (ACTION_ALARM_SOON.equals(intent.getAction())) {
            IslandBridge.post(IslandEvent.alarm(context));
            return;
        }
        schedule(context);
    }

    /** Arms (or cancels) the single heads-up for whatever alarm the clock has queued next. */
    public void schedule(Context context) {
        AlarmManager manager;
        try {
            manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        } catch (Throwable failure) {
            return;
        }
        if (manager == null) {
            return;
        }
        PendingIntent pending = pending(context);
        manager.cancel(pending);
        long trigger = triggerOf(manager);
        long now = System.currentTimeMillis();
        if (trigger <= now) {
            return;
        }
        long warnAt = trigger - LEAD_MS;
        if (warnAt <= now) {
            // The alarm is closer than the warning window, so say it right away.
            IslandBridge.post(IslandEvent.alarm(context));
            return;
        }
        try {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, warnAt, pending);
        } catch (Throwable ignored) {
            // Inexact scheduling can be refused by aggressive power savers; nothing else to do.
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

    private static PendingIntent pending(Context context) {
        Intent intent = new Intent(context, AlarmWatcher.class).setAction(ACTION_ALARM_SOON);
        return PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
