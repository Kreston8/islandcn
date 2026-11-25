package com.kreston8.islandcn;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

/**
 * The always-on pill on the launcher: weather, air quality, the mascot and one line of the day.
 *
 * <p>It is a second overlay window rather than a card, because it has to appear when nothing else
 * is happening and disappear the moment another app shows up; the service decides that and only
 * asks this view to rebuild its contents. The service also alternates this pill with a notification
 * card, which is why visibility is never set from here.
 *
 * <p>Both online extras are honest about their limits: temperature and air quality come from public
 * endpoints and only after the user typed a city, so an empty city keeps the app offline.
 */
final class StandbyView extends FrameLayout {

    private static final int H_DP = 44;
    private static final float R_DP = 22f;
    private static final int GAP_DP = 8;

    private final LinearLayout row;
    private final float density;

    private String signature = "";
    private boolean hasContent;
    private DaozaiView face;

    StandbyView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(Color.BLACK);
        background.setStroke(dp(1), 0xFF23232A);
        background.setCornerRadius(dp(R_DP));
        setBackground(background);

        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(12), 0);
        addView(row, new LayoutParams(LayoutParams.WRAP_CONTENT, dp(H_DP)));
        setVisibility(View.INVISIBLE);
    }

    boolean hasContent() {
        return hasContent;
    }

    /**
     * Rebuilds the pill from the stored order and switches.
     *
     * <p>Called on a timer, so the children are only replaced when something actually changed;
     * otherwise the mascot would restart its blink cycle four times a minute.
     *
     * @return true when at least one item is switched on and ready to draw.
     */
    boolean refresh() {
        Context context = getContext();
        String[] order = Prefs.order(context);
        boolean weatherOn = Prefs.get(context, Prefs.KEY_STANDBY_WEATHER);
        boolean aqiOn = Prefs.get(context, Prefs.KEY_STANDBY_AQI);
        boolean faceOn = Prefs.get(context, Prefs.KEY_STANDBY_PUPPY);
        boolean quoteOn = Prefs.get(context, Prefs.KEY_STANDBY_QUOTE);

        String temperature = null;
        String air = null;
        int code = -1;
        if (weatherOn || aqiOn) {
            Weather.Reading reading =
                    Weather.snapshot(context, Prefs.text(context, Prefs.KEY_CITY), this::onWeather);
            if (reading != null) {
                temperature = reading.temp;
                code = reading.code;
                air = reading.aqi;
            }
        }
        String quote = quoteOn ? quote(context) : "";
        String next = String.join("|", order) + "#" + temperature + "#" + code + "#" + air
                + "#" + faceOn + "#" + quoteOn + "#" + quote;
        if (next.equals(signature)) {
            return hasContent;
        }
        signature = next;

        face = null;
        row.removeAllViews();
        int count = 0;
        for (String item : order) {
            if ("weather".equals(item)) {
                if (!weatherOn || temperature == null) {
                    continue;
                }
                row.addView(weatherIcon(code, temperature));
                count++;
            } else if ("aqi".equals(item)) {
                if (!aqiOn || air == null || air.isEmpty()) {
                    continue;
                }
                TextView badge = label("AQI " + air, aqiColor(air), 12.5f, true);
                row.addView(badge, margin());
                count++;
            } else if ("puppy".equals(item)) {
                if (!faceOn) {
                    continue;
                }
                face = new DaozaiView(context);
                row.addView(face, fixed(dp(24), dp(24)));
                face.animate(getVisibility() == View.VISIBLE && isShown());
                count++;
            } else if ("quote".equals(item)) {
                if (!quoteOn || quote.isEmpty()) {
                    continue;
                }
                TextView line = label(quote, 0xFFC6C6CE, 12.5f, false);
                line.setMaxWidth(dp(230));
                line.setSingleLine(true);
                line.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(line, margin());
                count++;
            }
        }
        hasContent = count > 0;
        return hasContent;
    }

    /** A weather glyph plus the degree reading, sized to sit inside the 44 dp pill. */
    private LinearLayout weatherIcon(int code, String temperature) {
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(0, 0, 0, 0);

        ImageView glyph = new ImageView(getContext());
        glyph.setImageResource(iconFor(code));
        glyph.setScaleType(ImageView.ScaleType.FIT_CENTER);
        box.addView(glyph, new LinearLayout.LayoutParams(dp(18), dp(18)));

        TextView degrees = label(temperature, Color.WHITE, 15f, true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(4);
        box.addView(degrees, params);
        return box;
    }

    /** Stops the animation when the pill is hidden, so a hidden window costs nothing. */
    void pause() {
        if (face != null) {
            face.animate(false);
        }
    }

    /** Restarts the mascot after the pill came back from being covered. */
    void resume() {
        if (face != null) {
            face.animate(true);
        }
    }

    private void onWeather() {
        // The fetch finished on the main thread; the next refresh picks the value up.
        signature = "";
        refresh();
    }

    @Override
    protected void onDetachedFromWindow() {
        pause();
        super.onDetachedFromWindow();
    }

    private TextView label(String text, int color, float size, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setIncludeFontPadding(false);
        view.setSingleLine(true);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    /** Wrap-content parameters with the gap before every item but the first. */
    private LinearLayout.LayoutParams margin() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (row.getChildCount() > 0) {
            params.leftMargin = dp(GAP_DP);
        }
        return params;
    }

    private LinearLayout.LayoutParams fixed(int width, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        if (row.getChildCount() > 0) {
            params.leftMargin = dp(GAP_DP);
        }
        return params;
    }

    /** Today's line, picked by day of year so it changes once a day and never at random. */
    private static String quote(Context context) {
        try {
            Resources resources = context.getResources();
            String[] quotes = resources.getStringArray(R.array.quotes);
            if (quotes.length == 0) {
                return "";
            }
            int day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
            return quotes[Math.floorMod(day, quotes.length)];
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** WMO weather code to a glyph: clear, cloudy, wet, frozen. */
    private static int iconFor(int code) {
        if (code >= 0 && code <= 1) {
            return R.drawable.ic_wx_sun;
        }
        if (code >= 71 && code <= 77 || code == 85 || code == 86) {
            return R.drawable.ic_wx_snow;
        }
        if (code >= 51 && code <= 67 || code >= 80 && code <= 82 || code >= 95) {
            return R.drawable.ic_wx_rain;
        }
        return R.drawable.ic_wx_cloud;
    }

    /** US AQI bands, the same green/amber/orange/red a weather app would show. */
    private static int aqiColor(String value) {
        int level;
        try {
            level = Integer.parseInt(value.trim());
        } catch (Throwable ignored) {
            return 0xFFC6C6CE;
        }
        if (level <= 50) {
            return 0xFF6FCF97;
        }
        if (level <= 100) {
            return 0xFFF2C94C;
        }
        if (level <= 150) {
            return 0xFFF2994A;
        }
        return 0xFFEB5757;
    }

    private int dp(float value) {
        return Math.round(value * density);
    }

    /**
     * Current weather and air quality for a city name, or null while unknown.
     *
     * <p>Three requests, two hosts: the name becomes coordinates, the forecast gives the temperature
     * and the weather code, and the air-quality endpoint gives the US AQI. All three are cached for
     * half an hour, which is far cheaper than polling and still looks live. Nothing is ever requested
     * without a city, so leaving the field empty keeps the app completely offline.
     */
    static final class Weather {

        private static final long TTL_MS = 30 * 60_000L;
        private static final int TIMEOUT_MS = 6000;
        private static final int MAX_BYTES = 64 * 1024;

        private static String city = "";
        private static Reading cache;
        private static long at;
        private static boolean busy;

        private Weather() {
        }

        static final class Reading {
            final String temp;
            final int code;
            final String aqi;

            Reading(String temp, int code, String aqi) {
                this.temp = temp;
                this.code = code;
                this.aqi = aqi;
            }
        }

        static Reading snapshot(Context context, String name, Runnable done) {
            if (name == null || name.trim().isEmpty()) {
                return null;
            }
            String wanted = name.trim();
            if (!wanted.equals(city)) {
                // A different city invalidates the old reading instead of showing it for a while.
                city = wanted;
                cache = null;
                at = 0L;
            }
            if (cache != null && System.currentTimeMillis() - at < TTL_MS) {
                return cache;
            }
            fetch(context, wanted, done);
            return cache;
        }

        private static void fetch(final Context context, final String wanted, final Runnable done) {
            if (busy) {
                return;
            }
            busy = true;
            Thread worker = new Thread(() -> {
                Reading value = null;
                try {
                    value = request(wanted);
                } catch (Throwable ignored) {
                    // Offline, blocked, or the endpoint changed shape: the items just stay hidden.
                }
                final Reading result = value;
                busy = false;
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (result != null && wanted.equals(city)) {
                        cache = result;
                        at = System.currentTimeMillis();
                    }
                    if (done != null) {
                        done.run();
                    }
                });
            }, "island-weather");
            worker.setDaemon(true);
            worker.start();
        }

        private static Reading request(String name) {
            JSONObject found;
            try {
                found = new JSONObject(read(
                        "https://geocoding-api.open-meteo.com/v1/search?count=1&language=zh&format=json&name="
                                + encode(name)));
            } catch (Exception failure) {
                return null;
            }
            JSONArray results = found.optJSONArray("results");
            if (results == null || results.length() == 0) {
                return null;
            }
            JSONObject place = results.optJSONObject(0);
            if (place == null) {
                return null;
            }
            double latitude = place.optDouble("latitude", Double.NaN);
            double longitude = place.optDouble("longitude", Double.NaN);
            if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
                return null;
            }

            String temperature = null;
            int code = -1;
            try {
                JSONObject forecast = new JSONObject(read(
                        "https://api.open-meteo.com/v1/forecast?current=temperature_2m,weather_code"
                                + "&latitude=" + latitude + "&longitude=" + longitude));
                JSONObject current = forecast.optJSONObject("current");
                if (current != null && current.has("temperature_2m")) {
                    temperature = Math.round(current.optDouble("temperature_2m", 0d)) + "\u00b0";
                    code = current.optInt("weather_code", -1);
                }
            } catch (Exception ignored) {
                // The temperature is optional; the air quality below can still succeed.
            }

            String air = null;
            try {
                JSONObject quality = new JSONObject(read(
                        "https://air-quality-api.open-meteo.com/v1/air-quality?current=us_aqi"
                                + "&latitude=" + latitude + "&longitude=" + longitude));
                JSONObject current = quality.optJSONObject("current");
                if (current != null && current.has("us_aqi")) {
                    air = String.valueOf(Math.round(current.optDouble("us_aqi", 0d)));
                }
            } catch (Exception ignored) {
                // Same here: a missing AQI only hides one chip.
            }

            if (temperature == null && air == null) {
                return null;
            }
            return new Reading(temperature, code, air);
        }

        private static String read(String address) throws Exception {
            HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
            try {
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setRequestProperty("Accept", "application/json");
                if (connection.getResponseCode() / 100 != 2) {
                    return "";
                }
                InputStream in = connection.getInputStream();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) > 0 && out.size() < MAX_BYTES) {
                    out.write(buffer, 0, read);
                }
                in.close();
                return out.toString("UTF-8");
            } finally {
                connection.disconnect();
            }
        }

        private static String encode(String value) {
            try {
                return URLEncoder.encode(value, "UTF-8");
            } catch (UnsupportedEncodingException ignored) {
                return value;
            }
        }
    }

    /**
     * True when the launcher is the app in front.
     *
     * <p>Usage events only say what happened, so an empty window must not be read as "another app is
     * in front": that is what used to hide the pill a few seconds after it appeared. The last answer
     * is kept and only a newer event can change it; before anything is known the answer is true,
     * because an overlay pill over the launcher is the normal state.
     *
     * <p>Without {@code PACKAGE_USAGE_STATS} the answer stays at that default, which the service
     * turns into "no pill at all" before asking here.
     */
    static boolean homeInFront(Context context) {
        try {
            UsageStatsManager manager =
                    (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (manager == null) {
                return homeState == null || homeState;
            }
            Set<String> homes = homePackages(context);
            if (homes.isEmpty()) {
                return homeState == null || homeState;
            }
            long now = System.currentTimeMillis();
            long since = homeCheckedAt > 0L ? Math.max(homeCheckedAt - 2000L, now - 60000L)
                    : now - 60000L;
            homeCheckedAt = now;
            UsageEvents events = manager.queryEvents(since, now);
            if (events != null) {
                UsageEvents.Event event = new UsageEvents.Event();
                while (events.getNextEvent(event)) {
                    if (event.getEventType() != UsageEvents.Event.ACTIVITY_RESUMED) {
                        continue;
                    }
                    String pkg = event.getPackageName();
                    if (pkg == null || pkg.equals(context.getPackageName())) {
                        continue;
                    }
                    homeState = homes.contains(pkg);
                }
            }
            return homeState == null || homeState;
        } catch (Throwable ignored) {
            return homeState == null || homeState;
        }
    }

    /** Last answer about the launcher, kept across polls so a quiet window changes nothing. */
    private static Boolean homeState;
    private static long homeCheckedAt;

    private static Set<String> homePackages(Context context) {
        Set<String> homes = new HashSet<>();
        try {
            PackageManager manager = context.getPackageManager();
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            for (ResolveInfo info : manager.queryIntentActivities(home, 0)) {
                if (info.activityInfo != null && info.activityInfo.packageName != null) {
                    homes.add(info.activityInfo.packageName);
                }
            }
        } catch (Throwable ignored) {
            // Fall through with an empty set.
        }
        return homes;
    }
}
