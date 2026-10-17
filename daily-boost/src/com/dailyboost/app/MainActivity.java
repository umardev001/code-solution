package com.dailyboost.app;

import android.app.Activity;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static com.dailyboost.app.UI.*;

public class MainActivity extends Activity {
    private static final int REQ_TONE_GLOBAL = 11, REQ_TONE_REMINDER = 12;
    private static final int[] TINTS = {UI.TEAL, UI.BLUE, UI.VIOLET, UI.ROSE, UI.AMBER, UI.GREEN};
    private static final String[] TIPS = {
            "Har ghante 5 minute walk = roz ~600 extra steps. Chhoti aadat, bara asar.",
            "Khane se pehle ek glass pani pee lo – bhook control rehti hai.",
            "Din ka sab se zaroori kaam pehle karo, phir baqi.",
            "Raat ko 7–8 ghante ki neend weight loss mein madad karti hai.",
            "Sugar wali drinks ki jagah pani ya green tea chuno.",
            "25 minute focus, 5 minute break – Pomodoro se time bachta hai."
    };

    private FrameLayout content;
    private int tab = 0;
    private final LinearLayout[] navItems = new LinearLayout[4];
    private final TextView[] navIcons = new TextView[4];
    private final TextView[] navLabels = new TextView[4];

    private interface ToneCb { void got(String uri); }
    private ToneCb toneCb;
    private interface IntCb { void run(int n); }

    // live counters
    private final Handler tickHandler = new Handler();
    private final List<Runnable> tickers = new ArrayList<>();
    private final Runnable tickLoop = new Runnable() {
        @Override public void run() {
            for (int i = 0; i < tickers.size(); i++) tickers.get(i).run();
            tickHandler.postDelayed(this, 1000);
        }
    };

    /* ------------------------------------------------------------ lifecycle */
    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        if (Lock.needsLogin(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(BG);
            getWindow().setNavigationBarColor(BG);
        }
        if (Build.VERSION.SDK_INT >= 23) {
            int f = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) f |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(f);
        }
        Store.seedIfNeeded(this);
        Notifier.sync(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        // floating pill navigation
        LinearLayout bar = new LinearLayout(this);
        bar.setBackground(round(this, Color.WHITE, 30));
        bar.setElevation(dp(this, 10));
        bar.setPadding(dp(this, 6), dp(this, 6), dp(this, 6), dp(this, 6));
        String[] icons = {"🏠", "⏰", "✅", "⚙️"};
        String[] names = {"Today", "Reminders", "Tasks", "Settings"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(this, 2), dp(this, 7), dp(this, 2), dp(this, 7));
            item.setClickable(true);
            TextView ic = label(this, icons[i], 18, INK, false);
            ic.setGravity(Gravity.CENTER);
            TextView tx = label(this, names[i], 11, MUTED, true);
            tx.setSingleLine(true);
            tx.setEllipsize(TextUtils.TruncateAt.END);
            tx.setGravity(Gravity.CENTER);
            item.addView(ic);
            item.addView(tx);
            item.setOnClickListener(new Tap() { @Override public void tap() { showTab(idx); } });
            navItems[i] = item; navIcons[i] = ic; navLabels[i] = tx;
            bar.addView(item, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.setMargins(dp(this, 16), dp(this, 4), dp(this, 16), dp(this, 12));
        root.addView(bar, blp);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content == null) return;
        if (Lock.needsLogin(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        Lock.touch();
        Scheduler.scheduleAll(this);
        showTab(tab);
        tickHandler.removeCallbacks(tickLoop);
        tickHandler.post(tickLoop);
    }

    @Override
    protected void onPause() {
        Lock.touch();
        Notifier.stopPreview();
        tickHandler.removeCallbacks(tickLoop);
        super.onPause();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if ((req == REQ_TONE_GLOBAL || req == REQ_TONE_REMINDER) && res == RESULT_OK && data != null && toneCb != null) {
            Uri u = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            toneCb.got(u == null ? "" : u.toString());
        }
    }

    /* -------------------------------------------------------------- helpers */
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private int d(int v) { return dp(this, v); }

    private static String eta(long ms) {
        long m = Math.max(0, ms) / 60000L;
        if (m < 1) return "less than a minute";
        if (m < 60) return m + " min";
        long h = m / 60;
        return h + " h" + (m % 60 == 0 ? "" : " " + (m % 60) + " min");
    }

    private static String clock(long ms) {
        long s = Math.max(0, ms) / 1000L;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, sec) : String.format(Locale.US, "%02d:%02d", m, sec);
    }

    /** Shrinks the text size until it fits inside maxPx (never clipped). */
    private void fitText(TextView tv, int maxPx, int maxSp, int minSp) {
        android.graphics.Paint p = new android.graphics.Paint(tv.getPaint());
        float density = getResources().getDisplayMetrics().scaledDensity;
        String s = tv.getText().toString();
        int sp = maxSp;
        while (sp > minSp) {
            p.setTextSize(sp * density);
            if (p.measureText(s) <= maxPx) break;
            sp--;
        }
        tv.setTextSize(sp);
    }

    private static String atTime(long when) { return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(when)); }

