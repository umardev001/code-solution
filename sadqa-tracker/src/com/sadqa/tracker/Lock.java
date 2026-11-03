package com.sadqa.tracker;

import android.content.Context;

import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * 6-digit PIN. Stored only as a salted PBKDF2 hash INSIDE the encrypted vault (so it cannot be
 * stripped from outside), with escalating lock-outs after wrong attempts and an auto-lock.
 */
public final class Lock {
    public static final int PIN_LEN = 6;
    private static final int ITER = 120000;
    private static final long GRACE_MS = 20000;   // how long the app may sit in the background

    private static boolean unlocked = false;
    private static long leftAt = 0;

    private Lock() { }

    public static boolean hasPin() { return Store.auth() != null; }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static byte[] unhex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return b;
    }

    private static byte[] derive(String pin, byte[] salt) throws Exception {
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        return f.generateSecret(new PBEKeySpec(pin.toCharArray(), salt, ITER, 256)).getEncoded();
    }

    public static void setPin(Context c, String pin) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        JSONObject a = new JSONObject().put("salt", hex(salt)).put("hash", hex(derive(pin, salt))).put("fails", 0).put("until", 0L);
        Store.setAuth(c, a);
    }

    /** Milliseconds the user must still wait before another try (0 = may try now). */
    public static long waitMs() {
        JSONObject a = Store.auth();
        if (a == null) return 0;
        long left = a.optLong("until", 0) - System.currentTimeMillis();
        return left > 0 ? left : 0;
    }

    /** Returns true if the PIN is right. Wrong tries are counted and persisted. */
    public static boolean verify(Context c, String pin) throws Exception {
        JSONObject a = Store.auth();
        if (a == null || waitMs() > 0) return false;
        byte[] want = unhex(a.getString("hash"));
        boolean ok = MessageDigest.isEqual(want, derive(pin, unhex(a.getString("salt"))));
        int fails = ok ? 0 : a.optInt("fails", 0) + 1;
        long until = ok ? 0 : (Calc.lockoutMs(fails) == 0 ? 0 : System.currentTimeMillis() + Calc.lockoutMs(fails));
        if (!ok || a.optInt("fails", 0) != 0 || a.optLong("until", 0) != 0) {
            a.put("fails", fails).put("until", until);
            Store.setAuth(c, a);
        }
        return ok;
    }

    public static int triesLeftBeforeLock() {
        JSONObject a = Store.auth();
        int f = a == null ? 0 : a.optInt("fails", 0);
        return Math.max(0, 5 - f);
    }

    /* ------------------------------------------------------------------- session */
    public static void unlock() { unlocked = true; leftAt = 0; }

    public static void lockNow() { unlocked = false; Store.close(); }

    public static void onLeave() { if (unlocked) leftAt = System.currentTimeMillis(); }

    /** True while the session is valid; locks (and wipes memory) when the grace period has passed. */
    public static boolean isUnlocked() {
        if (!unlocked || !Store.isOpen()) return false;
        if (leftAt != 0 && System.currentTimeMillis() - leftAt > GRACE_MS) { lockNow(); return false; }
        leftAt = 0;
        return true;
    }
}
