package com.fitjourney.app;

import java.util.Calendar;
import java.util.Locale;

/** Pure helpers (no Android dependencies): health maths, formatting, date keys. */
public final class Fit {
    private Fit() { }

    /* ------------------------------------------------------------------ health */
    public static double bmi(double kg, int cm) {
        double m = cm / 100.0;
        return m <= 0 ? 0 : kg / (m * m);
    }

    public static String bmiLabel(double b) {
        if (b <= 0) return "–";
        if (b < 18.5) return "Underweight";
        if (b < 25) return "Healthy";
        if (b < 30) return "Overweight";
        return "Obese";
    }

    /** Walking distance from steps, using stride = 0.415 x height. */
    public static double km(int steps, int heightCm) { return steps * heightCm * 0.415 / 100000.0; }

    /** Rough energy burnt by walking. */
    public static int kcalWalk(int steps, double kg) { return (int) Math.round(steps * kg * 0.0005); }

    /** 0..1 progress from start weight to goal weight (works for loss and gain). */
    public static double progress(double start, double cur, double goal) {
        if (Math.abs(goal - start) < 0.0001) return 1;
        double p = (cur - start) / (goal - start);
        return Math.max(0, Math.min(1, p));
    }

    /** Least-squares slope (y per x unit); NaN when fewer than 2 points or x spread < 2. */
    public static double slope(double[] x, double[] y) {
        int n = x.length;
        if (n < 2) return Double.NaN;
        double mx = 0, my = 0;
        for (int i = 0; i < n; i++) { mx += x[i]; my += y[i]; }
        mx /= n; my /= n;
        double num = 0, den = 0, lo = x[0], hi = x[0];
        for (int i = 0; i < n; i++) {
            num += (x[i] - mx) * (y[i] - my);
            den += (x[i] - mx) * (x[i] - mx);
            lo = Math.min(lo, x[i]); hi = Math.max(hi, x[i]);
        }
        if (den == 0 || hi - lo < 2) return Double.NaN;
        return num / den;
    }

    /** Days until goal at the current trend, or -1 when not heading to the goal. */
    public static int etaDays(double cur, double goal, double slopePerDay) {
        if (Double.isNaN(slopePerDay)) return -1;
        double need = goal - cur;
        if (Math.abs(need) < 0.05) return 0;
        if (need * slopePerDay <= 0) return -1;
        double d = Math.ceil(need / slopePerDay);
        return d > 3650 ? -1 : (int) d;
    }

    /* -------------------------------------------------------------- formatting */
    public static String kg(double v) { return String.format(Locale.ENGLISH, "%.1f", v); }

    public static String num(long v) {
        String s = Long.toString(Math.abs(v));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) sb.append(',');
            sb.append(s.charAt(i));
        }
        return (v < 0 ? "-" : "") + sb;
    }

    /** 750 -> "750 ml", 2500 -> "2.5 L", 1250 -> "1.25 L". */
    public static String water(int ml) {
        if (ml < 1000) return ml + " ml";
        String s = String.format(Locale.ENGLISH, "%.2f", ml / 1000.0).replaceAll("0+$", "");
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s + " L";
    }

    /** Parses "72", "72.5", "72,5"; returns -1 on junk. */
    public static double parse(CharSequence t) {
        if (t == null) return -1;
        String s = t.toString().trim().replace(',', '.');
        if (s.isEmpty() || s.length() > 9) return -1;
        try {
            double d = Double.parseDouble(s);
            return (Double.isNaN(d) || Double.isInfinite(d) || d < 0) ? -1 : d;
        } catch (NumberFormatException e) { return -1; }
    }

    /* ------------------------------------------------------------------- dates */
    public static String key(Calendar c) {
        return String.format(Locale.ENGLISH, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    public static String key(long ts) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts);
        return key(c);
    }

    public static String today() { return key(System.currentTimeMillis()); }

    /** Noon of that day in millis (safe against DST). */
    public static long ts(String key) {
        Calendar c = Calendar.getInstance();
        c.set(Integer.parseInt(key.substring(0, 4)), Integer.parseInt(key.substring(5, 7)) - 1, Integer.parseInt(key.substring(8, 10)), 12, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public static String addDays(String key, int n) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts(key));
        c.add(Calendar.DAY_OF_MONTH, n);
        return key(c);
    }

    /** Whole days since 1970 for a key (for charts / regression). */
    public static long dayNo(String key) { return Math.round(ts(key) / 86400000.0); }

    private static final String[] MON = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    private static final String[] DOW = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    /** "3 Nov". */
    public static String shortDate(String key) { return Integer.parseInt(key.substring(8, 10)) + " " + MON[Integer.parseInt(key.substring(5, 7)) - 1]; }

    /** "3 Nov 2026". */
    public static String longDate(String key) { return shortDate(key) + " " + key.substring(0, 4); }

    public static String dow(String key) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts(key));
        return DOW[c.get(Calendar.DAY_OF_WEEK) - 1];
    }

    /** 0 = Monday ... 6 = Sunday. */
    public static int mondayIndex(String key) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts(key));
        return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
    }
}
