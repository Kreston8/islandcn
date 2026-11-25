package com.kreston8.islandcn;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Feeds posted notifications to the island.
 *
 * <p>Only what a user would call "a new message" gets through: group summaries, ongoing progress
 * notifications and our own status notification are dropped so the island does not flash on every
 * background update.
 */
public final class IslandListener extends NotificationListenerService {

    @Override
    public void onListenerConnected() {
        IslandBridge.attachListener(this);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || getPackageName().equals(sbn.getPackageName())) {
            return;
        }
        Notification notification = sbn.getNotification();
        if (notification == null) {
            return;
        }
        int flags = notification.flags;
        if ((flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
            return;
        }
        if ((flags & Notification.FLAG_ONGOING_EVENT) != 0) {
            return;
        }
        IslandBridge.post(IslandEvent.from(this, sbn));
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null) {
            return;
        }
        IslandBridge.removed(sbn.getKey());
    }
}
