package com.ironicxsoft.rozplan;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.PathInterpolator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Custom-drawn views: progress ring, tick box, tab icons, water glasses, workout figure, weight chart. */
public final class Views {
    private Views() { }

    /** Big progress ring with a percentage in the middle. */
    public static final class Ring extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), t = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private float shown = 0f, target = 0f;
        private ValueAnimator anim;

        public Ring(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            t.setTextAlign(Paint.Align.CENTER);
            t.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL));
        }

        public void set(int percent) {
            target = Math.max(0, Math.min(100, percent));
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(shown, target);
            anim.setDuration(520);
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { @Override public void onAnimationUpdate(ValueAnimator a) { shown = (Float) a.getAnimatedValue(); invalidate(); } });
            anim.start();
        }

        @Override protected void onMeasure(int w, int h) { int s = UI.dp(getContext(), 132); setMeasuredDimension(s, s); }

        @Override protected void onDraw(Canvas c) {
            float sw = getWidth() * 0.115f, pad = sw / 2f + UI.dp(getContext(), 1);
            box.set(pad, pad, getWidth() - pad, getHeight() - pad);
            p.setStrokeWidth(sw);
            p.setColor(UI.night ? UI.LINE : android.graphics.Color.WHITE);
            c.drawArc(box, 0, 360, false, p);
            p.setColor(UI.ACC);
            if (shown > 0.3f) c.drawArc(box, -90, 360f * shown / 100f, false, p);
            t.setColor(UI.INK);
            t.setTextSize(getWidth() * 0.26f);
            c.drawText(Math.round(shown) + "%", getWidth() / 2f, getHeight() / 2f + t.getTextSize() * 0.35f, t);
        }
    }

    /** Square tick box (timetable). */
    public static final class Tick extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean on;
        public Tick(Context c) { super(c); }
        public void setOn(boolean v) { on = v; invalidate(); }
        @Override protected void onMeasure(int w, int h) { int s = UI.dp(getContext(), 44); setMeasuredDimension(s, s); }
        @Override protected void onDraw(Canvas c) {
            float r = UI.dp(getContext(), 14);
            RectF b = new RectF(UI.dp(getContext(), 1), UI.dp(getContext(), 1), getWidth() - UI.dp(getContext(), 1), getHeight() - UI.dp(getContext(), 1));
            p.setStyle(Paint.Style.FILL);
            p.setColor(on ? UI.ACC : UI.BG);
            c.drawRoundRect(b, r, r, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(UI.dp(getContext(), 2));
            p.setColor(on ? UI.ACC : UI.LINE);
            c.drawRoundRect(b, r, r, p);
            if (on) {
                p.setColor(android.graphics.Color.WHITE);
                p.setStrokeWidth(UI.dp(getContext(), 3));
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
                float s = getWidth() / 24f;
                Path k = new Path();
                k.moveTo(7.2f * s, 12.4f * s); k.lineTo(10.6f * s, 15.8f * s); k.lineTo(16.8f * s, 8.6f * s);
                c.drawPath(k, p);
            }
        }
    }

    /** Line icons for the bottom bar (clock, bowl, dumbbell, chart). */
    public static final class Icon extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int kind;
        private int color;
        public Icon(Context c, int kind) { super(c); this.kind = kind; }
        public void setColor(int c) { color = c; invalidate(); }
        @Override protected void onMeasure(int w, int h) { int s = UI.dp(getContext(), 26); setMeasuredDimension(s, s); }
        @Override protected void onDraw(Canvas c) {
            float s = getWidth() / 24f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f * s);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(color);
            Path path = new Path();
            switch (kind) {
                case 0:
                    c.drawCircle(12 * s, 12 * s, 9 * s, p);
                    path.moveTo(12 * s, 7 * s); path.lineTo(12 * s, 12 * s); path.lineTo(15 * s, 14 * s); c.drawPath(path, p);
                    break;
                case 1:
                    path.moveTo(4 * s, 13 * s); path.lineTo(20 * s, 13 * s); path.arcTo(new RectF(4 * s, 5 * s, 20 * s, 21 * s), 0, 180); path.close(); c.drawPath(path, p);
                    path.reset(); path.moveTo(8 * s, 9 * s); path.cubicTo(8 * s, 7 * s, 10 * s, 7 * s, 10 * s, 5 * s); c.drawPath(path, p);
                    path.reset(); path.moveTo(13 * s, 9 * s); path.cubicTo(13 * s, 7 * s, 15 * s, 7 * s, 15 * s, 5 * s); c.drawPath(path, p);
                    break;
                case 2:
                    c.drawLine(3 * s, 9 * s, 3 * s, 15 * s, p); c.drawLine(6 * s, 7 * s, 6 * s, 17 * s, p);
                    c.drawLine(18 * s, 7 * s, 18 * s, 17 * s, p); c.drawLine(21 * s, 9 * s, 21 * s, 15 * s, p);
                    c.drawLine(6 * s, 12 * s, 18 * s, 12 * s, p);
                    break;
                default:
                    c.drawLine(4 * s, 19 * s, 20 * s, 19 * s, p);
                    path.moveTo(6 * s, 15 * s); path.lineTo(10 * s, 11 * s); path.lineTo(13 * s, 14 * s); path.lineTo(18 * s, 8 * s); c.drawPath(path, p);
            }
        }
    }

    /** Row of water glasses (12). */
    public static final class Glasses extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int have, total = 12;
        public Glasses(Context c) { super(c); }
        public void set(int have, int total) { this.have = have; this.total = total; invalidate(); }
        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(View.resolveSize(UI.dp(getContext(), 200), w), UI.dp(getContext(), 30)); }
        @Override protected void onDraw(Canvas c) {
            float gap = UI.dp(getContext(), 5);
            float cw = Math.min(UI.dp(getContext(), 22), (getWidth() - gap * (total - 1)) / total);
            float totalW = cw * total + gap * (total - 1), x0 = (getWidth() - totalW) / 2f, h = getHeight();
            for (int i = 0; i < total; i++) {
                float x = x0 + i * (cw + gap);
                Path g = new Path();
                float in = cw * 0.1f;
                g.moveTo(x, 0); g.lineTo(x + cw, 0); g.lineTo(x + cw - in, h); g.lineTo(x + in, h); g.close();
                p.setStyle(Paint.Style.FILL);
                p.setColor(i < have ? UI.BLUE : UI.LINE);
                c.drawPath(g, p);
            }
        }
    }

    /** Animated stick figure doing the exercise (interpolates between pose A and B). */
    public static final class Figure extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Map<String, float[]> a = new HashMap<>(), b = new HashMap<>();
        private float t = 0f;
        private ValueAnimator anim;

        public Figure(Context c, String poseA, String poseB) { super(c); parse(poseA, a); parse(poseB, b); }

        private static void parse(String s, Map<String, float[]> m) {
            for (String part : s.split(";")) {
                String[] kv = part.split(":");
                String[] xy = kv[1].split(",");
                m.put(kv[0], new float[]{Float.parseFloat(xy[0]), Float.parseFloat(xy[1])});
            }
        }

        private float[] pos(String k) {
            String alt = k.replace('2', '1');
            float[] pa = a.containsKey(k) ? a.get(k) : a.get(alt), pb = b.containsKey(k) ? b.get(k) : b.get(alt);
            return new float[]{pa[0] + (pb[0] - pa[0]) * t, pa[1] + (pb[1] - pa[1]) * t};
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(1300);
            anim.setRepeatMode(ValueAnimator.REVERSE);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setInterpolator(new PathInterpolator(0.45f, 0f, 0.55f, 1f));
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { @Override public void onAnimationUpdate(ValueAnimator v) { t = (Float) v.getAnimatedValue(); invalidate(); } });
            anim.start();
        }

        @Override protected void onDetachedFromWindow() { if (anim != null) anim.cancel(); super.onDetachedFromWindow(); }

        @Override protected void onMeasure(int w, int h) {
            int width = View.resolveSize(UI.dp(getContext(), 300), w);
            setMeasuredDimension(width, (int) (width * 1.15f / 2f));
        }

        private void line(Canvas c, String from, String to, float sc, float ox, float oy, int alpha, float width) {
            float[] f = pos(from), q = pos(to);
            p.setColor(UI.INK);
            p.setAlpha(alpha);
            p.setStrokeWidth(width * sc);
            c.drawLine(ox + (f[0] - 20) * sc, oy + (f[1] - 10) * sc, ox + (q[0] - 20) * sc, oy + (q[1] - 10) * sc, p);
        }

        @Override protected void onDraw(Canvas c) {
            float sc = Math.min(getWidth() / 160f, getHeight() / 125f);
            float ox = (getWidth() - 160 * sc) / 2f, oy = (getHeight() - 125 * sc) / 2f;
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStyle(Paint.Style.FILL);
            p.setColor(UI.ACC);
            p.setAlpha(46);
            c.drawOval(new RectF(ox + (22 - 20) * sc, oy + (127 - 10 - 4) * sc, ox + (178 - 20) * sc, oy + (127 - 10 + 4) * sc), p);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(UI.ACC);
            p.setAlpha(128);
            p.setStrokeWidth(2 * sc);
            c.drawLine(ox + 2 * sc, oy + 115 * sc, ox + 158 * sc, oy + 115 * sc, p);
            String[][] far = {{"Hp", "K2"}, {"K2", "F2"}, {"S", "E2"}, {"E2", "W2"}};
            String[][] near = {{"S", "Hp"}, {"Hp", "K1"}, {"K1", "F1"}, {"S", "E1"}, {"E1", "W1"}};
            for (String[] l : far) line(c, l[0], l[1], sc, ox, oy, 90, 8);
            for (String[] l : near) line(c, l[0], l[1], sc, ox, oy, 255, 9);
            float[] h = pos("Hd");
            p.setStyle(Paint.Style.FILL);
            p.setColor(UI.ACC);
            p.setAlpha(255);
            c.drawCircle(ox + (h[0] - 20) * sc, oy + (h[1] - 10) * sc, 9 * sc, p);
        }
    }

    /** Weight chart: line through start + log, dotted lines at 90 (March) and 75 (goal) kg. */
    public static final class Chart extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private List<Store.Weight> data;

        public Chart(Context c) { super(c); }
        public void set(List<Store.Weight> d) { data = d; invalidate(); }

        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(View.resolveSize(UI.dp(getContext(), 280), w), UI.dp(getContext(), 240)); }

        @Override protected void onDraw(Canvas c) {
            Context ctx = getContext();
            float w = getWidth(), h = getHeight(), pl = UI.dp(ctx, 40), pr = UI.dp(ctx, 14), pt = UI.dp(ctx, 16), pb = UI.dp(ctx, 24);
            float lo = 72, hi = 108;
            int n = 1 + (data == null ? 0 : data.size());
            float[] vals = new float[n];
            vals[0] = Store.START_KG;
            for (int i = 1; i < n; i++) vals[i] = (float) data.get(i - 1).kg;
            p.setTextSize(UI.dp(ctx, 12));
            int[] guides = {75, 90, 105};
            for (int g : guides) {
                float y = pt + (hi - g) / (hi - lo) * (h - pt - pb);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(UI.dp(ctx, 1));
                p.setColor(g == 105 ? UI.LINE : UI.ACC);
                p.setAlpha(g == 105 ? 255 : 150);
                if (g != 105) p.setPathEffect(new android.graphics.DashPathEffect(new float[]{UI.dp(ctx, 5), UI.dp(ctx, 5)}, 0));
                c.drawLine(pl, y, w - pr, y, p);
                p.setPathEffect(null);
                p.setStyle(Paint.Style.FILL);
                p.setColor(UI.MUTED);
                p.setAlpha(255);
                p.setTextAlign(Paint.Align.RIGHT);
                c.drawText(String.valueOf(g), pl - UI.dp(ctx, 6), y + UI.dp(ctx, 4), p);
            }
            p.setColor(UI.ACC2);
            p.setTextAlign(Paint.Align.RIGHT);
            c.drawText("March", w - pr, pt + (hi - 90) / (hi - lo) * (h - pt - pb) - UI.dp(ctx, 6), p);
            c.drawText("Goal", w - pr, pt + (hi - 75) / (hi - lo) * (h - pt - pb) - UI.dp(ctx, 6), p);
            Path line = new Path();
            float[] xs = new float[n], ys = new float[n];
            for (int i = 0; i < n; i++) {
                xs[i] = pl + (n < 2 ? 0 : (float) i / (n - 1)) * (w - pl - pr);
                float v = Math.max(lo, Math.min(hi, vals[i]));
                ys[i] = pt + (hi - v) / (hi - lo) * (h - pt - pb);
                if (i == 0) line.moveTo(xs[i], ys[i]); else line.lineTo(xs[i], ys[i]);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(UI.dp(ctx, 3));
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(UI.INK);
            c.drawPath(line, p);
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < n; i++) {
                p.setColor(i == n - 1 ? UI.ACC : UI.INK);
                c.drawCircle(xs[i], ys[i], UI.dp(ctx, i == n - 1 ? 6 : 4), p);
            }
        }
    }
}
