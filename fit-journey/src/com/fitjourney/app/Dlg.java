package com.fitjourney.app;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** All the input dialogs. */
public final class Dlg {
    private Dlg() { }

    public interface Num { void done(double v); }

    public static EditText field(Context c, LinearLayout form, String caption, String value, String hint, boolean decimal) {
        form.addView(UI.caption(c, caption));
        EditText e = UI.input(c, hint, value, decimal ? (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL) : InputType.TYPE_CLASS_NUMBER);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
        e.setSingleLine(true);
        form.addView(e);
        return e;
    }

    public static EditText text(Context c, LinearLayout form, String caption, String value, String hint) {
        form.addView(UI.caption(c, caption));
        EditText e = UI.textInput(c, hint, value, false);
        form.addView(e);
        return e;
    }

    private static String fmt(double v, boolean decimal) {
        if (v <= 0) return "";
        return decimal ? Fit.kg(v).replaceAll("\\.0$", "") : Long.toString(Math.round(v));
    }

    private static void keyboard(Dialog d) {
        if (d.getWindow() != null) d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    /**
     * One-number prompt. add=true: the typed number is ADDED by the caller (shows `current` as info);
     * add=false: the typed number replaces the value. resetLabel (nullable) adds a red "reset" button that returns 0.
     */
    public static void number(final Context c, String title, String caption, String unit, double current, final boolean decimal,
                              boolean add, final int tint, String info, String resetLabel, final Num cb) {
        LinearLayout form = new LinearLayout(c);
        form.setOrientation(LinearLayout.VERTICAL);
        if (info != null) {
            TextView t = UI.label(c, info, 14, UI.MUTED, false);
            t.setLineSpacing(0, 1.15f);
            form.addView(t);
        }
        form.addView(UI.caption(c, caption));
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final EditText e = UI.input(c, "0", add ? "" : fmt(current, decimal), decimal ? (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL) : InputType.TYPE_CLASS_NUMBER);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
        e.setSingleLine(true);
        e.setTextSize(24);
        e.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        e.setTextColor(tint);
        row.addView(e, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView u = UI.label(c, unit, 16, UI.MUTED, true);
        u.setPadding(UI.dp(c, 12), 0, UI.dp(c, 4), 0);
        row.addView(u);
        form.addView(row);
        Dialog d = UI.dialog(c, title, form, "Save", new UI.Action() {
            @Override public boolean run() {
                double v = Fit.parse(e.getText());
                if (v <= 0) { Toast.makeText(c, "Enter a number", Toast.LENGTH_SHORT).show(); return false; }
                cb.done(v);
                return true;
            }
        }, "Cancel", resetLabel, resetLabel == null ? null : new UI.Action() {
            @Override public boolean run() { cb.done(-1); return true; }
        });
        keyboard(d);
        d.show();
        e.requestFocus();
    }

    /** Edit every metric of one day. */
    public static void editDay(final Context c, final String key, final Runnable changed) {
        Store.Day d = Store.get(key);
        LinearLayout form = new LinearLayout(c);
        form.setOrientation(LinearLayout.VERTICAL);
        final EditText w = field(c, form, "WEIGHT (kg)", fmt(d.weight, true), "e.g. 72.4", true);
        final EditText st = field(c, form, "STEPS", fmt(d.steps, false), "e.g. 8000", false);
        final EditText wa = field(c, form, "WATER (ml)", fmt(d.water, false), "e.g. 2000", false);
        final EditText sl = field(c, form, "SLEEP (hours)", fmt(d.sleep, true), "e.g. 7.5", true);
        final EditText wo = field(c, form, "WORKOUT (minutes)", fmt(d.workout, false), "e.g. 30", false);
        final EditText ca = field(c, form, "CALORIES EATEN (kcal)", fmt(d.calories, false), "e.g. 1700", false);
        String title = key.equals(Fit.today()) ? "Today" : Fit.dow(key) + ", " + Fit.shortDate(key);
        Dialog dlg = UI.dialog(c, title, form, "Save", new UI.Action() {
            @Override public boolean run() {
                double wv = Fit.parse(w.getText());
                if (wv > 0 && (wv < 20 || wv > 400)) { Toast.makeText(c, "Weight looks wrong", Toast.LENGTH_SHORT).show(); return false; }
                synchronized (Store.class) {
                    Store.Day x = Store.mut(key);
                    x.weight = Math.max(0, wv);
                    x.steps = (int) Math.max(0, Fit.parse(st.getText()));
                    x.water = (int) Math.max(0, Fit.parse(wa.getText()));
                    x.sleep = Math.max(0, Fit.parse(sl.getText()));
                    x.workout = (int) Math.max(0, Fit.parse(wo.getText()));
                    x.calories = (int) Math.max(0, Fit.parse(ca.getText()));
                    Store.save();
                }
                changed.run();
                return true;
            }
        }, "Cancel", null, null);
        dlg.show();
    }

    /** Name, height, start & goal weight. */
    public static void profile(final Context c, final Runnable changed) {
        final Store.Profile p = Store.profile();
        LinearLayout form = new LinearLayout(c);
        form.setOrientation(LinearLayout.VERTICAL);
        final EditText n = text(c, form, "YOUR NAME", p.name, "Name");
        final EditText h = field(c, form, "HEIGHT (cm)", fmt(p.heightCm, false), "170", false);
        final EditText s = field(c, form, "STARTING WEIGHT (kg)", fmt(p.startKg, true), "80", true);
        final EditText g = field(c, form, "GOAL WEIGHT (kg)", fmt(p.goalKg, true), "70", true);
        Dialog dlg = UI.dialog(c, "My profile", form, "Save", new UI.Action() {
            @Override public boolean run() {
                double hv = Fit.parse(h.getText()), sv = Fit.parse(s.getText()), gv = Fit.parse(g.getText());
                if (hv < 80 || hv > 250) { Toast.makeText(c, "Height in cm (80–250)", Toast.LENGTH_SHORT).show(); return false; }
                if (sv < 20 || sv > 400 || gv < 20 || gv > 400) { Toast.makeText(c, "Weights in kg (20–400)", Toast.LENGTH_SHORT).show(); return false; }
                p.name = n.getText().toString().trim(); p.heightCm = (int) hv; p.startKg = sv; p.goalKg = gv;
                Store.setProfile(p);
                changed.run();
                return true;
            }
        }, "Cancel", null, null);
        dlg.show();
    }

    /** Daily targets. */
    public static void goals(final Context c, final Runnable changed) {
        final Store.Profile p = Store.profile();
        LinearLayout form = new LinearLayout(c);
        form.setOrientation(LinearLayout.VERTICAL);
        final EditText wa = field(c, form, "WATER (ml per day)", fmt(p.waterGoal, false), "2500", false);
        final EditText st = field(c, form, "STEPS per day", fmt(p.stepGoal, false), "8000", false);
        final EditText wo = field(c, form, "WORKOUT (minutes per day)", fmt(p.workoutGoal, false), "30", false);
        final EditText sl = field(c, form, "SLEEP (hours per night)", fmt(p.sleepGoal, true), "8", true);
        final EditText ca = field(c, form, "CALORIE BUDGET (kcal per day)", fmt(p.calGoal, false), "1800", false);
        Dialog dlg = UI.dialog(c, "Daily goals", form, "Save", new UI.Action() {
            @Override public boolean run() {
                double a = Fit.parse(wa.getText()), b = Fit.parse(st.getText()), o = Fit.parse(wo.getText()), l = Fit.parse(sl.getText()), k = Fit.parse(ca.getText());
                if (a < 250 || b < 500 || o < 5 || l < 3 || k < 500) { Toast.makeText(c, "Please enter realistic goals", Toast.LENGTH_SHORT).show(); return false; }
                p.waterGoal = (int) a; p.stepGoal = (int) b; p.workoutGoal = (int) o; p.sleepGoal = l; p.calGoal = (int) k;
                Store.setProfile(p);
                changed.run();
                return true;
            }
        }, "Cancel", null, null);
        dlg.show();
    }

    public static void confirm(Context c, String title, String msg, String yes, UI.Action onYes) {
        UI.message(c, title, msg, yes, onYes, "Cancel").show();
    }

    static int dim(int color, int a) { return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color)); }
    static View spacer(Context c, int dp) { View v = new View(c); v.setLayoutParams(new LinearLayout.LayoutParams(1, UI.dp(c, dp))); return v; }
}
