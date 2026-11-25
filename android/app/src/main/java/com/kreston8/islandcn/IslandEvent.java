package com.kreston8.islandcn;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Telephony;
import android.service.notification.StatusBarNotification;

/** One notification, reduced to what the island can draw. */
final class IslandEvent {

    static final int KIND_GENERIC = 0;
    static final int KIND_OTP = 1;
    static final int KIND_PICKUP = 2;
    static final int KIND_DELIVERY = 3;
    static final int KIND_TODO = 4;

    private static final int ICON_PX = 96;

    final String key;
    final String pkg;
    final String appLabel;
    final Bitmap icon;
    final CharSequence title;
    final CharSequence text;
    final PendingIntent contentIntent;
    final long postTime;
    final int kind;
    /** Text the copy button puts on the clipboard, or null when there is nothing to copy. */
    final String copyText;
    /** When the delivery is expected, or 0 when the card has no countdown. */
    final long etaMillis;

    private IslandEvent(
            String key,
            String pkg,
            String appLabel,
            Bitmap icon,
            CharSequence title,
            CharSequence text,
            PendingIntent contentIntent,
            long postTime,
            int kind,
            String copyText,
            long etaMillis) {
        this.key = key;
        this.pkg = pkg;
        this.appLabel = appLabel;
        this.icon = icon;
        this.title = title;
        this.text = text;
        this.contentIntent = contentIntent;
        this.postTime = postTime;
        this.kind = kind;
        this.copyText = copyText;
        this.etaMillis = etaMillis;
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
        String label = labelOf(context, pkg);
        ContentParser.Result parsed = ContentParser.classify(
                pkg,
                label,
                title == null ? null : title.toString(),
                text == null ? null : text.toString());
        return new IslandEvent(
                sbn.getKey(),
                pkg,
                label,
                iconOf(context, notification),
                title,
                text,
                notification.contentIntent,
                sbn.getPostTime(),
                parsed.kind,
                parsed.code,
                parsed.etaMillis);
    }

    /** An SMS the receiver classified as a code; returns null for ordinary messages. */
    static IslandEvent sms(Context context, String sender, String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        ContentParser.Result parsed = ContentParser.classify(null, null, sender, body);
        if (parsed.kind == KIND_GENERIC) {
            return null;
        }
        String smsPackage = null;
        try {
            smsPackage = Telephony.Sms.getDefaultSmsPackage(context);
        } catch (Throwable ignored) {
            // No default messaging app on some ROMs.
        }
        Bitmap icon = null;
        if (smsPackage != null) {
            try {
                icon = toBitmap(context.getPackageManager().getApplicationIcon(smsPackage), ICON_PX);
            } catch (Throwable ignored) {
                // Fall back to the bundled bubble below.
            }
        }
        if (icon == null) {
            icon = toBitmap(context.getDrawable(R.drawable.ic_message), ICON_PX);
        }
        return new IslandEvent(
                "sms:" + System.currentTimeMillis(),
                smsPackage,
                sender == null || sender.isEmpty() ? context.getString(R.string.sms_label) : sender,
                icon,
                sender == null || sender.isEmpty() ? context.getString(R.string.sms_label) : sender,
                body,
                null,
                System.currentTimeMillis(),
                parsed.kind,
                parsed.code,
                parsed.etaMillis);
    }

    /** A fake event so the user can see the animation before a real notification arrives. */
    static IslandEvent preview(Context context) {
        Bitmap icon = null;
        try {
            icon = toBitmap(context.getPackageManager().getApplicationIcon(context.getPackageName()), ICON_PX);
        } catch (Throwable ignored) {
            // A missing icon is cosmetic only.
        }
        String code = "483920";
        return new IslandEvent(
                null,
                context.getPackageName(),
                labelOf(context, context.getPackageName()),
                icon,
                context.getString(R.string.preview_title),
                context.getString(R.string.preview_text),
                null,
                System.currentTimeMillis(),
                KIND_OTP,
                code,
                0L);
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
