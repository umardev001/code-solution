package com.sadqa.tracker;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Custom-drawn views used by the dashboard. */
public final class Widgets {
    private Widgets() { }

    /** Rounded progress bar that animates to its value. */
    public static final class BarView extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private float shown = 0f;

        public BarView(Context c) {
            super(c);
            track.setColor(Color.argb(60, 255, 255, 255));
            fill.setColor(UI.GOLD);
        }

        public void setProgress(float target) {
            final float to = Math.max(0f, Math.min(1f, target));
            ValueAnimator a = ValueAnimator.ofFloat(shown, to);
            a.setDuration(750);
            a.setInterpolator(new DecelerateInterpolator());
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator v) { shown = (Float) v.getAnimatedValue(); invalidate(); }
            });
            a.start();
        }

        @Override protected void onDraw(Canvas c) {
            float h = getHeight(), r = h / 2f;
            box.set(0, 0, getWidth(), h);
            c.drawRoundRect(box, r, r, track);
            if (shown > 0f) {
                box.set(0, 0, Math.max(h, getWidth() * shown), h);
                c.drawRoundRect(box, r, r, fill);
            }
        }
    }
}
