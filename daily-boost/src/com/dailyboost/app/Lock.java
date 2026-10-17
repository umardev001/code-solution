package com.dailyboost.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;
import java.security.SecureRandom;

/** 4-digit PIN lock: salted SHA-256 hash, session tracking and auto-lock. */
public final class Lock {
    private static boolean unlocked = false;
    private static long lastActive = 0;

    private Lock() { }

    private static SharedPreferences sp(Context c) { return Store.prefs(c); }

    public static boolean hasPin(Context c) { return !sp(c).getString("pin_hash", "").isEmpty(); }

    /** PIN lock is active when a PIN exists and the user has not switched it off. */
    public static boolean enabled(Context c) { return hasPin(c) && sp(c).getBoolean("pin_enabled", true); }

    public static void setEnabled(Context c, boolean on) { sp(c).edit().putBoolean("pin_enabled", on).apply(); }

    private static String hash(String salt, String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((salt + ":" + pin).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void setPin(Context c, String pin) {
        byte[] r = new byte[12];
        new SecureRandom().nextBytes(r);
        StringBuilder salt = new StringBuilder();
        for (byte b : r) salt.append(String.format("%02x", b));
        sp(c).edit().putString("pin_salt", salt.toString()).putString("pin_hash", hash(salt.toString(), pin)).putBoolean("pin_enabled", true).apply();
    }

    public static boolean verify(Context c, String pin) {
        return hasPin(c) && hash(sp(c).getString("pin_salt", ""), pin).equals(sp(c).getString("pin_hash", ""));
    }

    public static void unlock() { unlocked = true; lastActive = System.currentTimeMillis(); }

    public static void touch() { lastActive = System.currentTimeMillis(); }

    public static boolean needsLogin(Context c) {
        if (!enabled(c)) return false;
        if (!unlocked) return true;
        int sec = Store.autoLockSec(c);
        if (sec < 0) return false;
        return System.currentTimeMillis() - lastActive > sec * 1000L;
    }

    public static void lockNow() { unlocked = false; }
}
