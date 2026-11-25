package com.kreston8.islandcn;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * The little mascot on the standby pill.
 *
 * <p>A face drawn by hand instead of an image, because it has to move: it breathes, blinks every few
 * seconds and sways a few degrees either way. Drawing it costs one Canvas pass on a 24 dp square at
 * 25 frames per second, and only while the pill is actually visible; the service pauses the pill
 * whenever it is covered, so a launcher in the background costs nothing.
 *
 * <p>The animation is computed from a clock rather than from an animator chain: every frame is a
 * pure function of elapsed time, which means a dropped frame never shifts the rhythm.
 */
final class DaozaiView extends View {

    private static final long FRAME_MS = 40L;
    private static final float SWAY_DEG = 8f;
    private static final float SWAY_S = 3.2f;
    private static final float BLINK_S = 4.3f;
    private static final float BREATH_S = 2.6f;

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
        paint.setStrokeJoin(Paint.Join.ROUND);
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
        float radius = Math.min(getWidth(), getHeight()) / 2f - dp(0.5f);

        // Sway and breathe around the centre so the face stays inside the 24 dp square.
        float sway = (float) Math.sin(t * 2 * Math.PI / SWAY_S) * SWAY_DEG;
        float breath = 1f + 0.02f * (float) Math.sin(t * 2 * Math.PI / BREATH_S);
        canvas.save();
        canvas.rotate(sway, cx, cy);
        canvas.scale(breath, breath, cx, cy);

        // Head.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFF7C58C);
        canvas.drawCircle(cx, cy, radius * 0.94f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(0.8f));
        paint.setColor(0xFF4A3623);
        canvas.drawCircle(cx, cy, radius * 0.94f, paint);

        // Cheeks, drawn under the eyes so the face reads as warm rather than flat.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x33E8623C);
        canvas.drawCircle(cx - radius * 0.52f, cy + radius * 0.16f, radius * 0.17f, paint);
        canvas.drawCircle(cx + radius * 0.52f, cy + radius * 0.16f, radius * 0.17f, paint);

        // Eyes: a slow blink every few seconds, closed for roughly 90 ms.
        float phase = t % BLINK_S;
        float open = 1f;
        if (phase > BLINK_S - 0.14f) {
            float edge = Math.min(phase - (BLINK_S - 0.14f), BLINK_S - phase);
            open = Math.max(0.06f, Math.min(1f, edge / 0.045f));
        }
        paint.setColor(0xFF2C2118);
        float eyeW = radius * 0.15f;
        float eyeH = radius * 0.19f * open;
        oval.set(cx - radius * 0.36f - eyeW, cy - radius * 0.18f - eyeH,
                cx - radius * 0.36f + eyeW, cy - radius * 0.18f + eyeH);
        canvas.drawOval(oval, paint);
        oval.set(cx + radius * 0.36f - eyeW, cy - radius * 0.18f - eyeH,
                cx + radius * 0.36f + eyeW, cy - radius * 0.18f + eyeH);
        canvas.drawOval(oval, paint);

        // Smile.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.1f));
        paint.setColor(0xFF2C2118);
        oval.set(cx - radius * 0.42f, cy + radius * 0.02f,
                cx + radius * 0.42f, cy + radius * 0.62f);
        canvas.drawArc(oval, 20f, 140f, false, paint);

        canvas.restore();
    }

    private float dp(float value) {
        return value * density;
    }
}
