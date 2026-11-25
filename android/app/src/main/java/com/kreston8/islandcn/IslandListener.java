package com.kreston8.islandcn;

import android.Manifest;
import android.app.Notification;
import android.content.pm.PackageManager;
import android.provider.Telephony;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Feeds posted notifications to the island.
 *
 * <p>Only what a user would call "a new message" gets through: group summaries, ongoing progress
 * notifications and our own status notification are dropped so the island does not flash on every
 * background update.
 *
 * <p>When {@link SmsReceiver} is enabled it already shows the code SMS, so the messaging app's own
 * notification is skipped; otherwise the same text would appear twice.
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
        if (handledByReceiver(sbn)) {
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

    private boolean handledByReceiver(StatusBarNotification sbn) {
        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        try {
            String smsPackage = Telephony.Sms.getDefaultSmsPackage(this);
            return smsPackage != null && smsPackage.equals(sbn.getPackageName());
        } catch (Throwable ignored) {
            return false;
        }
    }
}
