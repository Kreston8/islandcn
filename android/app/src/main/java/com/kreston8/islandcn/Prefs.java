package com.kreston8.islandcn;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The per-feature switches shown in the setup screen.
 *
 * <p>Card switches default to on, so an existing install keeps behaving as before; the two additions
 * that change the desktop look (battery chips, standby pill) default to off and go through
 * {@link #opt}. Reads go through the framework's in-memory preference map after the first load,
 * which keeps the hot path (every incoming card) free of disk I/O.
 */
final class Prefs {

    private static final String FILE = "island";

    static final String KEY_GENERIC = "on_generic";
    static final String KEY_OTP = "on_otp";
    static final String KEY_PICKUP = "on_pickup";
    static final String KEY_DELIVERY = "on_delivery";
    static final String KEY_TODO = "on_todo";
    static final String KEY_CALL = "on_call";
    static final String KEY_ALARM = "on_alarm";
    static final String KEY_NAV = "on_nav";
    static final String KEY_MUSIC = "on_music";
    /** Not a card kind: the karaoke line itself, which is the only feature that opens a socket. */
    static final String KEY_LYRICS = "on_lyrics";
    static final String KEY_ALARM_SHOWN = "alarm_shown";

    /** Bluetooth battery chips: off by default, each one widens the pill. */
    static final String KEY_BT = "on_bt";
    /** Desktop standby island and its three selectable items (items default to on). */
    static final String KEY_STANDBY = "on_standby";
    static final String KEY_STANDBY_WEATHER = "sb_weather";
    static final String KEY_STANDBY_PUPPY = "sb_puppy";
    static final String KEY_STANDBY_QUOTE = "sb_quote";
    static final String KEY_STANDBY_ORDER = "sb_order";
    /** City name typed by the user; the only reason the weather request is ever made. */
    static final String KEY_CITY = "weather_city";

    private Prefs() {
    }

    private static SharedPreferences store(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean get(Context context, String key) {
        try {
            return store(context).getBoolean(key, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    static void set(Context context, String key, boolean value) {
        try {
            store(context).edit().putBoolean(key, value).apply();
        } catch (Throwable ignored) {
            // A read-only profile keeps the previous value; nothing to recover from.
        }
    }

    /** True when a card of this kind is allowed. */
    static boolean enabled(Context context, int kind) {
        switch (kind) {
            case IslandEvent.KIND_OTP:
                return get(context, KEY_OTP);
            case IslandEvent.KIND_PICKUP:
                return get(context, KEY_PICKUP);
            case IslandEvent.KIND_DELIVERY:
                return get(context, KEY_DELIVERY);
            case IslandEvent.KIND_TODO:
                return get(context, KEY_TODO);
            case IslandEvent.KIND_CALL:
                return get(context, KEY_CALL);
            case IslandEvent.KIND_ALARM:
                return get(context, KEY_ALARM);
            case IslandEvent.KIND_NAV:
                return get(context, KEY_NAV);
            case IslandEvent.KIND_MUSIC:
                return get(context, KEY_MUSIC);
            default:
                return get(context, KEY_GENERIC);
        }
    }

    /**
     * The alarm time the island last announced.
     *
     * <p>Kept so one alarm produces one card even when the service restarts, and so the card can be
     * withdrawn the moment the system stops reporting that alarm.
     */
    static long alarmShown(Context context) {
        try {
            return store(context).getLong(KEY_ALARM_SHOWN, 0L);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    static void setAlarmShown(Context context, long trigger) {
        try {
            store(context).edit().putLong(KEY_ALARM_SHOWN, trigger).apply();
        } catch (Throwable ignored) {
            // A read-only profile keeps the previous value; the card is still withdrawn by hand.
        }
    }

    /** The switch that owns a kind, for the settings screen. */
    static String keyOf(int kind) {
        switch (kind) {
            case IslandEvent.KIND_OTP:
                return KEY_OTP;
            case IslandEvent.KIND_PICKUP:
                return KEY_PICKUP;
            case IslandEvent.KIND_DELIVERY:
                return KEY_DELIVERY;
            case IslandEvent.KIND_TODO:
                return KEY_TODO;
            case IslandEvent.KIND_CALL:
                return KEY_CALL;
            case IslandEvent.KIND_ALARM:
                return KEY_ALARM;
            case IslandEvent.KIND_NAV:
                return KEY_NAV;
            case IslandEvent.KIND_MUSIC:
                return KEY_MUSIC;
            default:
                return KEY_GENERIC;
        }
    }
    /**
     * True only when the user turned this on.
     *
     * <p>Used for the two additions that change what the island looks like on the desktop (battery
     * chips, standby), so a fresh install keeps the old single-pill behaviour until asked.
     */
    static boolean opt(Context context, String key) {
        try {
            return store(context).getBoolean(key, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static String text(Context context, String key) {
        try {
            String value = store(context).getString(key, "");
            return value == null ? "" : value;
        } catch (Throwable ignored) {
            return "";
        }
    }

    static void setText(Context context, String key, String value) {
        try {
            store(context).edit().putString(key, value).apply();
        } catch (Throwable ignored) {
            // A read-only profile keeps the previous value; the standby order still renders.
        }
    }

    /** The standby order as written by the settings screen, e.g. {@code "puppy,weather,quote"}. */
    static String[] order(Context context) {
        String raw = text(context, KEY_STANDBY_ORDER);
        if (raw.isEmpty()) {
            return new String[]{"weather", "puppy", "quote"};
        }
        String[] parts = raw.split(",");
        int count = 0;
        for (String part : parts) {
            if (part != null && !part.trim().isEmpty()) {
                parts[count++] = part.trim();
            }
        }
        String[] clean = new String[count];
        System.arraycopy(parts, 0, clean, 0, count);
        return count == 0 ? new String[]{"weather", "puppy", "quote"} : clean;
    }
}
