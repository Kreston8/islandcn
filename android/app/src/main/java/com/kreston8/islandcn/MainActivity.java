package com.kreston8.islandcn;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
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
    private TextView serviceState;
    private Button startStop;

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

        LinearLayout serviceCard = card();
        addFill(serviceCard, text(R.string.state_title, 16f, COLOR_PRIMARY, true), 0);
        serviceState = text(R.string.service_off, 13f, COLOR_IDLE, true);
        addFill(serviceCard, serviceState, 8);
        startStop = button(R.string.start, v -> toggleService());
        addTop(serviceCard, startStop, 12);
        addTop(serviceCard, button(R.string.preview, v -> preview()), 8);
        addFill(root, serviceCard, 14);

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
        if (Perms.canOverlay(this)) {
            startForegroundService(new Intent(this, IslandService.class));
        }
        startStop.postDelayed(this::refreshState, 800);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshState();
        if (requestCode == 3 && IslandService.isRunning()) {
            // The call watcher only registers when the service starts, so pick up the new grant.
            restartService();
        }
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
