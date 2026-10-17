package com.dailyboost.app;

import android.app.Activity;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static com.dailyboost.app.UI.*;

public class MainActivity extends Activity {
    private static final int REQ_TONE_GLOBAL = 11, REQ_TONE_REMINDER = 12;
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
    private final TextView[] tabLabels = new TextView[4];
    private final TextView[] tabIcons = new TextView[4];

    // tone picking state
    private interface ToneCb { void got(String uri); }
    private ToneCb toneCb;

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
            getWindow().setStatusBarColor(GREEN_DARK);
            getWindow().setNavigationBarColor(Color.WHITE);
        }
        Store.seedIfNeeded(this);
        Notifier.sync(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(Color.WHITE);
        bar.setElevation(dp(this, 8));
        String[] icons = {"🏠", "⏰", "✅", "⚙️"};
        String[] names = {"Today", "Reminders", "Tasks", "Settings"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setMinimumHeight(dp(this, 58));
            item.setPadding(dp(this, 2), dp(this, 7), dp(this, 2), dp(this, 7));
            item.setClickable(true);
            TextView ic = label(this, icons[i], 19, INK, false);
            ic.setGravity(Gravity.CENTER);
            TextView tx = label(this, names[i], 11, MUTED, true);
            tx.setSingleLine(true);
            tx.setEllipsize(TextUtils.TruncateAt.END);
            tx.setGravity(Gravity.CENTER);
            item.addView(ic);
            item.addView(tx);
            item.setOnClickListener(new Tap() { @Override public void tap() { showTab(idx); } });
            tabIcons[i] = ic;
            tabLabels[i] = tx;
            bar.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));
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
    }

    @Override
    protected void onPause() {
        Lock.touch();
        Notifier.stopPreview();
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

    /* ------------------------------------------------------------- helpers */
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private LinearLayout page(String title, String subtitle) {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, dp(this, 18));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setBackgroundColor(GREEN_DARK);
        head.setPadding(dp(this, 20), dp(this, 22), dp(this, 20), dp(this, 24));
        head.addView(label(this, title, 24, Color.WHITE, true));
        TextView sub = label(this, subtitle, 14, Color.parseColor("#CFE9DD"), false);
        sub.setPadding(0, dp(this, 4), 0, 0);
        head.addView(sub);
        col.addView(head, new LinearLayout.LayoutParams(-1, -2));
        sv.addView(col);
        content.removeAllViews();
        content.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        return col;
    }

    private void showTab(int t) {
        tab = t;
        for (int i = 0; i < 4; i++) {
            tabLabels[i].setTextColor(i == t ? GREEN : MUTED);
            tabIcons[i].setAlpha(i == t ? 1f : 0.55f);
        }
        if (t == 0) buildToday(); else if (t == 1) buildReminders(); else if (t == 2) buildTasks(); else buildSettings();
    }

    private TextView chip(String text, boolean on) {
        TextView c = label(this, text, 14, on ? Color.WHITE : INK, true);
        c.setSingleLine(true);
        c.setGravity(Gravity.CENTER);
        c.setMinHeight(dp(this, 40));
        c.setPadding(dp(this, 16), dp(this, 8), dp(this, 16), dp(this, 8));
        c.setBackground(round(this, on ? GREEN : FIELD, 20));
        c.setClickable(true);
        return c;
    }

    private interface IntCb { void run(int n); }

    private void askNumber(String title, int current, final IntCb cb) {
        final EditText e = input(this, "Number", String.valueOf(current), InputType.TYPE_CLASS_NUMBER);
        Dialog d = dialog(this, title, e, "Save", new Action() {
            @Override public boolean run() {
                try { cb.run(Integer.parseInt(e.getText().toString().trim())); return true; }
                catch (NumberFormatException ex) { toast("Enter a number"); return false; }
            }
        }, "Cancel", null, null);
        d.show();
    }

    private void choose(String title, String[] labels, int selected, final IntCb cb) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] ref = new Dialog[1];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView row = label(this, (i == selected ? "●   " : "○   ") + labels[i], 16, i == selected ? GREEN : INK, i == selected);
            row.setMinHeight(dp(this, 48));
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(this, 4), dp(this, 8), dp(this, 4), dp(this, 8));
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

    /** Two-line row: text on the left, switch on the right. */
    private View switchRow(String title, String desc, boolean on, CompoundButton.OnCheckedChangeListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(this, 8), 0, dp(this, 8));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(label(this, title, 16, INK, true));
        if (desc != null) {
            TextView d = label(this, desc, 13, MUTED, false);
            info.addView(d);
        }
        row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(on);
        sw.setOnCheckedChangeListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(this, 10), 0, 0, 0);
        row.addView(sw, lp);
        return row;
    }

    /** Row with a title and a value on the right; the whole row is tappable. */
    private View valueRow(String title, String value, Tap tap) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(this, 52));
        row.setPadding(0, dp(this, 6), 0, dp(this, 6));
        row.addView(label(this, title, 16, INK, true), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView v = label(this, value, 15, GREEN, true);
        v.setBackground(round(this, FIELD, 14));
        v.setPadding(dp(this, 14), dp(this, 8), dp(this, 14), dp(this, 8));
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.5f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(this, 10), 0, 0, 0);
        row.addView(v, lp);
        row.setClickable(true);
        row.setOnClickListener(tap);
        return row;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Color.parseColor("#E5ECE8"));
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, 1));
        return v;
    }

    /* --------------------------------------------------------------- Today */
    private void buildToday() {
        String date = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date());
        LinearLayout col = page("Daily Boost", date);

        LinearLayout next = card(this);
        next.addView(label(this, "NEXT REMINDER", 11, MUTED, true));
        Store.Reminder r = Scheduler.nextUp(this);
        if (r == null) {
            next.addView(label(this, "No active reminders – turn some on in the Reminders tab.", 15, INK, false));
        } else {
            long when = Scheduler.nextTrigger(r, System.currentTimeMillis());
            Date d = new Date(when);
            boolean sameDay = new SimpleDateFormat("yyyyMMdd", Locale.US).format(d).equals(new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date()));
            String at = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(d) + (sameDay ? "" : " (tomorrow)");
            next.addView(label(this, r.title, 18, INK, true));
            next.addView(label(this, at, 14, GREEN, true));
        }
        col.addView(next);

        LinearLayout water = card(this);
        final int goal = Store.waterGoal(this);
        final int have = Store.water(this);
        water.addView(label(this, "💧  WATER TODAY", 11, MUTED, true));
        TextView count = label(this, have + " / " + goal + " glasses", 26, INK, true);
        count.setPadding(0, dp(this, 4), 0, dp(this, 8));
        water.addView(count);
        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(goal);
        pb.setProgress(Math.min(have, goal));
        water.addView(pb, new LinearLayout.LayoutParams(-1, dp(this, 10)));
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(this, 14), 0, 0);
        TextView minus = button(this, "−", MUTED, Color.WHITE);
        TextView plus = button(this, "+ 1 glass", GREEN, Color.WHITE);
        TextView goalBtn = button(this, "Goal " + goal, AMBER, Color.WHITE);
        minus.setOnClickListener(new Tap() { @Override public void tap() { Store.setWater(MainActivity.this, Store.water(MainActivity.this) - 1); buildToday(); } });
        plus.setOnClickListener(new Tap() { @Override public void tap() { Store.setWater(MainActivity.this, Store.water(MainActivity.this) + 1); buildToday(); } });
        goalBtn.setOnClickListener(new Tap() { @Override public void tap() {
            askNumber("Daily water goal (glasses)", goal, new IntCb() { @Override public void run(int n) { Store.setWaterGoal(MainActivity.this, n); buildToday(); } });
        } });
        row.addView(minus, lp(this, 0, -2, 0, 0, 4, 0)); ((LinearLayout.LayoutParams) minus.getLayoutParams()).weight = 0.8f;
        row.addView(plus, lp(this, 0, -2, 4, 0, 4, 0)); ((LinearLayout.LayoutParams) plus.getLayoutParams()).weight = 1.6f;
        row.addView(goalBtn, lp(this, 0, -2, 4, 0, 0, 0)); ((LinearLayout.LayoutParams) goalBtn.getLayoutParams()).weight = 1.2f;
        water.addView(row);
        col.addView(water);

        LinearLayout tasks = card(this);
        tasks.addView(label(this, "✅  TODAY'S TASKS", 11, MUTED, true));
        int shown = 0;
        for (final Store.Task t : Store.tasks(this)) {
            if (t.done || shown >= 5) continue;
            shown++;
            CheckBox cb = new CheckBox(this);
            cb.setText(("project".equals(t.type) ? "💻 " : "search".equals(t.type) ? "🔍 " : "") + t.text);
            cb.setTextColor(INK);
            cb.setTextSize(15);
            cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(CompoundButton b, boolean on) { if (on) { setDone(t.id, true); buildToday(); } }
            });
            tasks.addView(cb);
        }
        if (shown == 0) tasks.addView(label(this, "All clear! Add tasks in the Tasks tab.", 15, INK, false));
        col.addView(tasks);

        LinearLayout tip = card(this);
        tip.setBackground(round(this, Color.parseColor("#FFF4DE"), 16));
        tip.addView(label(this, "💡  TIP", 11, Color.parseColor("#9A6A12"), true));
        TextView tv = label(this, TIPS[(int) ((System.currentTimeMillis() / 86400000L) % TIPS.length)], 15, INK, false);
        tv.setPadding(0, dp(this, 4), 0, 0);
        tip.addView(tv);
        col.addView(tip);
    }

    private void setDone(long id, boolean done) {
        List<Store.Task> l = Store.tasks(this);
        for (Store.Task t : l) if (t.id == id) t.done = done;
        Store.saveTasks(this, l);
    }

    /* ----------------------------------------------------------- Reminders */
    private void buildReminders() {
        LinearLayout col = page("Reminders", "Turn them on and your phone nudges you all day");

        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(this, 14), dp(this, 12), dp(this, 14), dp(this, 4));
        TextView add = button(this, "+ New reminder", GREEN, Color.WHITE);
        TextView test = button(this, "Test sound", AMBER, Color.WHITE);
        add.setOnClickListener(new Tap() { @Override public void tap() { editReminder(null); } });
        test.setOnClickListener(new Tap() { @Override public void tap() {
            Notifier.sync(MainActivity.this);
            Notifier.show(MainActivity.this, 99999, "Daily Boost 👋", "Notifications are working. Pani pee lo!", true, null);
        } });
        actions.addView(add, lp(this, 0, -2, 0, 0, 5, 0)); ((LinearLayout.LayoutParams) add.getLayoutParams()).weight = 1.5f;
        actions.addView(test, lp(this, 0, -2, 5, 0, 0, 0)); ((LinearLayout.LayoutParams) test.getLayoutParams()).weight = 1f;
        col.addView(actions);

        for (final Store.Reminder r : Store.reminders(this)) {
            LinearLayout c = card(this);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.addView(label(this, r.title, 17, INK, true));
            info.addView(label(this, r.schedule(), 13, MUTED, false));
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
            Switch sw = new Switch(this);
            sw.setChecked(r.enabled);
            sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                    r.enabled = on; Store.upsert(MainActivity.this, r); Scheduler.schedule(MainActivity.this, r);
                }
            });
            LinearLayout.LayoutParams swp = new LinearLayout.LayoutParams(-2, -2);
            swp.setMargins(dp(this, 10), 0, 0, 0);
            row.addView(sw, swp);
            c.addView(row);
            c.setClickable(true);
            c.setOnClickListener(new Tap() { @Override public void tap() { editReminder(r); } });
            col.addView(c);
        }
    }

    private String curTone = "";
    private TextView curToneLabel;

    private void editReminder(final Store.Reminder existing) {
        final Store.Reminder r = existing != null ? existing : Store.make(this, "", "", 60, 9 * 60, 21 * 60, true, false);
        final int[] startEnd = {r.startMin, r.endMin};
        curTone = r.tone == null ? "" : r.tone;

        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);

        f.addView(caption(this, "TITLE"));
        final EditText title = textInput(this, "e.g. Drink water", r.title, false);
        f.addView(title);
        f.addView(caption(this, "MESSAGE"));
        final EditText msg = textInput(this, "What should the notification say?", r.message, true);
        f.addView(msg);

        f.addView(caption(this, "REPEAT EVERY (MINUTES, 0 = ONCE A DAY)"));
        final EditText interval = input(this, "60", String.valueOf(r.intervalMin), InputType.TYPE_CLASS_NUMBER);
        f.addView(interval);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        String[] names = {"Daily", "30 min", "1 hour", "2 hours", "3 hours"};
        final int[] vals = {0, 30, 60, 120, 180};
        for (int i = 0; i < names.length; i++) {
            final int v = vals[i];
            TextView c = chip(names[i], false);
            c.setOnClickListener(new Tap() { @Override public void tap() { interval.setText(String.valueOf(v)); } });
            chips.addView(c, lp(this, -2, -2, 0, 0, 8, 0));
        }
        hs.addView(chips);
        f.addView(hs, lp(this, -1, -2, 0, 8, 0, 0));

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
        TextView reset = button(this, "Default", MUTED, Color.WHITE);
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

        final CheckBox water = new CheckBox(this);
        water.setText("Water reminder (adds a \"+1 glass\" button)");
        water.setTextColor(INK);
        water.setTextSize(15);
        water.setChecked(r.water);
        f.addView(water, lp(this, -1, -2, 0, 12, 0, 0));

        Dialog d = dialog(this, existing == null ? "New reminder" : "Edit reminder", f, "Save", new Action() {
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
                buildReminders();
                return true;
            }
        }, "Cancel", existing != null ? "Delete" : null, new Action() {
            @Override public boolean run() {
                Scheduler.cancel(MainActivity.this, r.id);
                Store.delete(MainActivity.this, r.id);
                Notifier.sync(MainActivity.this);
                buildReminders();
                return true;
            }
        });
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface di) { Notifier.stopPreview(); }
        });
        d.show();
    }

    private TextView pillValue(String t) {
        TextView v = label(this, t, 15, GREEN, true);
        v.setBackground(round(this, FIELD, 14));
        v.setPadding(dp(this, 16), dp(this, 9), dp(this, 16), dp(this, 9));
        v.setSingleLine(true);
        v.setClickable(true);
        return v;
    }

    private View timeLine(String title, View value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(this, 4), 0, 0);
        row.addView(label(this, title, 15, INK, false), new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(value, new LinearLayout.LayoutParams(-2, -2));
        return row;
    }

    /* --------------------------------------------------------------- Tasks */
    private String newType = "task";

    private void buildTasks() {
        LinearLayout col = page("Tasks", "Projects, to-dos and things to look up");

        LinearLayout add = card(this);
        final EditText input = textInput(this, "What do you need to do or search?", "", false);
        add.addView(input);
        final String[] types = {"task", "project", "search"};
        String[] names = {"Task", "Project", "Search"};
        final LinearLayout chipRow = new LinearLayout(this);
        final TextView[] cs = new TextView[3];
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            cs[i] = chip(names[i], types[i].equals(newType));
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
        add.addView(chipRow, lp(this, -1, -2, 0, 10, 0, 0));
        TextView addBtn = button(this, "Add", GREEN, Color.WHITE);
        addBtn.setOnClickListener(new Tap() { @Override public void tap() {
            String t = input.getText().toString().trim();
            if (t.isEmpty()) { toast("Type something first"); return; }
            Store.addTask(MainActivity.this, t, newType);
            buildTasks();
        } });
        add.addView(addBtn, lp(this, -1, -2, 0, 10, 0, 0));
        col.addView(add);

        List<Store.Task> list = Store.tasks(this);
        boolean anyDone = false;
        for (final Store.Task t : list) {
            if (t.done) anyDone = true;
            LinearLayout c = card(this);
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            CheckBox cb = new CheckBox(this);
            cb.setChecked(t.done);
            cb.setText(("project".equals(t.type) ? "💻 " : "search".equals(t.type) ? "🔍 " : "") + t.text);
            cb.setTextSize(15);
            cb.setTextColor(t.done ? MUTED : INK);
            if (t.done) cb.setPaintFlags(cb.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(CompoundButton b, boolean on) { setDone(t.id, on); buildTasks(); }
            });
            r.addView(cb, new LinearLayout.LayoutParams(0, -2, 1f));
            if ("search".equals(t.type)) {
                TextView go = button(this, "Search", AMBER, Color.WHITE);
                go.setMinHeight(dp(this, 38));
                go.setOnClickListener(new Tap() { @Override public void tap() {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(t.text)))); }
                    catch (Exception e) { toast("No browser found"); }
                } });
                r.addView(go, lp(this, -2, -2, 8, 0, 0, 0));
            }
            c.addView(r);
            c.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) {
                message(MainActivity.this, "Delete this item?", t.text, "Delete", new Action() {
                    @Override public boolean run() {
                        List<Store.Task> l = Store.tasks(MainActivity.this);
                        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).id == t.id) l.remove(i);
                        Store.saveTasks(MainActivity.this, l);
                        buildTasks();
                        return true;
                    }
                }, "Cancel").show();
                return true;
            } });
            col.addView(c);
        }
        if (list.isEmpty()) {
            TextView empty = label(this, "Nothing here yet. Add your first task above.", 15, MUTED, false);
            empty.setPadding(dp(this, 20), dp(this, 16), dp(this, 20), 0);
            col.addView(empty);
        } else {
            TextView hint = label(this, "Tip: long-press an item to delete it.", 12, MUTED, false);
            hint.setPadding(dp(this, 20), dp(this, 6), dp(this, 20), 0);
            col.addView(hint);
        }
        if (anyDone) {
            TextView clear = button(this, "Clear completed", MUTED, Color.WHITE);
            clear.setOnClickListener(new Tap() { @Override public void tap() {
                List<Store.Task> l = Store.tasks(MainActivity.this);
                for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).done) l.remove(i);
                Store.saveTasks(MainActivity.this, l);
                buildTasks();
            } });
            col.addView(clear, lp(this, -1, -2, 14, 10, 14, 0));
        }
    }

    /* ------------------------------------------------------------ Settings */
    private void buildSettings() {
        LinearLayout col = page("Settings", "Sounds, alarms, security and more");

        // ---- notifications
        LinearLayout n = card(this);
        n.addView(label(this, "🔔  NOTIFICATIONS & ALARM", 11, MUTED, true));
        final TextView toneName = label(this, Notifier.toneName(this, Store.tone(this)), 15, GREEN, true);
        toneName.setSingleLine(true);
        toneName.setEllipsize(TextUtils.TruncateAt.END);
        n.addView(label(this, "Reminder tone", 16, INK, true), lp(this, -2, -2, 0, 8, 0, 0));
        n.addView(toneName);
        LinearLayout tr = new LinearLayout(this);
        TextView change = button(this, "Change tone", GREEN, Color.WHITE);
        TextView play = button(this, "▶ Play", AMBER, Color.WHITE);
        TextView def = button(this, "Default", MUTED, Color.WHITE);
        change.setOnClickListener(new Tap() { @Override public void tap() {
            pickTone(REQ_TONE_GLOBAL, Store.tone(MainActivity.this), new ToneCb() { @Override public void got(String uri) {
                Store.setTone(MainActivity.this, uri); Notifier.sync(MainActivity.this); buildSettings();
            } });
        } });
        play.setOnClickListener(new Tap() { @Override public void tap() { Notifier.previewTone(MainActivity.this, Store.tone(MainActivity.this)); } });
        def.setOnClickListener(new Tap() { @Override public void tap() { Store.setTone(MainActivity.this, ""); Notifier.sync(MainActivity.this); buildSettings(); } });
        tr.addView(change, lp(this, 0, -2, 0, 8, 4, 0)); ((LinearLayout.LayoutParams) change.getLayoutParams()).weight = 1.5f;
        tr.addView(play, lp(this, 0, -2, 4, 8, 4, 0)); ((LinearLayout.LayoutParams) play.getLayoutParams()).weight = 1f;
        tr.addView(def, lp(this, 0, -2, 4, 8, 0, 0)); ((LinearLayout.LayoutParams) def.getLayoutParams()).weight = 1f;
        n.addView(tr);
        n.addView(divider(), lp(this, -1, 1, 0, 12, 0, 4));
        n.addView(switchRow("Alarm mode", "Plays on the alarm volume so you don't miss it, even on silent", Store.alarmMode(this), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) { Store.setAlarmMode(MainActivity.this, on); Notifier.sync(MainActivity.this); }
        }));
        n.addView(switchRow("Vibration", null, Store.vibrate(this), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) { Store.setVibrate(MainActivity.this, on); Notifier.sync(MainActivity.this); }
        }));
        n.addView(divider());
        n.addView(valueRow("Snooze for", Store.snoozeMin(this) + " min", new Tap() { @Override public void tap() {
            final int[] opts = {5, 10, 15, 30};
            int sel = 1;
            for (int i = 0; i < opts.length; i++) if (opts[i] == Store.snoozeMin(MainActivity.this)) sel = i;
            choose("Snooze length", new String[]{"5 minutes", "10 minutes", "15 minutes", "30 minutes"}, sel, new IntCb() {
                @Override public void run(int i) { Store.setSnoozeMin(MainActivity.this, opts[i]); buildSettings(); }
            });
        } }));
        col.addView(n);

        // ---- quiet hours
        LinearLayout q = card(this);
        q.addView(label(this, "🌙  QUIET HOURS", 11, MUTED, true));
        q.addView(switchRow("Mute at night", "No reminders between these times", Store.quietOn(this), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) { Store.setQuietOn(MainActivity.this, on); buildSettings(); }
        }));
        if (Store.quietOn(this)) {
            q.addView(valueRow("From", Store.fmt(Store.quietStart(this)), new Tap() { @Override public void tap() {
                pickTime(Store.quietStart(MainActivity.this), new IntCb() { @Override public void run(int m) { Store.setQuiet(MainActivity.this, m, Store.quietEnd(MainActivity.this)); buildSettings(); } });
            } }));
            q.addView(valueRow("Until", Store.fmt(Store.quietEnd(this)), new Tap() { @Override public void tap() {
                pickTime(Store.quietEnd(MainActivity.this), new IntCb() { @Override public void run(int m) { Store.setQuiet(MainActivity.this, Store.quietStart(MainActivity.this), m); buildSettings(); } });
            } }));
        }
        col.addView(q);

        // ---- daily
        LinearLayout daily = card(this);
        daily.addView(label(this, "💧  DAILY GOALS", 11, MUTED, true));
        daily.addView(valueRow("Water goal", Store.waterGoal(this) + " glasses", new Tap() { @Override public void tap() {
            askNumber("Daily water goal (glasses)", Store.waterGoal(MainActivity.this), new IntCb() { @Override public void run(int g) { Store.setWaterGoal(MainActivity.this, g); buildSettings(); } });
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
        col.addView(daily);

        // ---- security
        LinearLayout sec = card(this);
        sec.addView(label(this, "🔒  SECURITY", 11, MUTED, true));
        sec.addView(switchRow("PIN lock", "Ask for your 4-digit PIN when opening the app", Lock.enabled(this), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                if (on == Lock.enabled(MainActivity.this)) return;
                if (on) {
                    if (Lock.hasPin(MainActivity.this)) { Lock.setEnabled(MainActivity.this, true); buildSettings(); }
                    else startActivity(new Intent(MainActivity.this, LoginActivity.class).putExtra(LoginActivity.EXTRA_MODE, LoginActivity.MODE_CHANGE));
                } else { Lock.setEnabled(MainActivity.this, false); buildSettings(); }
            }
        }));
        if (Lock.hasPin(this)) {
            sec.addView(divider());
            sec.addView(valueRow("Change PIN", "Change", new Tap() { @Override public void tap() {
                startActivity(new Intent(MainActivity.this, LoginActivity.class).putExtra(LoginActivity.EXTRA_MODE, LoginActivity.MODE_CHANGE));
            } }));
            sec.addView(divider());
            int sec0 = Store.autoLockSec(this);
            String cur = sec0 < 0 ? "Never" : sec0 < 60 ? sec0 + " sec" : (sec0 / 60) + " min";
            sec.addView(valueRow("Auto-lock after", cur, new Tap() { @Override public void tap() {
                final int[] opts = {15, 60, 300, -1};
                int sel = 1;
                for (int i = 0; i < opts.length; i++) if (opts[i] == Store.autoLockSec(MainActivity.this)) sel = i;
                choose("Lock the app after leaving it for", new String[]{"15 seconds", "1 minute", "5 minutes", "Never"}, sel, new IntCb() {
                    @Override public void run(int i) { Store.setAutoLockSec(MainActivity.this, opts[i]); buildSettings(); }
                });
            } }));
        }
        col.addView(sec);

        // ---- phone settings
        LinearLayout sys = card(this);
        sys.addView(label(this, "📱  IF REMINDERS ARRIVE LATE", 11, MUTED, true));
        TextView hint = label(this, "Some phones stop background apps. Allow notifications and set battery to \"Unrestricted\".", 13, MUTED, false);
        hint.setPadding(0, dp(this, 4), 0, dp(this, 6));
        sys.addView(hint);
        TextView nset = button(this, "Notification settings", GREEN, Color.WHITE);
        nset.setOnClickListener(new Tap() { @Override public void tap() { openNotificationSettings(); } });
        TextView bset = button(this, "Battery & background", AMBER, Color.WHITE);
        bset.setOnClickListener(new Tap() { @Override public void tap() { openBatterySettings(); } });
        sys.addView(nset, lp(this, -1, -2, 0, 4, 0, 0));
        sys.addView(bset, lp(this, -1, -2, 0, 8, 0, 0));
        col.addView(sys);

        // ---- about
        LinearLayout about = card(this);
        about.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView name = label(this, "Daily Boost  •  v1.1", 16, INK, true);
        name.setGravity(Gravity.CENTER);
        TextView credit = label(this, "Designed and Developed by IronicXsoft", 13, MUTED, false);
        credit.setGravity(Gravity.CENTER);
        credit.setPadding(0, dp(this, 4), 0, 0);
        about.addView(name);
        about.addView(credit);
        col.addView(about);
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