    private static boolean sameDay(long a, long b) {
        Calendar x = Calendar.getInstance(), y = Calendar.getInstance();
        x.setTimeInMillis(a); y.setTimeInMillis(b);
        return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR);
    }

    private static final class Upcoming { Store.Reminder r; long when; }

    private List<Upcoming> upcoming(int limit) {
        List<Upcoming> l = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Store.Reminder r : Store.reminders(this)) {
            if (!r.enabled) continue;
            Upcoming u = new Upcoming(); u.r = r; u.when = Scheduler.nextTrigger(r, now); l.add(u);
        }
        Collections.sort(l, new Comparator<Upcoming>() { @Override public int compare(Upcoming a, Upcoming b) { return a.when < b.when ? -1 : (a.when > b.when ? 1 : 0); } });
        return l.size() > limit ? l.subList(0, limit) : l;
    }

    private LinearLayout page(String small, String big, View rightAction) {
        tickers.clear();
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, d(10));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(d(22), d(18), d(18), d(8));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(label(this, small, 14, MUTED, false));
        titles.addView(label(this, big, 30, INK, true));
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        if (rightAction != null) head.addView(rightAction);
        col.addView(head, new LinearLayout.LayoutParams(-1, -2));
        sv.addView(col);
        content.removeAllViews();
        content.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        return col;
    }

    private TextView roundButton(String glyph, int bg, int fg, int size) {
        TextView v = label(this, glyph, size > 44 ? 24 : 20, fg, true);
        v.setGravity(Gravity.CENTER);
        v.setIncludeFontPadding(false);
        v.setBackground(round(this, bg, size / 2));
        v.setLayoutParams(new LinearLayout.LayoutParams(d(size), d(size)));
        v.setClickable(true);
        return v;
    }

    private void animateIn(final LinearLayout col) {
        for (int i = 1; i < col.getChildCount(); i++) {
            View v = col.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(d(22));
            v.animate().alpha(1f).translationY(0).setStartDelay(55L * i).setDuration(380).start();
        }
    }

    private void showTab(int t) {
        tab = t;
        for (int i = 0; i < 4; i++) {
            boolean on = i == t;
            navLabels[i].setTextColor(on ? GREEN_DARK : MUTED);
            navIcons[i].setAlpha(on ? 1f : 0.5f);
            navItems[i].setBackground(on ? round(this, alpha(GREEN, 30), 24) : null);
        }
        LinearLayout col;
        if (t == 0) col = buildToday(); else if (t == 1) col = buildReminders(); else if (t == 2) col = buildTasks(); else col = buildSettings();
        animateIn(col);
    }

    private void rebuild() {
        int keep = 0;
        View sv = content.getChildAt(0);
        if (sv instanceof ScrollView) keep = sv.getScrollY();
        LinearLayout col;
        if (tab == 0) col = buildToday(); else if (tab == 1) col = buildReminders(); else if (tab == 2) col = buildTasks(); else col = buildSettings();
        final View nsv = content.getChildAt(0);
        final int y = keep;
        nsv.post(new Runnable() { @Override public void run() { nsv.scrollTo(0, y); } });
    }

    private TextView chip(String text, boolean on) {
        TextView c = label(this, text, 14, on ? Color.WHITE : INK, true);
        c.setSingleLine(true);
        c.setEllipsize(TextUtils.TruncateAt.END);
        c.setGravity(Gravity.CENTER);
        c.setMinHeight(d(40));
        c.setPadding(d(16), d(8), d(16), d(8));
        c.setBackground(round(this, on ? GREEN : FIELD, 20));
        c.setClickable(true);
        return c;
    }

    private void askNumber(String title, int current, final IntCb cb) {
        final EditText e = input(this, "Number", String.valueOf(current), InputType.TYPE_CLASS_NUMBER);
        dialog(this, title, e, "Save", new Action() {
            @Override public boolean run() {
                try { cb.run(Integer.parseInt(e.getText().toString().trim())); return true; }
                catch (NumberFormatException ex) { toast("Enter a number"); return false; }
            }
        }, "Cancel", null, null).show();
    }

    private void choose(String title, String[] labels, int selected, final IntCb cb) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] ref = new Dialog[1];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            boolean on = i == selected;
            TextView row = label(this, (on ? "●   " : "○   ") + labels[i], 16, on ? GREEN : INK, on);
            row.setMinHeight(d(48));
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(d(4), d(8), d(4), d(8));
            row.setClickable(true);
            row.setOnClickListener(new Tap() { @Override public void tap() { ref[0].dismiss(); cb.run(idx); } });
            box.addView(row);
        }
        ref[0] = dialog(this, title, box, null, null, "Cancel", null, null);
        ref[0].show();
    }

    private void pickTime(int minutes, final IntCb cb) {
        new TimePickerDialog(this, android.R.style.Theme_DeviceDefault_Light_Dialog, new TimePickerDialog.OnTimeSetListener() {
            @Override public void onTimeSet(TimePicker tp, int h, int m) { cb.run(h * 60 + m); }
        }, minutes / 60, minutes % 60, false).show();
    }

    private void pickTone(int req, String current, ToneCb cb) {
        toneCb = cb;
        Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION | RingtoneManager.TYPE_ALARM | RingtoneManager.TYPE_RINGTONE);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Choose reminder tone");
        if (current != null && !current.isEmpty()) i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(current));
        try { startActivityForResult(i, req); } catch (Exception e) { toast("No tone picker available on this phone"); }
    }

    /* ---------------------------------------------------------------- Today */
    private LinearLayout buildToday() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greet = hour < 12 ? "Good morning ☀️" : hour < 17 ? "Good afternoon 🌤️" : "Good evening 🌙";
        TextView lockBtn = null;
        if (Lock.enabled(this)) {
            lockBtn = roundButton("🔒", Color.WHITE, INK, 44);
            lockBtn.setElevation(d(3));
            lockBtn.setOnClickListener(new Tap() { @Override public void tap() {
                Lock.lockNow();
                startActivity(new Intent(MainActivity.this, LoginActivity.class));
                finish();
            } });
        }
        LinearLayout col = page(greet, "Daily Boost", lockBtn);

        // ---- hero: live countdown to the next reminder
        List<Upcoming> up = upcoming(5);
        FrameLayout hero = new FrameLayout(this);
        hero.setBackground(gradient(this, Color.parseColor("#0D9488"), Color.parseColor("#2563EB"), 32));
        hero.setElevation(d(6));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.setMargins(d(16), d(10), d(16), d(8));
        hero.setLayoutParams(hlp);
        LinearLayout hrow = new LinearLayout(this);
        hrow.setGravity(Gravity.CENTER_VERTICAL);
        hrow.setPadding(d(20), d(20), d(20), d(20));
        hero.addView(hrow, new FrameLayout.LayoutParams(-1, -2));

        if (up.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.addView(label(this, "😴  Nothing scheduled", 20, Color.WHITE, true));
            TextView sub = label(this, "Turn on a reminder and your countdown starts here.", 14, Color.parseColor("#DBEAFE"), false);
            sub.setPadding(0, d(6), 0, d(12));
            empty.addView(sub);
            TextView go = button(this, "Open reminders", Color.WHITE, GREEN_DARK);
            go.setOnClickListener(new Tap() { @Override public void tap() { showTab(1); } });
            empty.addView(go);
            hrow.addView(empty, new LinearLayout.LayoutParams(0, -2, 1f));
        } else {
            final Upcoming first = up.get(0);
            int ringSize = getResources().getDisplayMetrics().widthPixels < d(340) ? 112 : 132;
            FrameLayout ringBox = new FrameLayout(this);
            final Widgets.RingView ring = new Widgets.RingView(this);
            ringBox.addView(ring, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout center = new LinearLayout(this);
            center.setOrientation(LinearLayout.VERTICAL);
            center.setGravity(Gravity.CENTER);
            final TextView big = label(this, "--:--", 28, Color.WHITE, true);
            big.setSingleLine(true);
            big.setEllipsize(null);
            big.setGravity(Gravity.CENTER);
            center.addView(big, new LinearLayout.LayoutParams(-1, -2));
            final TextView rem = label(this, "remaining", 11, Color.parseColor("#BFDBFE"), false);
            rem.setGravity(Gravity.CENTER);
            center.addView(rem, new LinearLayout.LayoutParams(-1, -2));
            final int innerPx = (int) (d(ringSize) * 0.60f);
            ringBox.addView(center, new FrameLayout.LayoutParams(-1, -1));
            hrow.addView(ringBox, new LinearLayout.LayoutParams(d(ringSize), d(ringSize)));

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(d(18), 0, 0, 0);
            info.addView(label(this, "NEXT UP", 11, Color.parseColor("#BFDBFE"), true));
            TextView ttl = label(this, plainTitle(first.r.title).isEmpty() ? first.r.title : plainTitle(first.r.title), 20, Color.WHITE, true);
            ttl.setMaxLines(2);
            ttl.setEllipsize(TextUtils.TruncateAt.END);
            ttl.setPadding(0, d(2), 0, d(2));
            info.addView(ttl);
            info.addView(label(this, atTime(first.when) + (sameDay(first.when, System.currentTimeMillis()) ? "" : "  •  tomorrow"), 14, Color.parseColor("#DBEAFE"), false));
            TextView chipv = label(this, emojiOf(first.r.title, "🔔") + "  " + first.r.schedule().split("  \u2022  ")[0], 12, Color.WHITE, false);
            chipv.setBackground(round(this, Color.argb(48, 255, 255, 255), 14));
            chipv.setPadding(d(10), d(5), d(10), d(5));
            chipv.setMaxLines(2);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
            clp.setMargins(0, d(10), 0, 0);
            info.addView(chipv, clp);
            hrow.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));

            final long span = first.r.intervalMin > 0 ? first.r.intervalMin * 60000L : 24L * 3600000L;
            Runnable r = new Runnable() { @Override public void run() {
                long left = first.when - System.currentTimeMillis();
                if (left <= 0) { rebuild(); return; }
                big.setText(clock(left));
                fitText(big, innerPx, left >= 3600000L ? 24 : 30, 14);
                rem.setText(left >= 3600000L ? "hr : min : sec" : "min : sec");
                ring.setProgress((float) left / (float) Math.max(span, left));
            } };
            r.run();
            tickers.add(r);
        }
        col.addView(hero);

        // ---- water
        LinearLayout water = card(this);
        final int goal = Store.waterGoal(this), have = Store.water(this);
        LinearLayout wt = new LinearLayout(this);
        wt.setGravity(Gravity.CENTER_VERTICAL);
        TextView wic = iconChip(this, "💧", BLUE, 42);
        wt.addView(wic);
        LinearLayout wtx = new LinearLayout(this);
        wtx.setOrientation(LinearLayout.VERTICAL);
        wtx.setPadding(d(12), 0, 0, 0);
        wtx.addView(label(this, "Water today", 16, INK, true));
        wtx.addView(label(this, have >= goal ? "Goal reached 🎉" : (goal - have) + " more to go", 13, MUTED, false));
        wt.addView(wtx, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView cnt = label(this, have + "/" + goal, 26, BLUE, true);
        wt.addView(cnt);
        water.addView(wt);
        Widgets.GlassRow glasses = new Widgets.GlassRow(this);
        glasses.set(have, goal);
        water.addView(glasses, lp(this, -1, -2, 0, 14, 0, 0));
        LinearLayout wb = new LinearLayout(this);
        wb.setGravity(Gravity.CENTER_VERTICAL);
        wb.setPadding(0, d(14), 0, 0);
        TextView minus = roundButton("−", FIELD, INK, 46);
        minus.setOnClickListener(new Tap() { @Override public void tap() { Store.setWater(MainActivity.this, Store.water(MainActivity.this) - 1); rebuild(); } });
        wb.addView(minus);
        TextView plus = button(this, "+  Add a glass", BLUE, Color.WHITE);
        plus.setOnClickListener(new Tap() { @Override public void tap() { Store.setWater(MainActivity.this, Store.water(MainActivity.this) + 1); rebuild(); } });
        wb.addView(plus, lp(this, 0, -2, 10, 0, 10, 0));
        ((LinearLayout.LayoutParams) plus.getLayoutParams()).weight = 1f;
        TextView goalBtn = button(this, "Goal " + goal, FIELD, INK);
        goalBtn.setOnClickListener(new Tap() { @Override public void tap() {
            askNumber("Daily water goal (glasses)", goal, new IntCb() { @Override public void run(int n) { Store.setWaterGoal(MainActivity.this, n); rebuild(); } });
        } });
        wb.addView(goalBtn);
        water.addView(wb);
        col.addView(water);

        // ---- tasks summary
        LinearLayout tasks = card(this);
        List<Store.Task> all = Store.tasks(this);
        int done = 0;
        for (Store.Task t : all) if (t.done) done++;
        LinearLayout tt = new LinearLayout(this);
        tt.setGravity(Gravity.CENTER_VERTICAL);
        tt.addView(iconChip(this, "✅", GREEN, 42));
        LinearLayout ttx = new LinearLayout(this);
        ttx.setOrientation(LinearLayout.VERTICAL);
        ttx.setPadding(d(12), 0, 0, 0);
        ttx.addView(label(this, "Tasks", 16, INK, true));
        ttx.addView(label(this, all.isEmpty() ? "Nothing added yet" : done + " of " + all.size() + " done", 13, MUTED, false));
        tt.addView(ttx, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView seeAll = textButton(this, "See all", GREEN);
        seeAll.setOnClickListener(new Tap() { @Override public void tap() { showTab(2); } });
        tt.addView(seeAll);
        tasks.addView(tt);
        Widgets.MiniBar bar = new Widgets.MiniBar(this);
        bar.set(all.isEmpty() ? 0f : (float) done / all.size(), GREEN);
        tasks.addView(bar, lp(this, -1, -2, 0, 12, 0, 6));
        int shown = 0;
        for (final Store.Task t : all) {
            if (t.done || shown >= 3) continue;
            shown++;
            tasks.addView(taskRow(t, false));
        }
        if (shown == 0 && !all.isEmpty()) tasks.addView(label(this, "All done – great work! 🌟", 14, GREEN, true), lp(this, -2, -2, 0, 8, 0, 0));
        col.addView(tasks);

        // ---- coming up
        if (up.size() > 1) {
            LinearLayout cu = card(this);
            LinearLayout ch = new LinearLayout(this);
            ch.setGravity(Gravity.CENTER_VERTICAL);
            ch.addView(iconChip(this, "🗓️", VIOLET, 42));
            TextView cht = label(this, "Coming up", 16, INK, true);
            cht.setPadding(d(12), 0, 0, 0);
            ch.addView(cht);
            cu.addView(ch);
            for (int i = 1; i < up.size(); i++) {
                final Upcoming u = up.get(i);
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, d(12), 0, 0);
                TextView time = label(this, atTime(u.when), 13, MUTED, true);
                time.setMinWidth(d(66));
                row.addView(time);
                View dot = new View(this);
                dot.setBackground(round(this, TINTS[u.r.id % TINTS.length], 6));
                row.addView(dot, lp(this, d(12) / 2 + 0, d(12) / 2 + 0, 0, 0, 0, 0));
                ((LinearLayout.LayoutParams) dot.getLayoutParams()).width = d(10);
                ((LinearLayout.LayoutParams) dot.getLayoutParams()).height = d(10);
                ((LinearLayout.LayoutParams) dot.getLayoutParams()).setMargins(0, 0, d(12), 0);
                LinearLayout tx = new LinearLayout(this);
                tx.setOrientation(LinearLayout.VERTICAL);
                tx.addView(label(this, emojiOf(u.r.title, "🔔") + "  " + (plainTitle(u.r.title).isEmpty() ? u.r.title : plainTitle(u.r.title)), 15, INK, true));
                final TextView in = label(this, "", 12, MUTED, false);
                tx.addView(in);
                row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
                cu.addView(row);
                Runnable r = new Runnable() { @Override public void run() { in.setText("in " + eta(u.when - System.currentTimeMillis())); } };
                r.run();
                tickers.add(r);
            }
            col.addView(cu);
        }

        // ---- tip
        LinearLayout tip = card(this);
        tip.setBackground(round(this, Color.parseColor("#FFF6E0"), 24));
        tip.setElevation(0);
        LinearLayout tp = new LinearLayout(this);
        tp.setGravity(Gravity.CENTER_VERTICAL);
        tp.addView(iconChip(this, "💡", AMBER, 42));
        TextView tv = label(this, TIPS[(int) ((System.currentTimeMillis() / 86400000L) % TIPS.length)], 14, INK, false);
        tv.setPadding(d(12), 0, 0, 0);
        tv.setLineSpacing(0, 1.12f);
        tp.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));
        tip.addView(tp);
        col.addView(tip);
        return col;
    }

    private View taskRow(final Store.Task t, boolean full) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, d(full ? 4 : 8), 0, d(full ? 4 : 0));
        row.setMinimumHeight(d(44));
        final Widgets.RoundCheck rc = new Widgets.RoundCheck(this);
        rc.setChecked(t.done);
        row.addView(rc);
        TextView tx = label(this, t.text, 15, t.done ? MUTED : INK, !t.done);
        tx.setPadding(d(12), 0, d(8), 0);
        if (t.done) tx.setPaintFlags(tx.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
        int tint = "project".equals(t.type) ? VIOLET : "search".equals(t.type) ? AMBER : TEAL;
        TextView tag = label(this, "project".equals(t.type) ? "Project" : "search".equals(t.type) ? "Search" : "Task", 11, tint, true);
        tag.setBackground(round(this, alpha(tint, 30), 10));
        tag.setPadding(d(9), d(3), d(9), d(3));
        row.addView(tag);
        row.setClickable(true);
        row.setOnClickListener(new Tap() { @Override public void tap() { setDone(t.id, !t.done); rebuild(); } });
        return row;
    }

    private void setDone(long id, boolean done) {
        List<Store.Task> l = Store.tasks(this);
        for (Store.Task t : l) if (t.id == id) t.done = done;
        Store.saveTasks(this, l);
    }

    /* ------------------------------------------------------------ Reminders */
    private LinearLayout buildReminders() {
        TextView plus = roundButton("+", GREEN, Color.WHITE, 46);
        plus.setElevation(d(4));
        plus.setOnClickListener(new Tap() { @Override public void tap() { editReminder(null); } });
        List<Store.Reminder> all = Store.reminders(this);
        int on = 0;
        for (Store.Reminder r : all) if (r.enabled) on++;
        LinearLayout col = page(on + " of " + all.size() + " active", "Reminders", plus);

        LinearLayout tools = new LinearLayout(this);
        tools.setPadding(d(18), d(6), d(18), d(6));
        TextView test = chip("🔊  Test sound", false);
        test.setOnClickListener(new Tap() { @Override public void tap() {
            Notifier.sync(MainActivity.this);
            Notifier.show(MainActivity.this, 99999, "Daily Boost 👋", "Notifications are working. Pani pee lo!", true, null);
        } });
        tools.addView(test);
        col.addView(tools);

        int idx = 0;
        for (final Store.Reminder r : all) {
            final int tint = TINTS[idx++ % TINTS.length];
            LinearLayout c = card(this);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(iconChip(this, emojiOf(r.title, "🔔"), tint, 48));
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(d(14), 0, d(8), 0);
            String pt = plainTitle(r.title);
            info.addView(label(this, pt.isEmpty() ? r.title : pt, 17, INK, true));
            info.addView(label(this, r.schedule(), 12, MUTED, false));
            if (r.enabled) {
                final TextView nxt = label(this, "", 12, tint, true);
                nxt.setBackground(round(this, alpha(tint, 28), 10));
                nxt.setPadding(d(9), d(3), d(9), d(3));
                LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2);
                nl.setMargins(0, d(8), 0, 0);
                info.addView(nxt, nl);
                Runnable tk = new Runnable() { @Override public void run() {
                    long when = Scheduler.nextTrigger(r, System.currentTimeMillis());
                    nxt.setText("⏳  in " + eta(when - System.currentTimeMillis()));
                } };
                tk.run();
                tickers.add(tk);
            }
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
            Switch sw = new Switch(this);
            sw.setChecked(r.enabled);
            sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(CompoundButton b, boolean isOn) {
                    r.enabled = isOn; Store.upsert(MainActivity.this, r); Scheduler.schedule(MainActivity.this, r); rebuild();
                }
            });
            row.addView(sw);
            c.addView(row);
            c.setAlpha(r.enabled ? 1f : 0.72f);
            c.setClickable(true);
            c.setOnClickListener(new Tap() { @Override public void tap() { editReminder(r); } });
            col.addView(c);
        }
        return col;
    }

    private String curTone = "";
    private TextView curToneLabel;

    private TextView pillValue(String t) {
        TextView v = label(this, t, 15, GREEN, true);
        v.setBackground(round(this, FIELD, 14));
        v.setPadding(d(16), d(9), d(16), d(9));
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setClickable(true);
        return v;
    }

    private View timeLine(String title, View value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, d(4), 0, 0);
        row.addView(label(this, title, 15, INK, false), new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(value, new LinearLayout.LayoutParams(-2, -2));
        return row;
    }

    private void editReminder(final Store.Reminder existing) {
        final Store.Reminder r = existing != null ? existing : Store.make(this, "", "", 60, 9 * 60, 21 * 60, true, false);
        final int[] startEnd = {r.startMin, r.endMin};
        curTone = r.tone == null ? "" : r.tone;

        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.addView(caption(this, "TITLE"));
        final EditText title = textInput(this, "e.g. Drink water 💧", r.title, false);
        f.addView(title);
        f.addView(caption(this, "MESSAGE"));
        final EditText msg = textInput(this, "What should the notification say?", r.message, true);
        f.addView(msg);

        f.addView(caption(this, "REPEAT EVERY (MINUTES, 0 = ONCE A DAY)"));
        final EditText interval = input(this, "60", String.valueOf(r.intervalMin), InputType.TYPE_CLASS_NUMBER);
        f.addView(interval);
        LinearLayout chips = new LinearLayout(this);
        String[] names = {"Daily", "30 min", "1 hr", "2 hrs", "3 hrs"};
        final int[] vals = {0, 30, 60, 120, 180};
        for (int i = 0; i < names.length; i++) {
            final int v = vals[i];
            TextView c = chip(names[i], false);
            c.setTextSize(13);
            c.setPadding(d(4), d(8), d(4), d(8));
            c.setOnClickListener(new Tap() { @Override public void tap() { interval.setText(String.valueOf(v)); } });
            chips.addView(c, lp(this, 0, -2, i == 0 ? 0 : 3, 0, i == names.length - 1 ? 0 : 3, 0));
            ((LinearLayout.LayoutParams) c.getLayoutParams()).weight = 1f;
        }
        f.addView(chips, lp(this, -1, -2, 0, 8, 0, 0));

        f.addView(caption(this, "ACTIVE BETWEEN"));
        final TextView startV = pillValue(Store.fmt(startEnd[0]));
        final TextView endV = pillValue(Store.fmt(startEnd[1]));
        f.addView(timeLine("Starts at", startV));
        f.addView(timeLine("Ends at", endV));
        startV.setOnClickListener(new Tap() { @Override public void tap() {
            pickTime(startEnd[0], new IntCb() { @Override public void run(int m) { startEnd[0] = m; startV.setText(Store.fmt(m)); } });
        } });
        endV.setOnClickListener(new Tap() { @Override public void tap() {
            pickTime(startEnd[1], new IntCb() { @Override public void run(int m) { startEnd[1] = m; endV.setText(Store.fmt(m)); } });
        } });

        f.addView(caption(this, "SOUND"));
        curToneLabel = label(this, Notifier.toneName(this, curTone.isEmpty() ? Store.tone(this) : curTone), 15, INK, true);
        curToneLabel.setSingleLine(true);
        curToneLabel.setEllipsize(TextUtils.TruncateAt.END);
        f.addView(curToneLabel);
        LinearLayout toneRow = new LinearLayout(this);
        TextView change = button(this, "Change", GREEN, Color.WHITE);
        TextView play = button(this, "▶ Play", AMBER, Color.WHITE);
        TextView reset = button(this, "Default", FIELD, INK);
        change.setOnClickListener(new Tap() { @Override public void tap() {
            pickTone(REQ_TONE_REMINDER, curTone, new ToneCb() { @Override public void got(String uri) {
                curTone = uri;
                curToneLabel.setText(Notifier.toneName(MainActivity.this, uri.isEmpty() ? Store.tone(MainActivity.this) : uri));
            } });
        } });
        play.setOnClickListener(new Tap() { @Override public void tap() { Notifier.previewTone(MainActivity.this, curTone.isEmpty() ? Store.tone(MainActivity.this) : curTone); } });
        reset.setOnClickListener(new Tap() { @Override public void tap() {
            curTone = "";
            curToneLabel.setText(Notifier.toneName(MainActivity.this, Store.tone(MainActivity.this)));
        } });
        toneRow.addView(change, lp(this, 0, -2, 0, 6, 4, 0)); ((LinearLayout.LayoutParams) change.getLayoutParams()).weight = 1f;
        toneRow.addView(play, lp(this, 0, -2, 4, 6, 4, 0)); ((LinearLayout.LayoutParams) play.getLayoutParams()).weight = 1f;
        toneRow.addView(reset, lp(this, 0, -2, 4, 6, 0, 0)); ((LinearLayout.LayoutParams) reset.getLayoutParams()).weight = 1f;
        f.addView(toneRow);

        LinearLayout wrow = new LinearLayout(this);
        wrow.setGravity(Gravity.CENTER_VERTICAL);
        TextView wl = label(this, "Water reminder (adds a \"+1 glass\" button)", 14, INK, false);
        wrow.addView(wl, new LinearLayout.LayoutParams(0, -2, 1f));
        final Switch water = new Switch(this);
        water.setChecked(r.water);
        wrow.addView(water);
        f.addView(wrow, lp(this, -1, -2, 0, 14, 0, 0));

        Dialog dlg = dialog(this, existing == null ? "New reminder" : "Edit reminder", f, "Save", new Action() {
            @Override public boolean run() {
                String t = title.getText().toString().trim();
                if (t.isEmpty()) { toast("Please enter a title"); return false; }
                r.title = t;
                String m = msg.getText().toString().trim();
                r.message = m.isEmpty() ? t : m;
                try { r.intervalMin = Math.max(0, Integer.parseInt(interval.getText().toString().trim())); } catch (NumberFormatException ex) { r.intervalMin = 60; }
                r.startMin = startEnd[0];
                r.endMin = Math.max(startEnd[1], startEnd[0]);
                r.water = water.isChecked();
                r.tone = curTone;
                r.enabled = true;
                Store.upsert(MainActivity.this, r);
                Scheduler.schedule(MainActivity.this, r);
                Notifier.sync(MainActivity.this);
                rebuild();
                return true;
            }
        }, "Cancel", existing != null ? "Delete" : null, new Action() {
            @Override public boolean run() {
                Store.delete(MainActivity.this, r.id);
                Scheduler.cancel(MainActivity.this, r.id);
                Notifier.sync(MainActivity.this);
                rebuild();
                return true;
            }
        });
        dlg.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface di) { Notifier.stopPreview(); }
        });
        dlg.show();
    }

    /* ---------------------------------------------------------------- Tasks */
    private String newType = "task";

    private LinearLayout buildTasks() {
        List<Store.Task> list = Store.tasks(this);
        int left = 0;
        for (Store.Task t : list) if (!t.done) left++;
        LinearLayout col = page(left == 0 ? "All caught up" : left + " to do", "Tasks", null);

        LinearLayout add = card(this);
        LinearLayout inRow = new LinearLayout(this);
        inRow.setGravity(Gravity.CENTER_VERTICAL);
        final EditText input = textInput(this, "Add a task, project or search…", "", false);
        inRow.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView send = roundButton("+", GREEN, Color.WHITE, 48);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(d(48), d(48));
        slp.setMargins(d(10), 0, 0, 0);
        send.setLayoutParams(slp);
        inRow.addView(send);
        add.addView(inRow);
        final String[] types = {"task", "project", "search"};
        String[] names = {"✅ Task", "💻 Project", "🔍 Search"};
        LinearLayout chipRow = new LinearLayout(this);
        final TextView[] cs = new TextView[3];
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            cs[i] = chip(names[i], types[i].equals(newType));
            cs[i].setTextSize(13);
            cs[i].setPadding(d(6), d(8), d(6), d(8));
            cs[i].setOnClickListener(new Tap() { @Override public void tap() {
                newType = types[idx];
                for (int k = 0; k < 3; k++) {
                    boolean on = k == idx;
                    cs[k].setTextColor(on ? Color.WHITE : INK);
                    cs[k].setBackground(round(MainActivity.this, on ? GREEN : FIELD, 20));
                }
            } });
            chipRow.addView(cs[i], lp(this, 0, -2, i == 0 ? 0 : 4, 0, i == 2 ? 0 : 4, 0));
            ((LinearLayout.LayoutParams) cs[i].getLayoutParams()).weight = 1f;
        }
        add.addView(chipRow, lp(this, -1, -2, 0, 12, 0, 0));
        send.setOnClickListener(new Tap() { @Override public void tap() {
            String t = input.getText().toString().trim();
            if (t.isEmpty()) { toast("Type something first"); return; }
            Store.addTask(MainActivity.this, t, newType);
            rebuild();
        } });
        col.addView(add);

        boolean anyDone = false;
        for (final Store.Task t : list) {
            if (t.done) anyDone = true;
            LinearLayout c = card(this);
            c.addView(taskRow(t, true));
            if ("search".equals(t.type) && !t.done) {
                TextView go = button(this, "🔍  Search on Google", AMBER, Color.WHITE);
                go.setMinHeight(d(40));
                go.setOnClickListener(new Tap() { @Override public void tap() {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(t.text)))); }
                    catch (Exception e) { toast("No browser found"); }
                } });
                c.addView(go, lp(this, -1, -2, 0, 8, 0, 0));
            }
            c.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) {
                message(MainActivity.this, "Delete this item?", t.text, "Delete", new Action() {
                    @Override public boolean run() {
                        List<Store.Task> l = Store.tasks(MainActivity.this);
                        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).id == t.id) l.remove(i);
                        Store.saveTasks(MainActivity.this, l);
                        rebuild();
                        return true;
                    }
                }, "Cancel").show();
                return true;
            } });
            col.addView(c);
        }
        if (list.isEmpty()) {
            LinearLayout empty = card(this);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.addView(label(this, "📝", 34, INK, false));
            TextView et = label(this, "Nothing here yet.\nAdd your first task above.", 15, MUTED, false);
            et.setGravity(Gravity.CENTER);
            empty.addView(et);
            col.addView(empty);
        } else {
            TextView hint = label(this, "Tap to complete  •  long-press to delete", 12, MUTED, false);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(d(20), d(6), d(20), 0);
            col.addView(hint);
        }
        if (anyDone) {
            TextView clear = button(this, "Clear completed", FIELD, INK);
            clear.setOnClickListener(new Tap() { @Override public void tap() {
                List<Store.Task> l = Store.tasks(MainActivity.this);
                for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).done) l.remove(i);
                Store.saveTasks(MainActivity.this, l);
                rebuild();
            } });
            col.addView(clear, lp(this, -1, -2, 16, 10, 16, 0));
        }
        return col;
    }

    /* ------------------------------------------------------------- Settings */
    /** A settings group: tinted icon + caption, then rows. */
    private LinearLayout group(LinearLayout col, String emoji, int tint, String title) {
        LinearLayout g = card(this);
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.addView(iconChip(this, emoji, tint, 38));
        TextView t = label(this, title, 13, MUTED, true);
        t.setPadding(d(12), 0, 0, 0);
        h.addView(t);
        g.addView(h);
        col.addView(g);
        return g;
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
        TextView v = label(this, value, 14, GREEN_DARK, true);
        v.setBackground(round(this, alpha(GREEN, 28), 14));
        v.setPadding(d(14), d(8), d(14), d(8));
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.5f));
        row.addView(v, lp(this, -2, -2, 10, 0, 0, 0));
        row.setClickable(true);
        row.setOnClickListener(tap);
        return row;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Color.parseColor("#EDF1F7"));
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, 1));
        return v;
    }

    private LinearLayout buildSettings() {
        LinearLayout col = page("Make it yours", "Settings", null);

        LinearLayout n = group(col, "🔔", ROSE, "NOTIFICATIONS & ALARM");
        TextView toneName = label(this, Notifier.toneName(this, Store.tone(this)), 15, GREEN_DARK, true);
        toneName.setSingleLine(true);
        toneName.setEllipsize(TextUtils.TruncateAt.END);
        n.addView(label(this, "Reminder tone", 16, INK, true), lp(this, -2, -2, 0, 12, 0, 0));
        n.addView(toneName);
        LinearLayout tr = new LinearLayout(this);
        TextView change = button(this, "Change tone", GREEN, Color.WHITE);
        TextView play = button(this, "▶ Play", AMBER, Color.WHITE);
        TextView def = button(this, "Default", FIELD, INK);
        change.setOnClickListener(new Tap() { @Override public void tap() {
            pickTone(REQ_TONE_GLOBAL, Store.tone(MainActivity.this), new ToneCb() { @Override public void got(String uri) {
                Store.setTone(MainActivity.this, uri); Notifier.sync(MainActivity.this); rebuild();
            } });
        } });
        play.setOnClickListener(new Tap() { @Override public void tap() { Notifier.previewTone(MainActivity.this, Store.tone(MainActivity.this)); } });
        def.setOnClickListener(new Tap() { @Override public void tap() { Store.setTone(MainActivity.this, ""); Notifier.sync(MainActivity.this); rebuild(); } });
        tr.addView(change, lp(this, 0, -2, 0, 8, 4, 0)); ((LinearLayout.LayoutParams) change.getLayoutParams()).weight = 1.5f;
        tr.addView(play, lp(this, 0, -2, 4, 8, 4, 0)); ((LinearLayout.LayoutParams) play.getLayoutParams()).weight = 1f;
        tr.addView(def, lp(this, 0, -2, 4, 8, 0, 0)); ((LinearLayout.LayoutParams) def.getLayoutParams()).weight = 1f;
        n.addView(tr);
        n.addView(divider(), lp(this, -1, 1, 0, 12, 0, 2));
        n.addView(switchRow("Alarm mode", "Plays on the alarm volume so you don't miss it, even on silent", Store.alarmMode(this), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) { Store.setAlarmMode(MainActivity.this, on); Notifier.sync(MainActivity.this); }
        }));
        n.addView(divider());
        n.addView(switchRow("Vibration", null, Store.vibrate(this), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) { Store.setVibrate(MainActivity.this, on); Notifier.sync(MainActivity.this); }
        }));
        n.addView(divider());
        n.addView(switchRow("Countdown in status bar", "Shows a live timer to your next reminder", Store.countdownNotif(this), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) { Store.setCountdownNotif(MainActivity.this, on); Notifier.updateCountdown(MainActivity.this); }
        }));
        n.addView(divider());
        n.addView(valueRow("Snooze for", Store.snoozeMin(this) + " min", new Tap() { @Override public void tap() {
            final int[] opts = {5, 10, 15, 30};
            int sel = 1;
            for (int i = 0; i < opts.length; i++) if (opts[i] == Store.snoozeMin(MainActivity.this)) sel = i;
            choose("Snooze length", new String[]{"5 minutes", "10 minutes", "15 minutes", "30 minutes"}, sel, new IntCb() {
                @Override public void run(int i) { Store.setSnoozeMin(MainActivity.this, opts[i]); rebuild(); }
            });
        } }));

        LinearLayout q = group(col, "🌙", VIOLET, "QUIET HOURS");
        q.addView(switchRow("Mute at night", "No reminders between these times", Store.quietOn(this), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) { Store.setQuietOn(MainActivity.this, on); rebuild(); }
        }));
        if (Store.quietOn(this)) {
            q.addView(valueRow("From", Store.fmt(Store.quietStart(this)), new Tap() { @Override public void tap() {
                pickTime(Store.quietStart(MainActivity.this), new IntCb() { @Override public void run(int m) { Store.setQuiet(MainActivity.this, m, Store.quietEnd(MainActivity.this)); rebuild(); } });
            } }));
            q.addView(valueRow("Until", Store.fmt(Store.quietEnd(this)), new Tap() { @Override public void tap() {
                pickTime(Store.quietEnd(MainActivity.this), new IntCb() { @Override public void run(int m) { Store.setQuiet(MainActivity.this, Store.quietStart(MainActivity.this), m); rebuild(); } });
            } }));
        }

        LinearLayout daily = group(col, "💧", BLUE, "DAILY GOALS");
        daily.addView(valueRow("Water goal", Store.waterGoal(this) + " glasses", new Tap() { @Override public void tap() {
            askNumber("Daily water goal (glasses)", Store.waterGoal(MainActivity.this), new IntCb() { @Override public void run(int g) { Store.setWaterGoal(MainActivity.this, g); rebuild(); } });
        } }));
        daily.addView(divider());
        daily.addView(valueRow("Reset reminders", "Restore", new Tap() { @Override public void tap() {
            message(MainActivity.this, "Restore default reminders?", "This replaces your reminder list with the original routine (water, walk, project, planning, stretch, eye rest). Tasks are kept.", "Restore", new Action() {
                @Override public boolean run() {
                    for (Store.Reminder r : Store.reminders(MainActivity.this)) Scheduler.cancel(MainActivity.this, r.id);
                    Store.resetReminders(MainActivity.this);
                    Scheduler.scheduleAll(MainActivity.this);
                    Notifier.sync(MainActivity.this);
                    toast("Reminders restored");
                    return true;
                }
            }, "Cancel").show();
        } }));

        LinearLayout sec = group(col, "🔒", TEAL, "SECURITY");
        sec.addView(switchRow("PIN lock", "Ask for your 4-digit PIN when opening the app", Lock.enabled(this), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                if (on == Lock.enabled(MainActivity.this)) return;
                if (on) {
                    if (Lock.hasPin(MainActivity.this)) { Lock.setEnabled(MainActivity.this, true); rebuild(); }
                    else startActivity(new Intent(MainActivity.this, LoginActivity.class).putExtra(LoginActivity.EXTRA_MODE, LoginActivity.MODE_CHANGE));
                } else { Lock.setEnabled(MainActivity.this, false); rebuild(); }
            }
        }));
        if (Lock.hasPin(this)) {
            sec.addView(divider());
            sec.addView(valueRow("Change PIN", "Change", new Tap() { @Override public void tap() {
                startActivity(new Intent(MainActivity.this, LoginActivity.class).putExtra(LoginActivity.EXTRA_MODE, LoginActivity.MODE_CHANGE));
            } }));
            sec.addView(divider());
            int s0 = Store.autoLockSec(this);
            String cur = s0 < 0 ? "Never" : s0 < 60 ? s0 + " sec" : (s0 / 60) + " min";
            sec.addView(valueRow("Auto-lock after", cur, new Tap() { @Override public void tap() {
                final int[] opts = {15, 60, 300, -1};
                int sel = 1;
                for (int i = 0; i < opts.length; i++) if (opts[i] == Store.autoLockSec(MainActivity.this)) sel = i;
                choose("Lock the app after leaving it for", new String[]{"15 seconds", "1 minute", "5 minutes", "Never"}, sel, new IntCb() {
                    @Override public void run(int i) { Store.setAutoLockSec(MainActivity.this, opts[i]); rebuild(); }
                });
            } }));
        }

        LinearLayout sys = group(col, "📱", AMBER, "IF REMINDERS ARRIVE LATE");
        TextView hint = label(this, "Some phones stop background apps. Allow notifications and set battery to \"Unrestricted\".", 13, MUTED, false);
        hint.setPadding(0, d(10), 0, d(6));
        sys.addView(hint);
        TextView nset = button(this, "Notification settings", GREEN, Color.WHITE);
        nset.setOnClickListener(new Tap() { @Override public void tap() { openNotificationSettings(); } });
        TextView bset = button(this, "Battery & background", FIELD, INK);
        bset.setOnClickListener(new Tap() { @Override public void tap() { openBatterySettings(); } });
        sys.addView(nset, lp(this, -1, -2, 0, 4, 0, 0));
        sys.addView(bset, lp(this, -1, -2, 0, 8, 0, 0));

        LinearLayout about = card(this);
        about.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView name = label(this, "Daily Boost  •  v1.2", 16, INK, true);
        name.setGravity(Gravity.CENTER);
        TextView credit = label(this, "Designed and Developed by IronicXsoft", 13, MUTED, false);
        credit.setGravity(Gravity.CENTER);
        credit.setPadding(0, d(4), 0, 0);
        about.addView(name);
        about.addView(credit);
        col.addView(about);
        return col;
    }

    private void openNotificationSettings() {
        try {
            Intent i = new Intent(Build.VERSION.SDK_INT >= 26 ? Settings.ACTION_APP_NOTIFICATION_SETTINGS : "android.settings.APP_NOTIFICATION_SETTINGS");
            i.putExtra("android.provider.extra.APP_PACKAGE", getPackageName());
            i.putExtra("app_package", getPackageName());
            i.putExtra("app_uid", getApplicationInfo().uid);
            startActivity(i);
        } catch (Exception e) { openAppDetails(); }
    }

    private void openBatterySettings() {
        try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
        catch (Exception e) { openAppDetails(); }
    }

    private void openAppDetails() {
        try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
        catch (Exception e) { toast("Open Settings → Apps → Daily Boost"); }
    }
}
