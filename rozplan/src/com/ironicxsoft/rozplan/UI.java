package com.ironicxsoft.rozplan;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Design system for "Roz ka Plan": white / near-black ground, ink text, tomato accent, soft peach panels
 * (light + dark, following the phone setting). Layout rules: no fixed heights on text, long text wraps,
 * chips wrap in a FlowLayout, dialogs scroll and are width-capped, so nothing is ever cut off.
 */
public final class UI {
    public static boolean night;
    public static int BG, INK, MUTED, LINE, ACC, ACC2, PAN, CARD, OK, ON_INK, BLUE;

    private UI() { }

    public static void init(Context c) {
        night = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        ACC = Color.parseColor("#FF5A36");
        BLUE = Color.parseColor("#3A8DDE");
        if (night) {
            BG = Color.parseColor("#121417"); INK = Color.parseColor("#EEF0F3"); MUTED = Color.parseColor("#9AA1AD");
            LINE = Color.parseColor("#272B31"); ACC2 = Color.parseColor("#FF7A5C"); PAN = Color.parseColor("#24191A");
            CARD = Color.parseColor("#1B1E23"); OK = Color.parseColor("#4CC985");
        } else {
            BG = Color.WHITE; INK = Color.parseColor("#16181D"); MUTED = Color.parseColor("#646A75");
            LINE = Color.parseColor("#E7E8EC"); ACC2 = Color.parseColor("#D63D1C"); PAN = Color.parseColor("#FFF2ED");
            CARD = Color.parseColor("#F6F7F9"); OK = Color.parseColor("#1F8A4C");
        }
        ON_INK = BG;
    }

    public abstract static class Tap implements View.OnClickListener {
        public abstract void tap();
        @Override public final void onClick(View v) { tap(); }
    }

    /** Returns true when the dialog should close. */
    public interface Action { boolean run(); }

    public static int dp(Context c, int v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    public static GradientDrawable round(Context c, int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static GradientDrawable outline(Context c, int fill, int strokeColor, int strokeDp, int radiusDp) {
        GradientDrawable g = round(c, fill, radiusDp);
        g.setStroke(dp(c, strokeDp), strokeColor);
        return g;
    }

    public static int alpha(int color, int a) { return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color)); }

    private static int darker(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= 0.85f;
        return Color.HSVToColor(Color.alpha(color), hsv);
    }

