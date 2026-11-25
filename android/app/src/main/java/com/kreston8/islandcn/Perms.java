package com.kreston8.islandcn;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

/** The two permissions this app lives on, and the settings screens that grant them. */
final class Perms {

    private Perms() {
    }

    static boolean canOverlay(Context context) {
        return Settings.canDrawOverlays(context);
    }

    static void openOverlaySettings(Context context) {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Throwable ignored) {
            openFallback(context, new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
    }

    static boolean hasNotificationAccess(Context context) {
        String flat = Settings.Secure.getString(
                context.getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(context.getPackageName());
    }

    static void openNotificationAccessSettings(Context context) {
        openFallback(context, new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    private static void openFallback(Context context, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Throwable ignored) {
            // Some ROMs hide these screens; the in-app text still explains what to enable.
        }
    }
}
