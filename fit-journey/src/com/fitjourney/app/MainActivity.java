package com.fitjourney.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Shell with a bottom tab bar: Today · Progress · History · Me. */
public class MainActivity extends Activity implements SensorEventListener {
    private static final String[] ICONS = {"🏠", "⚖️", "📅", "⚙️"};
    private static final String[] NAMES = {"Today", "Progress", "History", "Me"};
    private static final int[][] BANNER = {{0xFF6D4AFF, 0xFF33A1FF}, {0xFF8B5CF6, 0xFFEC6FB5}, {0xFF12B8E6, 0xFF6D8BFF}, {0xFF6D4AFF, 0xFFB06BFF}};

    private FrameLayout content;
    private LinearLayout nav;
    private ScrollView sv;
    private int tab = 0;
    private final boolean[] seen = new boolean[4];
    private long lastRebuild = 0, lastTouch = 0;
    private int range = 30;                       // weight chart range in days (0 = all)
    private static float[] lastRings = new float[3];
    private static float lastWater = 0f;

    /* ------------------------------------------------------------ lifecycle */
    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        Store.init(this);
        if (!Store.hasProfile()) { startActivity(new Intent(this, SetupActivity.class)); finish(); return; }
        Reminders.schedule(this);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(BANNER[0][0]);
            getWindow().setNavigationBarColor(Color.WHITE);
            if (Build.VERSION.SDK_INT >= 26) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        FrameLayout root = new FrameLayout(this);
        root.addView(new Widgets.OrbsView(this, false, UI.ACCENT, UI.WATER, UI.STEPS), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        content = new FrameLayout(this);
        shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        nav = new LinearLayout(this);
        nav.setBackground(navBg());
        nav.setPadding(dp(8), dp(6), dp(8), dp(6));
        nav.setElevation(dp(14));
        shell.addView(nav, new LinearLayout.LayoutParams(-1, -2));
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        buildNav();
        render(false);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!Store.hasProfile()) return;
        SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor st = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (st != null) sm.registerListener(this, st, SensorManager.SENSOR_DELAY_UI);
        render(true);
    }

    @Override protected void onPause() {
        SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sm != null) sm.unregisterListener(this);
        super.onPause();
    }

    @Override public void onSensorChanged(SensorEvent e) {
        Steps.onReading(this, (long) e.values[0]);
        long now = System.currentTimeMillis();
        if (tab == 0 && now - lastRebuild > 4000 && now - lastTouch > 2500) render(true);   // never rebuild while the user is scrolling
    }

    @Override public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        lastTouch = System.currentTimeMillis();
        return super.dispatchTouchEvent(ev);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private int dp(int v) { return UI.dp(this, v); }

    /* ------------------------------------------------------------------ nav */
    private GradientDrawable navBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadii(new float[]{dp(26), dp(26), dp(26), dp(26), 0, 0, 0, 0});
        return g;
    }

    private void buildNav() {
        nav.removeAllViews();
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            boolean on = i == tab;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, dp(4), 0, dp(2));
            TextView ic = UI.label(this, ICONS[i], 20, UI.INK, false);
            ic.setGravity(Gravity.CENTER);
            ic.setIncludeFontPadding(false);
            ic.setPadding(dp(18), dp(6), dp(18), dp(6));
            if (on) {
                GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor("#E6DFFF"), Color.parseColor("#D3EEFF")});
                g.setCornerRadius(dp(18));
                ic.setBackground(g);
            } else ic.setAlpha(0.6f);
            item.addView(ic);
            TextView tx = UI.label(this, NAMES[i], 11, on ? UI.ACCENT : UI.MUTED, on);
            tx.setGravity(Gravity.CENTER);
            item.addView(tx, UI.lp(this, -2, -2, 0, 2, 0, 0));
            item.setClickable(true);
            item.setOnClickListener(new UI.Tap() { @Override public void tap() { if (tab != idx) { tab = idx; buildNav(); render(false); } } });
            nav.addView(item, new LinearLayout.LayoutParams(0, -2, 1f));
            if (on) {
                ic.setScaleX(0.8f); ic.setScaleY(0.8f);
                ic.animate().scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(new OvershootInterpolator(3f)).start();
            }
        }
    }

    /* --------------------------------------------------------------- render */
    private void render(boolean keepScroll) {
        if (content == null) return;
        lastRebuild = System.currentTimeMillis();
        if (Build.VERSION.SDK_INT >= 21) getWindow().setStatusBarColor(BANNER[tab][0]);
        final int keepY = keepScroll && sv != null ? sv.getScrollY() : 0;
        content.removeAllViews();
        sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        switch (tab) {
            case 0: today(col); break;
            case 1: progress(col); break;
            case 2: history(col); break;
            default: me(col); break;
        }
        View pad = new View(this);
        col.addView(pad, new LinearLayout.LayoutParams(1, dp(24)));
        sv.addView(col, new ViewGroup.LayoutParams(-1, -2));
        content.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        if (!seen[tab]) {
            seen[tab] = true;
            for (int i = 0; i < col.getChildCount(); i++) {
                View v = col.getChildAt(i);
                v.setAlpha(0f);
                v.setTranslationY(dp(26));
                v.animate().alpha(1f).translationY(0).setStartDelay(65L * Math.min(i, 7)).setDuration(460).setInterpolator(new DecelerateInterpolator()).start();
            }
        }
        sv.post(new Runnable() { @Override public void run() { sv.scrollTo(0, keepY); } });
    }

    private final Runnable again = new Runnable() { @Override public void run() { render(true); } };

    /* ---------------------------------------------------------- small kit */
    private LinearLayout header(LinearLayout col, String small, String big, View right) {
        Widgets.Banner top = new Widgets.Banner(this, BANNER[tab][0], BANNER[tab][1]);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(22), dp(20), dp(18), dp(64));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(UI.label(this, small, 13.5f, Color.argb(215, 255, 255, 255), false));
        TextView b = UI.label(this, big, 27, Color.WHITE, true);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        names.addView(b);
        top.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
        if (right != null) top.addView(right);
        col.addView(top, new LinearLayout.LayoutParams(-1, -2));
        return top;
    }

    private TextView bannerPill(String text) {
        TextView v = UI.label(this, text, 13, Color.WHITE, true);
        v.setBackground(UI.round(this, Color.argb(60, 255, 255, 255), 17));
        v.setPadding(dp(13), dp(7), dp(13), dp(7));
        v.setSingleLine(true);
        return v;
    }

    /** Layout params that pull a card up so it overlaps the banner above it. */
    private LinearLayout.LayoutParams overlap() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(16), -dp(44), dp(16), dp(6));
        return lp;
    }

    private TextView pill(String text, int color) {
        TextView v = UI.label(this, text, 12.5f, color, true);
        v.setBackground(UI.round(this, UI.alpha(color, 36), 16));
        v.setPadding(dp(12), dp(6), dp(12), dp(6));
        v.setSingleLine(true);
        return v;
    }

    private LinearLayout bar(int color, float frac, int heightDp) {
        LinearLayout b = new LinearLayout(this);
        b.setBackground(UI.round(this, UI.alpha(color, 40), heightDp));
        float f = Math.max(0f, Math.min(1f, frac));
        View fill = new View(this);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Widgets.darker(color, 0.8f), Widgets.lighter(color, 0.25f)});
        g.setCornerRadius(dp(heightDp));
        fill.setBackground(g);
        View rest = new View(this);
        b.addView(fill, new LinearLayout.LayoutParams(0, dp(heightDp), Math.max(f, 0.001f)));
        b.addView(rest, new LinearLayout.LayoutParams(0, dp(heightDp), Math.max(1f - f, 0.0001f)));
        return b;
    }

    private TextView bigGradientButton(String text, int c1, int c2, UI.Tap tap) {
        TextView go = UI.button(this, text, c1, Color.WHITE);
        go.setTextSize(15.5f);
        go.setMinHeight(dp(54));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{c1, c2});
        g.setCornerRadius(dp(27));
        go.setBackground(g);
        go.setElevation(dp(6));
        go.setOnClickListener(tap);
        return go;
    }

    private LinearLayout.LayoutParams cardLp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(l), dp(t), dp(r), dp(b));
        return lp;
    }

    private LinearLayout metricCard(String emoji, String label, int tint, View body, final Runnable tap) {
        LinearLayout c = UI.card(this);
        if (tap != null) {
            c.setClickable(true);
            c.setOnClickListener(new UI.Tap() { @Override public void tap() { tap.run(); } });
        }
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(UI.iconChip(this, emoji, tint, 34), new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView l = UI.label(this, label, 14, UI.MUTED, true);
        head.addView(l, UI.lp(this, -2, -2, 10, 0, 0, 0));
        c.addView(head);
        c.addView(body, UI.lp(this, -1, -2, 0, 8, 0, 0));
        return c;
    }

    private View mini(String emoji, String label, String value, String sub, int tint, final Runnable tap) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(UI.glass(this, 22));
        c.setElevation(dp(3));
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        c.setClickable(true);
        c.setOnClickListener(new UI.Tap() { @Override public void tap() { tap.run(); } });
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(UI.iconChip(this, emoji, tint, 30), new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView l = UI.label(this, label, 12.5f, UI.MUTED, true);
        head.addView(l, UI.lp(this, -2, -2, 8, 0, 0, 0));
        c.addView(head);
        TextView v = UI.label(this, value, 22, UI.INK, true);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        c.addView(v, UI.lp(this, -1, -2, 0, 12, 0, 0));
        TextView s = UI.label(this, sub, 12, tint, false);
        s.setSingleLine(true);
        s.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(s, UI.lp(this, -1, -2, 0, 2, 0, 0));
        return c;
    }

    private void pair(LinearLayout col, View a, View b) {
        LinearLayout r = new LinearLayout(this);
        r.setPadding(dp(16), dp(6), dp(16), dp(6));
        r.addView(a, UI.lp(this, 0, -1, 0, 0, 6, 0));
        r.addView(b, UI.lp(this, 0, -1, 6, 0, 0, 0));
        ((LinearLayout.LayoutParams) a.getLayoutParams()).weight = 1f;
        ((LinearLayout.LayoutParams) b.getLayoutParams()).weight = 1f;
        col.addView(r, new LinearLayout.LayoutParams(-1, -2));
    }

    private TextView smallBtn(String text, int tint, UI.Tap tap) {
        TextView v = UI.label(this, text, 13.5f, Widgets.darker(tint, 0.78f), true);
        v.setGravity(Gravity.CENTER);
        v.setSingleLine(true);
        v.setMinHeight(dp(42));
        v.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(21));
        g.setColor(UI.alpha(tint, 30));
        g.setStroke(Math.max(1, dp(1)), UI.alpha(tint, 110));
        v.setBackground(g);
        v.setClickable(true);
        v.setOnClickListener(tap);
        return v;
    }

    /* ================================================================ TODAY */
    private String coach(Store.Day d, Store.Profile p) {
        int stepsLeft = p.stepGoal - d.steps, waterLeft = p.waterGoal - d.water, workLeft = p.workoutGoal - d.workout;
        if (stepsLeft <= 0 && waterLeft <= 0 && workLeft <= 0) return "🎉 All three goals smashed today – amazing work!";
        float fs = (float) d.steps / p.stepGoal, fw = (float) d.water / p.waterGoal, fo = (float) d.workout / p.workoutGoal;
        if (fs <= 0 && fw <= 0 && fo <= 0) return "🌅 Fresh day! Start with a glass of water and a short walk.";
        float best = -1; int which = 0;
        float[] fr = {fs, fw, fo};
        boolean[] open = {stepsLeft > 0, waterLeft > 0, workLeft > 0};
        for (int i = 0; i < 3; i++) if (open[i] && fr[i] > best) { best = fr[i]; which = i; }
        if (which == 0) return "🚶 Only " + Fit.num(stepsLeft) + " steps to go – roughly a " + Math.max(5, stepsLeft / 100) + " minute walk.";
        if (which == 1) return "💧 " + Fit.water(waterLeft) + " more water to reach today’s goal.";
        return "💪 " + workLeft + " more minutes of workout and you’re there!";
    }

    private void today(LinearLayout col) {
        final Store.Profile p = Store.profile();
        final String key = Fit.today();
        final Store.Day d = Store.get(key);
        int hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greet = hr < 12 ? "Good morning" : (hr < 17 ? "Good afternoon" : "Good evening");
        int streak = Store.streak();
        TextView sp = bannerPill("🔥 " + streak + (streak == 1 ? " day" : " days"));
        header(col, greet + (p.name.isEmpty() ? "" : ","), p.name.isEmpty() ? "Let’s move 👋" : p.name + " 👋", sp);

        // hero rings
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setBackground(UI.glass(this, 30));
        hero.setElevation(dp(8));
        hero.setPadding(dp(16), dp(18), dp(16), dp(16));
        int sw = getResources().getDisplayMetrics().widthPixels;
        int size = Math.min(dp(250), (int) (sw * 0.68f));
        float[] fr = {frac(d.steps, p.stepGoal), frac(d.water, p.waterGoal), frac(d.workout, p.workoutGoal)};
        Widgets.RingsView rings = new Widgets.RingsView(this);
        rings.set(fr, Store.hits(d) + "/3", "goals today", lastRings);
        lastRings = fr;
        hero.addView(rings, new LinearLayout.LayoutParams(size, size));
        LinearLayout legend = new LinearLayout(this);
        legend.addView(legendItem("Steps", Fit.num(d.steps), "/ " + Fit.num(p.stepGoal), UI.STEPS), UI.lp(this, 0, -2, 0, 0, 4, 0));
        legend.addView(legendItem("Water", Fit.water(d.water), "/ " + Fit.water(p.waterGoal), UI.WATER), UI.lp(this, 0, -2, 4, 0, 4, 0));
        legend.addView(legendItem("Workout", d.workout + " min", "/ " + p.workoutGoal + " min", UI.WORK), UI.lp(this, 0, -2, 4, 0, 0, 0));
        for (int i = 0; i < 3; i++) ((LinearLayout.LayoutParams) legend.getChildAt(i).getLayoutParams()).weight = 1f;
        hero.addView(legend, UI.lp(this, -1, -2, 0, 14, 0, 0));
        TextView msg = UI.label(this, coach(d, p), 13.5f, Color.parseColor("#3B4470"), false);
        msg.setGravity(Gravity.CENTER);
        msg.setLineSpacing(0, 1.2f);
        hero.addView(msg, UI.lp(this, -1, -2, 6, 14, 6, 0));
        col.addView(hero, overlap());

        // water
        LinearLayout wbody = new LinearLayout(this);
        wbody.setGravity(Gravity.CENTER_VERTICAL);
        Widgets.WaveView wave = new Widgets.WaveView(this);
        float wf = frac(d.water, p.waterGoal);
        wave.startAt(lastWater);
        wave.set(wf, Math.round(wf * 100) + "%", Fit.water(d.water));
        lastWater = wf;
        wbody.addView(wave, new LinearLayout.LayoutParams(dp(132), dp(132)));
        LinearLayout wr = new LinearLayout(this);
        wr.setOrientation(LinearLayout.VERTICAL);
        TextView big = UI.label(this, Fit.water(d.water), 26, UI.INK, true);
        big.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        big.setSingleLine(true);
        wr.addView(big);
        wr.addView(UI.label(this, d.water >= p.waterGoal ? "Goal reached 🎉" : Fit.water(p.waterGoal - d.water) + " to go", 13, d.water >= p.waterGoal ? UI.GREEN : UI.MUTED, false));
        LinearLayout b1 = new LinearLayout(this);
        b1.addView(smallBtn("🥛 +250", UI.WATER, new UI.Tap() { @Override public void tap() { addWater(250); } }), UI.lp(this, 0, -2, 0, 0, 4, 0));
        b1.addView(smallBtn("🍶 +500", UI.WATER, new UI.Tap() { @Override public void tap() { addWater(500); } }), UI.lp(this, 0, -2, 4, 0, 0, 0));
        for (int i = 0; i < 2; i++) ((LinearLayout.LayoutParams) b1.getChildAt(i).getLayoutParams()).weight = 1f;
        wr.addView(b1, UI.lp(this, -1, -2, 0, 12, 0, 0));
        LinearLayout b2 = new LinearLayout(this);
        b2.addView(smallBtn("✎ Custom", UI.WATER, new UI.Tap() { @Override public void tap() { waterDialog(); } }), UI.lp(this, 0, -2, 0, 0, 4, 0));
        b2.addView(smallBtn("↩ −250", UI.MUTED, new UI.Tap() { @Override public void tap() { addWater(-250); } }), UI.lp(this, 0, -2, 4, 0, 0, 0));
        for (int i = 0; i < 2; i++) ((LinearLayout.LayoutParams) b2.getChildAt(i).getLayoutParams()).weight = 1f;
        wr.addView(b2, UI.lp(this, -1, -2, 0, 8, 0, 0));
        wbody.addView(wr, UI.lp(this, 0, -2, 14, 0, 0, 0));
        ((LinearLayout.LayoutParams) wr.getLayoutParams()).weight = 1f;
        col.addView(metricCard("💧", "Water", UI.WATER, wbody, null));

        // steps
        LinearLayout sbody = new LinearLayout(this);
        sbody.setOrientation(LinearLayout.VERTICAL);
        LinearLayout srow = new LinearLayout(this);
        srow.setGravity(Gravity.BOTTOM);
        TextView sbig = UI.label(this, Fit.num(d.steps), 34, UI.INK, true);
        sbig.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        sbig.setSingleLine(true);
        srow.addView(sbig);
        TextView sof = UI.label(this, "  / " + Fit.num(p.stepGoal) + " steps", 14, UI.MUTED, false);
        sof.setPadding(0, 0, 0, dp(5));
        srow.addView(sof);
        sbody.addView(srow);
        sbody.addView(bar(UI.STEPS, frac(d.steps, p.stepGoal), 10), UI.lp(this, -1, -2, 0, 10, 0, 0));
        double kg = Store.currentKg();
        TextView stat = UI.label(this, "📍 " + String.format(java.util.Locale.ENGLISH, "%.1f", Fit.km(d.steps, p.heightCm)) + " km     🔥 " + Fit.kcalWalk(d.steps, kg) + " kcal     "
                + (Steps.available(this) ? "✅ auto" : "✍️ manual"), 13, UI.MUTED, false);
        sbody.addView(stat, UI.lp(this, -1, -2, 0, 10, 0, 0));
        col.addView(metricCard("👟", "Steps", UI.STEPS, sbody, new Runnable() { @Override public void run() { stepsDialog(); } }));

        // weight / sleep / workout / calories
        double w = d.weight > 0 ? d.weight : Store.currentKg();
        double delta = w - p.startKg;
        String wsub = d.weight > 0 ? (Math.abs(delta) < 0.05 ? "No change yet" : (delta < 0 ? "▼ " : "▲ ") + Fit.kg(Math.abs(delta)) + " kg total") : "Tap to log today";
        View weight = mini("⚖️", "Weight", Fit.kg(w) + " kg", wsub, UI.WEIGHT, new Runnable() { @Override public void run() { weightDialog(); } });
        View sleep = mini("😴", "Sleep", d.sleep > 0 ? Fit.kg(d.sleep).replaceAll("\\.0$", "") + " h" : "—", "Goal " + Fit.kg(p.sleepGoal).replaceAll("\\.0$", "") + " h", UI.SLEEP, new Runnable() { @Override public void run() { sleepDialog(); } });
        pair(col, weight, sleep);
        int left = p.calGoal - d.calories;
        View work = mini("💪", "Workout", d.workout + " min", d.workout >= p.workoutGoal ? "Goal reached ✓" : "Goal " + p.workoutGoal + " min", UI.WORK, new Runnable() { @Override public void run() { workoutDialog(); } });
        View cal = mini("🍽️", "Calories", Fit.num(d.calories) + " kcal", left >= 0 ? Fit.num(left) + " left" : "Over by " + Fit.num(-left), left >= 0 ? UI.CAL : UI.RED, new Runnable() { @Override public void run() { caloriesDialog(); } });
        pair(col, work, cal);
        TextView edit = UI.textButton(this, "✎  Edit everything for today", UI.MUTED);
        edit.setOnClickListener(new UI.Tap() { @Override public void tap() { Dlg.editDay(MainActivity.this, key, again); } });
        col.addView(edit, UI.lp(this, -1, -2, 16, 4, 16, 0));
    }

    private float frac(double v, double goal) { return goal <= 0 ? 0f : (float) Math.min(1.0, v / goal); }

    private View legendItem(String label, String value, String goal, int color) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setBackground(UI.round(this, UI.alpha(color, 26), 16));
        l.setPadding(dp(4), dp(10), dp(4), dp(10));
        TextView t = UI.label(this, "● " + label, 11, color, true);
        l.addView(t);
        TextView v = UI.label(this, value, 15, UI.INK, true);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        l.addView(v, UI.lp(this, -2, -2, 0, 3, 0, 0));
        TextView g = UI.label(this, goal, 10.5f, UI.MUTED, false);
        g.setSingleLine(true);
        l.addView(g);
        return l;
    }

    private void addWater(int ml) {
        Store.Day x = Store.mut(Fit.today());
        x.water = Math.max(0, x.water + ml);
        Store.save();
        render(true);
    }

    private void waterDialog() {
        Dlg.number(this, "Add water", "HOW MUCH?", "ml", 0, false, true, UI.WATER, "Today so far: " + Fit.water(Store.get(Fit.today()).water), "Reset today", new Dlg.Num() {
            @Override public void done(double v) {
                Store.Day x = Store.mut(Fit.today());
                if (v < 0) x.water = 0; else x.water += (int) v;
                Store.save();
                render(true);
            }
        });
    }

    private void stepsDialog() {
        Dlg.number(this, "Today’s steps", "TOTAL STEPS TODAY", "steps", Store.get(Fit.today()).steps, false, false, UI.STEPS,
                Steps.available(this) ? "Your phone counts steps automatically. Change the total here only if it looks wrong." : "This phone has no step sensor – type your steps (from a watch or another app).",
                null, new Dlg.Num() {
                    @Override public void done(double v) { Store.mut(Fit.today()).steps = (int) v; Store.save(); render(true); }
                });
    }

    private void weightDialog() {
        Dlg.number(this, "Log weight", "WEIGHT", "kg", Store.get(Fit.today()).weight > 0 ? Store.get(Fit.today()).weight : Store.currentKg(), true, false, UI.WEIGHT,
                "Weigh yourself in the morning, before eating, for the most consistent trend.", null, new Dlg.Num() {
                    @Override public void done(double v) {
                        if (v < 20 || v > 400) { Toast.makeText(MainActivity.this, "Weight looks wrong", Toast.LENGTH_SHORT).show(); return; }
                        Store.mut(Fit.today()).weight = v; Store.save(); render(true);
                    }
                });
    }

    private void sleepDialog() {
        Dlg.number(this, "Last night’s sleep", "HOURS SLEPT", "hours", Store.get(Fit.today()).sleep, true, false, UI.SLEEP, null, null, new Dlg.Num() {
            @Override public void done(double v) {
                if (v > 24) { Toast.makeText(MainActivity.this, "Max 24 hours", Toast.LENGTH_SHORT).show(); return; }
                Store.mut(Fit.today()).sleep = v; Store.save(); render(true);
            }
        });
    }

    private void workoutDialog() {
        Dlg.number(this, "Add workout", "MINUTES", "min", 0, false, true, UI.WORK, "Today so far: " + Store.get(Fit.today()).workout + " min", "Reset today", new Dlg.Num() {
            @Override public void done(double v) {
                Store.Day x = Store.mut(Fit.today());
                if (v < 0) x.workout = 0; else x.workout += (int) v;
                Store.save(); render(true);
            }
        });
    }

    private void caloriesDialog() {
        Dlg.number(this, "Add calories", "KCAL EATEN", "kcal", 0, false, true, UI.CAL, "Today so far: " + Fit.num(Store.get(Fit.today()).calories) + " of " + Fit.num(Store.profile().calGoal) + " kcal", "Reset today", new Dlg.Num() {
            @Override public void done(double v) {
                Store.Day x = Store.mut(Fit.today());
                if (v < 0) x.calories = 0; else x.calories += (int) v;
                Store.save(); render(true);
            }
        });
    }

    /* ============================================================== PROGRESS */
    private void progress(LinearLayout col) {
        final Store.Profile p = Store.profile();
        List<Store.Day> ws = Store.weighIns();
        double cur = Store.currentKg();
        double delta = cur - p.startKg;
        boolean losing = p.goalKg < p.startKg;
        header(col, "Your journey", "Progress", null);

        // hero
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setBackground(UI.glass(this, 30));
        hero.setElevation(dp(8));
        hero.setPadding(dp(22), dp(20), dp(22), dp(20));
        TextView cap = UI.label(this, "CURRENT WEIGHT", 12, UI.WEIGHT, true);
        cap.setLetterSpacing(0.12f);
        hero.addView(cap);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.BOTTOM);
        TextView big = UI.label(this, Fit.kg(cur), 46, UI.INK, true);
        big.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        row.addView(big);
        TextView kgl = UI.label(this, " kg", 18, UI.MUTED, true);
        kgl.setPadding(0, 0, 0, dp(9));
        row.addView(kgl);
        hero.addView(row);
        boolean good = losing ? delta < -0.04 : delta > 0.04;
        String dtxt = Math.abs(delta) < 0.05 ? "Just getting started" : (delta < 0 ? "▼ " : "▲ ") + Fit.kg(Math.abs(delta)) + " kg " + (delta < 0 ? "lost" : "gained") + " since you started";
        hero.addView(UI.label(this, dtxt, 14, good ? UI.GREEN : (Math.abs(delta) < 0.05 ? UI.MUTED : UI.AMBER), true));
        double pr = Fit.progress(p.startKg, cur, p.goalKg);
        hero.addView(bar(UI.WEIGHT, (float) pr, 12), UI.lp(this, -1, -2, 0, 16, 0, 0));
        LinearLayout lab = new LinearLayout(this);
        TextView a = UI.label(this, "Start " + Fit.kg(p.startKg), 12, UI.MUTED, false);
        TextView m = UI.label(this, Math.round(pr * 100) + "%", 12, UI.INK, true);
        m.setGravity(Gravity.CENTER);
        TextView z = UI.label(this, "Goal " + Fit.kg(p.goalKg), 12, UI.GREEN, true);
        z.setGravity(Gravity.END);
        lab.addView(a, new LinearLayout.LayoutParams(0, -2, 1f));
        lab.addView(m, new LinearLayout.LayoutParams(0, -2, 1f));
        lab.addView(z, new LinearLayout.LayoutParams(0, -2, 1f));
        hero.addView(lab, UI.lp(this, -1, -2, 0, 6, 0, 0));
        col.addView(hero, overlap());

        // chart
        LinearLayout chart = UI.card(this);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(UI.label(this, "Weight trend", 16, UI.INK, true), new LinearLayout.LayoutParams(0, -2, 1f));
        int[] rs = {7, 30, 90, 0};
        String[] rn = {"7D", "30D", "90D", "All"};
        for (int i = 0; i < 4; i++) {
            final int r = rs[i];
            TextView c = UI.label(this, rn[i], 12, range == r ? Color.WHITE : UI.MUTED, true);
            c.setGravity(Gravity.CENTER);
            c.setPadding(dp(10), dp(6), dp(10), dp(6));
            if (range == r) {
                GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor("#6D4AFF"), Color.parseColor("#33A1FF")});
                g.setCornerRadius(dp(14));
                c.setBackground(g);
            }
            c.setClickable(true);
            c.setOnClickListener(new UI.Tap() { @Override public void tap() { range = r; render(true); } });
            head.addView(c, UI.lp(this, -2, -2, 2, 0, 0, 0));
        }
        chart.addView(head);
        List<Store.Day> shown = new ArrayList<Store.Day>();
        long minNo = range == 0 ? Long.MIN_VALUE : Fit.dayNo(Fit.today()) - range;
        for (Store.Day d : ws) if (Fit.dayNo(d.key) >= minNo) shown.add(d);
        double[] xs = new double[shown.size()], ys = new double[shown.size()];
        String[] ls = new String[shown.size()];
        for (int i = 0; i < shown.size(); i++) { xs[i] = Fit.dayNo(shown.get(i).key); ys[i] = shown.get(i).weight; ls[i] = Fit.shortDate(shown.get(i).key); }
        Widgets.LineChartView lc = new Widgets.LineChartView(this);
        lc.set(xs, ys, ls, p.goalKg);
        chart.addView(lc, UI.lp(this, -1, dp(220), 0, 10, 0, 0));
        if (shown.size() < 2) {
            TextView hint = UI.label(this, "Log your weight on a few different days to see your curve.", 12.5f, UI.MUTED, false);
            hint.setGravity(Gravity.CENTER);
            chart.addView(hint, UI.lp(this, -1, -2, 0, 4, 0, 0));
        } else {
            TextView hint = UI.label(this, "Touch the chart to see each weigh-in", 11.5f, UI.MUTED, false);
            hint.setGravity(Gravity.CENTER);
            chart.addView(hint, UI.lp(this, -1, -2, 0, 4, 0, 0));
        }
        col.addView(chart);

        // stats
        double toGo = Math.abs(cur - p.goalKg);
        boolean reached = losing ? cur <= p.goalKg : cur >= p.goalKg;
        List<Store.Day> recent = new ArrayList<Store.Day>();
        long since = Fit.dayNo(Fit.today()) - 28;
        for (Store.Day d : ws) if (Fit.dayNo(d.key) >= since) recent.add(d);
        double[] rx = new double[recent.size()], ry = new double[recent.size()];
        for (int i = 0; i < recent.size(); i++) { rx[i] = Fit.dayNo(recent.get(i).key); ry[i] = recent.get(i).weight; }
        double slope = Fit.slope(rx, ry);
        int eta = reached ? 0 : Fit.etaDays(cur, p.goalKg, slope);
        String pace = Double.isNaN(slope) ? "—" : (slope < 0 ? "−" : "+") + Fit.kg(Math.abs(slope) * 7) + " kg/wk";
        String etaS = reached ? "Reached! 🎉" : (eta < 0 ? "—" : Fit.shortDate(Fit.addDays(Fit.today(), eta)));
        String etaSub = reached ? "Goal weight" : (eta < 0 ? "Need more data" : "in " + eta + " days");
        pair(col, mini("🎯", "To goal", reached ? "0.0 kg" : Fit.kg(toGo) + " kg", reached ? "You did it" : (losing ? "to lose" : "to gain"), UI.GREEN, new Runnable() { @Override public void run() { Dlg.profile(MainActivity.this, again); } }),
                mini("📉", "Weekly pace", pace, "last 4 weeks", UI.WEIGHT, new Runnable() { @Override public void run() { } }));
        pair(col, mini("📆", "Estimated finish", etaS, etaSub, UI.WATER, new Runnable() { @Override public void run() { } }),
                mini("📝", "Weigh-ins", String.valueOf(ws.size()), ws.isEmpty() ? "none yet" : "since " + Fit.shortDate(ws.get(0).key), UI.CAL, new Runnable() { @Override public void run() { } }));

        // BMI
        double bmi = Fit.bmi(cur, p.heightCm);
        LinearLayout bc = UI.card(this);
        LinearLayout bh = new LinearLayout(this);
        bh.setGravity(Gravity.CENTER_VERTICAL);
        bh.addView(UI.label(this, "Body Mass Index", 16, UI.INK, true), new LinearLayout.LayoutParams(0, -2, 1f));
        int bcol = bmi < 18.5 ? UI.BLUE : (bmi < 25 ? UI.GREEN : (bmi < 30 ? UI.AMBER : UI.RED));
        bh.addView(pill(Fit.kg(bmi) + " • " + Fit.bmiLabel(bmi), bcol));
        bc.addView(bh);
        Widgets.BmiBar bb = new Widgets.BmiBar(this);
        bb.set(bmi);
        bc.addView(bb, UI.lp(this, -1, dp(58), 0, 8, 0, 0));
        double m1 = 18.5 * Math.pow(p.heightCm / 100.0, 2), m2 = 24.9 * Math.pow(p.heightCm / 100.0, 2);
        bc.addView(UI.label(this, "Healthy range at " + p.heightCm + " cm:  " + Fit.kg(m1) + " – " + Fit.kg(m2) + " kg", 12.5f, UI.MUTED, false));
        col.addView(bc);

        col.addView(bigGradientButton("＋  Log today’s weight", Color.parseColor("#6D4AFF"), Color.parseColor("#B06BFF"), new UI.Tap() { @Override public void tap() { weightDialog(); } }), cardLp(16, 12, 16, 4));

        // list
        TextView sec = UI.label(this, "WEIGH-INS", 13, UI.MUTED, true);
        sec.setLetterSpacing(0.1f);
        sec.setPadding(dp(22), dp(18), dp(22), dp(2));
        col.addView(sec);
        if (ws.isEmpty()) {
            LinearLayout e = UI.card(this);
            e.addView(UI.label(this, "No weigh-ins yet.", 14, UI.MUTED, false));
            col.addView(e);
        }
        for (int i = ws.size() - 1; i >= 0 && i >= ws.size() - 10; i--) {
            final Store.Day d = ws.get(i);
            double prev = i > 0 ? ws.get(i - 1).weight : p.startKg;
            double dv = d.weight - prev;
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setBackground(UI.glass(this, 20));
            r.setElevation(dp(2));
            r.setPadding(dp(14), dp(12), dp(16), dp(12));
            r.setClickable(true);
            r.setOnClickListener(new UI.Tap() { @Override public void tap() { Dlg.editDay(MainActivity.this, d.key, again); } });
            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.addView(UI.label(this, Fit.dow(d.key) + ", " + Fit.shortDate(d.key), 15, UI.INK, true));
            r.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));
            boolean dGood = losing ? dv < 0 : dv > 0;
            if (Math.abs(dv) >= 0.05) r.addView(UI.label(this, (dv < 0 ? "▼ " : "▲ ") + Fit.kg(Math.abs(dv)), 13, dGood ? UI.GREEN : UI.AMBER, true), UI.lp(this, -2, -2, 0, 0, 14, 0));
            r.addView(UI.label(this, Fit.kg(d.weight) + " kg", 17, UI.INK, true));
            col.addView(r, cardLp(16, 5, 16, 5));
        }
    }

    /* =============================================================== HISTORY */
    private void history(LinearLayout col) {
        final Store.Profile p = Store.profile();
        String today = Fit.today();
        header(col, "Last 7 days", "History", null);

        int n = 7;
        String[] lb = new String[n], sv_ = new String[n], wv = new String[n];
        float[] st = new float[n], wa = new float[n];
        long sumSteps = 0, sumWater = 0, sumWork = 0; double sumSleep = 0; int sleepDays = 0, bestSteps = 0; String bestDay = "";
        for (int i = 0; i < n; i++) {
            String k = Fit.addDays(today, i - (n - 1));
            Store.Day d = Store.get(k);
            lb[i] = Fit.dow(k).substring(0, 2);
            st[i] = d.steps; wa[i] = d.water;
            sv_[i] = d.steps >= 1000 ? String.format(java.util.Locale.ENGLISH, "%.1fk", d.steps / 1000f) : String.valueOf(d.steps);
            wv[i] = String.format(java.util.Locale.ENGLISH, "%.1f", d.water / 1000f);
            sumSteps += d.steps; sumWater += d.water; sumWork += d.workout;
            if (d.sleep > 0) { sumSleep += d.sleep; sleepDays++; }
            if (d.steps > bestSteps) { bestSteps = d.steps; bestDay = Fit.dow(k); }
        }
        // summary
        LinearLayout sum = UI.card(this);
        LinearLayout r1 = new LinearLayout(this);
        r1.addView(sumItem("AVG STEPS", Fit.num(sumSteps / n), UI.STEPS), UI.lp(this, 0, -2, 0, 0, 4, 0));
        r1.addView(sumItem("AVG WATER", Fit.water((int) (sumWater / n)), UI.WATER), UI.lp(this, 0, -2, 4, 0, 4, 0));
        r1.addView(sumItem("WORKOUT", sumWork + " min", UI.WORK), UI.lp(this, 0, -2, 4, 0, 0, 0));
        for (int i = 0; i < 3; i++) ((LinearLayout.LayoutParams) r1.getChildAt(i).getLayoutParams()).weight = 1f;
        sum.addView(r1);
        LinearLayout r2 = new LinearLayout(this);
        r2.addView(sumItem("AVG SLEEP", sleepDays == 0 ? "—" : Fit.kg(sumSleep / sleepDays) + " h", UI.SLEEP), UI.lp(this, 0, -2, 0, 0, 4, 0));
        r2.addView(sumItem("STREAK", Store.streak() + " d", UI.CAL), UI.lp(this, 0, -2, 4, 0, 4, 0));
        r2.addView(sumItem("BEST STREAK", Store.bestStreak() + " d", UI.GREEN), UI.lp(this, 0, -2, 4, 0, 0, 0));
        for (int i = 0; i < 3; i++) ((LinearLayout.LayoutParams) r2.getChildAt(i).getLayoutParams()).weight = 1f;
        sum.addView(r2, UI.lp(this, -1, -2, 0, 8, 0, 0));
        if (bestSteps > 0) sum.addView(UI.label(this, "🏆 Best day: " + bestDay + " with " + Fit.num(bestSteps) + " steps", 12.5f, UI.MUTED, false), UI.lp(this, -1, -2, 0, 12, 0, 0));
        col.addView(sum);
        ((LinearLayout.LayoutParams) sum.getLayoutParams()).topMargin = -dp(44);

        LinearLayout c1 = UI.card(this);
        c1.addView(UI.label(this, "👟 Steps", 16, UI.INK, true));
        Widgets.BarsView b1 = new Widgets.BarsView(this);
        b1.set(lb, st, sv_, p.stepGoal, UI.STEPS, n - 1);
        c1.addView(b1, UI.lp(this, -1, dp(160), 0, 8, 0, 0));
        col.addView(c1);

        LinearLayout c2 = UI.card(this);
        c2.addView(UI.label(this, "💧 Water (litres)", 16, UI.INK, true));
        Widgets.BarsView b2 = new Widgets.BarsView(this);
        b2.set(lb, wa, wv, p.waterGoal, UI.WATER, n - 1);
        c2.addView(b2, UI.lp(this, -1, dp(160), 0, 8, 0, 0));
        col.addView(c2);

        // consistency map: 5 weeks, Monday first
        String monday = Fit.addDays(today, -Fit.mondayIndex(today));
        String start = Fit.addDays(monday, -28);
        int[] lv = new int[35];
        int todayIdx = -1;
        for (int i = 0; i < 35; i++) {
            String k = Fit.addDays(start, i);
            if (k.compareTo(today) > 0) lv[i] = -1;
            else lv[i] = Store.hits(Store.get(k));
            if (k.equals(today)) todayIdx = i;
        }
        LinearLayout c3 = UI.card(this);
        LinearLayout h3 = new LinearLayout(this);
        h3.setGravity(Gravity.CENTER_VERTICAL);
        h3.addView(UI.label(this, "🗓️ Consistency", 16, UI.INK, true), new LinearLayout.LayoutParams(0, -2, 1f));
        h3.addView(UI.label(this, "goals hit per day", 11.5f, UI.MUTED, false));
        c3.addView(h3);
        Widgets.HeatView hv = new Widgets.HeatView(this);
        hv.set(lv, todayIdx);
        c3.addView(hv, UI.lp(this, -1, -2, 0, 10, 0, 0));
        LinearLayout leg = new LinearLayout(this);
        leg.setGravity(Gravity.CENTER);
        leg.setPadding(0, dp(10), 0, 0);
        leg.addView(UI.label(this, "0 ", 11, UI.MUTED, false));
        for (int i = 0; i <= 3; i++) {
            View sq = new View(this);
            sq.setBackground(UI.round(this, Widgets.HeatView.colorOf(i), 4));
            leg.addView(sq, UI.lp(this, dp(16), dp(16), 3, 0, 3, 0));
        }
        leg.addView(UI.label(this, " 3", 11, UI.MUTED, false));
        c3.addView(leg);
        col.addView(c3);

        // daily log
        TextView sec = UI.label(this, "DAILY LOG  •  tap to edit", 13, UI.MUTED, true);
        sec.setLetterSpacing(0.08f);
        sec.setPadding(dp(22), dp(18), dp(22), dp(2));
        col.addView(sec);
        for (int i = 0; i < 14; i++) {
            final String k = Fit.addDays(today, -i);
            Store.Day d = Store.get(k);
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setBackground(UI.glass(this, 20));
            r.setElevation(dp(2));
            r.setPadding(dp(14), dp(11), dp(14), dp(11));
            r.setClickable(true);
            r.setOnClickListener(new UI.Tap() { @Override public void tap() { Dlg.editDay(MainActivity.this, k, again); } });
            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.addView(UI.label(this, i == 0 ? "Today" : (i == 1 ? "Yesterday" : Fit.dow(k) + ", " + Fit.shortDate(k)), 15, UI.INK, true));
            String line = "👟 " + Fit.num(d.steps) + "   💧 " + Fit.water(d.water) + (d.weight > 0 ? "   ⚖️ " + Fit.kg(d.weight) : "");
            TextView ls = UI.label(this, line, 12, UI.MUTED, false);
            ls.setSingleLine(true);
            ls.setEllipsize(TextUtils.TruncateAt.END);
            mid.addView(ls);
            r.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));
            int hs = Store.hits(d);
            LinearLayout dots = new LinearLayout(this);
            for (int j = 0; j < 3; j++) {
                View dt = new View(this);
                dt.setBackground(UI.round(this, j < hs ? UI.GREEN : Color.parseColor("#DDE2F2"), 5));
                dots.addView(dt, UI.lp(this, dp(10), dp(10), 2, 0, 2, 0));
            }
            r.addView(dots);
            col.addView(r, cardLp(16, 4, 16, 4));
        }
    }

    private View sumItem(String label, String value, int color) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(UI.round(this, UI.alpha(color, 26), 16));
        l.setPadding(dp(10), dp(10), dp(10), dp(10));
        TextView a = UI.label(this, label, 10, color, true);
        a.setLetterSpacing(0.06f);
        a.setSingleLine(true);
        l.addView(a);
        TextView v = UI.label(this, value, 16, UI.INK, true);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        l.addView(v, UI.lp(this, -2, -2, 0, 3, 0, 0));
        return l;
    }

    /* =================================================================== ME */
    private void me(LinearLayout col) {
        final Store.Profile p = Store.profile();
        header(col, "Settings", "Me", null);
        LinearLayout pc = new LinearLayout(this);
        pc.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.parseColor("#6D4AFF"), Color.parseColor("#33A1FF")});
        hg.setCornerRadius(dp(28));
        pc.setBackground(hg);
        pc.setElevation(dp(8));
        pc.setPadding(dp(18), dp(18), dp(18), dp(18));
        String initial = p.name.isEmpty() ? "🙂" : p.name.substring(0, 1).toUpperCase();
        TextView av = UI.label(this, initial, 26, UI.ACCENT, true);
        av.setGravity(Gravity.CENTER);
        GradientDrawable ag = new GradientDrawable();
        ag.setShape(GradientDrawable.OVAL);
        ag.setColor(Color.WHITE);
        av.setBackground(ag);
        pc.addView(av, new LinearLayout.LayoutParams(dp(64), dp(64)));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(UI.label(this, p.name.isEmpty() ? "Your profile" : p.name, 20, Color.WHITE, true));
        t.addView(UI.label(this, "Goal  " + Fit.kg(p.startKg) + " → " + Fit.kg(p.goalKg) + " kg", 13.5f, Color.argb(235, 255, 255, 255), false));
        t.addView(UI.label(this, p.heightCm + " cm  •  BMI " + Fit.kg(Fit.bmi(Store.currentKg(), p.heightCm)), 12.5f, Color.argb(190, 255, 255, 255), false));
        pc.addView(t, UI.lp(this, 0, -2, 16, 0, 0, 0));
        ((LinearLayout.LayoutParams) t.getLayoutParams()).weight = 1f;
        col.addView(pc, overlap());

        LinearLayout card = UI.card(this);
        card.setPadding(dp(6), dp(6), dp(6), dp(6));
        card.addView(settingRow("👤", "Edit profile", "Name, height, start & goal weight", UI.ACCENT, new Runnable() { @Override public void run() { Dlg.profile(MainActivity.this, again); } }));
        card.addView(settingRow("🎯", "Daily goals", "Steps " + Fit.num(p.stepGoal) + " • Water " + Fit.water(p.waterGoal) + " • Workout " + p.workoutGoal + " min", UI.GREEN, new Runnable() { @Override public void run() { Dlg.goals(MainActivity.this, again); } }));
        card.addView(settingRow("🔔", "Water reminders", p.remindHours == 0 ? "Off" : "Every " + p.remindHours + (p.remindHours == 1 ? " hour" : " hours") + ", 9 am – 9 pm", UI.WATER, new Runnable() { @Override public void run() { reminderDialog(); } }));
        card.addView(settingRow("👟", "Step counter", Steps.available(this) ? "Automatic – counting in the background" : "Not available on this phone – enter steps manually", UI.STEPS, new Runnable() {
            @Override public void run() { Toast.makeText(MainActivity.this, Steps.available(MainActivity.this) ? "Steps are counted by your phone’s motion sensor." : "No step sensor found – tap the Steps card to type your steps.", Toast.LENGTH_LONG).show(); }
        }));
        card.addView(settingRow("📤", "Share my data", "Send your daily records as CSV", UI.CAL, new Runnable() { @Override public void run() { shareCsv(); } }));
        card.addView(settingRow("🗑️", "Reset everything", "Erase all records and start over", UI.RED, new Runnable() {
            @Override public void run() {
                Dlg.confirm(MainActivity.this, "Erase all data?", "This permanently deletes your profile and every daily record on this phone. This cannot be undone.", "Erase", new UI.Action() {
                    @Override public boolean run() {
                        Store.wipe();
                        startActivity(new Intent(MainActivity.this, SetupActivity.class));
                        finish();
                        return true;
                    }
                });
            }
        }));
        col.addView(card);
        TextView foot = UI.label(this, "Fit Journey 1.1  •  your data never leaves this phone", 12, UI.MUTED, false);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(16), 0, dp(8));
        col.addView(foot);
    }

    private View settingRow(String emoji, String title, String sub, int tint, final Runnable tap) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(10), dp(11), dp(10), dp(11));
        r.setClickable(true);
        r.setOnClickListener(new UI.Tap() { @Override public void tap() { tap.run(); } });
        r.addView(UI.iconChip(this, emoji, tint, 42), new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(UI.label(this, title, 15, UI.INK, true));
        TextView s = UI.label(this, sub, 12, UI.MUTED, false);
        t.addView(s);
        r.addView(t, UI.lp(this, 0, -2, 12, 0, 6, 0));
        ((LinearLayout.LayoutParams) t.getLayoutParams()).weight = 1f;
        r.addView(UI.label(this, "›", 22, UI.MUTED, true));
        return r;
    }

    private void reminderDialog() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] ref = new Dialog[1];
        final int[] opts = {0, 1, 2, 3, 4};
        for (int i = 0; i < opts.length; i++) {
            final int h = opts[i];
            boolean on = Store.profile().remindHours == h;
            TextView r = UI.label(this, (on ? "●  " : "○  ") + (h == 0 ? "Off" : "Every " + h + (h == 1 ? " hour" : " hours")), 16, on ? UI.WATER : UI.INK, on);
            r.setPadding(dp(6), dp(12), dp(6), dp(12));
            r.setClickable(true);
            r.setOnClickListener(new UI.Tap() {
                @Override public void tap() {
                    Store.Profile p = Store.profile();
                    p.remindHours = h;
                    Store.setProfile(p);
                    if (ref[0] != null) ref[0].dismiss();
                    render(true);
                }
            });
            body.addView(r);
        }
        ref[0] = UI.dialog(this, "Water reminders", body, null, null, "Close", null, null);
        ref[0].show();
    }

    private void shareCsv() {
        StringBuilder sb = new StringBuilder("date,weight_kg,steps,water_ml,sleep_h,workout_min,calories\n");
        for (Store.Day d : Store.all()) {
            sb.append(d.key).append(',').append(d.weight > 0 ? Fit.kg(d.weight) : "").append(',').append(d.steps).append(',').append(d.water).append(',')
                    .append(d.sleep > 0 ? Fit.kg(d.sleep) : "").append(',').append(d.workout).append(',').append(d.calories).append('\n');
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "Fit Journey data");
        i.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(i, "Share my data"));
    }
}
