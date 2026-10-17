package com.dailyboost.app;

import android.app.Dialog;
import android.content.Context;
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
 * Small UI toolkit. Rules that keep the layout from breaking on any phone / font size:
 * no fixed heights on text controls (min-height + padding instead), single-line labels are
 * ellipsized, long text wraps, dialogs scroll and are width-capped.
 */
public final class UI {
    public static final int GREEN = Color.parseColor("#0E9F7E");
    public static final int GREEN_DARK = Color.parseColor("#0B5F4E");
    public static final int BG = Color.parseColor("#F3F6FB");
    public static final int INK = Color.parseColor("#0F172A");
    public static final int MUTED = Color.parseColor("#64748B");
    public static final int AMBER = Color.parseColor("#E8890C");
    public static final int RED = Color.parseColor("#E5484D");
    public static final int FIELD = Color.parseColor("#EEF2F7");
    public static final int BLUE = Color.parseColor("#3B82F6");
    public static final int VIOLET = Color.parseColor("#8B5CF6");
    public static final int ROSE = Color.parseColor("#F43F5E");
    public static final int TEAL = Color.parseColor("#14B8A6");

    /** Exposed for UI snapshot tests. */
    public static View lastDialogRoot;

    private UI() { }

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

    private static int darker(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= 0.82f;
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

    /** Filled rounded button – never clips its text, grows with font size, one line. */
    public static TextView button(Context c, String t, int bg, int fg) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextColor(fg);
        v.setTextSize(14.5f);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.CENTER);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setMinHeight(dp(c, 46));
        v.setPadding(dp(c, 10), dp(c, 10), dp(c, 10), dp(c, 10));
        v.setBackground(pressable(c, bg, 24));
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    /** Quiet text-only button (dialog actions). */
    public static TextView textButton(Context c, String t, int color) {
        TextView v = label(c, t, 15, color, true);
        v.setGravity(Gravity.CENTER);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setMinHeight(dp(c, 44));
        v.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8));
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(c, Color.WHITE, 24));
        l.setElevation(dp(c, 3));
        l.setPadding(dp(c, 18), dp(c, 16), dp(c, 18), dp(c, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8));
        l.setLayoutParams(lp);
        return l;
    }

    public static EditText input(Context c, String hint, String text, int type) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setText(text);
        e.setInputType(type);
        e.setTextSize(16);
        e.setTextColor(INK);
        e.setHintTextColor(Color.parseColor("#93A89F"));
        e.setBackground(round(c, FIELD, 12));
        e.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        e.setMinHeight(dp(c, 48));
        e.setGravity(Gravity.CENTER_VERTICAL);
        return e;
    }

    public static EditText textInput(Context c, String hint, String text, boolean multiline) {
        EditText e = input(c, hint, text, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        if (multiline) { e.setMinLines(2); e.setMaxLines(5); e.setGravity(Gravity.TOP); }
        else e.setSingleLine(true);
        return e;
    }

    public static LinearLayout.LayoutParams lp(Context c, int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    public static LinearLayout.LayoutParams wrapRow(Context c, int top) { return lp(c, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, top, 0, 0); }

    /** Section label inside a form. */
    public static TextView caption(Context c, String t) {
        TextView v = label(c, t, 12, MUTED, true);
        v.setPadding(dp(c, 2), dp(c, 12), 0, dp(c, 4));
        return v;
    }

    /** Scroll view that never grows taller than a fraction of the screen. */
    public static final class CappedScroll extends ScrollView {
        private final int maxH;
        public CappedScroll(Context c) {
            super(c);
            maxH = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.62f);
        }
        @Override protected void onMeasure(int w, int h) {
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST));
        }
    }

    /** Own dialog: rounded card, scrollable body, width-capped, text-button row. */
    public static Dialog dialog(Context c, String title, View body, String pos, final Action onPos, String neg, String neutral, final Action onNeutral) {
        final Dialog d = new Dialog(c);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(round(c, Color.WHITE, 28));
        root.setPadding(dp(c, 20), dp(c, 20), dp(c, 14), dp(c, 10));

        TextView t = label(c, title, 20, INK, true);
        t.setPadding(0, 0, dp(c, 6), dp(c, 8));
        root.addView(t);

        CappedScroll sc = new CappedScroll(c);
        sc.setFillViewport(false);
        sc.setVerticalScrollBarEnabled(false);
        sc.setPadding(0, 0, dp(c, 6), 0);
        sc.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(c, 10), 0, 0);
        if (neutral != null) {
            TextView n = textButton(c, neutral, RED);
            n.setOnClickListener(new Tap() { @Override public void tap() { if (onNeutral == null || onNeutral.run()) d.dismiss(); } });
            row.addView(n);
        }
        View spacer = new View(c);
        row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        if (neg != null) {
            TextView n = textButton(c, neg, MUTED);
            n.setOnClickListener(new Tap() { @Override public void tap() { d.dismiss(); } });
            row.addView(n);
        }
        if (pos != null) {
            TextView p = textButton(c, pos, GREEN);
            p.setOnClickListener(new Tap() { @Override public void tap() { if (onPos == null || onPos.run()) d.dismiss(); } });
            row.addView(p);
        }
        root.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        lastDialogRoot = root;
        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int sw = c.getResources().getDisplayMetrics().widthPixels;
            int width = Math.min(sw - dp(c, 28), dp(c, 420));
            w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return d;
    }

    public static Dialog message(Context c, String title, String msg, String pos, Action onPos, String neg) {
        TextView m = label(c, msg, 15, INK, false);
        m.setLineSpacing(0, 1.15f);
        return dialog(c, title, m, pos, onPos, neg, null, null);
    }

    /** Soft diagonal gradient with rounded corners. */
    public static GradientDrawable gradient(Context c, int from, int to, int radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{from, to});
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static int alpha(int color, int a) { return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color)); }

    /** Rounded-square tinted chip holding an emoji. */
    public static TextView iconChip(Context c, String emoji, int tint, int sizeDp) {
        TextView v = new TextView(c);
        v.setText(emoji);
        v.setTextSize(sizeDp * 0.42f);
        v.setGravity(Gravity.CENTER);
        v.setIncludeFontPadding(false);
        v.setBackground(round(c, alpha(tint, 38), 16));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    /** First emoji-looking character group in a title, or a fallback. */
    public static String emojiOf(String title, String fallback) {
        if (title == null) return fallback;
        for (int i = 0; i < title.length(); ) {
            int cp = title.codePointAt(i);
            if (cp >= 0x2190) return new String(Character.toChars(cp));
            i += Character.charCount(cp);
        }
        return fallback;
    }

    /** Title without its trailing emoji. */
    public static String plainTitle(String title) {
        if (title == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < title.length(); ) {
            int cp = title.codePointAt(i);
            if (cp < 0x2190) sb.appendCodePoint(cp);
            i += Character.charCount(cp);
        }
        return sb.toString().trim();
    }
}
