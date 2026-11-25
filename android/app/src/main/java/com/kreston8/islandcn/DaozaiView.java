package com.kreston8.islandcn;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * The two eyes on the standby pill.
 *
 * <p>Deliberately nothing else: no head, no cheeks, no mouth and no colour. The pill underneath is
 * black, so any painted "face" would either be invisible or turn the pill into a coloured blob. Two
 * light eyes read the same way as the assistant mascot they are modelled on, and they cost one
 * Canvas pass on a 24 dp square at 25 frames per second, only while the pill is actually visible.
 *
 * <p>Two moods are drawn: wide round eyes and smiling crescent eyes. The mood is picked by hashing
 * the current six second slot, and a blink happens once per three second slot with a random offset;
 * both are pure functions of elapsed time, so a dropped frame never shifts the rhythm and no state
 * (and no timer beyond the frame callback) is needed.
 */
final class DaozaiView extends View {

    private static final long FRAME_MS = 40L;
    /** Seconds per mood slot; the hash decides which mood, so it sometimes holds for two slots. */
    private static final float MOOD_S = 6f;
    /** Seconds per blink slot; roughly one blink per slot, some slots are skipped. */
    private static final float BLINK_S = 3.4f;
    private static final float BLINK_LEN = 0.20f;
    private static final int EYE_COLOR = 0xFFE9E9F0;

    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private long started;
    private boolean running;

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            invalidate();
            postDelayed(this, FRAME_MS);
        }
    };

    DaozaiView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    /** Starts or stops the frame loop; called when the pill is shown or covered. */
    void animate(boolean value) {
        if (value == running) {
            return;
        }
        running = value;
        removeCallbacks(frame);
        if (value) {
            if (started == 0L) {
                started = android.os.SystemClock.elapsedRealtime();
            }
            post(frame);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isShown()) {
            animate(true);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        animate(false);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        animate(visibility == View.VISIBLE && getWindowToken() != null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (started == 0L) {
            started = android.os.SystemClock.elapsedRealtime();
        }
        float t = (android.os.SystemClock.elapsedRealtime() - started) / 1000f;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float size = Math.min(getWidth(), getHeight());

        int slot = (int) Math.floor(t / MOOD_S);
        boolean smile = (hash(slot * 2654435761L) & 1) == 1;
        float open = openAt(t);

        float gap = size * 0.185f;
        paint.setColor(EYE_COLOR);
        if (smile) {
            drawSmileEye(canvas, cx - gap, cy, size, open);
            drawSmileEye(canvas, cx + gap, cy, size, open);
        } else {
            drawRoundEye(canvas, cx - gap, cy, size, open);
            drawRoundEye(canvas, cx + gap, cy, size, open);
        }
    }

    /** Wide eye: a rounded pillar that squashes almost flat while blinking. */
    private void drawRoundEye(Canvas canvas, float x, float y, float size, float open) {
        float halfW = size * 0.070f;
        float halfH = size * 0.125f * open + halfW * (1f - open);
        oval.set(x - halfW, y - halfH, x + halfW, y + halfH);
        canvas.drawRoundRect(oval, halfW, halfW, paint);
    }

    /** Smiling eye: the top of a circle, flattened into a line while blinking. */
    private void drawSmileEye(Canvas canvas, float x, float y, float size, float open) {
        float stroke = size * 0.085f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        float halfW = size * 0.135f;
        float halfH = size * 0.105f * Math.max(0.08f, open);
        oval.set(x - halfW, y - halfH, x + halfW, y + halfH);
        canvas.drawArc(oval, 20f, 140f, false, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    /**
     * 1 while the eyes are open, tapering to near 0 for one blink.
     *
     * <p>Each slot gets a hashed offset and a cheap gate, so blinks do not land on a metronome.
     */
    private float openAt(float t) {
        int slot = (int) Math.floor(t / BLINK_S);
        long h = hash(slot * 2654435761L + 991L);
        if ((h & 3L) == 0L) {
            return 1f;
        }
        float offset = ((h >>> 8) & 1023L) / 1023f * (BLINK_S - BLINK_LEN - 0.2f);
        float local = t - slot * BLINK_S - offset;
        if (local <= 0f || local >= BLINK_LEN) {
            return 1f;
        }
        float p = local / BLINK_LEN;
        float close = 1f - Math.abs(p * 2f - 1f);
        return Math.max(0.06f, 1f - 0.94f * close);
    }

    /** Cheap integer mix; enough randomness for two moods and a blink. */
    private static long hash(long value) {
        long x = value ^ (value >>> 33);
        x *= 0xff51afd7ed558ccdL;
        x ^= x >>> 33;
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= x >>> 33;
        return x;
    }

    private float dp(float value) {
        return value * density;
    }
}
