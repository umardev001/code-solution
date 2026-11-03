package com.sadqa.tracker;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import java.util.Random;

/**
 * Login backdrop: deep emerald night sky with twinkling stars, a slowly turning Islamic
 * eight-point-star pattern, a crescent moon, drifting golden / emerald glows and a golden
 * spark burst on success.
 */
final class SkyView extends View {
    private static final int N = 70;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bg = new Paint();
    private final Path tmp = new Path();
    private final Path moon = new Path();
    private final float[] sx = new float[N], sy = new float[N], sr = new float[N], sp = new float[N], sph = new float[N];
    private RadialGradient gold, em;
    private float t, density = 1f;
    private ValueAnimator anim;

    // burst
    private static final int B = 44;
    private final float[] ba = new float[B], bs = new float[B], bz = new float[B];
    private float bx, by, bp = -1f;

    SkyView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        Random r = new Random(11);
        for (int i = 0; i < N; i++) {
            sx[i] = r.nextFloat(); sy[i] = r.nextFloat() * 0.85f;
            sr[i] = 0.6f + r.nextFloat() * 1.3f; sp[i] = 1 + r.nextInt(4); sph[i] = r.nextFloat() * 6.28f;
        }
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Math.max(1f, density));
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        bg.setShader(new LinearGradient(0, 0, 0, h, new int[]{Color.parseColor("#020C08"), Color.parseColor("#06251B"), Color.parseColor("#0B4A37")},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        gold = new RadialGradient(0, 0, w * 0.75f, Color.argb(80, 242, 194, 91), Color.argb(0, 242, 194, 91), Shader.TileMode.CLAMP);
        em = new RadialGradient(0, 0, w * 0.9f, Color.argb(95, 25, 195, 125), Color.argb(0, 25, 195, 125), Shader.TileMode.CLAMP);
        // crescent
        float r = 15 * density, cx = w - 46 * density, cy = 52 * density;
        Path a = new Path(), b = new Path();
        a.addCircle(cx, cy, r, Path.Direction.CW);
        b.addCircle(cx + r * 0.5f, cy - r * 0.25f, r * 0.86f, Path.Direction.CW);
        moon.reset();
        if (android.os.Build.VERSION.SDK_INT >= 19) { moon.set(a); moon.op(b, Path.Op.DIFFERENCE); } else moon.set(a);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        anim = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        anim.setDuration(24000);
        anim.setInterpolator(new LinearInterpolator());
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) { t = (Float) a.getAnimatedValue(); invalidate(); }
        });
        anim.start();
    }

    @Override protected void onDetachedFromWindow() { if (anim != null) anim.cancel(); super.onDetachedFromWindow(); }

    /** Golden sparks flying out from (x, y) in this view's coordinates. */
    void burst(float x, float y) {
        bx = x; by = y;
        Random r = new Random();
        for (int i = 0; i < B; i++) {
            ba[i] = (float) (r.nextFloat() * Math.PI * 2);
            bs[i] = (0.35f + r.nextFloat() * 0.65f) * getWidth() * 0.55f;
            bz[i] = (1.5f + r.nextFloat() * 3f) * density;
        }
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(1000);
        a.setInterpolator(new DecelerateInterpolator(1.6f));
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator v) { bp = (Float) v.getAnimatedValue(); invalidate(); }
        });
        a.start();
    }

    private void star(Canvas c, float cx, float cy, float rad, float rot, int alpha) {
        line.setColor(Color.argb(alpha, 242, 194, 91));
        for (int sq = 0; sq < 2; sq++) {
            tmp.reset();
            for (int k = 0; k < 4; k++) {
                double ang = rot + sq * Math.PI / 4 + k * Math.PI / 2;
                float x = cx + rad * (float) Math.cos(ang), y = cy + rad * (float) Math.sin(ang);
                if (k == 0) tmp.moveTo(x, y); else tmp.lineTo(x, y);
            }
            tmp.close();
            c.drawPath(tmp, line);
        }
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawRect(0, 0, w, h, bg);

        // drifting glows
        p.setStyle(Paint.Style.FILL);
        c.save();
        c.translate(w * (0.88f + 0.06f * (float) Math.sin(t)), h * (0.10f + 0.04f * (float) Math.cos(t)));
        p.setShader(gold); c.drawCircle(0, 0, w * 0.75f, p);
        c.restore();
        c.save();
        c.translate(w * (0.08f + 0.08f * (float) Math.cos(t)), h * (0.86f + 0.04f * (float) Math.sin(t)));
        p.setShader(em); c.drawCircle(0, 0, w * 0.9f, p);
        c.restore();
        p.setShader(null);

        // stars
        for (int i = 0; i < N; i++) {
            float tw = 0.5f + 0.5f * (float) Math.sin(t * sp[i] + sph[i]);
            p.setColor(Color.argb((int) (40 + 190 * tw), 255, 244, 214));
            c.drawCircle(sx[i] * w, sy[i] * h, sr[i] * density * (0.7f + 0.5f * tw), p);
        }

        // turning khatam pattern behind the logo
        float cx = w / 2f, cy = h * 0.27f, R = w * 0.62f;
        for (int i = 0; i < 4; i++) {
            float rad = R * (1f - 0.2f * i);
            float rot = (i % 2 == 0 ? 0.5f : -0.5f) * t + i * 0.39f;
            star(c, cx, cy, rad, rot, 46 - i * 8);
        }
        line.setColor(Color.argb(22, 242, 194, 91));
        c.drawCircle(cx, cy, R * 0.74f, line);
        c.drawCircle(cx, cy, R * 1.02f, line);

        // crescent with halo
        p.setColor(Color.argb(28, 242, 194, 91));
        c.drawCircle(w - 40 * density, 50 * density, 38 * density, p);
        p.setColor(Color.parseColor("#F8DE8F"));
        c.drawPath(moon, p);
        float tw = 0.5f + 0.5f * (float) Math.sin(t * 3);
        p.setColor(Color.argb((int) (120 + 135 * tw), 255, 241, 190));
        c.drawCircle(w - 78 * density, 70 * density, (1.8f + tw) * density, p);

        // success burst
        if (bp >= 0f && bp < 1f) {
            for (int i = 0; i < B; i++) {
                float d = bs[i] * bp;
                p.setColor(Color.argb((int) (255 * (1f - bp)), 248, 222, 143));
                c.drawCircle(bx + d * (float) Math.cos(ba[i]), by + d * (float) Math.sin(ba[i]), bz[i] * (1f - 0.6f * bp), p);
            }
        }
    }
}
