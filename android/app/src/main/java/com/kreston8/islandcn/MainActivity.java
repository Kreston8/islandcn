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
    private LinearLayout orderBox;

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
        switchRow(featuresCard, R.string.label_generic, Prefs.KEY_GENERIC);
        switchRow(featuresCard, R.string.label_otp, Prefs.KEY_OTP);
        switchRow(featuresCard, R.string.label_pickup, Prefs.KEY_PICKUP);
        switchRow(featuresCard, R.string.label_delivery, Prefs.KEY_DELIVERY);
        switchRow(featuresCard, R.string.label_todo, Prefs.KEY_TODO);
        switchRow(featuresCard, R.string.label_call, Prefs.KEY_CALL);
        switchRow(featuresCard, R.string.label_alarm, Prefs.KEY_ALARM);
        switchRow(featuresCard, R.string.label_nav, Prefs.KEY_NAV);
        switchRow(featuresCard, R.string.label_music, Prefs.KEY_MUSIC);
        switchRow(featuresCard, R.string.label_lyrics, Prefs.KEY_LYRICS);
        addFill(featuresCard, text(R.string.music_hint, 12f, 0xFF8A8A94, false), 12);
        addFill(featuresCard, text(R.string.lyrics_hint, 12f, 0xFF8A8A94, false), 6);
        addFill(root, featuresCard, 14);

        LinearLayout btCard = card();
        addFill(btCard, text(R.string.perm_bt_title, 16f, COLOR_PRIMARY, true), 0);
        addFill(btCard, text(R.string.perm_bt_desc, 13f, COLOR_SECONDARY, false), 6);
        btState = text(R.string.missing, 13f, COLOR_MISSING, true);
        addFill(btCard, btState, 10);
        addTop(btCard, button(R.string.grant, v -> requestBluetooth()), 12);
        switchRow(btCard, R.string.label_bt, Prefs.KEY_BT, false);
        addFill(btCard, text(R.string.bt_hint, 12f, 0xFF8A8A94, false), 8);
        addFill(root, btCard, 14);

        LinearLayout standbyCard = card();
        addFill(standbyCard, text(R.string.label_standby, 16f, COLOR_PRIMARY, true), 0);
        addFill(standbyCard, text(R.string.standby_hint, 13f, COLOR_SECONDARY, false), 6);
        switchRow(standbyCard, R.string.label_standby_enable, Prefs.KEY_STANDBY, false);
        switchRow(standbyCard, R.string.label_standby_weather, Prefs.KEY_STANDBY_WEATHER);
        switchRow(standbyCard, R.string.label_standby_puppy, Prefs.KEY_STANDBY_PUPPY);
        switchRow(standbyCard, R.string.label_standby_quote, Prefs.KEY_STANDBY_QUOTE);
        addFill(standbyCard, text(R.string.standby_order_title, 13f, COLOR_PRIMARY, true), 14);
        orderBox = new LinearLayout(this);
        orderBox.setOrientation(LinearLayout.VERTICAL);
        addFill(standbyCard, orderBox, 0);
        renderOrder();
        cityButton = button(R.string.weather_city_set, v -> askCity());
        addTop(standbyCard, cityButton, 12);
        updateCity();
        addFill(standbyCard, text(R.string.weather_city_desc, 12f, 0xFF8A8A94, false), 8);
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
            if (IslandService.isRunning() && !isStandbyKey(key)) {
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
                || Prefs.KEY_STANDBY_PUPPY.equals(key)
                || Prefs.KEY_STANDBY_QUOTE.equals(key);
    }

    /** Rebuilds the order list: one row per item with 上移 / 下移, in the stored order. */
    private void renderOrder() {
        orderBox.removeAllViews();
        String[] order = Prefs.order(this);
        for (int i = 0; i < order.length; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(text(labelOf(order[i]), 13f, COLOR_PRIMARY, false),
                    new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(smallButton(R.string.standby_move_up, v -> move(index, -1)));
            row.addView(smallButton(R.string.standby_move_down, v -> move(index, 1)));
            addFill(orderBox, row, 8);
        }
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
        renderOrder();
    }

    private int labelOf(String item) {
        if ("weather".equals(item)) {
            return R.string.label_standby_weather;
        }
        if ("puppy".equals(item)) {
            return R.string.label_standby_puppy;
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