    private static StateListDrawable pressable(Context c, int color, int radiusDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, round(c, darker(color), radiusDp));
        s.addState(new int[]{}, round(c, color, radiusDp));
        return s;
    }

    public static TextView label(Context c, String t, int sp, int color, boolean bold) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    /** Heavy rounded display type used for headings and big numbers. */
    public static TextView display(Context c, String t, int sp, int color) {
        TextView v = label(c, t, sp, color, false);
        v.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        v.setLineSpacing(0, 0.95f);
        return v;
    }

    /** Filled rounded button; grows with the text, never clips it. */
    public static TextView button(Context c, String t, int bg, int fg) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextColor(fg);
        v.setTextSize(15);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.CENTER);
        v.setMinHeight(dp(c, 46));
        v.setMinWidth(dp(c, 46));
        v.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10));
        v.setBackground(pressable(c, bg, 12));
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    public static TextView textButton(Context c, String t, int color) {
        TextView v = label(c, t, 15, color, true);
        v.setGravity(Gravity.CENTER);
        v.setMinHeight(dp(c, 44));
        v.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8));
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    /** Pill chip (habits, days). */
    public static TextView chip(Context c, String t, boolean on) {
        TextView v = label(c, t, 15, on ? BG : INK, true);
        v.setGravity(Gravity.CENTER);
        v.setMinHeight(dp(c, 44));
        v.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8));
        v.setBackground(outline(c, on ? INK : BG, on ? INK : LINE, 2, 22));
        v.setClickable(true);
        return v;
    }

    public static void styleChip(Context c, TextView v, boolean on) {
        v.setTextColor(on ? BG : INK);
        v.setBackground(outline(c, on ? INK : BG, on ? INK : LINE, 2, 22));
    }

    public static EditText input(Context c, String hint, String text, int type) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setText(text);
        e.setInputType(type);
        e.setTextSize(16);
        e.setTextColor(INK);
        e.setHintTextColor(MUTED);
        e.setBackground(outline(c, BG, LINE, 2, 12));
        e.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10));
        e.setMinHeight(dp(c, 48));
        return e;
    }

    public static LinearLayout.LayoutParams lp(Context c, int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    public static LinearLayout.LayoutParams weight(Context c, float w, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    public static LinearLayout card(Context c, int fill, int radiusDp) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(c, fill, radiusDp));
        return l;
    }

    /** Wrapping row layout: children flow onto new lines instead of being cut off. */
    public static final class Flow extends ViewGroup {
        private final int hGap, vGap;
        public Flow(Context c, int hGapDp, int vGapDp) { super(c); hGap = dp(c, hGapDp); vGap = dp(c, vGapDp); }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            int maxW = MeasureSpec.getSize(wSpec) - getPaddingLeft() - getPaddingRight();
            int x = 0, y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                if (ch.getVisibility() == GONE) continue;
                ch.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int cw = ch.getMeasuredWidth(), chh = ch.getMeasuredHeight();
                if (x > 0 && x + cw > maxW) { x = 0; y += rowH + vGap; rowH = 0; }
                x += cw + hGap;
                rowH = Math.max(rowH, chh);
            }
            setMeasuredDimension(MeasureSpec.getSize(wSpec), y + rowH + getPaddingTop() + getPaddingBottom());
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int maxW = r - l - getPaddingLeft() - getPaddingRight();
            int x = 0, y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                if (ch.getVisibility() == GONE) continue;
                int cw = ch.getMeasuredWidth(), chh = ch.getMeasuredHeight();
                if (x > 0 && x + cw > maxW) { x = 0; y += rowH + vGap; rowH = 0; }
                ch.layout(getPaddingLeft() + x, getPaddingTop() + y, getPaddingLeft() + x + cw, getPaddingTop() + y + chh);
                x += cw + hGap;
                rowH = Math.max(rowH, chh);
            }
        }
    }

    public static final class CappedScroll extends ScrollView {
        private final int maxH;
        public CappedScroll(Context c) { super(c); maxH = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.62f); }
        @Override protected void onMeasure(int w, int h) { super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST)); }
    }

    public static Dialog dialog(Context c, String title, View body, String pos, final Action onPos, String neg) {
        final Dialog d = new Dialog(c);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(round(c, night ? CARD : BG, 24));
        root.setPadding(dp(c, 22), dp(c, 22), dp(c, 14), dp(c, 10));
        TextView t = display(c, title, 22, INK);
        t.setPadding(0, 0, dp(c, 8), dp(c, 10));
        root.addView(t);
        CappedScroll sc = new CappedScroll(c);
        sc.setVerticalScrollBarEnabled(false);
        sc.setPadding(0, 0, dp(c, 8), 0);
        sc.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        row.setPadding(0, dp(c, 10), 0, 0);
        if (neg != null) {
            TextView n = textButton(c, neg, MUTED);
            n.setOnClickListener(new Tap() { @Override public void tap() { d.dismiss(); } });
            row.addView(n);
        }
        if (pos != null) {
            TextView p = textButton(c, pos, ACC2);
            p.setOnClickListener(new Tap() { @Override public void tap() { if (onPos == null || onPos.run()) d.dismiss(); } });
            row.addView(p);
        }
        root.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int sw = c.getResources().getDisplayMetrics().widthPixels;
            w.setLayout(Math.min(sw - dp(c, 28), dp(c, 420)), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return d;
    }

    public static Dialog message(Context c, String title, String msg, String pos, Action onPos, String neg) {
        TextView m = label(c, msg, 15, INK, false);
        m.setLineSpacing(0, 1.15f);
        return dialog(c, title, m, pos, onPos, neg);
    }
}
