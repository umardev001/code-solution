package com.fitjourney.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

/** Custom-drawn views: activity rings, water bottle, weight curve, bar charts, heat map, BMI bar, glowing backdrop. */
public final class Widgets {
    private Widgets() { }

    private static float sp(Context c, float v) { return v * c.getResources().getDisplayMetrics().scaledDensity; }
    private static float dp(Context c, float v) { return v * c.getResources().getDisplayMetrics().density; }

    static int lighter(int c, float f) {
        return Color.rgb(Math.min(255, (int) (Color.red(c) + (255 - Color.red(c)) * f)), Math.min(255, (int) (Color.green(c) + (255 - Color.green(c)) * f)), Math.min(255, (int) (Color.blue(c) + (255 - Color.blue(c)) * f)));
    }

    static int darker(int c, float f) {
        return Color.rgb((int) (Color.red(c) * f), (int) (Color.green(c) * f), (int) (Color.blue(c) * f));
    }

    private static ValueAnimator anim(long ms, float from, float to, android.animation.TimeInterpolator ip, ValueAnimator.AnimatorUpdateListener l) {
        ValueAnimator a = ValueAnimator.ofFloat(from, to);
        a.setDuration(ms);
        a.setInterpolator(ip);
        a.addUpdateListener(l);
        a.start();
        return a;
    }

