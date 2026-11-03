package com.fitjourney.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Profile + one record per day, kept as a single JSON document in private storage. */
public final class Store {
    public interface Backend { String read(); void write(String s); }

    public static final class Profile {
        public String name = "";
        public int heightCm = 170;
        public double startKg = 70, goalKg = 65;
        public int waterGoal = 2500, stepGoal = 8000, workoutGoal = 30, calGoal = 1800;
        public double sleepGoal = 8;
        public int remindHours = 2;         // 0 = off
        public long startTs = System.currentTimeMillis();
    }

    public static final class Day {
        public String key;
        public double weight;     // 0 = not logged
        public int steps, water, workout, calories;
        public double sleep;

        Day(String key) { this.key = key; }

        boolean empty() { return weight == 0 && steps == 0 && water == 0 && workout == 0 && calories == 0 && sleep == 0; }
    }

    private static Backend backend;
    private static Profile profile;
    private static long stepLast = -1;
    private static long lastRemind = 0;
    private static final TreeMap<String, Day> days = new TreeMap<String, Day>();
    private static boolean loaded = false;

    private Store() { }

    /* ------------------------------------------------------------------ setup */
    public static synchronized void init(Context c) {
        if (backend == null) {
            final SharedPreferences sp = c.getApplicationContext().getSharedPreferences("fit_journey", Context.MODE_PRIVATE);
            backend = new Backend() {
                @Override public String read() { return sp.getString("data", null); }
                @Override public void write(String s) { sp.edit().putString("data", s).apply(); }
            };
        }
        if (!loaded) load();
    }

    /** For tests. */
    public static synchronized void use(Backend b) { backend = b; loaded = false; profile = null; days.clear(); stepLast = -1; load(); }

    private static void load() {
        loaded = true;
        days.clear();
        profile = null;
        String s = backend.read();
        if (s == null) return;
        try {
            JSONObject r = new JSONObject(s);
            JSONObject p = r.optJSONObject("p");
            if (p != null) {
                Profile x = new Profile();
                x.name = p.optString("n"); x.heightCm = p.optInt("h", 170);
                x.startKg = p.optDouble("s", 70); x.goalKg = p.optDouble("g", 65);
                x.waterGoal = p.optInt("wg", 2500); x.stepGoal = p.optInt("sg", 8000);
                x.workoutGoal = p.optInt("og", 30); x.calGoal = p.optInt("cg", 1800);
                x.sleepGoal = p.optDouble("lg", 8); x.remindHours = p.optInt("rh", 2);
                x.startTs = p.optLong("t", System.currentTimeMillis());
                profile = x;
            }
            stepLast = r.optLong("sl", -1);
            lastRemind = r.optLong("lr", 0);
            JSONObject d = r.optJSONObject("d");
            if (d != null) {
                Iterator<String> it = d.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject o = d.getJSONObject(k);
                    Day y = new Day(k);
                    y.weight = o.optDouble("w", 0); y.steps = o.optInt("s"); y.water = o.optInt("a");
                    y.workout = o.optInt("o"); y.calories = o.optInt("c"); y.sleep = o.optDouble("l", 0);
                    days.put(k, y);
                }
            }
        } catch (Exception ignored) { }
    }

    public static synchronized void save() {
        try {
            JSONObject r = new JSONObject();
            if (profile != null) {
                Profile x = profile;
                r.put("p", new JSONObject().put("n", x.name).put("h", x.heightCm).put("s", x.startKg).put("g", x.goalKg)
                        .put("wg", x.waterGoal).put("sg", x.stepGoal).put("og", x.workoutGoal).put("cg", x.calGoal)
                        .put("lg", x.sleepGoal).put("rh", x.remindHours).put("t", x.startTs));
            }
            r.put("sl", stepLast).put("lr", lastRemind);
            JSONObject d = new JSONObject();
            for (Map.Entry<String, Day> e : days.entrySet()) {
                Day y = e.getValue();
                if (y.empty()) continue;
                d.put(e.getKey(), new JSONObject().put("w", y.weight).put("s", y.steps).put("a", y.water)
                        .put("o", y.workout).put("c", y.calories).put("l", y.sleep));
            }
            r.put("d", d);
            backend.write(r.toString());
        } catch (Exception ignored) { }
    }

    public static synchronized void wipe() { days.clear(); profile = null; stepLast = -1; lastRemind = 0; save(); }

    /* ---------------------------------------------------------------- profile */
    public static synchronized boolean hasProfile() { return profile != null; }
    public static synchronized Profile profile() { return profile != null ? profile : new Profile(); }
    public static synchronized void setProfile(Profile p) { profile = p; save(); }

    /* ------------------------------------------------------------------- days */
    /** Read-only view (a blank day is returned, not stored, when nothing was logged). */
    public static synchronized Day get(String key) {
        Day d = days.get(key);
        return d != null ? d : new Day(key);
    }

    /** Day to modify – created if needed. Call save() afterwards. */
    public static synchronized Day mut(String key) {
        Day d = days.get(key);
        if (d == null) { d = new Day(key); days.put(key, d); }
        return d;
    }

    public static synchronized long stepLast() { return stepLast; }
    public static synchronized void setStepLast(long v) { stepLast = v; }
    public static synchronized long lastRemind() { return lastRemind; }
    public static synchronized void setLastRemind(long v) { lastRemind = v; save(); }

    /** Weigh-ins, oldest first. */
    public static synchronized List<Day> weighIns() {
        List<Day> l = new ArrayList<Day>();
        for (Day d : days.values()) if (d.weight > 0) l.add(d);
        return l;
    }

    public static synchronized double currentKg() {
        double w = 0;
        for (Day d : days.values()) if (d.weight > 0) w = d.weight;
        return w > 0 ? w : profile().startKg;
    }

    public static synchronized List<Day> all() { return new ArrayList<Day>(days.values()); }

    /* ------------------------------------------------------------------ goals */
    /** How many of the three daily goals (steps, water, workout) were reached: 0..3. */
    public static int hits(Day d) {
        Profile p = profile();
        int n = 0;
        if (d.steps >= p.stepGoal) n++;
        if (d.water >= p.waterGoal) n++;
        if (d.workout >= p.workoutGoal) n++;
        return n;
    }

    /** A "good day" = at least 2 of the 3 goals. */
    public static boolean good(Day d) { return hits(d) >= 2; }

    public static synchronized int streak() {
        String k = Fit.today();
        if (!good(get(k))) k = Fit.addDays(k, -1);       // today may still be in progress
        int n = 0;
        while (good(get(k)) && n < 3650) { n++; k = Fit.addDays(k, -1); }
        return n;
    }

    public static synchronized int bestStreak() {
        int best = 0, run = 0;
        String prev = null;
        for (Day d : days.values()) {
            if (!good(d)) { run = 0; prev = null; continue; }
            run = (prev != null && Fit.addDays(prev, 1).equals(d.key)) ? run + 1 : 1;
            prev = d.key;
            best = Math.max(best, run);
        }
        return best;
    }
}
