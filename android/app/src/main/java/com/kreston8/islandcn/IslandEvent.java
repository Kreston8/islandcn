package com.kreston8.islandcn;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

/** One notification, reduced to what the island can draw. */
final class IslandEvent {

    private static final int ICON_PX = 96;

    final String key;
    final String pkg;
    final String appLabel;
    final Bitmap icon;
    final CharSequence title;
    final CharSequence text;
    final PendingIntent contentIntent;
    final long postTime;

    private IslandEvent(
            String key,
            String pkg,
            String appLabel,
            Bitmap icon,
            CharSequence title,
            CharSequence text,
            PendingIntent contentIntent,
            long postTime) {
        this.key = key;
        this.pkg = pkg;
        this.appLabel = appLabel;
        this.icon = icon;
        this.title = title;
        this.text = text;
        this.contentIntent = contentIntent;
        this.postTime = postTime;
    }

    static IslandEvent from(Context context, StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        if (notification == null) {
            return null;
        }
        CharSequence title = null;
        CharSequence text = null;
        Bundle extras = notification.extras;
        if (extras != null) {
            title = extras.getCharSequence(Notification.EXTRA_TITLE);
            text = extras.getCharSequence(Notification.EXTRA_TEXT);
            if (isEmpty(text)) {
                text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
            }
            if (isEmpty(text)) {
                text = extras.getCharSequence(Notification.EXTRA_SUB_TEXT);
            }
        }
        if (isEmpty(title) && isEmpty(text)) {
            return null;
        }
        String pkg = sbn.getPackageName();
        return new IslandEvent(
                sbn.getKey(),
                pkg,
                labelOf(context, pkg),
                iconOf(context, notification),
                title,
                text,
                notification.contentIntent,
                sbn.getPostTime());
    }

    /** A fake event so the user can see the animation before a real notification arrives. */
    static IslandEvent preview(Context context) {
        Bitmap icon = null;
        try {
            icon = toBitmap(context.getPackageManager().getApplicationIcon(context.getPackageName()), ICON_PX);
        } catch (Throwable ignored) {
            // A missing icon is cosmetic only.
        }
        return new IslandEvent(
                null,
                context.getPackageName(),
                labelOf(context, context.getPackageName()),
                icon,
                context.getString(R.string.preview_title),
                context.getString(R.string.preview_text),
                null,
                System.currentTimeMillis());
    }

    private static String labelOf(Context context, String pkg) {
        try {
            PackageManager pm = context.getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Throwable ignored) {
            return pkg;
        }
    }

    private static Bitmap iconOf(Context context, Notification notification) {
        Bitmap large = toBitmap(notification.getLargeIcon() == null
                ? null
                : notification.getLargeIcon().loadDrawable(context), ICON_PX);
        if (large != null) {
            return large;
        }
        return toBitmap(notification.getSmallIcon() == null
                ? null
                : notification.getSmallIcon().loadDrawable(context), ICON_PX);
    }

    private static Bitmap toBitmap(Drawable drawable, int size) {
        if (drawable == null || size <= 0) {
            return null;
        }
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, size, size);
        drawable.draw(canvas);
        return bitmap;
    }

    private static boolean isEmpty(CharSequence value) {
        return value == null || value.length() == 0;
    }
}
