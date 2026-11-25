package com.kreston8.islandcn;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a notification and decides which card template fits it.
 *
 * <p>Everything here is text matching on the title and body, which is the only thing a
 * NotificationListenerService actually gets. When nothing matches the event stays generic and the
 * island draws the plain title/body card.
 */
final class ContentParser {

    /** What the parser found, if anything. */
    static final class Result {
        final int kind;
        final String code;
        final long etaMillis;

        Result(int kind, String code, long etaMillis) {
            this.kind = kind;
            this.code = code;
            this.etaMillis = etaMillis;
        }
    }

    private static final Result NONE = new Result(IslandEvent.KIND_GENERIC, null, 0L);

    private static final Pattern OTP_KEYWORD = Pattern.compile(
            "(验证码|校验码|动态码|动态密码|确认码|激活码|授权码|验证代号|登录码"
                    + "|verification code|security code|login code|one[- ]time (?:password|code))",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DIGITS = Pattern.compile("(\\d{4,8})");
    private static final Pattern LETTER_CODE = Pattern.compile("\\b([A-Za-z]-\\d{6})\\b");

    private static final Pattern PICKUP_KEYWORD = Pattern.compile(
            "(取件码|取货码|提货码|取件口令|领取码|取件号)");

    private static final Pattern PICKUP_CODE = Pattern.compile(
            "(\\d{1,3}-\\d{1,3}-\\d{3,5})|([A-Za-z]?\\d{4,8})");

    private static final Pattern DELIVERY_KEYWORD = Pattern.compile(
            "(外卖|骑手|配送|送达|送到|准时达|便当|餐品)");

    private static final Pattern CLOCK_ETA = Pattern.compile(
            "(\\d{1,2})\\s*[:：]\\s*(\\d{2})\\s*(?:前)?\\s*(?:送达|送到|到达)");

    private static final Pattern MINUTE_ETA = Pattern.compile(
            "(?:预计|还有|剩余|约)\\s*(\\d{1,3})\\s*分钟");

    private static final String[] NOTES_PACKAGES = {"com.miui.notes", "com.miui.notepad"};

    private ContentParser() {
    }

    static Result classify(String pkg, String appLabel, String title, String text) {
        String sample = ((title == null ? "" : title) + " " + (text == null ? "" : text)).trim();
        if (sample.isEmpty()) {
            return NONE;
        }
        if (isNotesApp(pkg, appLabel)) {
            CharSequence content = text == null || text.length() == 0 ? title : text;
            return new Result(IslandEvent.KIND_TODO, content == null ? null : content.toString(), 0L);
        }
        String code = verificationCode(sample);
        if (code != null) {
            return new Result(IslandEvent.KIND_OTP, code, 0L);
        }
        String pickup = pickupCode(sample);
        if (pickup != null) {
            return new Result(IslandEvent.KIND_PICKUP, pickup, 0L);
        }
        long eta = deliveryEta(sample);
        if (eta > 0L) {
            return new Result(IslandEvent.KIND_DELIVERY, null, eta);
        }
        return NONE;
    }

    private static boolean isNotesApp(String pkg, String appLabel) {
        if (pkg != null) {
            for (String notes : NOTES_PACKAGES) {
                if (notes.equals(pkg)) {
                    return true;
                }
            }
        }
        if (appLabel == null) {
            return false;
        }
        return appLabel.contains("笔记") || appLabel.contains("便签") || appLabel.contains("备忘录");
    }

    /**
     * Codes usually follow the keyword ("验证码 483920") but plenty of senders put them first
     * ("483920 是您的验证码"), so both sides of the keyword are searched.
     */
    private static String verificationCode(String sample) {
        Matcher keyword = OTP_KEYWORD.matcher(sample);
        if (!keyword.find()) {
            return null;
        }
        Matcher letterCode = LETTER_CODE.matcher(sample);
        if (letterCode.find()) {
            return letterCode.group(1);
        }
        String after = sample.substring(keyword.end(), Math.min(sample.length(), keyword.end() + 24));
        Matcher forward = DIGITS.matcher(after);
        if (forward.find()) {
            return forward.group(1);
        }
        String before = sample.substring(Math.max(0, keyword.start() - 24), keyword.start());
        Matcher backward = DIGITS.matcher(before);
        String found = null;
        while (backward.find()) {
            found = backward.group(1);
        }
        return found;
    }

    private static String pickupCode(String sample) {
        Matcher keyword = PICKUP_KEYWORD.matcher(sample);
        if (!keyword.find()) {
            return null;
        }
        String after = sample.substring(keyword.end(), Math.min(sample.length(), keyword.end() + 24));
        Matcher code = PICKUP_CODE.matcher(after);
        if (code.find()) {
            return code.group(1) != null ? code.group(1) : code.group(2);
        }
        return null;
    }

    private static long deliveryEta(String sample) {
        if (!DELIVERY_KEYWORD.matcher(sample).find()) {
            return 0L;
        }
        long now = System.currentTimeMillis();
        Matcher clock = CLOCK_ETA.matcher(sample);
        if (clock.find()) {
            int hour = Integer.parseInt(clock.group(1));
            int minute = Integer.parseInt(clock.group(2));
            if (hour > 23 || minute > 59) {
                return 0L;
            }
            Calendar target = Calendar.getInstance();
            target.set(Calendar.HOUR_OF_DAY, hour);
            target.set(Calendar.MINUTE, minute);
            target.set(Calendar.SECOND, 0);
            target.set(Calendar.MILLISECOND, 0);
            long value = target.getTimeInMillis();
            if (value < now - 6L * 60L * 60L * 1000L) {
                // "23:50 送达" read at 00:05 belongs to the next day.
                value += 24L * 60L * 60L * 1000L;
            }
            return value > now ? value : 0L;
        }
        Matcher minutes = MINUTE_ETA.matcher(sample);
        if (minutes.find()) {
            int count = Integer.parseInt(minutes.group(1));
            if (count > 0 && count <= 240) {
                return now + count * 60L * 1000L;
            }
        }
        return 0L;
    }
}
