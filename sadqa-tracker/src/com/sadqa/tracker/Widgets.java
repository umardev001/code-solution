package com.sadqa.tracker;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/** Custom-drawn views used by the dashboard. */
public final class Widgets {
    private Widgets() { }

    private static float sp(Context c, float v) { return v * c.getResources().getDisplayMetrics().scaledDensity; }

    /** Dial gauge: how much of the sadqa due has been given. Gold arc, glow, lit tick marks. */
    public static final class DonutView extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG),
                glow = new Paint(Paint.ANTI_ALIAS_FLAG), tick = new Paint(Paint.ANTI_ALIAS_FLAG),
                big = new Paint(Paint.ANTI_ALIAS_FLAG), small = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private final Matrix rot = new Matrix();
        private float shown = 0f, target = 0f;
        private int pct = 0;
        private String caption = "";

        public DonutView(Context c) {
            super(c);
            setLayerType(LAYER_TYPE_SOFTWARE, null);   // needed for the blur glow
            float sw = UI.dp(c, 14);
            track.setStyle(Paint.Style.STROKE); track.setStrokeWidth(sw); track.setColor(Color.argb(34, 255, 255, 255));
            arc.setStyle(Paint.Style.STROKE); arc.setStrokeWidth(sw); arc.setStrokeCap(Paint.Cap.ROUND);
            glow.setStyle(Paint.Style.STROKE); glow.setStrokeWidth(sw + UI.dp(c, 4)); glow.setStrokeCap(Paint.Cap.ROUND);
            glow.setColor(Color.argb(150, 242, 194, 91)); glow.setMaskFilter(new BlurMaskFilter(UI.dp(c, 10), BlurMaskFilter.Blur.NORMAL));
            tick.setStrokeWidth(Math.max(1f, UI.dp(c, 2) * 0.8f)); tick.setStrokeCap(Paint.Cap.ROUND);
            big.setColor(Color.WHITE); big.setTextAlign(Paint.Align.CENTER); big.setTextSize(sp(c, 40)); big.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            small.setColor(UI.MUTED); small.setTextAlign(Paint.Align.CENTER); small.setTextSize(sp(c, 12));
            dot.setColor(Color.WHITE);
        }

        /** frac 0..1 of the ring to fill, pct = number shown in the middle. */
        public void set(float frac, int pct, String caption, float from) {
            this.target = Math.max(0f, Math.min(1f, frac));
            this.pct = pct; this.caption = caption;
            shown = from;
            ValueAnimator a = ValueAnimator.ofFloat(from, target);
            a.setDuration(1100);
            a.setInterpolator(new DecelerateInterpolator(1.6f));
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator v) { shown = (Float) v.getAnimatedValue(); invalidate(); }
            });
            a.start();
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            SweepGradient g = new SweepGradient(w / 2f, h / 2f, new int[]{Color.parseColor("#E0A526"), Color.parseColor("#F8DE8F"), Color.parseColor("#FFF3C9")}, new float[]{0f, 0.8f, 1f});
            rot.setRotate(-90, w / 2f, h / 2f);
            g.setLocalMatrix(rot);
            arc.setShader(g);
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f;
            float sw = track.getStrokeWidth();
            float outer = Math.min(w, h) / 2f;
            float r = outer - UI.dp(getContext(), 20) - sw / 2f;
            box.set(cx - r, cy - r, cx + r, cy + r);

            // ticks
            int n = 60;
            float t0 = r + sw / 2f + UI.dp(getContext(), 5), t1 = t0 + UI.dp(getContext(), 6);
            for (int i = 0; i < n; i++) {
                double ang = -Math.PI / 2 + i * 2 * Math.PI / n;
                boolean lit = i < Math.round(shown * n);
                tick.setColor(lit ? Color.argb(235, 242, 194, 91) : Color.argb(40, 255, 255, 255));
                float len = (i % 5 == 0) ? t1 : t1 - UI.dp(getContext(), 2);
                c.drawLine(cx + t0 * (float) Math.cos(ang), cy + t0 * (float) Math.sin(ang), cx + len * (float) Math.cos(ang), cy + len * (float) Math.sin(ang), tick);
            }

            c.drawArc(box, 0, 360, false, track);
            if (shown > 0.002f) {
                c.drawArc(box, -90, 360f * shown, false, glow);
                c.drawArc(box, -90, 360f * shown, false, arc);
                double ang = Math.toRadians(-90 + 360f * shown);
                dot.setColor(Color.WHITE);
                c.drawCircle(cx + r * (float) Math.cos(ang), cy + r * (float) Math.sin(ang), sw * 0.22f, dot);
            }
            // centre text
            int shownPct = Math.round(pct * (target > 0 ? Math.min(1f, shown / target) : 1f));
            c.drawText(shownPct + "%", cx, cy + big.getTextSize() * 0.12f, big);
            c.drawText(caption, cx, cy + big.getTextSize() * 0.12f + small.getTextSize() * 1.5f, small);
        }
    }

    /** Last months: revenue (emerald) vs sadqa given (gold) bars. Selected month is highlighted. */
    public static final class BarsView extends View {
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG),
                base = new Paint(Paint.ANTI_ALIAS_FLAG), hi = new Paint(Paint.ANTI_ALIAS_FLAG), val = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private long[] rev = new long[0], given = new long[0];
        private String[] labels = new String[0];
        private int selected = -1;
        private float grow = 0f;

        public BarsView(Context c) {
            super(c);
            txt.setTextAlign(Paint.Align.CENTER); txt.setTextSize(sp(c, 11));
            val.setTextAlign(Paint.Align.CENTER); val.setTextSize(sp(c, 9.5f)); val.setColor(UI.MUTED);
            base.setColor(Color.argb(40, 255, 255, 255)); base.setStrokeWidth(Math.max(1f, UI.dp(c, 1)));
            hi.setColor(Color.argb(24, 242, 194, 91));
        }

        public void set(String[] labels, long[] rev, long[] given, int selected) {
            this.labels = labels; this.rev = rev; this.given = given; this.selected = selected;
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(900);
            a.setInterpolator(new OvershootInterpolator(0.9f));
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator v) { grow = (Float) v.getAnimatedValue(); invalidate(); }
            });
            a.start();
        }

        @Override protected void onDraw(Canvas c) {
            int n = labels.length;
            if (n == 0) return;
            float w = getWidth(), h = getHeight(), d = UI.dp(getContext(), 1);
            float labelH = UI.dp(getContext(), 22), top = UI.dp(getContext(), 16);
            float baseY = h - labelH, area = baseY - top;
            long max = 1;
            for (int i = 0; i < n; i++) max = Math.max(max, Math.max(rev[i], given[i]));
            float colW = w / n, bw = Math.min(UI.dp(getContext(), 16), colW * 0.26f), gap = UI.dp(getContext(), 4);
            for (int i = 0; i < n; i++) {
                float cx = colW * i + colW / 2f;
                if (i == selected) {
                    r.set(colW * i + d * 3, 0, colW * (i + 1) - d * 3, h);
                    c.drawRoundRect(r, UI.dp(getContext(), 14), UI.dp(getContext(), 14), hi);
                }
                drawBar(c, cx - bw - gap / 2f, baseY, bw, area, rev[i], max, Color.parseColor("#34E0A1"), Color.parseColor("#0E9F6E"));
                drawBar(c, cx + gap / 2f, baseY, bw, area, given[i], max, Color.parseColor("#FBE7A1"), Color.parseColor("#E0A526"));
                txt.setColor(i == selected ? UI.GOLD : UI.MUTED);
                txt.setTypeface(i == selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                c.drawText(labels[i], cx, h - UI.dp(getContext(), 6), txt);
            }
            c.drawLine(0, baseY + d, w, baseY + d, base);
        }

        private void drawBar(Canvas c, float x, float baseY, float bw, float area, long v, long max, int c1, int c2) {
            float bh = v <= 0 ? UI.dp(getContext(), 3) : Math.max(UI.dp(getContext(), 5), area * v / max) * Math.min(grow, 1.12f);
            r.set(x, baseY - bh, x + bw, baseY);
            bar.setShader(new android.graphics.LinearGradient(0, r.top, 0, r.bottom, c1, c2, android.graphics.Shader.TileMode.CLAMP));
            if (v <= 0) bar.setAlpha(60);
            float rad = bw / 2.2f;
            c.drawRoundRect(r, rad, rad, bar);
            bar.setAlpha(255);
        }
    }
}
