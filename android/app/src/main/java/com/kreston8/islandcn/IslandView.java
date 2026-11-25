package com.kreston8.islandcn;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
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
    private static final int H_EXPANDED_DP = 44;
    private static final int H_EXPANDED_DP = 46;
    private static final int W_EXPANDED_MIN_DP = 250;
    private static final float R_EXPANDED_DP = 22f;
    private static final long EXPAND_MS = 420L;
    private static final long COLLAPSE_MS = 280L;
    private static final long TICK_MS = 15000L;
    private static final long CYCLE_MS = 2600L;
    private static final int H_MUSIC_DP = 46;
    private static final int W_MUSIC_MIN_DP = 240;
    private static final long LYRIC_MS = 100L;
    private static final int BT_MAX = 3;
    /** Roughly one chip: the icon is 14 dp, the percentage under it a little wider. */
    private static final int BT_CHIP_DP = 26;

    private final float density;
    private final int minWidthPx;
    private final int maxWidthPx;
    /** Current width before the Bluetooth chips, recomputed for every card. */
    private int baseWidthPx;
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
    private final LinearLayout btRow;

    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::updateCountdown;
    private final Runnable advance = this::advanceCycle;

    private ValueAnimator animator;
    private float fraction;
    private boolean expanded;
    /** True while the card is the slim music strip, which sizes and pads differently. */
    private boolean musicMode;
    private Actions actions;
    private float touchDownY;
    private boolean swipedAway;
    private String copyValue;
    private long etaMillis;
    private int kind = IslandEvent.KIND_GENERIC;
    private int expandedHeightDp = H_EXPANDED_DP;
    private CharSequence[] cycle;
    private int cycleIndex;
    private final int musicMinPx;
    private int cardWidthPx;
    private float expandedRadiusDp = R_EXPANDED_DP;
    private final Runnable lyricTick = this::tickLyric;
    private Lyric lyric;
    private long lyricBase;
    private long lyricAt;
    private int sungLine = -1;
    private int sungChars = -1;

    IslandView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        maxWidthPx = Math.min(
                dp(W_EXPANDED_MAX_DP),
                getResources().getDisplayMetrics().widthPixels - dp(24));
        minWidthPx = Math.min(dp(W_EXPANDED_MIN_DP), maxWidthPx);
        musicMinPx = Math.min(dp(W_MUSIC_MIN_DP), maxWidthPx);
        baseWidthPx = minWidthPx;
        cardWidthPx = minWidthPx;

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
        expandedBox.setPadding(dp(10), 0, dp(6), 0);
        expandedBox.setVisibility(View.INVISIBLE);
        card.addView(expandedBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        appIcon = new ImageView(context);
        appIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        expandedBox.addView(appIcon, new LinearLayout.LayoutParams(dp(28), dp(28)));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams columnParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        columnParams.leftMargin = dp(10);
        columnParams.rightMargin = dp(4);
        expandedBox.addView(column, columnParams);

        headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        column.addView(headerRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tagView = new TextView(context);
        tagView.setTextColor(0xFF7CE38B);
        tagView.setTextSize(12f);
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
        titleView.setTextSize(15.5f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        headerRow.addView(titleView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        textView = new TextView(context);
        textView.setTextColor(0xFFB9B9C2);
        textView.setTextSize(13.5f);
        textView.setMaxLines(1);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = dp(1);
        column.addView(textView, textParams);

        copyRow = new LinearLayout(context);
        copyRow.setOrientation(LinearLayout.HORIZONTAL);
        copyRow.setGravity(Gravity.CENTER_VERTICAL);
        copyRow.setVisibility(View.GONE);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        copyParams.leftMargin = dp(6);
        // Sits in the title row: a third line would not fit the 44 dp strip.
        headerRow.addView(copyRow, copyParams);

        codeView = new TextView(context);
        codeView.setTextColor(0xFF7CE38B);
        codeView.setTextSize(14.5f);
        codeView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        codeView.setLetterSpacing(0.14f);
        codeView.setBackground(rounded(0xFF1E2A22, 8f));
        codeView.setPadding(dp(6), dp(1), dp(6), dp(1));
        copyRow.addView(codeView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView copyHint = new TextView(context);
        copyHint.setText(R.string.copy_hint);
        copyHint.setTextColor(0xFF8A8A94);
        copyHint.setTextSize(11f);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.leftMargin = dp(5);
        copyRow.addView(copyHint, hintParams);
        copyRow.setOnClickListener(v -> {
            if (actions != null && copyValue != null) {
                actions.onCopy(copyValue);
            }
        });

        countdownView = new TextView(context);
        countdownView.setTextColor(0xFFF0B860);
        countdownView.setTextSize(13f);
        countdownView.setVisibility(View.GONE);
        LinearLayout.LayoutParams countdownParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countdownParams.topMargin = dp(1);
        column.addView(countdownView, countdownParams);

        btRow = new LinearLayout(context);
        btRow.setOrientation(LinearLayout.HORIZONTAL);
        btRow.setGravity(Gravity.CENTER_VERTICAL);
        btRow.setVisibility(View.GONE);
        LinearLayout.LayoutParams btParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btParams.leftMargin = dp(4);
        // Right end of the pill, before the close button.
        expandedBox.addView(btRow, btParams);

        TextView close = new TextView(context);
        close.setText("\u00d7");
        close.setTextColor(0xFF8A8A94);
        close.setTextSize(20f);
        close.setPadding(dp(8), dp(8), dp(8), dp(8));
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
        boolean music = event.kind == IslandEvent.KIND_MUSIC;
        setMusicMode(music);
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
        // One body line only: the strip is as tall as the music card.
        textView.setMaxLines(1);

        etaMillis = event.etaMillis;
        kind = event.kind;
        startCycle(music ? null : event.cycle);
        ticker.removeCallbacks(tick);
        boolean hasEta = etaMillis > System.currentTimeMillis();
        // A 44 dp strip holds two lines, so the countdown takes the body line when it has one.
        countdownView.setVisibility(hasEta ? View.VISIBLE : View.GONE);
        textView.setVisibility(hasEta ? View.GONE : View.VISIBLE);
        if (hasEta) {
            updateCountdown();
        }

        // Every card is the same 44 dp strip: icon, one title line, one body line.
        expandedHeightDp = H_EXPANDED_DP;
        boolean karaoke =
                startLyric(event.kind == IslandEvent.KIND_MUSIC ? event.lyric : null, event.position);
        if (music && !karaoke) {
            // No timed lyric for this track, so the single line falls back to the song name.
            textView.setVisibility(View.VISIBLE);
            textView.setText(heading);
        }

        // Grow to fit the line first; only a line that still does not fit scrolls.
        fitWidth();
        renderBt(event.bt);
        applyFraction(fraction);
        applyScrolling(!karaoke);
        setContentDescription(event.appLabel);
    }

    boolean isExpanded() {
        return expanded;
    }

    void stopTicker() {
        ticker.removeCallbacks(tick);
        ticker.removeCallbacks(advance);
        ticker.removeCallbacks(lyricTick);
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
        if (value) {
            scheduleAdvance();
            scheduleLyric();
        } else {
            ticker.removeCallbacks(advance);
            ticker.removeCallbacks(lyricTick);
        }
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

    /** Starts rotating the extra lines (song, then artist) while the card is open. */
    private void startCycle(CharSequence[] lines) {
        ticker.removeCallbacks(advance);
        cycle = lines != null && lines.length > 1 ? lines : null;
        cycleIndex = 0;
        if (cycle == null) {
            return;
        }
        // The heading already shows the first line, so start on the one after it.
        if (cycle[0] != null && TextUtils.equals(cycle[0], titleView.getText())) {
            cycleIndex = 1;
            textView.setText(cycle[1]);
        }
        scheduleAdvance();
    }

    private void scheduleAdvance() {
        ticker.removeCallbacks(advance);
        if (cycle != null && expanded) {
            ticker.postDelayed(advance, CYCLE_MS);
        }
    }

    private void advanceCycle() {
        if (cycle == null || !expanded) {
            return;
        }
        cycleIndex = (cycleIndex + 1) % cycle.length;
        textView.setText(cycle[cycleIndex]);
        textView.setVisibility(View.VISIBLE);
        scheduleAdvance();
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
            case IslandEvent.KIND_MUSIC:
                return R.string.tag_music;
            default:
                return 0;
        }
    }

    /**
     * Turns the card into the slim music strip, or back into the normal card.
     *
     * <p>Music is the only kind that must stay small: the cover plus one lyric line, so it covers
     * as little of the launcher as possible.
     */
    private void setMusicMode(boolean music) {
        LinearLayout.LayoutParams icon = (LinearLayout.LayoutParams) appIcon.getLayoutParams();
        musicMode = music;
        if (music) {
            headerRow.setVisibility(View.GONE);
            tagView.setVisibility(View.GONE);
            copyRow.setVisibility(View.GONE);
            countdownView.setVisibility(View.GONE);
            textView.setTextSize(16.5f);
            textView.setMaxLines(1);
            textView.setTextColor(Color.WHITE);
            icon.width = dp(28);
            icon.height = dp(28);
            appIcon.setLayoutParams(icon);
            expandedBox.setPadding(dp(10), 0, dp(12), 0);
            baseWidthPx = musicMinPx;
            cardWidthPx = musicMinPx;
            expandedRadiusDp = H_MUSIC_DP / 2f;
        } else {
            headerRow.setVisibility(View.VISIBLE);
            textView.setTextSize(13.5f);
            textView.setMaxLines(1);
            textView.setTextColor(0xFFB9B9C2);
            icon.width = dp(28);
            icon.height = dp(28);
            appIcon.setLayoutParams(icon);
            expandedBox.setPadding(dp(10), 0, dp(6), 0);
            baseWidthPx = minWidthPx;
            cardWidthPx = minWidthPx;
            expandedRadiusDp = R_EXPANDED_DP;
        }
    }

    /**
     * Starts the karaoke line for the track.
     *
     * @return true when the track has timed lyrics, so the caller can fall back to plain text.
     */
    private boolean startLyric(Lyric lines, long position) {
        ticker.removeCallbacks(lyricTick);
        lyric = lines == null || lines.isEmpty() ? null : lines;
        sungLine = -1;
        sungChars = -1;
        if (lyric == null) {
            return false;
        }
        lyricBase = Math.max(0L, position);
        lyricAt = SystemClock.elapsedRealtime();
        paintLyric(lyricBase);
        scheduleLyric();
        return true;
    }

    private void scheduleLyric() {
        ticker.removeCallbacks(lyricTick);
        if (lyric != null && expanded) {
            ticker.postDelayed(lyricTick, LYRIC_MS);
        }
    }

    private void tickLyric() {
        if (lyric == null || !expanded) {
            return;
        }
        paintLyric(lyricBase + (SystemClock.elapsedRealtime() - lyricAt));
        scheduleLyric();
    }

    /**
     * Paints one line and lights up the characters already sung.
     *
     * <p>Timestamps are per line, so the character position is interpolated across the line. Spans
     * are only rebuilt when the character count changes, a handful of updates per second.
     */
    private void paintLyric(long position) {
        int index = lyric.indexAt(position);
        if (index < 0) {
            // Before the first stamp there is no line yet, so show the opening one, still unlit.
            if (lyric.lines.length == 0) {
                return;
            }
            index = 0;
        }
        long start = lyric.times[index];
        long end = lyric.endAt(index);
        String line = lyric.lines[index];
        float ratio = end > start ? (position - start) / (float) (end - start) : 1f;
        int lit = Math.round(line.length() * Math.max(0f, Math.min(1f, ratio)));
        if (index == sungLine && lit == sungChars) {
            return;
        }
        sungLine = index;
        sungChars = lit;
        SpannableString styled = new SpannableString(line);
        if (lit > 0) {
            styled.setSpan(new ForegroundColorSpan(Color.WHITE), 0, lit,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            styled.setSpan(new StyleSpan(Typeface.BOLD), 0, lit,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (lit < line.length()) {
            styled.setSpan(new ForegroundColorSpan(0x66FFFFFF), lit, line.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        textView.setText(styled);
    }

    private void applyFraction(float value) {
        // Overshoot pushes the fraction past 1, which is fine for the bounce but not for sizes.
        float clamped = Math.max(0f, Math.min(1f, value));
        ViewGroup.LayoutParams params = card.getLayoutParams();
        params.width = Math.round(lerp(dp(W_COLLAPSED_DP), cardWidthPx, clamped));
        params.height = Math.round(lerp(dp(H_COLLAPSED_DP), dp(expandedHeightDp), clamped));
        card.setLayoutParams(params);

        cardBackground.setCornerRadius(lerp(dp(H_COLLAPSED_DP) / 2f, dp(expandedRadiusDp), clamped));

        expandedBox.setAlpha(clamped);
        expandedBox.setVisibility(clamped > 0.02f ? View.VISIBLE : View.INVISIBLE);
        expandedBox.setScaleX(0.88f + 0.12f * clamped);
        expandedBox.setScaleY(0.88f + 0.12f * clamped);

        collapsedIcon.setAlpha(1f - clamped);
        collapsedIcon.setVisibility(clamped > 0.55f ? View.INVISIBLE : View.VISIBLE);
    }

    /**
     * Draws one chip per connected Bluetooth device, up to {@link #BT_MAX}.
     *
     * <p>Android exposes no public battery API for accessories: the percentage is whatever the
     * device last pushed through the system's own broadcast. A device that never reported one
     * keeps its icon and shows {@code --} instead of inventing a number.
     */
    private void renderBt(java.util.List<IslandEvent.Bt> devices) {
        btRow.removeAllViews();
        int count = devices == null ? 0 : Math.min(devices.size(), BT_MAX);
        if (count == 0) {
            btRow.setVisibility(View.GONE);
            cardWidthPx = baseWidthPx;
            return;
        }
        for (int i = 0; i < count; i++) {
            IslandEvent.Bt device = devices.get(i);
            if (device == null) {
                continue;
            }
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                chipParams.leftMargin = dp(5);
            }
            btRow.addView(btChip(device), chipParams);
        }
        btRow.setVisibility(View.VISIBLE);
        // The chips sit beside the text instead of eating it, so the pill grows by their width.
        cardWidthPx = Math.min(maxWidthPx, baseWidthPx + dp(BT_CHIP_DP) * count);
    }

    /**
     * Grows the pill to fit its text instead of cutting it off.
     *
     * <p>The old fixed widths are now minimums: a short message keeps the familiar size, a long one
     * widens until the line fits, and only past {@link #maxWidthPx} does the marquee take over.
     * Measuring the text views' own paints keeps the estimate honest with the applied fonts.
     */
    private void fitWidth() {
        int chrome = musicMode
                ? dp(10) + dp(28) + dp(10) + dp(12) + dp(6)
                : dp(10) + dp(28) + dp(10) + dp(4) + dp(30) + dp(10);
        int header = 0;
        if (headerRow.getVisibility() == View.VISIBLE) {
            if (tagView.getVisibility() == View.VISIBLE) {
                header += Math.round(tagView.getPaint().measureText(tagView.getText().toString()))
                        + tagView.getPaddingLeft() + tagView.getPaddingRight() + dp(6);
            }
            if (copyRow.getVisibility() == View.VISIBLE) {
                header += measure(codeView) + dp(6) + codeView.getPaddingLeft()
                        + codeView.getPaddingRight() + dp(40);
            }
        }
        int title = headerRow.getVisibility() == View.VISIBLE ? measure(titleView) : 0;
        int body = measure(textView);
        if (countdownView.getVisibility() == View.VISIBLE) {
            body = Math.max(body, measure(countdownView));
        }
        int need = chrome + Math.max(title, body + header);
        baseWidthPx = Math.max(musicMode ? musicMinPx : minWidthPx,
                Math.min(maxWidthPx, need));
        cardWidthPx = baseWidthPx;
    }

    private static int measure(TextView view) {
        CharSequence text = view.getText();
        if (text == null || text.length() == 0) {
            return 0;
        }
        return Math.round(view.getPaint().measureText(text, 0, text.length()));
    }

    /**
     * Turns marquee on for the two text lines, or off while karaoke paints them.
     *
     * <p>The lit characters are rebuilt several times a second; a marquee restarts whenever the
     * text changes, so the lyric line would jump back to the left mid-word. Lyric lines therefore
     * ellipsize instead of scrolling.
     */
    private void applyScrolling(boolean scroll) {
        TextView[] lines = {titleView, textView};
        for (TextView view : lines) {
            view.setSingleLine(true);
            if (scroll) {
                view.setEllipsize(TextUtils.TruncateAt.MARQUEE);
                view.setMarqueeRepeatLimit(-1);
                view.setSelected(true);
            } else {
                view.setEllipsize(TextUtils.TruncateAt.END);
                view.setSelected(false);
            }
        }
    }

    private View btChip(IslandEvent.Bt device) {
        LinearLayout chip = new LinearLayout(getContext());
        chip.setOrientation(LinearLayout.VERTICAL);
        chip.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView icon = new ImageView(getContext());
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImageResource(device.icon);
        chip.addView(icon, new LinearLayout.LayoutParams(dp(14), dp(14)));
        TextView percent = new TextView(getContext());
        percent.setText(device.level < 0 ? "--" : device.level + "%");
        percent.setTextColor(device.level >= 0 && device.level <= 20 ? 0xFFF0B860 : 0xFFB9B9C2);
        percent.setTextSize(10.5f);
        percent.setIncludeFontPadding(false);
        percent.setSingleLine(true);
        chip.addView(percent, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        chip.setContentDescription(device.name + " " + percent.getText());
        return chip;
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
