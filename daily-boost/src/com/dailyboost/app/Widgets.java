package com.dailyboost.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Custom-drawn views used by the dashboard. */
public final class Widgets {
    private Widgets() { }

    /** Circular countdown ring (arc drains as time passes). */
    public static final class RingView extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private float progress = 1f;   // 0..1 remaining

        public RingView(Context c) {
            super(c);
            float w = UI.dp(c, 10);
            track.setStyle(Paint.Style.STROKE); track.setStrokeWidth(w); track.setColor(Color.argb(55, 255, 255, 255));
            arc.setStyle(Paint.Style.STROKE); arc.setStrokeWidth(w); arc.setStrokeCap(Paint.Cap.ROUND); arc.setColor(Color.WHITE);
            dot.setColor(Color.WHITE);
        }

        public void setProgress(float p) { progress = Math.max(0f, Math.min(1f, p)); invalidate(); }

        @Override protected void onDraw(Canvas c) {
            float pad = track.getStrokeWidth() * 1.2f + UI.dp(getContext(), 2);
            box.set(pad, pad, getWidth() - pad, getHeight() - pad);
            c.drawArc(box, 0, 360, false, track);
            c.drawArc(box, -90, 360f * progress, false, arc);
            // glowing tip
            double ang = Math.toRadians(-90 + 360f * progress);
            float cx = box.centerX() + (box.width() / 2f) * (float) Math.cos(ang);
            float cy = box.centerY() + (box.height() / 2f) * (float) Math.sin(ang);
            dot.setColor(Color.argb(70, 255, 255, 255));
            c.drawCircle(cx, cy, arc.getStrokeWidth() * 1.1f, dot);
            dot.setColor(Color.WHITE);
            c.drawCircle(cx, cy, arc.getStrokeWidth() * 0.62f, dot);
        }
    }

    /** Row of little water glasses that fill up. */
    public static final class GlassRow extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private int goal = 8, have = 0;
        private float anim = 1f;

        public GlassRow(Context c) { super(c); }

        public void set(int have, int goal) {
            this.have = have; this.goal = Math.max(1, goal);
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(520);
            a.setInterpolator(new DecelerateInterpolator());
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { @Override public void onAnimationUpdate(ValueAnimator v) { anim = (Float) v.getAnimatedValue(); invalidate(); } });
            a.start();
        }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(View.resolveSize(UI.dp(getContext(), 100), w), UI.dp(getContext(), 54));
        }

        @Override protected void onDraw(Canvas c) {
            int n = Math.min(goal, 10);
            // if the goal is bigger than 10 we scale the filled amount
            float filledUnits = goal > 10 ? Math.min(10f, have * 10f / goal) : Math.min(have, goal);
            float gap = UI.dp(getContext(), 6);
            float cw = (getWidth() - gap * (n - 1)) / n;
            cw = Math.min(cw, UI.dp(getContext(), 30));
            float total = cw * n + gap * (n - 1);
            float x0 = (getWidth() - total) / 2f;
            float h = getHeight() - UI.dp(getContext(), 4);
            for (int i = 0; i < n; i++) {
                float x = x0 + i * (cw + gap), top = UI.dp(getContext(), 2);
                float inset = cw * 0.12f;
                path.reset();
                path.moveTo(x, top);
                path.lineTo(x + cw, top);
                path.lineTo(x + cw - inset, top + h);
                path.lineTo(x + inset, top + h);
                path.close();
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.parseColor("#E6ECF5"));
                c.drawPath(path, p);
                float fill = Math.max(0f, Math.min(1f, filledUnits - i)) * anim;
                if (fill > 0f) {
                    c.save();
                    c.clipPath(path);
                    p.setColor(UI.BLUE);
                    c.drawRect(x, top + h * (1f - fill), x + cw, top + h, p);
                    p.setColor(Color.argb(90, 255, 255, 255));
                    c.drawRect(x, top + h * (1f - fill), x + cw, top + h * (1f - fill) + UI.dp(getContext(), 2), p);
                    c.restore();
                }
            }
        }
    }

    /** Circular check box with an animated tick. */
    public static final class RoundCheck extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean on;

        public RoundCheck(Context c) { super(c); }

        public void setChecked(boolean v) { on = v; invalidate(); }

        @Override protected void onMeasure(int w, int h) { int s = UI.dp(getContext(), 28); setMeasuredDimension(s, s); }

        @Override protected void onDraw(Canvas c) {
            float r = getWidth() / 2f - UI.dp(getContext(), 2);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            if (on) {
                p.setStyle(Paint.Style.FILL); p.setColor(UI.GREEN);
                c.drawCircle(cx, cy, r, p);
                p.setStyle(Paint.Style.STROKE); p.setColor(Color.WHITE); p.setStrokeWidth(UI.dp(getContext(), 2)); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
                Path k = new Path();
                k.moveTo(cx - r * 0.42f, cy + r * 0.02f); k.lineTo(cx - r * 0.1f, cy + r * 0.36f); k.lineTo(cx + r * 0.46f, cy - r * 0.32f);
                c.drawPath(k, p);
            } else {
                p.setStyle(Paint.Style.STROKE); p.setColor(Color.parseColor("#B8C4D6")); p.setStrokeWidth(UI.dp(getContext(), 2));
                c.drawCircle(cx, cy, r, p);
            }
        }
    }

    /** Thin rounded progress bar. */
    public static final class MiniBar extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float value;
        private int color = UI.GREEN;

        public MiniBar(Context c) { super(c); }

        public void set(float v, int color) { value = Math.max(0f, Math.min(1f, v)); this.color = color; invalidate(); }

        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(View.resolveSize(UI.dp(getContext(), 100), w), UI.dp(getContext(), 8)); }

        @Override protected void onDraw(Canvas c) {
            float r = getHeight() / 2f;
            p.setColor(Color.parseColor("#E6ECF5"));
            c.drawRoundRect(new RectF(0, 0, getWidth(), getHeight()), r, r, p);
            if (value > 0f) {
                p.setColor(color);
                c.drawRoundRect(new RectF(0, 0, Math.max(getHeight(), getWidth() * value), getHeight()), r, r, p);
            }
        }
    }
}
