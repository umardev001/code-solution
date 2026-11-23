package com.ironicxsoft.bolo;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Settings + conversion history (SharedPreferences). */
public final class Store {
    private Store() { }

    public static final class Item {
        public long time;
        public String src;
        public String out;
    }

    private static SharedPreferences sp(Context c) { return c.getApplicationContext().getSharedPreferences("bolo", Context.MODE_PRIVATE); }

    // ---- settings
    public static String lang(Context c) { return sp(c).getString("lang", "ur-PK"); }
    public static void setLang(Context c, String v) { sp(c).edit().putString("lang", v).apply(); }
    public static String apiKey(Context c) { return sp(c).getString("api_key", ""); }
    public static void setApiKey(Context c, String v) { sp(c).edit().putString("api_key", v == null ? "" : v.trim()).apply(); }
    public static String tone(Context c) { return sp(c).getString("tone", "Professional"); }
    public static void setTone(Context c, String v) { sp(c).edit().putString("tone", v).apply(); }
    public static boolean autoTranslate(Context c) { return sp(c).getBoolean("auto", false); }
    public static void setAutoTranslate(Context c, boolean v) { sp(c).edit().putBoolean("auto", v).apply(); }
    public static boolean voiceCommands(Context c) { return sp(c).getBoolean("cmd", true); }
    public static void setVoiceCommands(Context c, boolean v) { sp(c).edit().putBoolean("cmd", v).apply(); }
    public static boolean hasAi(Context c) { return apiKey(c).length() > 20; }

    // ---- history
    public static List<Item> history(Context c) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("history", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Item it = new Item();
                it.time = o.optLong("t"); it.src = o.optString("s"); it.out = o.optString("o");
                out.add(it);
            }
        } catch (JSONException ignored) { }
        return out;
    }

    private static void save(Context c, List<Item> l) {
        JSONArray a = new JSONArray();
        try {
            for (Item it : l) { JSONObject o = new JSONObject(); o.put("t", it.time); o.put("s", it.src); o.put("o", it.out); a.put(o); }
        } catch (JSONException ignored) { }
        sp(c).edit().putString("history", a.toString()).apply();
    }

    public static void addHistory(Context c, String src, String out) {
        List<Item> l = history(c);
        Item it = new Item();
        it.time = System.currentTimeMillis(); it.src = src; it.out = out;
        l.add(0, it);
        while (l.size() > 60) l.remove(l.size() - 1);
        save(c, l);
    }

    public static void removeHistory(Context c, long time) {
        List<Item> l = history(c);
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).time == time) l.remove(i);
        save(c, l);
    }

    public static void clearHistory(Context c) { sp(c).edit().putString("history", "[]").apply(); }

    // ---- draft (survives app restarts)
    public static String draft(Context c) { return sp(c).getString("draft", ""); }
    public static void setDraft(Context c, String s) { sp(c).edit().putString("draft", s).apply(); }
}
