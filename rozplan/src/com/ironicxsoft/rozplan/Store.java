package com.ironicxsoft.rozplan;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Everything is stored on the phone (SharedPreferences, small JSON blobs). */
public final class Store {
    private Store() { }

    public static final int START_KG = 105;

    private static SharedPreferences sp(Context c) { return c.getApplicationContext().getSharedPreferences("rozplan", Context.MODE_PRIVATE); }
    public static SharedPreferences prefs(Context c) { return sp(c); }

    public static String key(Calendar c) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
    }
    public static String today() { return key(Calendar.getInstance()); }

    /* ------------------------------------------------------------- settings */
    public static int wakeMin(Context c) { return sp(c).getInt("wake", 7 * 60); }
    public static void setWakeMin(Context c, int m) { sp(c).edit().putInt("wake", m).apply(); }

    /** Workout weekdays, Calendar.DAY_OF_WEEK-1 style (0 = Sunday). Default Mon, Wed, Fri. */
    public static boolean[] workoutDays(Context c) {
        boolean[] d = new boolean[7];
        String s = sp(c).getString("days", "1,3,5");
        for (String p : s.split(",")) { try { d[Integer.parseInt(p.trim())] = true; } catch (Exception ignored) { } }
        return d;
    }
    public static void setWorkoutDays(Context c, boolean[] d) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) if (d[i]) { if (sb.length() > 0) sb.append(','); sb.append(i); }
        sp(c).edit().putString("days", sb.toString().isEmpty() ? "-" : sb.toString()).apply();
    }

    /* ------------------------------------------------------------- daily data */
    public static final class Day {
        public final java.util.Set<String> done = new java.util.HashSet<>();
        public final java.util.Set<String> habits = new java.util.HashSet<>();
        public int water = 0;
        public final java.util.Map<String, Integer> meals = new java.util.HashMap<>();
        public int pct = 0;
    }

    public static Day day(Context c, String key) {
        Day d = new Day();
        try {
            JSONObject o = new JSONObject(sp(c).getString("d-" + key, "{}"));
            JSONArray a = o.optJSONArray("done");
            if (a != null) for (int i = 0; i < a.length(); i++) d.done.add(a.getString(i));
            a = o.optJSONArray("hab");
            if (a != null) for (int i = 0; i < a.length(); i++) d.habits.add(a.getString(i));
            d.water = o.optInt("water");
            d.pct = o.optInt("pct");
            JSONObject m = o.optJSONObject("meals");
            if (m != null) { java.util.Iterator<String> it = m.keys(); while (it.hasNext()) { String k = it.next(); d.meals.put(k, m.getInt(k)); } }
        } catch (JSONException ignored) { }
        return d;
    }

    public static void saveDay(Context c, String key, Day d) {
        try {
            JSONObject o = new JSONObject();
            o.put("done", new JSONArray(new ArrayList<>(d.done)));
            o.put("hab", new JSONArray(new ArrayList<>(d.habits)));
            o.put("water", d.water);
            o.put("pct", d.pct);
            JSONObject m = new JSONObject();
            for (java.util.Map.Entry<String, Integer> e : d.meals.entrySet()) m.put(e.getKey(), e.getValue());
            o.put("meals", m);
            sp(c).edit().putString("d-" + key, o.toString()).apply();
        } catch (JSONException ignored) { }
    }

    /* ------------------------------------------------------------- weight log */
    public static final class Weight { public String date; public double kg; }

    public static List<Weight> weights(Context c) {
        List<Weight> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("weights", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Weight w = new Weight(); w.date = o.getString("d"); w.kg = o.getDouble("kg"); l.add(w);
            }
        } catch (JSONException ignored) { }
        Collections.sort(l, new Comparator<Weight>() { @Override public int compare(Weight a, Weight b) { return a.date.compareTo(b.date); } });
        return l;
    }

    private static void saveWeights(Context c, List<Weight> l) {
        JSONArray a = new JSONArray();
        try { for (Weight w : l) a.put(new JSONObject().put("d", w.date).put("kg", w.kg)); } catch (JSONException ignored) { }
        sp(c).edit().putString("weights", a.toString()).apply();
    }

    public static void setWeightToday(Context c, double kg) {
        List<Weight> l = weights(c);
        String t = today();
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).date.equals(t)) l.remove(i);
        Weight w = new Weight(); w.date = t; w.kg = kg; l.add(w);
        saveWeights(c, l);
    }

    public static void deleteWeight(Context c, String date) {
        List<Weight> l = weights(c);
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).date.equals(date)) l.remove(i);
        saveWeights(c, l);
    }

    public static Date parse(String key) {
        try { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(key); } catch (Exception e) { return new Date(); }
    }

    public static int autoLockSec(Context c) { return sp(c).getInt("auto_lock", 60); }
    public static void setAutoLockSec(Context c, int s) { sp(c).edit().putInt("auto_lock", s).apply(); }

    /** Wipe everything (forgot-PIN reset). */
    public static void clearAll(Context c) { sp(c).edit().clear().apply(); }
}
