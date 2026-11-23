package com.ironicxsoft.rozplan;

import android.app.Activity;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static com.ironicxsoft.rozplan.UI.*;

public class MainActivity extends Activity {
    private FrameLayout content;
    private int tab = 0;
    private final LinearLayout[] navItems = new LinearLayout[4];
    private final TextView[] navLabels = new TextView[4];
    private final Views.Icon[] navIcons = new Views.Icon[4];

    private String todayKey;
    private Store.Day day;
    private Data.Task[] routine;
    private boolean isWorkoutDay;

    // exercise timer
    private final Handler handler = new Handler();
    private TextView timerText;
    private int timerLeft = 0;
    private final Runnable timerTick = new Runnable() {
        @Override public void run() {
            timerLeft--;
            if (timerText != null) timerText.setText(timerLeft > 0 ? fmtTimer(timerLeft) : "Bas!");
            if (timerLeft > 0) handler.postDelayed(this, 1000);
            else { try { ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(400); } catch (Exception ignored) { } }
        }
    };

    private interface IntCb { void run(int n); }

    /* ------------------------------------------------------------- lifecycle */
    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        UI.init(this);
        if (Lock.needsLogin(this)) { startActivity(new Intent(this, LoginActivity.class)); finish(); return; }
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(BG);
            getWindow().setNavigationBarColor(BG);
        }
        if (Build.VERSION.SDK_INT >= 23 && !UI.night) {
            int f = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) f |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(f);
        }
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        View line = new View(this);
        line.setBackgroundColor(LINE);
        root.addView(line, new LinearLayout.LayoutParams(-1, 1));
        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(BG);
        String[] names = {"Aaj", "Khana", "Exercise", "Progress"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setMinimumHeight(dp(this, 60));
            item.setPadding(dp(this, 2), dp(this, 8), dp(this, 2), dp(this, 8));
            item.setClickable(true);
            Views.Icon ic = new Views.Icon(this, i);
            TextView tx = label(this, names[i], 12, MUTED, true);
            tx.setGravity(Gravity.CENTER);
            item.addView(ic, new LinearLayout.LayoutParams(dp(this, 26), dp(this, 26)));
            item.addView(tx, lp(this, -2, -2, 0, 2, 0, 0));
            item.setOnClickListener(new Tap() { @Override public void tap() { showTab(idx); } });
            navItems[i] = item; navLabels[i] = tx; navIcons[i] = ic;
            bar.addView(item, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        if (content == null) return;
        if (Lock.needsLogin(this)) { startActivity(new Intent(this, LoginActivity.class)); finish(); return; }
        Lock.touch();
        showTab(tab);
    }

    @Override protected void onPause() { Lock.touch(); super.onPause(); }

    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }

    /* ---------------------------------------------------------------- helpers */
    private int d(int v) { return dp(this, v); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void loadDay() {
        Calendar now = Calendar.getInstance();
        todayKey = Store.key(now);
        day = Store.day(this, todayKey);
        boolean[] wd = Store.workoutDays(this);
        isWorkoutDay = wd[now.get(Calendar.DAY_OF_WEEK) - 1];
        routine = Data.routine(isWorkoutDay);
    }

    private void saveDay() { day.pct = pctNow(); Store.saveDay(this, todayKey, day); }

    private int pctNow() {
        int total = routine.length + Data.HABITS.length + 1, done = 0;
        for (Data.Task t : routine) if (day.done.contains(t.id)) done++;
        for (String[] h : Data.HABITS) if (day.habits.contains(h[0])) done++;
        if (day.water >= 12) done++;
        return Math.round(done * 100f / total);
    }

    private static String fmtTimer(int s) { return (s / 60) + ":" + String.format(Locale.US, "%02d", s % 60); }

    private String[] fmtClock(int minutes) {
        int m = ((minutes % 1440) + 1440) % 1440;
        int h = m / 60, mm = m % 60;
        String ap = h < 12 ? "AM" : "PM";
        h = h % 12 == 0 ? 12 : h % 12;
        return new String[]{h + ":" + String.format(Locale.US, "%02d", mm), ap};
    }

    private LinearLayout page(String small, String title, boolean scroll) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(d(20), d(20), d(20), d(28));
        col.addView(label(this, small, 14, MUTED, false));
        TextView h = display(this, title, 34, INK);
        col.addView(h, lp(this, -1, -2, 0, 4, 0, 0));
        if (scroll) {
            ScrollView sv = new ScrollView(this);
            sv.setVerticalScrollBarEnabled(false);
            sv.addView(col);
            content.removeAllViews();
            content.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        }
        return col;
    }

    private TextView h2(String t) {
        TextView v = display(this, t, 24, INK);
        v.setPadding(0, d(28), 0, d(12));
        return v;
    }

    private void showTab(int t) {
        tab = t;
        handler.removeCallbacks(timerTick);
        timerText = null;
        for (int i = 0; i < 4; i++) {
            boolean on = i == t;
            navLabels[i].setTextColor(on ? ACC2 : MUTED);
            navIcons[i].setColor(on ? ACC2 : MUTED);
        }
        loadDay();
        if (t == 0) buildToday(); else if (t == 1) buildFood(); else if (t == 2) buildExercise(); else buildProgress();
    }

    private void rebuild() {
        int y = 0;
        View first = content.getChildAt(0);
        ScrollView sv = findScroll(first);
        if (sv != null) y = sv.getScrollY();
        showTabKeep(y);
    }

    private ScrollView findScroll(View v) {
        if (v instanceof ScrollView) return (ScrollView) v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) { ScrollView s = findScroll(((ViewGroup) v).getChildAt(i)); if (s != null) return s; }
        return null;
    }

    private void showTabKeep(final int y) {
        loadDay();
        if (tab == 0) buildToday(); else if (tab == 1) buildFood(); else if (tab == 2) buildExercise(); else buildProgress();
        final ScrollView sv = findScroll(content.getChildAt(0));
        if (sv != null) sv.post(new Runnable() { @Override public void run() { sv.scrollTo(0, y); } });
    }

    private void fitText(TextView tv, int maxPx, int maxSp, int minSp) {
        android.graphics.Paint p = new android.graphics.Paint(tv.getPaint());
        float density = getResources().getDisplayMetrics().scaledDensity;
        String s = tv.getText().toString();
        int sp = maxSp;
        while (sp > minSp) { p.setTextSize(sp * density); if (p.measureText(s) <= maxPx) break; sp--; }
        tv.setTextSize(sp);
    }

    private void choose(String title, String[] labels, int selected, final IntCb cb) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] ref = new Dialog[1];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            boolean on = i == selected;
            TextView row = label(this, (on ? "●   " : "○   ") + labels[i], 16, on ? ACC2 : INK, on);
            row.setMinHeight(d(48));
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(d(4), d(8), d(4), d(8));
            row.setClickable(true);
            row.setOnClickListener(new Tap() { @Override public void tap() { ref[0].dismiss(); cb.run(idx); } });
            box.addView(row);
        }
        ref[0] = dialog(this, title, box, null, null, "Cancel");
        ref[0].show();
    }

    /* ======================================================================= AAJ */
    private void buildToday() {
        Calendar now = Calendar.getInstance();
        String date = Data.DAYS[now.get(Calendar.DAY_OF_WEEK) - 1] + ", " + new SimpleDateFormat("d MMMM", Locale.UK).format(now.getTime());
        LinearLayout col = page(date, "Aaj ka plan", true);
        final int wake = Store.wakeMin(this);

        // ---- hero
        int pct = pctNow();
        int cur = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int rel = (((cur - wake) % 1440) + 1440) % 1440;
        Data.Task next = null;
        for (Data.Task t : routine) if (!day.done.contains(t.id) && t.off + 30 >= rel) { next = t; break; }
        if (next == null) for (Data.Task t : routine) if (!day.done.contains(t.id)) { next = t; break; }

        boolean narrow = getResources().getConfiguration().screenWidthDp < 420;
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(narrow ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        hero.setGravity(narrow ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);
        hero.setBackground(round(this, PAN, 24));
        hero.setPadding(d(20), d(20), d(20), d(20));
        Views.Ring ring = new Views.Ring(this);
        ring.set(pct);
        hero.addView(ring, new LinearLayout.LayoutParams(d(132), d(132)));
        LinearLayout now1 = new LinearLayout(this);
        now1.setOrientation(LinearLayout.VERTICAL);
        now1.setGravity(narrow ? Gravity.CENTER_HORIZONTAL : Gravity.START);
        TextView sm = label(this, "Abhi karna hai", 14, ACC2, true);
        sm.setGravity(narrow ? Gravity.CENTER : Gravity.START);
        now1.addView(sm);
        TextView nt = display(this, next != null ? next.title : "Aaj ka sab ho gaya, shabash!", 24, INK);
        nt.setGravity(narrow ? Gravity.CENTER : Gravity.START);
        now1.addView(nt, lp(this, -2, -2, 0, 4, 0, 8));
        TextView pill = label(this, isWorkoutDay ? "Aaj workout ka din" : "Aaj walk ka din", 13, Color.WHITE, true);
        pill.setBackground(round(this, ACC, 16));
        pill.setPadding(d(12), d(5), d(12), d(5));
        now1.addView(pill, new LinearLayout.LayoutParams(-2, -2));
        if (narrow) ((LinearLayout.LayoutParams) pill.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
        LinearLayout.LayoutParams nlp = narrow ? new LinearLayout.LayoutParams(-1, -2) : new LinearLayout.LayoutParams(0, -2, 1f);
        nlp.setMargins(narrow ? 0 : d(20), narrow ? d(14) : 0, 0, 0);
        hero.addView(now1, nlp);
        col.addView(hero, lp(this, -1, -2, 0, 16, 0, 8));

        // ---- timetable
        for (final Data.Task t : routine) {
            View sep = new View(this);
            sep.setBackgroundColor(LINE);
            col.addView(sep, new LinearLayout.LayoutParams(-1, 1));
            LinearLayout row = new LinearLayout(this);
            row.setPadding(0, d(14), 0, d(14));
            LinearLayout tc = new LinearLayout(this);
            tc.setOrientation(LinearLayout.VERTICAL);
            String[] ck = fmtClock(wake + t.off);
            tc.addView(display(this, ck[0], 17, INK));
            tc.addView(label(this, ck[1], 13, MUTED, false));
            row.addView(tc, new LinearLayout.LayoutParams(d(76), -2));
            LinearLayout x = new LinearLayout(this);
            x.setOrientation(LinearLayout.VERTICAL);
            boolean done = day.done.contains(t.id);
            TextView title = label(this, t.title, 16, done ? MUTED : INK, true);
            if (done) title.setPaintFlags(title.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            x.addView(title);
            x.addView(label(this, t.desc, 14, MUTED, false), lp(this, -2, -2, 0, 2, 0, 0));
            row.addView(x, new LinearLayout.LayoutParams(0, -2, 1f));
            Views.Tick tick = new Views.Tick(this);
            tick.setOn(done);
            row.addView(tick, lp(this, 44, 44, 12, 0, 0, 0));
            row.setClickable(true);
            row.setOnClickListener(new Tap() { @Override public void tap() {
                if (!day.done.remove(t.id)) day.done.add(t.id);
                saveDay(); rebuild();
            } });
            col.addView(row);
        }

        // ---- habits
        col.addView(h2("Aaj ki aadatein"));
        Flow hab = new Flow(this, 8, 8);
        for (final String[] h : Data.HABITS) {
            boolean on = day.habits.contains(h[0]);
            TextView c = chip(this, (on ? "✓ " : "") + h[1], on);
            c.setOnClickListener(new Tap() { @Override public void tap() {
                if (!day.habits.remove(h[0])) day.habits.add(h[0]);
                saveDay(); rebuild();
            } });
            hab.addView(c);
        }
        col.addView(hab);

        // ---- water
        LinearLayout water = card(this, CARD, 16);
        water.setPadding(d(14), d(14), d(14), d(14));
        LinearLayout wr = new LinearLayout(this);
        wr.setGravity(Gravity.CENTER_VERTICAL);
        wr.addView(squareBtn("−", new Tap() { @Override public void tap() { if (day.water > 0) { day.water--; saveDay(); rebuild(); } } }));
        LinearLayout wt = new LinearLayout(this);
        wt.setOrientation(LinearLayout.VERTICAL);
        wt.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView wc = display(this, day.water + "/12", 26, INK);
        wc.setGravity(Gravity.CENTER);
        wt.addView(wc);
        TextView wl = label(this, "glass paani (3 litre)", 14, MUTED, false);
        wl.setGravity(Gravity.CENTER);
        wt.addView(wl);
        wr.addView(wt, new LinearLayout.LayoutParams(0, -2, 1f));
        wr.addView(squareBtn("+", new Tap() { @Override public void tap() { if (day.water < 12) { day.water++; saveDay(); rebuild(); } } }));
        water.addView(wr);
        Views.Glasses g = new Views.Glasses(this);
        g.set(day.water, 12);
        water.addView(g, lp(this, -1, -2, 0, 12, 0, 0));
        col.addView(water, lp(this, -1, -2, 0, 14, 0, 0));

        // ---- settings
        col.addView(h2("Settings"));
        LinearLayout wk = new LinearLayout(this);
        wk.setGravity(Gravity.CENTER_VERTICAL);
        wk.addView(label(this, "Uthne ka time", 16, INK, true), new LinearLayout.LayoutParams(0, -2, 1f));
        String[] wc2 = fmtClock(wake);
        TextView wb = button(this, wc2[0] + " " + wc2[1], BG, INK);
        wb.setBackground(outline(this, BG, LINE, 2, 12));
        wb.setOnClickListener(new Tap() { @Override public void tap() {
            new TimePickerDialog(MainActivity.this, UI.night ? android.R.style.Theme_DeviceDefault_Dialog : android.R.style.Theme_DeviceDefault_Light_Dialog, new TimePickerDialog.OnTimeSetListener() {
                @Override public void onTimeSet(TimePicker tp, int h, int m) { Store.setWakeMin(MainActivity.this, h * 60 + m); rebuild(); }
            }, wake / 60, wake % 60, false).show();
        } });
        wk.addView(wb);
        col.addView(wk);
        TextView hint = label(this, "Sara schedule isi time ke hisab se badal jata hai. Workout ke din:", 14, MUTED, false);
        hint.setPadding(0, d(10), 0, d(10));
        col.addView(hint);
        Flow days = new Flow(this, 8, 8);
        final boolean[] wd = Store.workoutDays(this);
        for (int i = 0; i < 7; i++) {
            final int di = i;
            TextView c = chip(this, Data.DAYS[i], wd[i]);
            c.setOnClickListener(new Tap() { @Override public void tap() { wd[di] = !wd[di]; Store.setWorkoutDays(MainActivity.this, wd); rebuild(); } });
            days.addView(c);
        }
        col.addView(days);

        // ---- security
        col.addView(h2("PIN lock"));
        LinearLayout sec = card(this, CARD, 16);
        sec.setPadding(d(16), d(6), d(16), d(6));
        sec.addView(switchRow("PIN se app kholo", "Har dafa app kholne par 4-digit PIN", Lock.enabled(this), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                if (on == Lock.enabled(MainActivity.this)) return;
                if (on) {
                    if (Lock.hasPin(MainActivity.this)) { Lock.setEnabled(MainActivity.this, true); rebuild(); }
                    else startActivity(new Intent(MainActivity.this, LoginActivity.class).putExtra(LoginActivity.EXTRA_MODE, LoginActivity.MODE_CHANGE));
                } else { Lock.setEnabled(MainActivity.this, false); rebuild(); }
            }
        }));
        if (Lock.hasPin(this)) {
            sec.addView(rowDivider());
            sec.addView(valueRow("PIN badlo", "Change", new Tap() { @Override public void tap() {
                startActivity(new Intent(MainActivity.this, LoginActivity.class).putExtra(LoginActivity.EXTRA_MODE, LoginActivity.MODE_CHANGE));
            } }));
            sec.addView(rowDivider());
            int s0 = Store.autoLockSec(this);
            String curS = s0 < 0 ? "Kabhi nahi" : s0 < 60 ? s0 + " sec" : (s0 / 60) + " min";
            sec.addView(valueRow("Auto-lock", curS, new Tap() { @Override public void tap() {
                final int[] opts = {15, 60, 300, -1};
                int sel = 1;
                for (int i = 0; i < opts.length; i++) if (opts[i] == Store.autoLockSec(MainActivity.this)) sel = i;
                choose("App kitni der baad lock ho?", new String[]{"15 seconds", "1 minute", "5 minutes", "Kabhi nahi"}, sel, new IntCb() {
                    @Override public void run(int i) { Store.setAutoLockSec(MainActivity.this, opts[i]); rebuild(); }
                });
            } }));
        }
        col.addView(sec);

        TextView credit = label(this, "Designed and Developed by IronicXsoft", 12, MUTED, false);
        credit.setGravity(Gravity.CENTER);
        credit.setPadding(0, d(26), 0, 0);
        col.addView(credit);
    }

    private TextView squareBtn(String glyph, Tap tap) {
        TextView v = label(this, glyph, 24, ON_INK, true);
        v.setGravity(Gravity.CENTER);
        v.setIncludeFontPadding(false);
        v.setBackground(round(this, INK, 12));
        v.setLayoutParams(new LinearLayout.LayoutParams(d(44), d(44)));
        v.setClickable(true);
        v.setOnClickListener(tap);
        return v;
    }

    private View rowDivider() {
        View v = new View(this);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, 1));
        return v;
    }

    private View switchRow(String title, String desc, boolean on, CompoundButton.OnCheckedChangeListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, d(10), 0, d(10));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(label(this, title, 16, INK, true));
        if (desc != null) info.addView(label(this, desc, 13, MUTED, false));
        row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(on);
        sw.setOnCheckedChangeListener(l);
        row.addView(sw, lp(this, -2, -2, 10, 0, 0, 0));
        return row;
    }

    private View valueRow(String title, String value, Tap tap) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(d(52));
        row.setPadding(0, d(6), 0, d(6));
        row.addView(label(this, title, 16, INK, true), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView v = label(this, value, 14, ACC2, true);
        v.setBackground(round(this, alpha(ACC, 30), 14));
        v.setPadding(d(14), d(8), d(14), d(8));
        row.addView(v, lp(this, -2, -2, 10, 0, 0, 0));
        row.setClickable(true);
        row.setOnClickListener(tap);
        return row;
    }

    /* ===================================================================== KHANA */
    private void buildFood() {
        LinearLayout col = page("Roz ka khana", "Khana", false);
        // sticky totals (fixed above the list)
        int kc = 0, pr = 0;
        for (Data.Meal m : Data.MEALS) { Integer s = day.meals.get(m.key); if (s != null) { kc += (Integer) m.options[s][1]; pr += (Integer) m.options[s][2]; } }
        TextView intro = label(this, "Har meal mein ek option chuno. Calories andazan hain (ghar ka khana, kam oil).", 14, MUTED, false);
        intro.setPadding(0, d(4), 0, d(10));
        col.addView(intro);
        LinearLayout tot = new LinearLayout(this);
        tot.setOrientation(LinearLayout.VERTICAL);
        LinearLayout tr = new LinearLayout(this);
        tr.setGravity(Gravity.CENTER_VERTICAL);
        tr.addView(label(this, String.format(Locale.US, "%,d / 1,850 kcal", kc), 16, INK, true), new LinearLayout.LayoutParams(0, -2, 1f));
        tr.addView(label(this, "Protein " + pr + " / 130g", 14, MUTED, false));
        tot.addView(tr);
        LinearLayout track = new LinearLayout(this);
        track.setBackground(round(this, LINE, 5));
        float frac = Math.min(1f, kc / 1850f);
        View fill = new View(this);
        fill.setBackground(round(this, ACC, 5));
        track.addView(fill, new LinearLayout.LayoutParams(0, -1, frac));
        track.addView(new View(this), new LinearLayout.LayoutParams(0, -1, 1f - frac));
        tot.addView(track, lp(this, -1, 10, 0, 8, 0, 0));
        col.addView(tot);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.addView(col, new LinearLayout.LayoutParams(-1, -2));
        View sep = new View(this);
        sep.setBackgroundColor(LINE);
        outer.addView(sep, new LinearLayout.LayoutParams(-1, 1));
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(d(20), d(16), d(20), d(28));
        sv.addView(list);
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        content.removeAllViews();
        content.addView(outer, new FrameLayout.LayoutParams(-1, -1));
        col.setPadding(d(20), d(20), d(20), d(12));

        for (final Data.Meal m : Data.MEALS) {
            LinearLayout card = card(this, CARD, 20);
            card.setPadding(d(16), d(16), d(16), d(10));
            Integer sel = day.meals.get(m.key);
            LinearLayout head = new LinearLayout(this);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(display(this, m.name, 19, INK), new LinearLayout.LayoutParams(0, -2, 1f));
            head.addView(label(this, sel != null ? m.options[sel][1] + " kcal" : "chuno", 14, MUTED, false));
            card.addView(head);
            for (int i = 0; i < m.options.length; i++) {
                final int oi = i;
                boolean on = sel != null && sel == i;
                LinearLayout opt = new LinearLayout(this);
                opt.setOrientation(LinearLayout.VERTICAL);
                opt.setBackground(outline(this, BG, on ? ACC : BG, 2, 14));
                opt.setPadding(d(14), d(12), d(14), d(12));
                opt.setMinimumHeight(d(44));
                opt.addView(label(this, (String) m.options[i][0], 15, INK, on));
                opt.addView(label(this, m.options[i][1] + " kcal  ·  " + m.options[i][2] + "g protein", 13, on ? ACC2 : MUTED, false), lp(this, -2, -2, 0, 2, 0, 0));
                opt.setClickable(true);
                opt.setOnClickListener(new Tap() { @Override public void tap() {
                    Integer cur = day.meals.get(m.key);
                    if (cur != null && cur == oi) day.meals.remove(m.key); else day.meals.put(m.key, oi);
                    saveDay(); rebuild();
                } });
                card.addView(opt, lp(this, -1, -2, 0, 8, 0, 0));
            }
            list.addView(card, lp(this, -1, -2, 0, 0, 0, 14));
        }
        list.addView(h2("In se bacho"));
        Flow no = new Flow(this, 8, 8);
        for (String s : Data.AVOID) {
            TextView t = label(this, s, 14, INK, false);
            t.setBackground(round(this, PAN, 16));
            t.setPadding(d(12), d(6), d(12), d(6));
            no.addView(t);
        }
        list.addView(no);
        TextView note = label(this, "1 extra roti ≈ 120 kcal. 1 tablespoon oil ≈ 120 kcal. Protein kam reh jaye to 1 anda ya 1 katori dahi aur le lo. Hafte mein 1 meal biryani/bahar ka chal jata hai, bas plate chhoti.", 14, MUTED, false);
        note.setPadding(0, d(14), 0, 0);
        list.addView(note);
    }

    /* ================================================================== EXERCISE */
    private void buildExercise() {
        LinearLayout col = page(isWorkoutDay ? "Aaj workout ka din hai" : "Aaj walk ka din hai", "Ghar ka workout", true);
        StringBuilder wds = new StringBuilder();
        boolean[] wd = Store.workoutDays(this);
        for (int i = 0; i < 7; i++) if (wd[i]) { if (wds.length() > 0) wds.append(", "); wds.append(Data.DAYS[i]); }
        TextView intro = label(this, (isWorkoutDay ? "Aaj workout ka din hai. " : "Aaj walk ka din hai, workout " + wds + " ko. ") + "Har exercise ke 3 set, set ke beech 60 second aaram.", 14, MUTED, false);
        intro.setPadding(0, d(6), 0, d(6));
        col.addView(intro);

        // timer
        LinearLayout timer = new LinearLayout(this);
        timer.setOrientation(LinearLayout.VERTICAL);
        timer.setBackground(round(this, PAN, 20));
        timer.setPadding(d(16), d(14), d(16), d(16));
        timerText = display(this, "0:30", 44, INK);
        timer.addView(timerText);
        Flow tb = new Flow(this, 8, 8);
        TextView p30 = button(this, "30s plank", ACC, Color.WHITE);
        p30.setOnClickListener(new Tap() { @Override public void tap() { startTimer(30); } });
        TextView r60 = button(this, "60s aaram", INK, ON_INK);
        r60.setOnClickListener(new Tap() { @Override public void tap() { startTimer(60); } });
        TextView stop = button(this, "Roko", LINE, INK);
        stop.setOnClickListener(new Tap() { @Override public void tap() { handler.removeCallbacks(timerTick); timerLeft = 0; if (timerText != null) timerText.setText("0:30"); } });
        tb.addView(p30); tb.addView(r60); tb.addView(stop);
        timer.addView(tb, lp(this, -1, -2, 0, 10, 0, 0));
        col.addView(timer, lp(this, -1, -2, 0, 12, 0, 20));

        int n = 1;
        for (final Data.Exercise e : Data.EXERCISES) {
            LinearLayout card = card(this, CARD, 24);
            card.setClipToOutline(true);
            FrameLayout pic = new FrameLayout(this);
            android.graphics.drawable.GradientDrawable picBg = new android.graphics.drawable.GradientDrawable();
            picBg.setColor(PAN);
            float pr = d(24);
            picBg.setCornerRadii(new float[]{pr, pr, pr, pr, 0, 0, 0, 0});
            pic.setBackground(picBg);
            pic.addView(new Views.Figure(this, e.a, e.b), new FrameLayout.LayoutParams(-1, -2));
            card.addView(pic, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout b = new LinearLayout(this);
            b.setOrientation(LinearLayout.VERTICAL);
            b.setPadding(d(18), d(16), d(18), d(18));
            b.addView(display(this, (n++) + ". " + e.name, 21, INK));
            b.addView(label(this, e.reps, 14, MUTED, false), lp(this, -2, -2, 0, 2, 0, 8));
            int si = 1;
            for (String s : e.steps) {
                LinearLayout r = new LinearLayout(this);
                TextView num = label(this, (si++) + ".", 15, ACC2, true);
                r.addView(num, new LinearLayout.LayoutParams(d(24), -2));
                r.addView(label(this, s, 15, INK, false), new LinearLayout.LayoutParams(0, -2, 1f));
                b.addView(r, lp(this, -1, -2, 0, 4, 0, 0));
            }
            TextView warn = label(this, "⚠ " + e.warn, 14, ACC2, false);
            b.addView(warn, lp(this, -1, -2, 0, 10, 0, 0));
            TextView yt = button(this, "▶  YouTube video dekho", INK, ON_INK);
            yt.setOnClickListener(new Tap() { @Override public void tap() {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(e.query)))); }
                catch (Exception ex) { toast("Browser nahi mila"); }
            } });
            b.addView(yt, lp(this, -2, -2, 0, 12, 0, 0));
            card.addView(b);
            col.addView(card, lp(this, -1, -2, 0, 0, 0, 18));
        }
        TextView note = label(this, "Shuru mein 5 min halki walk ya jagah par march karo (warm up). Ghutnay ya kamar mein tez dard ho to woh exercise us din chhor do.", 14, MUTED, false);
        col.addView(note);
    }

    private void startTimer(int secs) {
        handler.removeCallbacks(timerTick);
        timerLeft = secs;
        if (timerText != null) timerText.setText(fmtTimer(secs));
        handler.postDelayed(timerTick, 1000);
    }

    /* =================================================================== PROGRESS */
    private void buildProgress() {
        LinearLayout col = page("Hafta dar hafta", "Progress", true);
        List<Store.Weight> w = Store.weights(this);
        double last = w.isEmpty() ? Store.START_KG : w.get(w.size() - 1).kg;
        double lost = Math.max(0, Store.START_KG - last);

        LinearLayout stats = new LinearLayout(this);
        String[][] st = {{"Shuru", String.valueOf(Store.START_KG)}, {"Abhi", trim(last)}, {"Kam hua", trim(lost)}};
        int tileW = (getResources().getDisplayMetrics().widthPixels - d(40) - d(10)) / 3 - d(26);
        int common = 28;
        for (int i = 0; i < 3; i++) {
            TextView probe = display(this, st[i][1], 28, INK);
            fitText(probe, tileW, 28, 12);
            common = Math.min(common, Math.round(probe.getTextSize() / getResources().getDisplayMetrics().scaledDensity));
        }
        for (int i = 0; i < 3; i++) {
            LinearLayout t = card(this, CARD, 16);
            t.setPadding(d(12), d(12), d(12), d(12));
            t.addView(label(this, st[i][0], 13, MUTED, false));
            TextView v = display(this, st[i][1], common, INK);
            v.setSingleLine(true);
            t.addView(v, lp(this, -2, -2, 0, 4, 0, 0));
            t.addView(label(this, "kg", 13, MUTED, true));
            stats.addView(t, weight(this, 1f, i == 0 ? 0 : 5, 0, i == 2 ? 0 : 5, 0));
        }
        stats.setBaselineAligned(false);
        col.addView(stats, lp(this, -1, -2, 0, 16, 0, 0));

        LinearLayout chartCard = card(this, CARD, 20);
        chartCard.setPadding(d(8), d(8), d(8), d(8));
        Views.Chart chart = new Views.Chart(this);
        chart.set(w);
        chartCard.addView(chart, new LinearLayout.LayoutParams(-1, -2));
        col.addView(chartCard, lp(this, -1, -2, 0, 14, 0, 10));
        col.addView(label(this, "Dotted lines: mid-March target 90–92 kg, aakhri goal 75 kg. Hafte mein 1 dafa subah khali pait weight karo.", 14, MUTED, false));

        LinearLayout form = new LinearLayout(this);
        form.setGravity(Gravity.CENTER_VERTICAL);
        final EditText in = input(this, "Aaj ka weight (kg)", "", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        in.setSingleLine(true);
        form.addView(in, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView save = button(this, "Save", ACC, Color.WHITE);
        save.setOnClickListener(new Tap() { @Override public void tap() {
            try {
                double v = Double.parseDouble(in.getText().toString().trim().replace(',', '.'));
                if (v < 40 || v > 200) { toast("40 se 200 kg ke beech likhein"); return; }
                Store.setWeightToday(MainActivity.this, v);
                rebuild();
            } catch (NumberFormatException ex) { toast("Weight likhein"); }
        } });
        form.addView(save, lp(this, -2, -2, 8, 0, 0, 0));
        col.addView(form, lp(this, -1, -2, 0, 16, 0, 0));

        col.addView(h2("Pichle 14 din"));
        Flow dots = new Flow(this, 6, 6);
        Calendar c = Calendar.getInstance();
        for (int i = 13; i >= 0; i--) {
            Calendar dc = (Calendar) c.clone();
            dc.add(Calendar.DAY_OF_YEAR, -i);
            String k = Store.key(dc);
            int p = k.equals(todayKey) ? pctNow() : Store.day(this, k).pct;
            View dot = new View(this);
            dot.setBackground(round(this, p >= 70 ? OK : p > 0 ? ACC : LINE, 6));
            dots.addView(dot, new ViewGroup.LayoutParams(d(22), d(22)));
        }
        col.addView(dots);
        col.addView(label(this, "Hara = din ka 70% ya zyada plan poora. Laal = kuch kiya. Yaad rakho: 2 din lagatar miss nahi.", 14, MUTED, false), lp(this, -1, -2, 0, 10, 0, 0));

        col.addView(h2("Weight log"));
        if (w.isEmpty()) {
            col.addView(label(this, "Abhi koi entry nahi. Upar aaj ka weight likho.", 14, MUTED, false));
        } else {
            for (int i = w.size() - 1; i >= 0; i--) {
                final Store.Weight e = w.get(i);
                View sep = new View(this);
                sep.setBackgroundColor(LINE);
                col.addView(sep, new LinearLayout.LayoutParams(-1, 1));
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(d(48));
                row.addView(label(this, new SimpleDateFormat("d MMM", Locale.UK).format(Store.parse(e.date)), 15, INK, false), new LinearLayout.LayoutParams(0, -2, 1f));
                row.addView(label(this, trim(e.kg) + " kg", 16, INK, true));
                TextView del = label(this, "✕", 16, MUTED, false);
                del.setGravity(Gravity.CENTER);
                del.setClickable(true);
                del.setOnClickListener(new Tap() { @Override public void tap() { Store.deleteWeight(MainActivity.this, e.date); rebuild(); } });
                row.addView(del, lp(this, 44, 44, 4, 0, 0, 0));
                col.addView(row);
            }
        }
    }

    private static String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(Locale.US, "%.1f", v);
    }
}
