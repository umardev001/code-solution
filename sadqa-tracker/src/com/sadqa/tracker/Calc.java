package com.sadqa.tracker;

/** Pure money maths (no Android dependencies, unit-testable). */
public final class Calc {
    /** Rupees of sadqa per this many rupees of revenue (1 lakh). */
    public static final long PER = 100000L;
    public static final long MAX_AMOUNT = 999999999999L; // keeps amount*rate inside a long

    private Calc() { }

    /** Sadqa due on a revenue amount, rounded to the nearest rupee. */
    public static long due(long amount, long ratePerLakh) {
        return (amount * ratePerLakh + PER / 2) / PER;
    }

    /** 1234567 -> "Rs 12,34,567" (Pakistani / Indian lakh grouping). */
    public static String money(long v) {
        String s = Long.toString(Math.abs(v));
        if (s.length() > 3) {
            String last3 = s.substring(s.length() - 3);
            String rest = s.substring(0, s.length() - 3);
            StringBuilder sb = new StringBuilder();
            int n = rest.length();
            for (int i = 0; i < n; i++) {
                sb.append(rest.charAt(i));
                int left = n - i - 1;
                if (left > 0 && left % 2 == 0) sb.append(',');
            }
            s = sb.append(',').append(last3).toString();
        }
        return (v < 0 ? "-" : "") + "Rs " + s;
    }

    /** Digits-only parse; returns -1 when empty / invalid / too large. */
    public static long parseAmount(CharSequence t) {
        if (t == null) return -1;
        String s = t.toString().replace(",", "").trim();
        if (s.isEmpty() || s.length() > 12) return -1;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) < '0' || s.charAt(i) > '9') return -1;
        long v = Long.parseLong(s);
        return v > MAX_AMOUNT ? -1 : v;
    }

    /** True for PINs that are trivially guessable (111111, 123456, 654321, 121212...). */
    public static boolean weakPin(String p) {
        if (p.length() < 4) return true;
        boolean same = true, up = true, down = true, pair = p.length() % 2 == 0;
        for (int i = 1; i < p.length(); i++) {
            int a = p.charAt(i - 1), b = p.charAt(i);
            if (a != b) same = false;
            if (b != a + 1) up = false;
            if (b != a - 1) down = false;
        }
        for (int i = 2; i < p.length(); i++) if (p.charAt(i) != p.charAt(i - 2)) pair = false;
        return same || up || down || pair;
    }

    /** Lock-out after repeated wrong PINs: 5 free tries, then 30s doubling, capped at 1 hour. */
    public static long lockoutMs(int fails) {
        if (fails < 5) return 0;
        long ms = 30000L << Math.min(fails - 5, 7);
        return Math.min(ms, 3600000L);
    }
}
