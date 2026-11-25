package com.kreston8.islandcn;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
 */
public final class IslandView extends FrameLayout {

    /** What the island asks the service to do when the user touches it. */
    interface Actions {
        void onOpen();

        void onDismiss();
    }

    private static final int W_COLLAPSED_DP = 78;
    private static final int H_COLLAPSED_DP = 32;
    private static final int H_EXPANDED_DP = 92;
    private static final int W_EXPANDED_MAX_DP = 360;
    private static final float R_EXPANDED_DP = 26f;
    private static final long EXPAND_MS = 420L;
    private static final long COLLAPSE_MS = 280L;

    private final float density;
    private final int expandedWidthPx;
    private final FrameLayout card;
    private final GradientDrawable cardBackground = new GradientDrawable();
    private final ImageView collapsedIcon;
    private final LinearLayout expandedBox;
    private final ImageView appIcon;
    private final TextView titleView;
    private final TextView textView;

    private ValueAnimator animator;
    private float fraction;
    private boolean expanded;
    private Actions actions;
    private float touchDownY;
    private boolean swipedAway;

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

        titleView = new TextView(context);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(14f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        column.addView(titleView);

        textView = new TextView(context);
        textView.setTextColor(0xFFB9B9C2);
        textView.setTextSize(12f);
        textView.setMaxLines(2);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = dp(2);
        column.addView(textView, textParams);

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
        if (event.text == null || event.text.length() == 0) {
            textView.setVisibility(View.GONE);
        } else {
            textView.setVisibility(View.VISIBLE);
            textView.setText(event.text);
        }
        setContentDescription(event.appLabel);
    }

    boolean isExpanded() {
        return expanded;
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

    private void applyFraction(float value) {
        // Overshoot pushes the fraction past 1, which is fine for the bounce but not for sizes.
        float clamped = Math.max(0f, Math.min(1f, value));
        ViewGroup.LayoutParams params = card.getLayoutParams();
        params.width = Math.round(lerp(dp(W_COLLAPSED_DP), expandedWidthPx, clamped));
        params.height = Math.round(lerp(dp(H_COLLAPSED_DP), dp(H_EXPANDED_DP), clamped));
        card.setLayoutParams(params);

        cardBackground.setCornerRadius(lerp(dp(H_COLLAPSED_DP) / 2f, dp(R_EXPANDED_DP), clamped));

        expandedBox.setAlpha(clamped);
        expandedBox.setVisibility(clamped > 0.02f ? View.VISIBLE : View.INVISIBLE);
        expandedBox.setScaleX(0.88f + 0.12f * clamped);
        expandedBox.setScaleY(0.88f + 0.12f * clamped);

        collapsedIcon.setAlpha(1f - clamped);
        collapsedIcon.setVisibility(clamped > 0.55f ? View.INVISIBLE : View.VISIBLE);
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private int dp(float value) {
        return Math.round(value * density);
    }
}
