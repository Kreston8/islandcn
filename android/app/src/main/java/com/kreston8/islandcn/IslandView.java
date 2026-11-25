package com.kreston8.islandcn;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The island itself: a black pill that springs open into a card with the notification.
 *
 * <p>The overlay window is WRAP_CONTENT, so animating the card's layout params is what makes the
 * window grow and shrink. Content cross-fades while the corners interpolate from a pill radius to a
 * card radius, which is what sells the "dynamic island" motion without any drawing code.
 *
 * <p>The card body has three optional rows: a kind tag, a copyable code and a delivery countdown.
 * They are what turn a plain notification into a verification-code or delivery card.
 */
public final class IslandView extends FrameLayout {

    /** What the island asks the service to do when the user touches it. */
    interface Actions {
        void onOpen();

        void onDismiss();

        void onCopy(String value);
    }

    private static final int W_COLLAPSED_DP = 78;
    private static final int H_COLLAPSED_DP = 32;
    private static final int H_EXPANDED_DP = 92;
    private static final int W_EXPANDED_MAX_DP = 360;
    private static final float R_EXPANDED_DP = 26f;
    private static final long EXPAND_MS = 420L;
    private static final long COLLAPSE_MS = 280L;
    private static final long TICK_MS = 15000L;

    private final float density;
    private final int expandedWidthPx;
    private final FrameLayout card;
    private final GradientDrawable cardBackground = new GradientDrawable();
    private final ImageView collapsedIcon;
    private final LinearLayout expandedBox;
    private final ImageView appIcon;
    private final LinearLayout headerRow;
    private final TextView tagView;
    private final TextView titleView;
    private final TextView textView;
    private final LinearLayout copyRow;
    private final TextView codeView;
    private final TextView countdownView;

    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::updateCountdown;

    private ValueAnimator animator;
    private float fraction;
    private boolean expanded;
    private Actions actions;
    private float touchDownY;
    private boolean swipedAway;
    private String copyValue;
    private long etaMillis;
    private int kind = IslandEvent.KIND_GENERIC;
    private int expandedHeightDp = H_EXPANDED_DP;

    IslandView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        expandedWidthPx = Math.min(
                dp(W_EXPANDED_MAX_DP),
                getResources().getDisplayMetrics().widthPixels - dp(24));

        card = new FrameLayout(context);
        cardBackground.setShape(GradientDrawable.RECTANGLE);
        cardBackground.setColor(Color.BLACK);
        cardBackground.setStroke(dp(1), 0xFF23232A);
        cardBackground.setCornerRadius(dp(H_COLLAPSED_DP) / 2f);
        card.setBackground(cardBackground);
        addView(card, new FrameLayout.LayoutParams(dp(W_COLLAPSED_DP), dp(H_COLLAPSED_DP)));

