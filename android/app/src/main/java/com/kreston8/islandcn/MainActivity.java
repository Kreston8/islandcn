package com.kreston8.islandcn;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Gravity;
import android.widget.Switch;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.function.Consumer;

/** Setup screen: the two permissions, the on/off switch, and a preview button. */
public final class MainActivity extends Activity {

    private static final int COLOR_BG = 0xFF0A0A0C;
    private static final int COLOR_CARD = 0xFF15151A;
    private static final int COLOR_PRIMARY = 0xFFFFFFFF;
    private static final int COLOR_SECONDARY = 0xFF9A9AA4;
    private static final int COLOR_OK = 0xFF34C759;
    private static final int COLOR_MISSING = 0xFFFF9F0A;
    private static final int COLOR_IDLE = 0xFF7C7C86;

    private TextView overlayState;
    private TextView accessState;
    private TextView smsState;
    private TextView phoneState;
    private TextView usageState;
    private TextView serviceState;
    private Button startStop;
    private TextView btState;
    private Button cityButton;
    private LinearLayout standbyRows;
    private TextView weatherState;
    private TextView cityHint;
    /** Every first-level switch, so resume can re-read them and rebuild the open second level. */
    private final java.util.List<Primary> primaries = new java.util.ArrayList<Primary>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(32), dp(20), dp(36));
        scroll.addView(root);
        setContentView(scroll);

        root.addView(text(R.string.setup_title, 26f, COLOR_PRIMARY, true));

        TextView subtitle = text(R.string.setup_subtitle, 13f, COLOR_SECONDARY, false);
        subtitle.setLineSpacing(dp(3), 1f);
        addFill(root, subtitle, 8);

        LinearLayout overlayCard = card();
        addFill(overlayCard, text(R.string.perm_overlay_title, 16f, COLOR_PRIMARY, true), 0);
        addFill(overlayCard, text(R.string.perm_overlay_desc, 13f, COLOR_SECONDARY, false), 6);
        overlayState = text(R.string.missing, 13f, COLOR_MISSING, true);
        addFill(overlayCard, overlayState, 10);
        addTop(overlayCard, button(R.string.grant, v -> Perms.openOverlaySettings(this)), 12);
        addFill(root, overlayCard, 24);

        LinearLayout accessCard = card();
        addFill(accessCard, text(R.string.perm_access_title, 16f, COLOR_PRIMARY, true), 0);
        addFill(accessCard, text(R.string.perm_access_desc, 13f, COLOR_SECONDARY, false), 6);
        accessState = text(R.string.missing, 13f, COLOR_MISSING, true);
        addFill(accessCard, accessState, 10);
        addTop(accessCard, button(R.string.grant, v -> Perms.openNotificationAccessSettings(this)), 12);
        addFill(root, accessCard, 14);

        LinearLayout smsCard = card();
        addFill(smsCard, text(R.string.perm_sms_title, 16f, COLOR_PRIMARY, true), 0);
        addFill(smsCard, text(R.string.perm_sms_desc, 13f, COLOR_SECONDARY, false), 6);
        smsState = text(R.string.missing, 13f, COLOR_MISSING, true);
        addFill(smsCard, smsState, 10);
        addTop(smsCard, button(R.string.grant, v -> requestSms()), 12);
        addFill(root, smsCard, 14);

        LinearLayout phoneCard = card();
        addFill(phoneCard, text(R.string.perm_phone_title, 16f, COLOR_PRIMARY, true), 0);
        addFill(phoneCard, text(R.string.perm_phone_desc, 13f, COLOR_SECONDARY, false), 6);
        phoneState = text(R.string.missing, 13f, COLOR_MISSING, true);
        addFill(phoneCard, phoneState, 10);
        addTop(phoneCard, button(R.string.grant, v -> requestPhone()), 12);
        addFill(root, phoneCard, 14);

        LinearLayout usageCard = card();
        addFill(usageCard, text(R.string.perm_usage_title, 16f, COLOR_PRIMARY, true), 0);
        addFill(usageCard, text(R.string.perm_usage_desc, 13f, COLOR_SECONDARY, false), 6);
        usageState = text(R.string.missing, 13f, COLOR_MISSING, true);
        addFill(usageCard, usageState, 10);
        addTop(usageCard, button(R.string.grant, v -> Perms.openUsageAccessSettings(this)), 12);
        addFill(root, usageCard, 14);

        LinearLayout serviceCard = card();
        addFill(serviceCard, text(R.string.state_title, 16f, COLOR_PRIMARY, true), 0);
        serviceState = text(R.string.service_off, 13f, COLOR_IDLE, true);
        addFill(serviceCard, serviceState, 8);
        startStop = button(R.string.start, v -> toggleService());
        addTop(serviceCard, startStop, 12);
        addTop(serviceCard, button(R.string.preview, v -> preview()), 8);
        addFill(root, serviceCard, 14);

        LinearLayout featuresCard = card();
        addFill(featuresCard, text(R.string.settings_title, 16f, COLOR_PRIMARY, true), 0);
        addFill(featuresCard, text(R.string.settings_desc, 13f, COLOR_SECONDARY, false), 6);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        addFill(featuresCard, rows, 0);
        primaryRow(rows, R.string.label_notify_wechat, Prefs.KEY_NOTIFY_WECHAT, true, null);
        primaryRow(rows, R.string.label_qq, Prefs.KEY_NOTIFY_QQ, true, null);
        primaryRow(rows, R.string.label_notify_pickup, Prefs.KEY_NOTIFY_PICKUP, true, null);
        primaryRow(rows, R.string.label_delivery, Prefs.KEY_DELIVERY, true, this::deliveryRows);
        primaryRow(rows, R.string.label_todo, Prefs.KEY_TODO, true, null);
        primaryRow(rows, R.string.label_call, Prefs.KEY_CALL, true, null);
        primaryRow(rows, R.string.label_alarm, Prefs.KEY_ALARM, true, null);
        primaryRow(rows, R.string.label_music, Prefs.KEY_MUSIC, true,
                box -> subRow(box, getString(R.string.label_lyrics), Prefs.KEY_LYRICS));
        primaryRow(rows, R.string.label_bt, Prefs.KEY_BT, true, this::bluetoothRows);
        addFill(featuresCard, text(R.string.music_hint, 12f, 0xFF8A8A94, false), 12);
        addFill(featuresCard, text(R.string.lyrics_hint, 12f, 0xFF8A8A94, false), 6);
        addFill(root, featuresCard, 14);

        LinearLayout btCard = card();
        addFill(btCard, text(R.string.perm_bt_title, 16f, COLOR_PRIMARY, true), 0);
        addFill(btCard, text(R.string.perm_bt_desc, 13f, COLOR_SECONDARY, false), 6);
        btState = text(R.string.missing, 13f, COLOR_MISSING, true);
        addFill(btCard, btState, 10);
        addTop(btCard, button(R.string.grant, v -> requestBluetooth()), 12);
        addFill(btCard, text(R.string.bt_hint, 12f, 0xFF8A8A94, false), 8);
        addFill(root, btCard, 14);

        LinearLayout standbyCard = card();
        addFill(standbyCard, text(R.string.label_standby, 16f, COLOR_PRIMARY, true), 0);
        addFill(standbyCard, text(R.string.standby_hint, 13f, COLOR_SECONDARY, false), 6);
        switchRow(standbyCard, R.string.label_standby_enable, Prefs.KEY_STANDBY, false);
        addFill(standbyCard, text(R.string.standby_order_title, 13f, COLOR_PRIMARY, true), 14);
        standbyRows = new LinearLayout(this);
        standbyRows.setOrientation(LinearLayout.VERTICAL);
        addFill(standbyCard, standbyRows, 0);
        renderStandbyRows();
        cityButton = button(R.string.weather_city_set, v -> askCity());
        addTop(standbyCard, cityButton, 12);
        cityHint = text(R.string.weather_city_hint, 12f, 0xFF8A8A94, false);
        cityHint.setLineSpacing(dp(3), 1f);
        addFill(standbyCard, cityHint, 6);
        weatherState = text(R.string.weather_state_wait, 12f, 0xFF8A8A94, false);
        weatherState.setLineSpacing(dp(3), 1f);
        addFill(standbyCard, weatherState, 8);
        addTop(standbyCard, button(R.string.weather_test, v -> testWeather()), 10);
        updateCity();
        addFill(standbyCard, text(R.string.weather_city_desc, 12f, 0xFF8A8A94, false), 10);
        addFill(standbyCard, text(R.string.standby_need_usage, 12f, 0xFF8A8A94, false), 6);
        addFill(root, standbyCard, 14);

        TextView miui = text(R.string.miui_hint, 12f, 0xFF8A8A94, false);
        miui.setLineSpacing(dp(3), 1f);
        addFill(root, miui, 22);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        refreshState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshState();
        syncPrimaries();
    }

    private void refreshState() {
        boolean overlay = Perms.canOverlay(this);
        overlayState.setText(overlay ? R.string.granted : R.string.missing);
        overlayState.setTextColor(overlay ? COLOR_OK : COLOR_MISSING);

        boolean access = Perms.hasNotificationAccess(this);
        accessState.setText(access ? R.string.granted : R.string.missing);
        accessState.setTextColor(access ? COLOR_OK : COLOR_MISSING);

        boolean sms = canReceiveSms();
        smsState.setText(sms ? R.string.granted : R.string.missing);
        smsState.setTextColor(sms ? COLOR_OK : COLOR_MISSING);

        boolean phone = canWatchCalls();
        phoneState.setText(phone ? R.string.granted : R.string.missing);
        phoneState.setTextColor(phone ? COLOR_OK : COLOR_MISSING);

        boolean usage = Perms.hasUsageAccess(this);
        usageState.setText(usage ? R.string.granted : R.string.missing);
        usageState.setTextColor(usage ? COLOR_OK : COLOR_MISSING);

        boolean bluetooth = canReadBluetooth();
        btState.setText(bluetooth ? R.string.granted : R.string.missing);
        btState.setTextColor(bluetooth ? COLOR_OK : COLOR_MISSING);

        boolean running = IslandService.isRunning();
        serviceState.setText(running ? R.string.service_on : R.string.service_off);
        serviceState.setTextColor(running ? COLOR_OK : COLOR_IDLE);
        startStop.setText(running ? R.string.stop_island : R.string.start);
        refreshWeatherState();
    }

    private void toggleService() {
        if (IslandService.isRunning()) {
            stopService(new Intent(this, IslandService.class));
            startStop.postDelayed(this::refreshState, 300);
            return;
        }
        if (!Perms.canOverlay(this)) {
            toast(R.string.need_overlay);
            Perms.openOverlaySettings(this);
            return;
        }
        startForegroundService(new Intent(this, IslandService.class));
        startStop.postDelayed(this::refreshState, 700);
    }

    private void preview() {
        if (!IslandService.isRunning()) {
            toast(R.string.preview_hint);
            return;
        }
        IslandBridge.post(IslandEvent.preview(this));
    }

    private boolean canReceiveSms() {
        return checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestSms() {
        if (canReceiveSms()) {
            toast(R.string.granted);
            return;
        }
        requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, 2);
    }

    private boolean canWatchCalls() {
        return checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPhone() {
        if (canWatchCalls()) {
            toast(R.string.granted);
            return;
        }
        requestPermissions(new String[]{
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG}, 3);
    }

    private void restartService() {
        stopService(new Intent(this, IslandService.class));
        if (!Perms.canOverlay(this)) {
            return;
        }
        // The stop is asynchronous; starting right away can land on the dying instance.
        startStop.postDelayed(() -> {
            try {
                startForegroundService(new Intent(this, IslandService.class));
            } catch (Throwable ignored) {
                // The screen went away while waiting; the next launch starts it again.
            }
        }, 400);
        startStop.postDelayed(this::refreshState, 1200);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshState();
        if (requestCode == 3 && IslandService.isRunning()) {
            // The call watcher only registers when the service starts, so pick up the new grant.
            restartService();
        }
        if (requestCode == 4 && IslandService.isRunning()) {
            // The battery receiver registers with the service, so the new grant needs a restart.
            restartService();
        }
    }

    /** One settings row: label on the left, toggle on the right. */
    private void switchRow(LinearLayout parent, int labelRes, String key) {
        switchRow(parent, labelRes, key, true);
    }

    /**
     * @param defaultOn false for the two additions that change the shape of the island, so an
     *                  existing install keeps the layout it already knows until asked otherwise.
     */
    private void switchRow(LinearLayout parent, int labelRes, String key, boolean defaultOn) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text(labelRes, 14f, COLOR_PRIMARY, false), new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Switch toggle = new Switch(this);
        toggle.setChecked(defaultOn ? Prefs.get(this, key) : Prefs.opt(this, key));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            Prefs.set(this, key, checked);
            if (IslandService.isRunning() && !isLiveKey(key)) {
                // These watchers only register at start-up, so they need to be re-read.
                restartService();
            }
        });
        row.addView(toggle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        addFill(parent, row, 10);
    }

    /** The standby switches are read on every timer tick, so toggling one needs no restart. */
    private boolean isStandbyKey(String key) {
        return Prefs.KEY_STANDBY.equals(key)
                || Prefs.KEY_STANDBY_WEATHER.equals(key)
                || Prefs.KEY_STANDBY_AQI.equals(key)
                || Prefs.KEY_STANDBY_PUPPY.equals(key)
                || Prefs.KEY_STANDBY_QUOTE.equals(key)
                || Prefs.KEY_STANDBY_BT.equals(key);
    }

    /**
     * Switches read while the service runs, so flipping one needs no restart: the source filters are
     * consulted on every notification, the platform filter on every delivery card, and the battery
     * chips are rebuilt each time the pill is drawn.
     */
    private boolean isLiveKey(String key) {
        return isStandbyKey(key)
                || Prefs.KEY_NOTIFY_WECHAT.equals(key)
                || Prefs.KEY_NOTIFY_QQ.equals(key)
                || Prefs.KEY_NOTIFY_PICKUP.equals(key)
                || Prefs.KEY_DELIVERY.equals(key)
                || Prefs.KEY_DELIVERY_MEITUAN.equals(key)
                || Prefs.KEY_DELIVERY_SHANGOU.equals(key)
                || Prefs.KEY_DELIVERY_JD.equals(key)
                || Prefs.KEY_DELIVERY_QIXIAN.equals(key)
                || Prefs.KEY_BT.equals(key)
                || key.startsWith(Prefs.KEY_BT_DEVICE);
    }

    /** A first-level switch plus the second-level rows it owns. */
    private final class Primary {
        private final String key;
        private final boolean defaultOn;
        private final LinearLayout subs;
        private final Consumer<LinearLayout> fill;

        Primary(String key, boolean defaultOn, LinearLayout subs, Consumer<LinearLayout> fill) {
            this.key = key;
            this.defaultOn = defaultOn;
            this.subs = subs;
            this.fill = fill;
        }

        boolean on() {
            return defaultOn ? Prefs.get(MainActivity.this, key) : Prefs.opt(MainActivity.this, key);
        }

        /** Collapses or rebuilds the second level, so a closed parent never shows stale rows. */
        void sync() {
            boolean on = on();
            subs.removeAllViews();
            subs.setVisibility(on ? View.VISIBLE : View.GONE);
            if (on && fill != null) {
                fill.accept(subs);
            }
        }
    }

    /**
     * One first-level row: the switch that owns a whole feature, then, while it is on, the
     * second-level switches underneath it. Closed parents fold away on their own.
     */
    private Primary primaryRow(LinearLayout parent, int labelRes, String key, boolean defaultOn,
                               Consumer<LinearLayout> fill) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text(labelRes, 15f, COLOR_PRIMARY, false), new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout subs = new LinearLayout(this);
        subs.setOrientation(LinearLayout.VERTICAL);
        subs.setPadding(dp(6), 0, 0, 0);
        Primary primary = new Primary(key, defaultOn, subs, fill);
        Switch toggle = new Switch(this);
        toggle.setChecked(primary.on());
        toggle.setOnCheckedChangeListener((button, checked) -> {
            Prefs.set(this, key, checked);
            primary.sync();
            if (IslandService.isRunning() && !isLiveKey(key)) {
                // These watchers only register at start-up, so they need to be re-read.
                restartService();
            }
        });
        row.addView(toggle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        addFill(parent, row, 12);
        addFill(parent, subs, 0);
        primaries.add(primary);
        primary.sync();
        return primary;
    }

    /** One second-level row: same shape as a first-level one, smaller type, indented. */
    private void subRow(LinearLayout parent, CharSequence label, String key) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(13f);
        name.setTextColor(COLOR_SECONDARY);
        row.addView(name, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Switch toggle = new Switch(this);
        toggle.setChecked(Prefs.get(this, key));
        toggle.setOnCheckedChangeListener((button, checked) -> Prefs.set(this, key, checked));
        row.addView(toggle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        addFill(parent, row, 6);
    }

    /** The four delivery platforms, each with its own switch under the 外卖进度 master. */
    private void deliveryRows(LinearLayout parent) {
        subRow(parent, getString(R.string.label_delivery_meituan), Prefs.KEY_DELIVERY_MEITUAN);
        subRow(parent, getString(R.string.label_delivery_shangou), Prefs.KEY_DELIVERY_SHANGOU);
        subRow(parent, getString(R.string.label_delivery_jd), Prefs.KEY_DELIVERY_JD);
        subRow(parent, getString(R.string.label_delivery_qixian), Prefs.KEY_DELIVERY_QIXIAN);
    }

    /**
     * One switch per connected accessory. The list is read live rather than remembered, because
     * pairings change while the screen is open and a cached list would offer devices in a drawer.
     */
    private void bluetoothRows(LinearLayout parent) {
        java.util.List<String[]> devices;
        try {
            devices = BtWatcher.connected(this);
        } catch (Throwable ignored) {
            devices = new java.util.ArrayList<String[]>();
        }
        if (devices.isEmpty()) {
            TextView none = text(R.string.bt_no_device, 12f, 0xFF8A8A94, false);
            none.setLineSpacing(dp(3), 1f);
            addFill(parent, none, 8);
            return;
        }
        for (String[] device : devices) {
            String address = device[0];
            String name = device.length > 1 && device[1] != null && !device[1].isEmpty()
                    ? device[1] : address;
            subRow(parent, name, Prefs.KEY_BT_DEVICE + address);
        }
    }

    /** Re-reads every switch and rebuilds whatever second level is open (called on resume). */
    private void syncPrimaries() {
        for (Primary primary : primaries) {
            primary.sync();
        }
    }

    /** One row per standby item: its own switch, then the two arrows, in the stored order. */
    private void renderStandbyRows() {
        if (standbyRows == null) {
            return;
        }
        standbyRows.removeAllViews();
        String[] order = Prefs.order(this);
        for (int i = 0; i < order.length; i++) {
            final int index = i;
            String item = order[i];
            String key = itemKey(item);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            Switch toggle = new Switch(this);
            toggle.setChecked(Prefs.get(this, key));
            toggle.setOnCheckedChangeListener((button, checked) -> Prefs.set(this, key, checked));
            row.addView(toggle, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            TextView label = text(labelOf(item), 13f, COLOR_PRIMARY, false);
            label.setPadding(dp(8), 0, 0, 0);
            row.addView(label, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(smallButton(R.string.standby_move_up, v -> move(index, -1)));
            row.addView(smallButton(R.string.standby_move_down, v -> move(index, 1)));
            addFill(standbyRows, row, 8);
        }
    }

    /** The preference behind one standby item, so its switch and the pill always agree. */
    private String itemKey(String item) {
        if ("weather".equals(item)) {
            return Prefs.KEY_STANDBY_WEATHER;
        }
        if ("puppy".equals(item)) {
            return Prefs.KEY_STANDBY_PUPPY;
        }
        if ("aqi".equals(item)) {
            return Prefs.KEY_STANDBY_AQI;
        }
        if ("bt".equals(item)) {
            return Prefs.KEY_STANDBY_BT;
        }
        return Prefs.KEY_STANDBY_QUOTE;
    }

    /** Swaps one item with its neighbour and stores the result as a comma list. */
    private void move(int index, int delta) {
        String[] order = Prefs.order(this);
        int target = index + delta;
        if (target < 0 || target >= order.length) {
            return;
        }
        String swap = order[index];
        order[index] = order[target];
        order[target] = swap;
        Prefs.setText(this, Prefs.KEY_STANDBY_ORDER, String.join(",", order));
        renderStandbyRows();
    }

    private int labelOf(String item) {
        if ("weather".equals(item)) {
            return R.string.label_standby_weather;
        }
        if ("puppy".equals(item)) {
            return R.string.label_standby_puppy;
        }
        if ("aqi".equals(item)) {
            return R.string.label_standby_aqi;
        }
        if ("bt".equals(item)) {
            return R.string.label_standby_bt;
        }
        return R.string.label_standby_quote;
    }

    /** City for the temperature item; typing it is what turns that one request on. */
    private void askCity() {
        EditText input = new EditText(this);
        input.setHint(R.string.weather_city_hint);
        input.setText(Prefs.text(this, Prefs.KEY_CITY));
        new AlertDialog.Builder(this)
                .setTitle(R.string.weather_city_title)
                .setMessage(R.string.weather_city_desc)
                .setView(input)
                .setPositiveButton(R.string.weather_city_set, (dialog, which) -> {
                    Prefs.setText(this, Prefs.KEY_CITY, input.getText().toString().trim());
                    updateCity();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateCity() {
        String city = Prefs.text(this, Prefs.KEY_CITY).trim();
        cityButton.setText(city.isEmpty() ? getString(R.string.weather_city_set) : city);
        refreshWeatherState();
    }

    /**
     * What the standby pill's last weather request did, taken straight from the view that made it.
     *
     * <p>Before this the only sign of a failure was a row that never appeared, which is exactly the
     * "weather does nothing" report this state is here to answer.
     */
    private void refreshWeatherState() {
        if (weatherState == null) {
            return;
        }
        weatherState.setText(StandbyView.Weather.statusText(this));
    }

    /**
     * Runs one weather request on the spot and shows the raw answer, so a misspelt city, a blocked
     * network and an empty response can be told apart instead of guessed at.
     */
    private void testWeather() {
        String city = Prefs.text(this, Prefs.KEY_CITY).trim();
        weatherState.setText(R.string.weather_testing);
        new Thread(() -> {
            String result = StandbyView.Weather.probe(this, city);
            runOnUiThread(() -> weatherState.setText(result));
        }, "weather-probe").start();
    }

    private boolean canReadBluetooth() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBluetooth() {
        if (canReadBluetooth()) {
            toast(R.string.granted);
            return;
        }
        requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 4);
    }

    private Button smallButton(int resId, View.OnClickListener listener) {
        Button button = button(resId, listener);
        button.setTextSize(11f);
        button.setPadding(dp(10), dp(4), dp(10), dp(4));
        return button;
    }
    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_LONG).show();
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackground(rounded(COLOR_CARD, 18f));
        layout.setPadding(dp(16), dp(16), dp(16), dp(16));
        return layout;
    }

    private Button button(int resId, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(resId);
        button.setAllCaps(false);
        button.setTextColor(COLOR_PRIMARY);
        button.setBackground(rounded(0xFF23232B, 12f));
        button.setPadding(dp(18), dp(8), dp(18), dp(8));
        button.setOnClickListener(listener);
        return button;
    }

    private TextView text(int resId, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(resId);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private void addFill(LinearLayout parent, View child, float topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topDp);
        parent.addView(child, params);
    }

    private void addTop(LinearLayout parent, View child, float topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topDp);
        parent.addView(child, params);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
