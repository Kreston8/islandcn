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
import android.view.animation.LinearInterpolator;
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
 * The always-on pill on the launcher: temperature, the golden-retriever head, one line of the day.
 *
 * <p>It is a second overlay window rather than a card, because it has to appear when nothing else
 * is happening and disappear the moment a real card or another app shows up; the service decides
 * that and only asks this view to rebuild its contents.
 *
 * <p>Both extras it needs are honest about their limits: the temperature comes from a public
 * endpoint and only after the user typed a city, and the dog is a bundled vector that sways a few
 * degrees either way so it reads as alive without costing a frame budget.
 */
final class StandbyView extends FrameLayout {

    private static final int H_DP = 44;
    private static final float R_DP = 22f;
    private static final long SWAY_MS = 900L;
    private static final float SWAY_DEG = 7f;
    private static final int GAP_DP = 8;

    private final LinearLayout row;
    private final float density;

    private String signature = "";
    private boolean hasContent;
    private ImageView puppy;

    /** Reverses the sway forever; a linear track keeps it calm instead of cartoonish. */
    private final Runnable sway = new Runnable() {
        @Override
        public void run() {
            if (puppy == null) {
                return;
            }
            float target = puppy.getRotation() > 0f ? -SWAY_DEG : SWAY_DEG;
            puppy.animate().rotation(target).setDuration(SWAY_MS)
                    .setInterpolator(new LinearInterpolator()).withEndAction(sway).start();
        }
    };

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

    /**
     * Rebuilds the pill from the stored order and switches.
     *
     * <p>Called on a timer, so the children are only replaced when something actually changed;
     * otherwise the dog would restart its sway four times a minute.
     *
     * @return true when at least one item is switched on and ready to draw.
     */
    boolean refresh() {
        Context context = getContext();
        String[] order = Prefs.order(context);
        String temperature = Prefs.get(context, Prefs.KEY_STANDBY_WEATHER)
                ? Weather.temperature(context, Prefs.text(context, Prefs.KEY_CITY), this::onWeather)
                : null;
        boolean puppyOn = Prefs.get(context, Prefs.KEY_STANDBY_PUPPY);
        boolean quoteOn = Prefs.get(context, Prefs.KEY_STANDBY_QUOTE);
        String quote = quoteOn ? quote(context) : "";
        String next = String.join("|", order)
                + "#" + temperature + "#" + puppyOn + "#" + quoteOn + "#" + quote;
        if (next.equals(signature)) {
            return hasContent;
        }
        signature = next;

        if (puppy != null) {
            puppy.animate().cancel();
            puppy = null;
        }
        row.removeAllViews();
        int count = 0;
        for (String item : order) {
            if ("weather".equals(item)) {
                if (temperature == null) {
                    continue;
                }
                row.addView(label(temperature, Color.WHITE, 14f, true), margin());
                count++;
            } else if ("puppy".equals(item)) {
                if (!puppyOn) {
                    continue;
                }
                puppy = new ImageView(context);
                puppy.setImageResource(R.drawable.ic_puppy);
                puppy.setScaleType(ImageView.ScaleType.FIT_CENTER);
                row.addView(puppy, fixed(dp(22), dp(22)));
                puppy.post(sway);
                count++;
            } else if ("quote".equals(item)) {
                if (!quoteOn || quote.isEmpty()) {
                    continue;
                }
                TextView line = label(quote, 0xFFB9B9C2, 11f, false);
                line.setMaxWidth(dp(190));
                line.setSingleLine(true);
                line.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(line, margin());
                count++;
            }
        }
        hasContent = count > 0;
        return hasContent;
    }

    /** Stops the animation when the pill is hidden, so a hidden window costs nothing. */
    void pause() {
        if (puppy != null) {
            puppy.animate().cancel();
        }
        // Force a rebuild on the next show, which restarts the sway from the middle.
        signature = "";
    }

    private void onWeather() {
        // The fetch finished on the main thread; the next refresh picks the value up.
        signature = "";
        refresh();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (puppy != null) {
            puppy.animate().cancel();
        }
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

    private int dp(float value) {
        return Math.round(value * density);
    }

    /**
     * Current temperature for a city name, or null while unknown.
     *
     * <p>Two requests: the name is turned into coordinates, then the forecast is read. The result is
     * kept for half an hour, which is far cheaper than polling and still looks live. Nothing is ever
     * requested without a city, so leaving the field empty keeps the app completely offline.
     */
    static final class Weather {

        private static final long TTL_MS = 30 * 60_000L;
        private static final int TIMEOUT_MS = 6000;
        private static final int MAX_BYTES = 64 * 1024;

        private static String city = "";
        private static String text;
        private static long at;
        private static boolean busy;

        private Weather() {
        }

        static String temperature(Context context, String name, Runnable done) {
            if (name == null || name.trim().isEmpty()) {
                return null;
            }
            String wanted = name.trim();
            if (!wanted.equals(city)) {
                // A different city invalidates the old reading instead of showing it for a while.
                city = wanted;
                text = null;
                at = 0L;
            }
            if (text != null && System.currentTimeMillis() - at < TTL_MS) {
                return text;
            }
            fetch(context, wanted, done);
            return text;
        }

        private static void fetch(final Context context, final String wanted, final Runnable done) {
            if (busy) {
                return;
            }
            busy = true;
            final Context app = context.getApplicationContext();
            Thread worker = new Thread(() -> {
                String value = null;
                try {
                    value = request(wanted);
                } catch (Throwable ignored) {
                    // Offline, blocked, or the endpoint changed shape: the item just stays hidden.
                }
                final String result = value;
                busy = false;
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (result != null && wanted.equals(city)) {
                        text = result;
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

        private static String request(String name) {
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
            JSONObject forecast;
            try {
                forecast = new JSONObject(read(
                        "https://api.open-meteo.com/v1/forecast?current=temperature_2m&latitude="
                                + latitude + "&longitude=" + longitude));
            } catch (Exception failure) {
                return null;
            }
            JSONObject current = forecast.optJSONObject("current");
            if (current == null || !current.has("temperature_2m")) {
                return null;
            }
            return Math.round(current.optDouble("temperature_2m", 0d)) + "\u00b0";
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
     * <p>Without {@code PACKAGE_USAGE_STATS} the answer is unknown, which is reported as false: the
     * standby pill then never appears, which is better than appearing over a video.
     */
    static boolean homeInFront(Context context) {
        try {
            UsageStatsManager manager =
                    (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (manager == null) {
                return false;
            }
            Set<String> homes = homePackages(context);
            if (homes.isEmpty()) {
                return false;
            }
            long now = System.currentTimeMillis();
            UsageEvents events = manager.queryEvents(now - 8000L, now);
            if (events == null) {
                return false;
            }
            UsageEvents.Event event = new UsageEvents.Event();
            String last = null;
            while (events.getNextEvent(event)) {
                if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                    last = event.getPackageName();
                }
            }
            return last != null && homes.contains(last);
        } catch (Throwable ignored) {
            return false;
        }
    }

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
