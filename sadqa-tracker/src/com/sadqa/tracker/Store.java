package com.sadqa.tracker;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/** In-memory copy of the decrypted records. Exists only while the app is unlocked. */
public final class Store {
    public static final int DEFAULT_RATE = 5000;   // Rs per 1 lakh (5%)

    public static final class Entry {
        public long id;
        public long ts;        // date (noon, local time)
        public long amount;    // rupees
        public int rate;       // income only: sadqa Rs per lakh at the time it was added
        public String note = "";

        public long due() { return Calc.due(amount, rate); }
    }

    private static JSONObject root;
    public static final List<Entry> income = new ArrayList<Entry>();
    public static final List<Entry> paid = new ArrayList<Entry>();

    private Store() { }

    public static boolean isOpen() { return root != null; }

    /** Loads (and decrypts) the vault. Throws if the file exists but cannot be decrypted. */
    public static synchronized void open(Context c) throws Exception {
        if (root != null) return;
        JSONObject r = Vault.read(c);
        if (r == null) r = new JSONObject().put("v", 1).put("rate", DEFAULT_RATE);
        income.clear(); paid.clear();
        load(r.optJSONArray("income"), income);
        load(r.optJSONArray("paid"), paid);
        root = r;
    }

    public static synchronized void close() {
        root = null;
        income.clear();
        paid.clear();
    }

    /** Erases everything on this phone, including the encryption key. */
    public static synchronized void wipe(Context c) {
        close();
        Vault.destroy(c);
    }

    private static void load(JSONArray a, List<Entry> out) {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            Entry e = new Entry();
            e.id = o.optLong("i"); e.ts = o.optLong("t"); e.amount = o.optLong("a");
            e.rate = o.optInt("r", DEFAULT_RATE); e.note = o.optString("n", "");
            out.add(e);
        }
    }

    private static JSONArray dump(List<Entry> l) throws Exception {
        JSONArray a = new JSONArray();
        for (Entry e : l) a.put(new JSONObject().put("i", e.id).put("t", e.ts).put("a", e.amount).put("r", e.rate).put("n", e.note));
        return a;
    }

    public static synchronized void save(Context c) throws Exception {
        if (root == null) throw new IllegalStateException("locked");
        root.put("income", dump(income));
        root.put("paid", dump(paid));
        Vault.write(c, root);
    }

    /* ------------------------------------------------------------- settings / auth */
    public static int rate() { return root == null ? DEFAULT_RATE : root.optInt("rate", DEFAULT_RATE); }

    public static void setRate(Context c, int r) throws Exception { root.put("rate", r); save(c); }

    static JSONObject auth() { return root == null ? null : root.optJSONObject("auth"); }

    static void setAuth(Context c, JSONObject a) throws Exception { root.put("auth", a); save(c); }

    /* -------------------------------------------------------------------- mutations */
    public static long newId() {
        long m = System.currentTimeMillis();
        for (Entry e : income) m = Math.max(m, e.id + 1);
        for (Entry e : paid) m = Math.max(m, e.id + 1);
        return m;
    }

    public static Entry find(List<Entry> l, long id) {
        for (Entry e : l) if (e.id == id) return e;
        return null;
    }

    /* --------------------------------------------------------------------- numbers */
    public static int ym(long ts) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts);
        return c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH);
    }

    public static int nowYm() { return ym(System.currentTimeMillis()); }

    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July", "August",
            "September", "October", "November", "December"};

    public static String monthName(int ym) { return MONTHS[ym % 12] + " " + (ym / 12); }

    public static String shortMonth(int ym) { return MONTHS[ym % 12].substring(0, 3) + " " + (ym / 12); }

    /** {revenue, sadqaDue, sadqaPaid} for one month; ym < 0 means all time. */
    public static long[] sums(int ym) {
        long rev = 0, due = 0, pd = 0;
        for (Entry e : income) if (ym < 0 || ym(e.ts) == ym) { rev += e.amount; due += e.due(); }
        for (Entry e : paid) if (ym < 0 || ym(e.ts) == ym) pd += e.amount;
        return new long[]{rev, due, pd};
    }

    /** Months that have entries (newest first), always including the current month. */
    public static List<Integer> months() {
        TreeSet<Integer> s = new TreeSet<Integer>();
        s.add(nowYm());
        for (Entry e : income) s.add(ym(e.ts));
        for (Entry e : paid) s.add(ym(e.ts));
        List<Integer> l = new ArrayList<Integer>(s);
        Collections.reverse(l);
        return l;
    }

    public static final Comparator<Entry> NEWEST_FIRST = new Comparator<Entry>() {
        @Override public int compare(Entry a, Entry b) {
            if (a.ts != b.ts) return a.ts < b.ts ? 1 : -1;
            return a.id < b.id ? 1 : (a.id == b.id ? 0 : -1);
        }
    };
}
