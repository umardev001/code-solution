package com.dailyboost.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** All app data lives in SharedPreferences as small JSON blobs. */
public final class Store {
    private static final String PREFS = "daily_boost";

    public static final class Reminder {
        public int id;
        public String title;
        public String message;
        public int intervalMin;   // 0 = once a day at startMin
        public int startMin;      // minutes after midnight
        public int endMin;
        public boolean enabled;
        public boolean water;     // notification gets a "+1 glass" button

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("title", title); o.put("message", message);
            o.put("interval", intervalMin); o.put("start", startMin); o.put("end", endMin);
            o.put("enabled", enabled); o.put("water", water);
            return o;
        }

        static Reminder fromJson(JSONObject o) {
            Reminder r = new Reminder();
            r.id = o.optInt("id"); r.title = o.optString("title"); r.message = o.optString("message");
            r.intervalMin = o.optInt("interval"); r.startMin = o.optInt("start"); r.endMin = o.optInt("end");
            r.enabled = o.optBoolean("enabled"); r.water = o.optBoolean("water");
            return r;
        }

        public String schedule() {
            if (intervalMin <= 0) return "Daily at " + fmt(startMin);
            String every = intervalMin % 60 == 0 ? (intervalMin / 60 == 1 ? "hour" : (intervalMin / 60) + " hours") : intervalMin + " min";
            return "Every " + every + "  •  " + fmt(startMin) + " – " + fmt(endMin);
        }
    }

    public static final class Task {
        public long id;
        public String text;
        public String type;   // task | project | search
        public boolean done;
    }

    public static String fmt(int min) {
        int h = (min / 60) % 24, m = min % 60;
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return h12 + ":" + (m < 10 ? "0" : "") + m + (h < 12 ? " AM" : " PM");
    }

    private static SharedPreferences sp(Context c) { return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    /* ------------------------------------------------------------ reminders */
    public static List<Reminder> reminders(Context c) {
        List<Reminder> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("reminders", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(Reminder.fromJson(a.getJSONObject(i)));
        } catch (JSONException ignored) { }
        return out;
    }

    public static Reminder reminder(Context c, int id) {
        for (Reminder r : reminders(c)) if (r.id == id) return r;
        return null;
    }

    public static void saveReminders(Context c, List<Reminder> list) {
        JSONArray a = new JSONArray();
        try { for (Reminder r : list) a.put(r.toJson()); } catch (JSONException ignored) { }
        sp(c).edit().putString("reminders", a.toString()).apply();
    }

    public static void upsert(Context c, Reminder r) {
        List<Reminder> list = reminders(c);
        boolean found = false;
        for (int i = 0; i < list.size(); i++) if (list.get(i).id == r.id) { list.set(i, r); found = true; }
        if (!found) list.add(r);
        saveReminders(c, list);
    }

    public static void delete(Context c, int id) {
        List<Reminder> list = reminders(c);
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id == id) list.remove(i);
        saveReminders(c, list);
    }

    public static int nextId(Context c) {
        int id = sp(c).getInt("next_id", 100) + 1;
        sp(c).edit().putInt("next_id", id).apply();
        return id;
    }

    public static Reminder make(Context c, String title, String msg, int interval, int start, int end, boolean enabled, boolean water) {
        Reminder r = new Reminder();
        r.id = nextId(c); r.title = title; r.message = msg; r.intervalMin = interval;
        r.startMin = start; r.endMin = end; r.enabled = enabled; r.water = water;
        return r;
    }

    /** First launch: a ready-made weight-loss / productivity routine. */
    public static void seedIfNeeded(Context c) {
        if (sp(c).getBoolean("seeded", false)) return;
        List<Reminder> l = new ArrayList<>();
        l.add(make(c, "Drink water 💧", "Pani pee lo – ek glass abhi!", 60, 9 * 60, 21 * 60, true, true));
        l.add(make(c, "5-minute walk 🚶", "Utho aur 5 minute walk karo – weight loss ke liye.", 60, 9 * 60 + 30, 20 * 60 + 30, true, false));
        l.add(make(c, "Project time 💻", "Ab project par focus karo – 25 minute bina phone ke.", 0, 10 * 60, 10 * 60, true, false));
        l.add(make(c, "Plan tomorrow 📝", "Aaj ke tasks check karo aur kal ka plan likho.", 0, 21 * 60 + 30, 21 * 60 + 30, true, false));
        l.add(make(c, "Stretch & posture 🧘", "Seedhe baitho, shoulders aur neck stretch karo.", 120, 10 * 60, 18 * 60, false, false));
        l.add(make(c, "Eye rest 👀", "20 second ke liye 20 feet door dekho.", 45, 10 * 60, 18 * 60, false, false));
        saveReminders(c, l);
        sp(c).edit().putBoolean("seeded", true).apply();
    }

    /* ----------------------------------------------------------------- water */
    private static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }

    public static int water(Context c) {
        SharedPreferences p = sp(c);
        return today().equals(p.getString("water_day", "")) ? p.getInt("water_count", 0) : 0;
    }

    public static void setWater(Context c, int n) {
        sp(c).edit().putString("water_day", today()).putInt("water_count", Math.max(0, n)).apply();
    }

    public static int waterGoal(Context c) { return sp(c).getInt("water_goal", 8); }
    public static void setWaterGoal(Context c, int g) { sp(c).edit().putInt("water_goal", Math.max(1, g)).apply(); }

    /* ----------------------------------------------------------------- tasks */
    public static List<Task> tasks(Context c) {
        List<Task> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("tasks", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Task t = new Task();
                t.id = o.optLong("id"); t.text = o.optString("text"); t.type = o.optString("type", "task"); t.done = o.optBoolean("done");
                out.add(t);
            }
        } catch (JSONException ignored) { }
        return out;
    }

    public static void saveTasks(Context c, List<Task> list) {
        JSONArray a = new JSONArray();
        try {
            for (Task t : list) {
                JSONObject o = new JSONObject();
                o.put("id", t.id); o.put("text", t.text); o.put("type", t.type); o.put("done", t.done);
                a.put(o);
            }
        } catch (JSONException ignored) { }
        sp(c).edit().putString("tasks", a.toString()).apply();
    }

    public static void addTask(Context c, String text, String type) {
        List<Task> l = tasks(c);
        Task t = new Task();
        t.id = System.currentTimeMillis(); t.text = text; t.type = type; t.done = false;
        l.add(0, t);
        saveTasks(c, l);
    }
}
