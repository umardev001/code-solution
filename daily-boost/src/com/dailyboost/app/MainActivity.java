package com.dailyboost.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int GREEN = Color.parseColor("#1B8A5A");
    private static final int GREEN_DARK = Color.parseColor("#0F5C3B");
    private static final int BG = Color.parseColor("#F3F7F5");
    private static final int INK = Color.parseColor("#17302A");
    private static final int MUTED = Color.parseColor("#5F7A70");
    private static final int AMBER = Color.parseColor("#E8A33D");

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
    private Button[] tabButtons = new Button[3];

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(GREEN_DARK);
            getWindow().setNavigationBarColor(Color.WHITE);
        }
        Store.seedIfNeeded(this);
        Notifier.createChannel(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(Color.WHITE);
        bar.setElevation(dp(8));
        String[] names = {"🏠  Today", "⏰  Reminders", "✅  Tasks"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            Button b = new Button(this);
            b.setText(names[i]);
            b.setAllCaps(false);
            b.setTextSize(14);
            b.setBackground(null);
            b.setOnClickListener(v -> showTab(idx));
            tabButtons[i] = b;
            bar.addView(b, new LinearLayout.LayoutParams(0, dp(56), 1f));
        }
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Scheduler.scheduleAll(this);
        showTab(tab);
    }

    /* ------------------------------------------------------------- helpers */
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView text(String t, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(Color.WHITE, 16));
        c.setElevation(dp(2));
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(14), dp(7), dp(14), dp(7));
        c.setLayoutParams(lp);
        return c;
    }

    private Button pill(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackground(round(color, 24));
        b.setPadding(dp(18), 0, dp(18), 0);
        return b;
    }

    private LinearLayout page(String title, String subtitle) {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, dp(16));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setBackground(round(GREEN_DARK, 0));
        head.setPadding(dp(20), dp(22), dp(20), dp(24));
        head.addView(text(title, 24, Color.WHITE, true));
        TextView sub = text(subtitle, 14, Color.parseColor("#CFE9DD"), false);
        sub.setPadding(0, dp(4), 0, 0);
        head.addView(sub);
        col.addView(head, new LinearLayout.LayoutParams(-1, -2));
        sv.addView(col);
        content.removeAllViews();
        content.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        return col;
    }

    private void showTab(int t) {
        tab = t;
        for (int i = 0; i < 3; i++) {
            tabButtons[i].setTextColor(i == t ? GREEN : MUTED);
            tabButtons[i].setTypeface(i == t ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
        if (t == 0) buildToday(); else if (t == 1) buildReminders(); else buildTasks();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    /* --------------------------------------------------------------- Today */
    private void buildToday() {
        String date = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date());
        LinearLayout col = page("Daily Boost", date);

        // next reminder
        LinearLayout next = card();
        next.addView(text("NEXT REMINDER", 11, MUTED, true));
        Store.Reminder r = Scheduler.nextUp(this);
        if (r == null) {
            next.addView(text("No active reminders – turn some on in the Reminders tab.", 15, INK, false));
        } else {
            long when = Scheduler.nextTrigger(r, System.currentTimeMillis());
            Date d = new Date(when);
            boolean sameDay = new SimpleDateFormat("yyyyMMdd", Locale.US).format(d).equals(new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date()));
            String at = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(d) + (sameDay ? "" : " (tomorrow)");
            next.addView(text(r.title, 18, INK, true));
            next.addView(text(at, 14, GREEN, true));
        }
        col.addView(next);

        // water
        final LinearLayout water = card();
        final int goal = Store.waterGoal(this);
        final int have = Store.water(this);
        water.addView(text("💧  WATER TODAY", 11, MUTED, true));
        final TextView count = text(have + " / " + goal + " glasses", 26, INK, true);
        count.setPadding(0, dp(4), 0, dp(8));
        water.addView(count);
        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(goal);
        pb.setProgress(Math.min(have, goal));
        water.addView(pb, new LinearLayout.LayoutParams(-1, dp(10)));
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(12), 0, 0);
        Button minus = pill("− 1", MUTED);
        Button plus = pill("+ 1 glass", GREEN);
        Button goalBtn = pill("Goal: " + goal, AMBER);
        minus.setOnClickListener(v -> { Store.setWater(this, Store.water(this) - 1); buildToday(); });
        plus.setOnClickListener(v -> { Store.setWater(this, Store.water(this) + 1); buildToday(); });
        goalBtn.setOnClickListener(v -> askNumber("Daily water goal (glasses)", goal, n -> { Store.setWaterGoal(this, n); buildToday(); }));
        row.addView(minus, new LinearLayout.LayoutParams(0, dp(44), 1f));
        row.addView(plus, new LinearLayout.LayoutParams(0, dp(44), 2f));
        row.addView(goalBtn, new LinearLayout.LayoutParams(0, dp(44), 1.4f));
        for (int i = 0; i < row.getChildCount(); i++) ((LinearLayout.LayoutParams) row.getChildAt(i).getLayoutParams()).setMargins(dp(3), 0, dp(3), 0);
        water.addView(row);
        col.addView(water);

        // pending tasks
        LinearLayout tasks = card();
        tasks.addView(text("✅  TODAY'S TASKS", 11, MUTED, true));
        int shown = 0;
        for (final Store.Task t : Store.tasks(this)) {
            if (t.done || shown >= 5) continue;
            shown++;
            CheckBox cb = new CheckBox(this);
            cb.setText(("project".equals(t.type) ? "💻 " : "search".equals(t.type) ? "🔍 " : "") + t.text);
            cb.setTextColor(INK);
            cb.setTextSize(15);
            cb.setOnCheckedChangeListener((b, c) -> { if (c) { setDone(t.id, true); buildToday(); } });
            tasks.addView(cb);
        }
        if (shown == 0) tasks.addView(text("All clear! Add tasks in the Tasks tab.", 15, INK, false));
        col.addView(tasks);

        // tip
        LinearLayout tip = card();
        tip.setBackground(round(Color.parseColor("#FFF4DE"), 16));
        tip.addView(text("💡  TIP", 11, Color.parseColor("#9A6A12"), true));
        TextView tv = text(TIPS[(int) ((System.currentTimeMillis() / 86400000L) % TIPS.length)], 15, INK, false);
        tv.setPadding(0, dp(4), 0, 0);
        tip.addView(tv);
        col.addView(tip);
    }

    private void setDone(long id, boolean done) {
        List<Store.Task> l = Store.tasks(this);
        for (Store.Task t : l) if (t.id == id) t.done = done;
        Store.saveTasks(this, l);
    }

    private interface IntCb { void run(int n); }

    private void askNumber(String title, int current, final IntCb cb) {
        final EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setText(String.valueOf(current));
        new AlertDialog.Builder(this).setTitle(title).setView(e)
                .setPositiveButton("Save", (d, w) -> {
                    try { cb.run(Integer.parseInt(e.getText().toString().trim())); } catch (NumberFormatException ex) { toast("Enter a number"); }
                }).setNegativeButton("Cancel", null).show();
    }

    /* ----------------------------------------------------------- Reminders */
    private void buildReminders() {
        LinearLayout col = page("Reminders", "Turn them on and your phone nudges you all day");

        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(14), dp(10), dp(14), dp(2));
        Button add = pill("+ New", GREEN);
        Button test = pill("Test notification", AMBER);
        add.setOnClickListener(v -> editReminder(null));
        test.setOnClickListener(v -> { Notifier.show(this, 99999, "Daily Boost 👋", "Notifications are working. Pani pee lo!", true); });
        actions.addView(add, new LinearLayout.LayoutParams(0, dp(44), 1f));
        actions.addView(test, new LinearLayout.LayoutParams(0, dp(44), 1.6f));
        ((LinearLayout.LayoutParams) add.getLayoutParams()).setMargins(0, 0, dp(8), 0);
        col.addView(actions);

        for (final Store.Reminder r : Store.reminders(this)) {
            LinearLayout c = card();
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.addView(text(r.title, 17, INK, true));
            info.addView(text(r.schedule(), 13, MUTED, false));
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
            Switch sw = new Switch(this);
            sw.setChecked(r.enabled);
            sw.setOnCheckedChangeListener((b, on) -> { r.enabled = on; Store.upsert(this, r); Scheduler.schedule(this, r); });
            row.addView(sw);
            c.addView(row);
            c.setOnClickListener(v -> editReminder(r));
            col.addView(c);
        }
    }

    private void editReminder(final Store.Reminder existing) {
        final Store.Reminder r = existing != null ? existing : Store.make(this, "", "", 60, 9 * 60, 21 * 60, true, false);
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText title = new EditText(this);
        title.setHint("Title (e.g. Drink water)");
        title.setText(r.title);
        final EditText msg = new EditText(this);
        msg.setHint("Message");
        msg.setText(r.message);
        final EditText interval = new EditText(this);
        interval.setHint("Repeat every N minutes (0 = once a day)");
        interval.setInputType(InputType.TYPE_CLASS_NUMBER);
        interval.setText(String.valueOf(r.intervalMin));
        final Button start = pill("Start: " + Store.fmt(r.startMin), GREEN);
        final Button end = pill("End: " + Store.fmt(r.endMin), GREEN);
        start.setOnClickListener(v -> new TimePickerDialog(this, (tp, h, m) -> { r.startMin = h * 60 + m; start.setText("Start: " + Store.fmt(r.startMin)); }, r.startMin / 60, r.startMin % 60, false).show());
        end.setOnClickListener(v -> new TimePickerDialog(this, (tp, h, m) -> { r.endMin = h * 60 + m; end.setText("End: " + Store.fmt(r.endMin)); }, r.endMin / 60, r.endMin % 60, false).show());
        final CheckBox water = new CheckBox(this);
        water.setText("Water reminder (adds a \"+1 glass\" button)");
        water.setChecked(r.water);
        f.addView(title); f.addView(msg); f.addView(interval);
        LinearLayout times = new LinearLayout(this);
        times.setPadding(0, dp(10), 0, dp(6));
        times.addView(start, new LinearLayout.LayoutParams(0, dp(44), 1f));
        times.addView(end, new LinearLayout.LayoutParams(0, dp(44), 1f));
        ((LinearLayout.LayoutParams) start.getLayoutParams()).setMargins(0, 0, dp(6), 0);
        f.addView(times);
        f.addView(water);
        ScrollView sv = new ScrollView(this);
        sv.addView(f);

        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(existing == null ? "New reminder" : "Edit reminder").setView(sv)
                .setPositiveButton("Save", (d, w) -> {
                    String t = title.getText().toString().trim();
                    if (t.isEmpty()) { toast("Please enter a title"); return; }
                    r.title = t;
                    r.message = msg.getText().toString().trim().isEmpty() ? t : msg.getText().toString().trim();
                    try { r.intervalMin = Math.max(0, Integer.parseInt(interval.getText().toString().trim())); } catch (NumberFormatException ex) { r.intervalMin = 60; }
                    if (r.endMin < r.startMin) r.endMin = r.startMin;
                    r.water = water.isChecked();
                    r.enabled = true;
                    Store.upsert(this, r);
                    Scheduler.schedule(this, r);
                    buildReminders();
                })
                .setNegativeButton("Cancel", null);
        if (existing != null) {
            b.setNeutralButton("Delete", (d, w) -> { Scheduler.cancel(this, r.id); Store.delete(this, r.id); buildReminders(); });
        }
        b.show();
    }

    /* --------------------------------------------------------------- Tasks */
    private void buildTasks() {
        LinearLayout col = page("Tasks", "Projects, to-dos and things to look up");

        LinearLayout add = card();
        final EditText input = new EditText(this);
        input.setHint("What do you need to do or search?");
        input.setSingleLine(true);
        add.addView(input);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(8), 0, 0);
        final Spinner type = new Spinner(this);
        type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Task", "Project", "Search"}));
        Button addBtn = pill("Add", GREEN);
        row.addView(type, new LinearLayout.LayoutParams(0, dp(44), 1f));
        row.addView(addBtn, new LinearLayout.LayoutParams(0, dp(44), 1f));
        add.addView(row);
        addBtn.setOnClickListener(v -> {
            String t = input.getText().toString().trim();
            if (t.isEmpty()) { toast("Type something first"); return; }
            Store.addTask(this, t, type.getSelectedItem().toString().toLowerCase(Locale.US));
            buildTasks();
        });
        col.addView(add);

        List<Store.Task> list = Store.tasks(this);
        boolean anyDone = false;
        for (final Store.Task t : list) {
            if (t.done) anyDone = true;
            LinearLayout c = card();
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            CheckBox cb = new CheckBox(this);
            cb.setChecked(t.done);
            cb.setText(("project".equals(t.type) ? "💻 " : "search".equals(t.type) ? "🔍 " : "") + t.text);
            cb.setTextSize(15);
            cb.setTextColor(t.done ? MUTED : INK);
            if (t.done) cb.setPaintFlags(cb.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            cb.setOnCheckedChangeListener((b, on) -> { setDone(t.id, on); buildTasks(); });
            r.addView(cb, new LinearLayout.LayoutParams(0, -2, 1f));
            if ("search".equals(t.type)) {
                Button go = pill("Search", AMBER);
                go.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(t.text)))));
                r.addView(go, new LinearLayout.LayoutParams(-2, dp(38)));
            }
            c.addView(r);
            c.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setTitle("Delete this item?").setMessage(t.text)
                        .setPositiveButton("Delete", (d, w) -> {
                            List<Store.Task> l = Store.tasks(this);
                            for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).id == t.id) l.remove(i);
                            Store.saveTasks(this, l);
                            buildTasks();
                        }).setNegativeButton("Cancel", null).show();
                return true;
            });
            col.addView(c);
        }
        if (list.isEmpty()) {
            TextView empty = text("Nothing here yet. Add your first task above.", 15, MUTED, false);
            empty.setPadding(dp(20), dp(16), dp(20), 0);
            col.addView(empty);
        }
        if (anyDone) {
            Button clear = pill("Clear completed", MUTED);
            clear.setOnClickListener(v -> {
                List<Store.Task> l = Store.tasks(this);
                for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).done) l.remove(i);
                Store.saveTasks(this, l);
                buildTasks();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(44));
            lp.setMargins(dp(14), dp(10), dp(14), 0);
            col.addView(clear, lp);
        }
    }
}