        collapsedIcon = new ImageView(context);
        collapsedIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(collapsedIcon, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));

        expandedBox = new LinearLayout(context);
        expandedBox.setOrientation(LinearLayout.HORIZONTAL);
        expandedBox.setGravity(Gravity.CENTER_VERTICAL);
        expandedBox.setPadding(dp(14), 0, dp(8), 0);
        expandedBox.setVisibility(View.INVISIBLE);
        card.addView(expandedBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        appIcon = new ImageView(context);
        appIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        expandedBox.addView(appIcon, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams columnParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        columnParams.leftMargin = dp(12);
        columnParams.rightMargin = dp(6);
        expandedBox.addView(column, columnParams);

        headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        column.addView(headerRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tagView = new TextView(context);
        tagView.setTextColor(0xFF7CE38B);
        tagView.setTextSize(10f);
        tagView.setTypeface(Typeface.DEFAULT_BOLD);
        tagView.setBackground(rounded(0xFF1E2A22, 6f));
        tagView.setPadding(dp(6), dp(2), dp(6), dp(2));
        tagView.setVisibility(View.GONE);
        LinearLayout.LayoutParams tagParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tagParams.rightMargin = dp(6);
        headerRow.addView(tagView, tagParams);

        titleView = new TextView(context);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(14f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        headerRow.addView(titleView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        textView = new TextView(context);
        textView.setTextColor(0xFFB9B9C2);
        textView.setTextSize(12f);
        textView.setMaxLines(2);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = dp(2);
        column.addView(textView, textParams);

        copyRow = new LinearLayout(context);
        copyRow.setOrientation(LinearLayout.HORIZONTAL);
        copyRow.setGravity(Gravity.CENTER_VERTICAL);
        copyRow.setVisibility(View.GONE);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        copyParams.topMargin = dp(5);
        column.addView(copyRow, copyParams);

        codeView = new TextView(context);
        codeView.setTextColor(0xFF7CE38B);
        codeView.setTextSize(15f);
        codeView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        codeView.setLetterSpacing(0.14f);
        codeView.setBackground(rounded(0xFF1E2A22, 8f));
        codeView.setPadding(dp(9), dp(2), dp(9), dp(2));
        copyRow.addView(codeView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView copyHint = new TextView(context);
        copyHint.setText(R.string.copy_hint);
        copyHint.setTextColor(0xFF8A8A94);
        copyHint.setTextSize(10f);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.leftMargin = dp(8);
        copyRow.addView(copyHint, hintParams);
        copyRow.setOnClickListener(v -> {
            if (actions != null && copyValue != null) {
                actions.onCopy(copyValue);
            }
        });

        countdownView = new TextView(context);
        countdownView.setTextColor(0xFFF0B860);
        countdownView.setTextSize(11f);
        countdownView.setVisibility(View.GONE);
        LinearLayout.LayoutParams countdownParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countdownParams.topMargin = dp(3);
        column.addView(countdownView, countdownParams);

        TextView close = new TextView(context);
        close.setText("\u00d7");
        close.setTextColor(0xFF8A8A94);
        close.setTextSize(18f);
        close.setPadding(dp(10), dp(10), dp(10), dp(10));
        close.setOnClickListener(v -> {
            if (actions != null) {
                actions.onDismiss();
            }
        });
        expandedBox.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.setOnClickListener(v -> {
            if (swipedAway) {
                swipedAway = false;
                return;
            }
            if (expanded) {
                if (actions != null) {
                    actions.onOpen();
                }
            } else {
                setExpanded(true, true);
            }
        });
        card.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                touchDownY = event.getY();
                swipedAway = false;
            } else if (event.getAction() == MotionEvent.ACTION_UP && touchDownY - event.getY() > dp(34)) {
                swipedAway = true;
                if (actions != null) {
                    actions.onDismiss();
                }
            }
            return false;
        });

        applyFraction(0f);
    }

    void setActions(Actions actions) {
        this.actions = actions;
    }

    void bind(IslandEvent event) {
        if (event == null) {
            return;
        }
        if (event.icon != null) {
            appIcon.setImageBitmap(event.icon);
            collapsedIcon.setImageBitmap(event.icon);
        }
        CharSequence heading = event.title == null || event.title.length() == 0
                ? event.appLabel
                : event.title;
        titleView.setText(heading);

        int tagRes = tagOf(event.kind);
        if (tagRes != 0) {
            tagView.setText(tagRes);
            tagView.setVisibility(View.VISIBLE);
        } else {
            tagView.setVisibility(View.GONE);
        }

        if (event.text == null || event.text.length() == 0) {
            textView.setVisibility(View.GONE);
        } else {
            textView.setVisibility(View.VISIBLE);
            textView.setText(event.text);
        }

        copyValue = event.copyText;
        if (copyValue == null || copyValue.isEmpty()) {
            copyRow.setVisibility(View.GONE);
        } else {
            codeView.setText(copyValue);
            copyRow.setVisibility(View.VISIBLE);
        }
        // The code row needs the vertical space, so the body shrinks to one line.
        textView.setMaxLines(copyRow.getVisibility() == View.VISIBLE ? 1 : 2);

        etaMillis = event.etaMillis;
        kind = event.kind;
        ticker.removeCallbacks(tick);
        if (etaMillis > System.currentTimeMillis()) {
            countdownView.setVisibility(View.VISIBLE);
            updateCountdown();
        } else {
            countdownView.setVisibility(View.GONE);
        }

        expandedHeightDp = H_EXPANDED_DP
                + (copyRow.getVisibility() == View.VISIBLE ? 26 : 0)
                + (countdownView.getVisibility() == View.VISIBLE ? 18 : 0);
        applyFraction(fraction);

        setContentDescription(event.appLabel);
    }

    boolean isExpanded() {
        return expanded;
    }

    void stopTicker() {
        ticker.removeCallbacks(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        stopTicker();
        super.onDetachedFromWindow();
    }

    void setExpanded(boolean value, boolean animate) {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        expanded = value;
        float target = value ? 1f : 0f;
        if (!animate) {
            fraction = target;
            applyFraction(target);
            return;
        }
        animator = ValueAnimator.ofFloat(fraction, target);
        animator.setDuration(value ? EXPAND_MS : COLLAPSE_MS);
        animator.setInterpolator(value
                ? new OvershootInterpolator(1.15f)
                : new DecelerateInterpolator(1.4f));
        animator.addUpdateListener(a -> {
            fraction = (float) a.getAnimatedValue();
            applyFraction(fraction);
        });
        animator.start();
    }

    private void updateCountdown() {
        if (countdownView.getVisibility() != View.VISIBLE) {
            return;
        }
        long left = etaMillis - System.currentTimeMillis();
        boolean alarm = kind == IslandEvent.KIND_ALARM;
        if (left <= 0L) {
            countdownView.setText(alarm ? R.string.alarm_ringing : R.string.eta_arriving);
            ticker.removeCallbacks(tick);
            return;
        }
        long minutes = (left + 59_999L) / 60_000L;
        countdownView.setText(getContext().getString(
                alarm ? R.string.alarm_minutes_left : R.string.eta_minutes, minutes));
        ticker.removeCallbacks(tick);
        ticker.postDelayed(tick, TICK_MS);

    }

    private static int tagOf(int kind) {
        switch (kind) {
            case IslandEvent.KIND_OTP:
                return R.string.tag_otp;
            case IslandEvent.KIND_PICKUP:
                return R.string.tag_pickup;
            case IslandEvent.KIND_DELIVERY:
                return R.string.tag_delivery;
            case IslandEvent.KIND_TODO:
                return R.string.tag_todo;
            case IslandEvent.KIND_CALL:
                return R.string.tag_call;
            case IslandEvent.KIND_ALARM:
                return R.string.tag_alarm;
            case IslandEvent.KIND_NAV:
                return R.string.tag_nav;
            default:
                return 0;
        }
    }

    private void applyFraction(float value) {
        // Overshoot pushes the fraction past 1, which is fine for the bounce but not for sizes.
        float clamped = Math.max(0f, Math.min(1f, value));
        ViewGroup.LayoutParams params = card.getLayoutParams();
        params.width = Math.round(lerp(dp(W_COLLAPSED_DP), expandedWidthPx, clamped));
        params.height = Math.round(lerp(dp(H_COLLAPSED_DP), dp(expandedHeightDp), clamped));
        card.setLayoutParams(params);

        cardBackground.setCornerRadius(lerp(dp(H_COLLAPSED_DP) / 2f, dp(R_EXPANDED_DP), clamped));

        expandedBox.setAlpha(clamped);
        expandedBox.setVisibility(clamped > 0.02f ? View.VISIBLE : View.INVISIBLE);
        expandedBox.setScaleX(0.88f + 0.12f * clamped);
        expandedBox.setScaleY(0.88f + 0.12f * clamped);

        collapsedIcon.setAlpha(1f - clamped);
        collapsedIcon.setVisibility(clamped > 0.55f ? View.INVISIBLE : View.VISIBLE);
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private int dp(float value) {
        return Math.round(value * density);
    }
}
