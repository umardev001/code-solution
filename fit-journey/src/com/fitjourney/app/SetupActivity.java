package com.fitjourney.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Launcher: first-run welcome + profile setup. Forwards straight to the app once set up. */
public class SetupActivity extends Activity {
    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        Store.init(this);
        Reminders.schedule(this);
        if (Store.hasProfile()) { startActivity(new Intent(this, MainActivity.class)); finish(); return; }
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(Color.parseColor("#F8F9FF"));
            getWindow().setNavigationBarColor(Color.parseColor("#EAF0FF"));
            int f = android.os.Build.VERSION.SDK_INT >= 23 ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0;
            if (android.os.Build.VERSION.SDK_INT >= 26) f |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(f);
        }
        build();
    }

    private int dp(int v) { return UI.dp(this, v); }

    private void build() {
        FrameLayout root = new FrameLayout(this);
        root.addView(new Widgets.OrbsView(this, true, UI.ACCENT, UI.WATER, UI.STEPS), new FrameLayout.LayoutParams(-1, -1));
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(20), dp(34), dp(20), dp(30));

        Widgets.RingsView rings = new Widgets.RingsView(this);
        rings.set(new float[]{0.82f, 0.64f, 0.46f}, "Fit", "Journey", new float[3]);
        col.addView(rings, new LinearLayout.LayoutParams(dp(170), dp(170)));

        TextView title = UI.label(this, "Let’s start your journey", 28, UI.INK, true);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setGravity(Gravity.CENTER);
        col.addView(title, UI.lp(this, -2, -2, 0, 18, 0, 0));
        TextView sub = UI.label(this, "Track your weight, steps and water every day – and watch the kilos melt away.", 14.5f, UI.MUTED, false);
        sub.setGravity(Gravity.CENTER);
        sub.setLineSpacing(0, 1.2f);
        col.addView(sub, UI.lp(this, -2, -2, 12, 8, 12, 0));

        LinearLayout card = UI.card(this);
        ((LinearLayout.LayoutParams) card.getLayoutParams()).setMargins(0, dp(22), 0, 0);
        final EditText name = Dlg.text(this, card, "YOUR NAME", "", "e.g. Umar");
        final EditText height = Dlg.field(this, card, "HEIGHT (cm)", "", "e.g. 170", false);
        final EditText cur = Dlg.field(this, card, "CURRENT WEIGHT (kg)", "", "e.g. 85", true);
        final EditText goal = Dlg.field(this, card, "GOAL WEIGHT (kg)", "", "e.g. 72", true);
        final EditText water = Dlg.field(this, card, "WATER GOAL (ml per day)", "2500", "2500", false);
        final EditText steps = Dlg.field(this, card, "STEPS GOAL (per day)", "8000", "8000", false);
        col.addView(card);

        TextView go = UI.button(this, "Start my journey  →", UI.ACCENT, Color.WHITE);
        go.setTextSize(16);
        go.setMinHeight(dp(56));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor("#6D4AFF"), Color.parseColor("#33A1FF")});
        g.setCornerRadius(dp(28));
        go.setBackground(g);
        go.setElevation(dp(8));
        go.setOnClickListener(new UI.Tap() {
            @Override public void tap() {
                double h = Fit.parse(((EditText) height).getText()), c = Fit.parse(cur.getText()), t = Fit.parse(goal.getText());
                double w = Fit.parse(water.getText()), st = Fit.parse(steps.getText());
                if (h < 80 || h > 250) { toast("Enter your height in cm"); return; }
                if (c < 20 || c > 400) { toast("Enter your current weight in kg"); return; }
                if (t < 20 || t > 400) { toast("Enter your goal weight in kg"); return; }
                if (w < 250) w = 2500;
                if (st < 500) st = 8000;
                Store.Profile p = new Store.Profile();
                p.name = name.getText().toString().trim(); p.heightCm = (int) h; p.startKg = c; p.goalKg = t;
                p.waterGoal = (int) w; p.stepGoal = (int) st;
                Store.setProfile(p);
                Store.mut(Fit.today()).weight = c;
                Store.save();
                startActivity(new Intent(SetupActivity.this, MainActivity.class));
                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });
        col.addView(go, UI.lp(this, -1, -2, 0, 20, 0, 0));
        TextView note = UI.label(this, "🔒  Your data stays on this phone", 12, UI.MUTED, false);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(14), 0, 0);
        col.addView(note);

        sv.addView(col, new ViewGroup.LayoutParams(-1, -2));
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        for (int i = 0; i < col.getChildCount(); i++) {
            View v = col.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(dp(30));
            v.animate().alpha(1f).translationY(0).setStartDelay(90L * i).setDuration(520).setInterpolator(new AccelerateDecelerateInterpolator()).start();
        }
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
