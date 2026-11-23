package com.ironicxsoft.bolo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

public final class Widgets {
    private Widgets() { }

    /** Big round microphone button with pulsing rings while listening. */
    public static final class MicView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private boolean listening;
        private float level;      // 0..1 live loudness
        private float phase;      // 0..1 ring animation
        private ValueAnimator anim;

        public MicView(Context c) { super(c); }

        public void setListening(boolean on) {
            listening = on;
            if (on) startAnim(); else { stopAnim(); level = 0; invalidate(); }
        }

        public void setLevel(float v) { level = level * 0.6f + v * 0.4f; }

        private void startAnim() {
            if (anim != null) return;
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(1600);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setInterpolator(new LinearInterpolator());
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) { phase = (Float) a.getAnimatedValue(); invalidate(); }
            });
            anim.start();
        }

        private void stopAnim() { if (anim != null) { anim.cancel(); anim = null; } }

        @Override protected void onDetachedFromWindow() { stopAnim(); super.onDetachedFromWindow(); }

        @Override protected void onMeasure(int w, int h) {
            int s = UI.dp(getContext(), 120);
            setMeasuredDimension(s, s);
        }

        @Override protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float base = getWidth() * 0.30f;
            int c1 = listening ? Color.parseColor("#F43F5E") : Color.parseColor("#0E9F7E");
            int c2 = listening ? Color.parseColor("#FB923C") : Color.parseColor("#2563EB");
            if (listening) {
                for (int i = 0; i < 2; i++) {
                    float t = (phase + i * 0.5f) % 1f;
                    float r = base * (1.05f + t * (0.55f + level * 0.5f));
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(Color.argb((int) (90 * (1f - t)), 244, 63, 94));
                    c.drawCircle(cx, cy, r, p);
                }
            }
            float r0 = base * (1f + (listening ? level * 0.12f : 0f));
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(40, 0, 0, 0));
            c.drawCircle(cx, cy + UI.dp(getContext(), 3), r0, p);
            p.setColor(Color.WHITE);
            p.setShader(new LinearGradient(cx - r0, cy - r0, cx + r0, cy + r0, c1, c2, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r0, p);
            p.setShader(null);
            // microphone glyph (or stop square while listening)
            p.setColor(Color.WHITE);
            if (listening) {
                float s = r0 * 0.42f;
                c.drawRoundRect(new RectF(cx - s, cy - s, cx + s, cy + s), s * 0.3f, s * 0.3f, p);
            } else {
                float w = r0 * 0.34f, h = r0 * 0.62f;
                c.drawRoundRect(new RectF(cx - w, cy - r0 * 0.5f, cx + w, cy - r0 * 0.5f + h * 1.45f), w, w, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(r0 * 0.1f);
                p.setStrokeCap(Paint.Cap.ROUND);
                path.reset();
                path.addArc(new RectF(cx - r0 * 0.5f, cy - r0 * 0.5f, cx + r0 * 0.5f, cy + r0 * 0.38f), 0, 180);
                c.drawPath(path, p);
                c.drawLine(cx, cy + r0 * 0.38f, cx, cy + r0 * 0.62f, p);
                c.drawLine(cx - r0 * 0.22f, cy + r0 * 0.62f, cx + r0 * 0.22f, cy + r0 * 0.62f, p);
                p.setStyle(Paint.Style.FILL);
            }
        }
    }

    /** Little live sound-wave bars. */
    public static final class WaveView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] bars = new float[27];
        private boolean on;
        private ValueAnimator anim;
        private float level;

        public WaveView(Context c) { super(c); }

        public void setLevel(float v) { level = v; }

        public void setOn(boolean v) {
            on = v;
            if (v && anim == null) {
                anim = ValueAnimator.ofFloat(0f, 1f);
                anim.setDuration(90);
                anim.setRepeatCount(ValueAnimator.INFINITE);
                anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override public void onAnimationUpdate(ValueAnimator a) {
                        System.arraycopy(bars, 1, bars, 0, bars.length - 1);
                        bars[bars.length - 1] = Math.max(0.06f, level * (0.6f + 0.4f * (float) Math.random()));
                        invalidate();
                    }
                });
                anim.start();
            } else if (!v) {
                if (anim != null) { anim.cancel(); anim = null; }
                for (int i = 0; i < bars.length; i++) bars[i] = 0f;
                invalidate();
            }
        }

        @Override protected void onDetachedFromWindow() { if (anim != null) { anim.cancel(); anim = null; } super.onDetachedFromWindow(); }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(View.resolveSize(UI.dp(getContext(), 200), w), UI.dp(getContext(), 36));
        }

        @Override protected void onDraw(Canvas c) {
            int n = bars.length;
            float gap = UI.dp(getContext(), 3), bw = (getWidth() - gap * (n - 1)) / n, mid = getHeight() / 2f;
            for (int i = 0; i < n; i++) {
                float h = Math.max(UI.dp(getContext(), 3), bars[i] * getHeight());
                p.setColor(on ? Color.parseColor("#0E9F7E") : Color.parseColor("#D5DEE9"));
                p.setAlpha(on ? (int) (90 + 165 * ((float) i / n)) : 255);
                float x = i * (bw + gap);
                c.drawRoundRect(new RectF(x, mid - h / 2f, x + bw, mid + h / 2f), bw / 2f, bw / 2f, p);
            }
        }
    }
}