    /* =================================================================== rings */
    /** Three concentric progress rings (like activity rings) with a glow. */
    public static final class RingsView extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG),
                glow = new Paint(Paint.ANTI_ALIAS_FLAG), big = new Paint(Paint.ANTI_ALIAS_FLAG), small = new Paint(Paint.ANTI_ALIAS_FLAG),
                dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private final Matrix rot = new Matrix();
        private float[] frac = new float[3];
        private int[] colors = {UI.STEPS, UI.WATER, UI.WORK};
        private Shader[] shaders = new Shader[3];
        private String top = "", bottom = "";
        private float p = 0f;

        public RingsView(Context c) {
            super(c);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
            track.setStyle(Paint.Style.STROKE);
            arc.setStyle(Paint.Style.STROKE); arc.setStrokeCap(Paint.Cap.ROUND);
            glow.setStyle(Paint.Style.STROKE); glow.setStrokeCap(Paint.Cap.ROUND); glow.setMaskFilter(new BlurMaskFilter(dp(c, 8), BlurMaskFilter.Blur.NORMAL));
            big.setColor(UI.INK); big.setTextAlign(Paint.Align.CENTER); big.setTextSize(sp(c, 38)); big.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            small.setColor(UI.MUTED); small.setTextAlign(Paint.Align.CENTER); small.setTextSize(sp(c, 12));
            dot.setColor(Color.WHITE);
        }

        public void set(float[] f, String top, String bottom, float[] from) {
            frac = f; this.top = top; this.bottom = bottom;
            final float[] start = from;
            anim(1300, 0f, 1f, new DecelerateInterpolator(1.8f), new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator v) { p = (Float) v.getAnimatedValue(); invalidate(); }
            });
            this.start = start;
        }
        private float[] start = new float[3];

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            for (int i = 0; i < 3; i++) {
                SweepGradient g = new SweepGradient(w / 2f, h / 2f, new int[]{darker(colors[i], 0.75f), colors[i], lighter(colors[i], 0.45f)}, new float[]{0f, 0.75f, 1f});
                rot.setRotate(-90, w / 2f, h / 2f);
                g.setLocalMatrix(rot);
                shaders[i] = g;
            }
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f, s = Math.min(w, h);
            float t = s * 0.082f, gap = s * 0.022f;
            float r0 = s / 2f - t / 2f - dp(getContext(), 6);
            for (int i = 0; i < 3; i++) {
                float r = r0 - i * (t + gap);
                box.set(cx - r, cy - r, cx + r, cy + r);
                track.setStrokeWidth(t); track.setColor(Color.argb(52, Color.red(colors[i]), Color.green(colors[i]), Color.blue(colors[i])));
                c.drawArc(box, 0, 360, false, track);
                float f = start[i] + (frac[i] - start[i]) * p;
                f = Math.max(0f, Math.min(1f, f));
                if (f > 0.003f) {
                    glow.setStrokeWidth(t + dp(getContext(), 3)); glow.setColor(Color.argb(120, Color.red(colors[i]), Color.green(colors[i]), Color.blue(colors[i])));
                    c.drawArc(box, -90, 360f * f, false, glow);
                    arc.setStrokeWidth(t); arc.setShader(shaders[i]);
                    c.drawArc(box, -90, 360f * f, false, arc);
                    arc.setShader(null);
                    double ang = Math.toRadians(-90 + 360f * f);
                    dot.setColor(Color.argb(210, 255, 255, 255));
                    c.drawCircle(cx + r * (float) Math.cos(ang), cy + r * (float) Math.sin(ang), t * 0.2f, dot);
                }
            }
            c.drawText(top, cx, cy + big.getTextSize() * 0.12f, big);
            c.drawText(bottom, cx, cy + big.getTextSize() * 0.12f + small.getTextSize() * 1.45f, small);
        }
    }

    /* ==================================================================== water */
    /** Round bottle that fills with an animated wave. */
    public static final class WaveView extends View {
        private final Paint fillA = new Paint(Paint.ANTI_ALIAS_FLAG), fillB = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG),
                txt = new Paint(Paint.ANTI_ALIAS_FLAG), sub = new Paint(Paint.ANTI_ALIAS_FLAG), bub = new Paint(Paint.ANTI_ALIAS_FLAG), back = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path(), wave = new Path();
        private ValueAnimator loop, level;
        private float phase, shown = 0f, target = 0f;
        private String main = "", caption = "";

        public WaveView(Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE); ring.setStrokeWidth(dp(c, 3)); ring.setColor(Color.argb(170, 18, 184, 230));
            back.setColor(Color.argb(46, 18, 184, 230));
            txt.setColor(Color.WHITE); txt.setTextAlign(Paint.Align.CENTER); txt.setTextSize(sp(c, 21)); txt.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            txt.setShadowLayer(dp(c, 3), 0, dp(c, 1), Color.argb(120, 0, 30, 60));
            sub.setColor(Color.WHITE); sub.setAlpha(210); sub.setTextAlign(Paint.Align.CENTER); sub.setTextSize(sp(c, 11));
            bub.setColor(Color.argb(90, 255, 255, 255));
        }

        /** Start the fill animation from this level (so updates glide instead of refilling). */
        public void startAt(float f) { shown = Math.max(0f, Math.min(1f, f)); }

        public void set(float frac, String main, String caption) {
            this.main = main; this.caption = caption;
            target = Math.max(0f, Math.min(1f, frac));
            if (level != null) level.cancel();
            level = anim(1200, shown, target, new DecelerateInterpolator(1.5f), new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator v) { shown = (Float) v.getAnimatedValue(); }
            });
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            loop = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
            loop.setDuration(2600);
            loop.setInterpolator(new LinearInterpolator());
            loop.setRepeatCount(ValueAnimator.INFINITE);
            loop.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator v) { phase = (Float) v.getAnimatedValue(); invalidate(); }
            });
            loop.start();
        }

        @Override protected void onDetachedFromWindow() {
            if (loop != null) loop.cancel();
            if (level != null) level.cancel();
            super.onDetachedFromWindow();
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            fillA.setShader(new LinearGradient(0, 0, 0, h, Color.parseColor("#5CE6FF"), Color.parseColor("#1495E8"), Shader.TileMode.CLAMP));
            fillB.setShader(new LinearGradient(0, 0, 0, h, Color.parseColor("#9AF0FF"), Color.parseColor("#28B5F0"), Shader.TileMode.CLAMP));
            fillB.setAlpha(150);
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f - ring.getStrokeWidth();
            clip.reset();
            clip.addCircle(cx, cy, r, Path.Direction.CW);
            c.drawCircle(cx, cy, r, back);
            c.save();
            c.clipPath(clip);
            float top = cy + r - (2 * r) * shown;
            float amp = shown <= 0.01f || shown >= 0.99f ? 0f : r * 0.07f;
            for (int layer = 0; layer < 2; layer++) {
                wave.reset();
                wave.moveTo(cx - r, cy + r);
                float ph = phase * (layer == 0 ? 1 : -1) + layer * 1.7f;
                for (float x = cx - r; x <= cx + r + 4; x += 4) {
                    wave.lineTo(x, top + amp * (float) Math.sin((x - cx) / r * 3.4f + ph));
                }
                wave.lineTo(cx + r, cy + r);
                wave.close();
                c.drawPath(wave, layer == 0 ? fillA : fillB);
            }
            // bubbles
            if (shown > 0.08f) {
                for (int i = 0; i < 4; i++) {
                    float f = ((phase / 6.2832f) + i * 0.27f) % 1f;
                    float bx = cx + (i - 1.5f) * r * 0.32f + (float) Math.sin(phase * 2 + i) * r * 0.05f;
                    float by = cy + r - (cy + r - top) * f;
                    c.drawCircle(bx, by, dp(getContext(), 1.5f + i % 2), bub);
                }
            }
            c.restore();
            c.drawCircle(cx, cy, r, ring);
            boolean onWater = shown > 0.56f;
            txt.setColor(onWater ? Color.WHITE : UI.INK);
            txt.setShadowLayer(onWater ? dp(getContext(), 3) : 0f, 0, dp(getContext(), 1), Color.argb(120, 0, 30, 60));
            sub.setColor(onWater ? Color.WHITE : UI.MUTED);
            c.drawText(main, cx, cy + txt.getTextSize() * 0.15f, txt);
            c.drawText(caption, cx, cy + txt.getTextSize() * 0.15f + sub.getTextSize() * 1.6f, sub);
        }
    }

    /* ======================================================================= weight */
    /** Smooth weight curve with gradient fill, goal line, and touch-to-inspect. */
    public static final class LineChartView extends View {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), grid = new Paint(Paint.ANTI_ALIAS_FLAG),
                txt = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG), goalP = new Paint(Paint.ANTI_ALIAS_FLAG),
                tipP = new Paint(Paint.ANTI_ALIAS_FLAG), tipT = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path(), area = new Path();
        private final RectF tip = new RectF();
        private double[] xs = new double[0], ys = new double[0];
        private String[] labels = new String[0];
        private double goal = 0;
        private float grow = 0f;
        private int sel = -1;
        private float[] px = new float[0], py = new float[0];

        public LineChartView(Context c) {
            super(c);
            line.setStyle(Paint.Style.STROKE); line.setStrokeWidth(dp(c, 3)); line.setStrokeCap(Paint.Cap.ROUND); line.setStrokeJoin(Paint.Join.ROUND);
            line.setColor(UI.WEIGHT);
            grid.setColor(Color.argb(22, 27, 33, 64)); grid.setStrokeWidth(Math.max(1f, dp(c, 1)));
            txt.setColor(UI.MUTED); txt.setTextSize(sp(c, 10.5f));
            goalP.setStyle(Paint.Style.STROKE); goalP.setStrokeWidth(dp(c, 1.6f)); goalP.setColor(UI.GREEN); goalP.setPathEffect(new DashPathEffect(new float[]{dp(c, 6), dp(c, 5)}, 0));
            tipP.setColor(Color.parseColor("#1B2140")); tipT.setColor(Color.WHITE); tipT.setTextSize(sp(c, 12)); tipT.setTextAlign(Paint.Align.CENTER); tipT.setTypeface(Typeface.DEFAULT_BOLD);
        }

        public void set(double[] x, double[] y, String[] labels, double goal) {
            this.xs = x; this.ys = y; this.labels = labels; this.goal = goal; this.sel = -1;
            anim(1100, 0f, 1f, new DecelerateInterpolator(1.4f), new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator v) { grow = (Float) v.getAnimatedValue(); invalidate(); }
            });
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (px.length == 0) return false;
            int a = e.getAction();
            if (a == MotionEvent.ACTION_DOWN && getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
                int best = 0;
                for (int i = 1; i < px.length; i++) if (Math.abs(px[i] - e.getX()) < Math.abs(px[best] - e.getX())) best = i;
                sel = best;
                invalidate();
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            }
            return true;
        }

        @Override protected void onDraw(Canvas c) {
            Context ctx = getContext();
            float w = getWidth(), h = getHeight();
            float l = dp(ctx, 38), r = dp(ctx, 14), t = dp(ctx, 14), b = dp(ctx, 26);
            int n = xs.length;
            if (n == 0) {
                txt.setTextAlign(Paint.Align.CENTER);
                c.drawText("Log your weight to see the curve", w / 2f, h / 2f, txt);
                return;
            }
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (double v : ys) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
            if (goal > 0) { lo = Math.min(lo, goal); hi = Math.max(hi, goal); }
            double pad = Math.max(0.6, (hi - lo) * 0.18);
            lo -= pad; hi += pad;
            double x0 = xs[0], x1 = xs[n - 1];
            if (x1 - x0 < 1) x1 = x0 + 1;
            // grid + y labels
            txt.setTextAlign(Paint.Align.RIGHT);
            for (int i = 0; i <= 3; i++) {
                float y = t + (h - t - b) * i / 3f;
                c.drawLine(l, y, w - r, y, grid);
                c.drawText(String.format(java.util.Locale.ENGLISH, "%.0f", hi - (hi - lo) * i / 3.0), l - dp(ctx, 6), y + dp(ctx, 4), txt);
            }
            txt.setTextAlign(Paint.Align.LEFT);
            c.drawText(labels[0], l, h - dp(ctx, 6), txt);
            if (n > 1) { txt.setTextAlign(Paint.Align.RIGHT); c.drawText(labels[n - 1], w - r, h - dp(ctx, 6), txt); }
            // goal
            if (goal > 0) {
                float gy = (float) (t + (h - t - b) * (hi - goal) / (hi - lo));
                path.reset(); path.moveTo(l, gy); path.lineTo(w - r, gy);
                c.drawPath(path, goalP);
                txt.setTextAlign(Paint.Align.RIGHT); txt.setColor(UI.GREEN);
                c.drawText("Goal " + Fit.kg(goal), w - r, gy - dp(ctx, 5), txt);
                txt.setColor(UI.MUTED);
            }
            // points
            px = new float[n]; py = new float[n];
            for (int i = 0; i < n; i++) {
                px[i] = (float) (l + (w - l - r) * (xs[i] - x0) / (x1 - x0));
                py[i] = (float) (t + (h - t - b) * (hi - ys[i]) / (hi - lo));
            }
            c.save();
            c.clipRect(0, 0, l + (w - l) * grow, h);
            path.reset();
            path.moveTo(px[0], py[0]);
            for (int i = 1; i < n; i++) {
                float mx = (px[i - 1] + px[i]) / 2f;
                path.cubicTo(mx, py[i - 1], mx, py[i], px[i], py[i]);
            }
            area.set(path);
            area.lineTo(px[n - 1], h - b);
            area.lineTo(px[0], h - b);
            area.close();
            fill.setShader(new LinearGradient(0, t, 0, h - b, Color.argb(95, 139, 92, 246), Color.argb(0, 139, 92, 246), Shader.TileMode.CLAMP));
            c.drawPath(area, fill);
            c.drawPath(path, line);
            dot.setColor(UI.WEIGHT);
            if (n <= 40) for (int i = 0; i < n; i++) c.drawCircle(px[i], py[i], dp(ctx, 3.2f), dot);
            c.restore();
            // last point halo
            int last = n - 1;
            dot.setColor(Color.argb(60, 139, 92, 246));
            c.drawCircle(px[last], py[last], dp(ctx, 10), dot);
            dot.setColor(UI.WEIGHT);
            c.drawCircle(px[last], py[last], dp(ctx, 6), dot);
            dot.setColor(Color.WHITE);
            c.drawCircle(px[last], py[last], dp(ctx, 3.2f), dot);
            // selection
            if (sel >= 0 && sel < n) {
                grid.setColor(Color.argb(90, 27, 33, 64));
                c.drawLine(px[sel], t, px[sel], h - b, grid);
                grid.setColor(Color.argb(22, 27, 33, 64));
                dot.setColor(UI.WEIGHT);
                c.drawCircle(px[sel], py[sel], dp(ctx, 6.5f), dot);
                dot.setColor(Color.WHITE);
                c.drawCircle(px[sel], py[sel], dp(ctx, 3.5f), dot);
                String s = Fit.kg(ys[sel]) + " kg  •  " + labels[sel];
                float tw = tipT.measureText(s) + dp(ctx, 20), th = dp(ctx, 28);
                float tx = Math.max(tw / 2f + 2, Math.min(w - tw / 2f - 2, px[sel]));
                tip.set(tx - tw / 2f, Math.max(2, py[sel] - th - dp(ctx, 12)), tx + tw / 2f, Math.max(2, py[sel] - th - dp(ctx, 12)) + th);
                c.drawRoundRect(tip, th / 2f, th / 2f, tipP);
                c.drawText(s, tx, tip.centerY() + dp(ctx, 4), tipT);
            }
        }
    }

    /* ==================================================================== bars */
    /** Seven-ish bars with a dashed goal line; the last bar (today) is highlighted. */
    public static final class BarsView extends View {
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG), goalP = new Paint(Paint.ANTI_ALIAS_FLAG),
                base = new Paint(Paint.ANTI_ALIAS_FLAG), val = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float[] v = new float[0];
        private String[] labels = new String[0], vals = new String[0];
        private float goal;
        private int color = UI.STEPS, hi = -1;
        private float grow;

        public BarsView(Context c) {
            super(c);
            txt.setTextAlign(Paint.Align.CENTER); txt.setTextSize(sp(c, 11));
            val.setTextAlign(Paint.Align.CENTER); val.setTextSize(sp(c, 9.5f)); val.setColor(UI.INK);
            goalP.setStyle(Paint.Style.STROKE); goalP.setStrokeWidth(dp(c, 1.5f)); goalP.setPathEffect(new DashPathEffect(new float[]{dp(c, 5), dp(c, 4)}, 0));
            base.setColor(Color.argb(40, 27, 33, 64)); base.setStrokeWidth(Math.max(1f, dp(c, 1)));
        }

        public void set(String[] labels, float[] v, String[] vals, float goal, int color, int highlight) {
            this.labels = labels; this.v = v; this.vals = vals; this.goal = goal; this.color = color; this.hi = highlight;
            anim(900, 0f, 1f, new OvershootInterpolator(0.8f), new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) { grow = (Float) a.getAnimatedValue(); invalidate(); }
            });
        }

        @Override protected void onDraw(Canvas c) {
            int n = v.length;
            if (n == 0) return;
            Context ctx = getContext();
            float w = getWidth(), h = getHeight(), labelH = dp(ctx, 22), top = dp(ctx, 18), baseY = h - labelH, area = baseY - top;
            float max = Math.max(goal, 1f);
            for (float x : v) max = Math.max(max, x);
            float colW = w / n, bw = Math.min(dp(ctx, 26), colW * 0.52f);
            for (int i = 0; i < n; i++) {
                float cx = colW * i + colW / 2f;
                boolean today = i == hi;
                float bh = v[i] <= 0 ? dp(ctx, 3) : Math.max(dp(ctx, 6), area * v[i] / max) * Math.min(grow, 1.1f);
                r.set(cx - bw / 2f, baseY - bh, cx + bw / 2f, baseY);
                bar.setShader(new LinearGradient(0, r.top, 0, r.bottom, today ? color : lighter(color, 0.35f), today ? darker(color, 0.85f) : lighter(color, 0.1f), Shader.TileMode.CLAMP));
                bar.setAlpha(v[i] <= 0 ? 60 : 255);
                c.drawRoundRect(r, bw / 3f, bw / 3f, bar);
                if (v[i] > 0 && grow > 0.9f) c.drawText(vals[i], cx, r.top - dp(ctx, 4), val);
                txt.setColor(today ? UI.INK : UI.MUTED);
                txt.setTypeface(today ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                c.drawText(labels[i], cx, h - dp(ctx, 6), txt);
            }
            c.drawLine(0, baseY + 1, w, baseY + 1, base);
            if (goal > 0) {
                float gy = baseY - area * goal / max;
                goalP.setColor(Color.argb(190, Color.red(UI.GREEN), Color.green(UI.GREEN), Color.blue(UI.GREEN)));
                Path p = new Path(); p.moveTo(0, gy); p.lineTo(w, gy);
                c.drawPath(p, goalP);
            }
        }
    }

    /* ==================================================================== heat */
    /** Consistency calendar: 7 columns (Mon..Sun), one row per week. levels: -1 future, 0..3 goals hit. */
    public static final class HeatView extends View {
        private final Paint cell = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private int[] levels = new int[0];
        private int todayIdx = -1;
        private float pop;
        private static final String[] D = {"M", "T", "W", "T", "F", "S", "S"};

        public HeatView(Context c) {
            super(c);
            txt.setTextAlign(Paint.Align.CENTER); txt.setTextSize(sp(c, 10.5f)); txt.setColor(UI.MUTED);
        }

        public static int colorOf(int level) {
            switch (level) {
                case 1: return Color.parseColor("#C9D6FF");
                case 2: return Color.parseColor("#8E73FF");
                case 3: return Color.parseColor("#10B981");
                default: return Color.parseColor("#E9EDF8");
            }
        }

        public void set(int[] levels, int todayIdx) {
            this.levels = levels; this.todayIdx = todayIdx;
            anim(800, 0f, 1f, new OvershootInterpolator(1.4f), new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) { pop = (Float) a.getAnimatedValue(); invalidate(); }
            });
        }

        public int rowsFor(int n) { return (n + 6) / 7; }

        @Override protected void onDraw(Canvas c) {
            Context ctx = getContext();
            float w = getWidth(), head = dp(ctx, 18), gap = dp(ctx, 5);
            float cw = (w - gap * 6) / 7f;
            for (int i = 0; i < 7; i++) c.drawText(D[i], i * (cw + gap) + cw / 2f, head - dp(ctx, 5), txt);
            for (int i = 0; i < levels.length; i++) {
                int row = i / 7, col = i % 7;
                float x = col * (cw + gap), y = head + row * (cw + gap);
                float k = Math.max(0f, Math.min(1f, pop * 1.2f - (i / (float) levels.length) * 0.2f));
                float inset = cw * (1f - k) / 2f;
                r.set(x + inset, y + inset, x + cw - inset, y + cw - inset);
                if (levels[i] < 0) { cell.setColor(Color.argb(14, 27, 33, 64)); }
                else cell.setColor(colorOf(levels[i]));
                c.drawRoundRect(r, cw * 0.28f, cw * 0.28f, cell);
                if (i == todayIdx) {
                    cell.setStyle(Paint.Style.STROKE); cell.setStrokeWidth(dp(ctx, 2)); cell.setColor(UI.INK);
                    c.drawRoundRect(r, cw * 0.28f, cw * 0.28f, cell);
                    cell.setStyle(Paint.Style.FILL);
                }
            }
        }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec);
            float gap = dp(getContext(), 5), cw = (w - gap * 6) / 7f;
            int rows = Math.max(1, rowsFor(levels.length));
            setMeasuredDimension(w, (int) (dp(getContext(), 18) + rows * (cw + gap)));
        }
    }

    /* ===================================================================== BMI */
    /** Colour-banded BMI scale with a marker. */
    public static final class BmiBar extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private double bmi;
        private float grow;

        public BmiBar(Context c) {
            super(c);
            txt.setTextAlign(Paint.Align.CENTER); txt.setTextSize(sp(c, 10)); txt.setColor(UI.MUTED);
        }

        public void set(double bmi) {
            this.bmi = bmi;
            anim(900, 0f, 1f, new DecelerateInterpolator(1.5f), new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) { grow = (Float) a.getAnimatedValue(); invalidate(); }
            });
        }

        private float pos(double v, float w) { return (float) (Math.max(15, Math.min(40, v)) - 15) / 25f * w; }

        @Override protected void onDraw(Canvas c) {
            Context ctx = getContext();
            float w = getWidth(), h = getHeight(), by = h * 0.55f, bh = dp(ctx, 10);
            double[] cuts = {15, 18.5, 25, 30, 40};
            int[] cols = {Color.parseColor("#5AA2FF"), Color.parseColor("#34D399"), Color.parseColor("#FFB84D"), Color.parseColor("#FF6B7F")};
            for (int i = 0; i < 4; i++) {
                float a = pos(cuts[i], w), b = pos(cuts[i + 1], w);
                p.setColor(cols[i]);
                r.set(a + (i == 0 ? 0 : dp(ctx, 1.5f)), by, b - (i == 3 ? 0 : dp(ctx, 1.5f)), by + bh);
                float rad = bh / 2f;
                c.drawRoundRect(r, rad, rad, p);
            }
            for (int i = 1; i < 4; i++) c.drawText(i == 1 ? "18.5" : (i == 2 ? "25" : "30"), pos(cuts[i], w), by + bh + dp(ctx, 15), txt);
            if (bmi > 0) {
                float x = pos(bmi, w) * grow + pos(15, w) * (1 - grow);
                p.setColor(UI.INK);
                Path tri = new Path();
                tri.moveTo(x, by - dp(ctx, 3)); tri.lineTo(x - dp(ctx, 7), by - dp(ctx, 13)); tri.lineTo(x + dp(ctx, 7), by - dp(ctx, 13)); tri.close();
                c.drawPath(tri, p);
                c.drawCircle(x, by + bh / 2f, bh * 0.8f, p);
                p.setColor(Color.WHITE);
                c.drawCircle(x, by + bh / 2f, bh * 0.42f, p);
            }
        }
    }

    /* ================================================================== banner */
    /** Colourful rounded header with soft decorative bubbles. Children (title row) are laid out on top. */
    public static final class Banner extends android.widget.LinearLayout {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Banner(Context c, int c1, int c2) {
            super(c);
            setWillNotDraw(false);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
            float r = dp(c, 34);
            g.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
            setBackground(g);
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setColor(Color.argb(30, 255, 255, 255));
            c.drawCircle(w * 0.88f, h * 0.18f, h * 0.62f, p);
            p.setColor(Color.argb(20, 255, 255, 255));
            c.drawCircle(w * 0.06f, h * 0.95f, h * 0.5f, p);
            p.setColor(Color.argb(60, 255, 255, 255));
            c.drawCircle(w * 0.62f, h * 0.30f, dp(getContext(), 4), p);
            c.drawCircle(w * 0.74f, h * 0.55f, dp(getContext(), 2.5f), p);
            c.drawCircle(w * 0.40f, h * 0.18f, dp(getContext(), 2f), p);
        }
    }

    /* ==================================================================== orbs */
    /** Soft glowing colour orbs; optionally drifting. */
    public static final class OrbsView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), bg = new Paint();
        private final int[] cols;
        private final float[][] pos = {{0.12f, 0.08f, 0.9f}, {0.92f, 0.30f, 0.8f}, {0.2f, 0.78f, 0.95f}};
        private float t;
        private ValueAnimator loop;
        private final boolean animated;
        private final Shader[] shaders = new Shader[3];

        public OrbsView(Context c, boolean animated, int c1, int c2, int c3) {
            super(c);
            this.animated = animated;
            cols = new int[]{c1, c2, c3};
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            bg.setShader(new LinearGradient(0, 0, 0, h, Color.parseColor("#F8F9FF"), Color.parseColor("#EAF0FF"), Shader.TileMode.CLAMP));
            for (int i = 0; i < 3; i++) {
                float rad = w * pos[i][2];
                shaders[i] = new RadialGradient(0, 0, rad, Color.argb(70, Color.red(cols[i]), Color.green(cols[i]), Color.blue(cols[i])), Color.argb(0, Color.red(cols[i]), Color.green(cols[i]), Color.blue(cols[i])), Shader.TileMode.CLAMP);
            }
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!animated) return;
            loop = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
            loop.setDuration(20000);
            loop.setInterpolator(new LinearInterpolator());
            loop.setRepeatCount(ValueAnimator.INFINITE);
            loop.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator v) { t = (Float) v.getAnimatedValue(); invalidate(); }
            });
            loop.start();
        }

        @Override protected void onDetachedFromWindow() { if (loop != null) loop.cancel(); super.onDetachedFromWindow(); }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            c.drawRect(0, 0, w, h, bg);
            for (int i = 0; i < 3; i++) {
                float x = w * (pos[i][0] + (animated ? 0.07f * (float) Math.sin(t + i * 2.1f) : 0f));
                float y = h * (pos[i][1] + (animated ? 0.04f * (float) Math.cos(t + i * 1.3f) : 0f));
                c.save();
                c.translate(x, y);
                p.setShader(shaders[i]);
                c.drawCircle(0, 0, w * pos[i][2], p);
                c.restore();
            }
            p.setShader(null);
        }
    }
}
