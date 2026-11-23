package com.ironicxsoft.bolo;

import java.util.Locale;

/**
 * Detects a spoken command such as "isay English mein translate kar do" at the end of an
 * utterance (Urdu script, Roman Urdu or English) and separates it from the message itself.
 */
public final class Cmd {
    public final String text;        // the message without the command
    public final boolean translate;  // true when a translate command was spoken

    private Cmd(String text, boolean translate) { this.text = text; this.translate = translate; }

    private static final String[] ENGLISH = {"english", "inglish", "angrezi", "angrazi", "انگلش", "انگریزی", "انگلیش", "اینگلش", "انگرېزی"};
    private static final String[] ACTION = {"translate", "convert", "translation", "kar do", "kardo", "kar dein", "karo", "kijiye", "ترجمہ", "ترجمه", "ٹرانسلیٹ", "ترانسلیٹ", "کنورٹ", "کر دو", "کردو", "کریں", "کیجیے", "کر دیں", "کردیں"};
    private static final String[] FILLER = {"اسے", "اس", "کو", "اب", "اور", "پلیز", "isay", "isey", "is", "ko", "ab", "aur", "please", "pls", "plz", "ok", "okay", "اچھا", "تو", "this", "it"};

    private static boolean has(String s, String[] keys) {
        String low = s.toLowerCase(Locale.ROOT);
        for (String k : keys) if (low.contains(k)) return true;
        return false;
    }

    private static boolean isFiller(String w) {
        String low = w.toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}۔،]", "");
        for (String f : FILLER) if (low.equals(f)) return true;
        return false;
    }

    public static Cmd parse(String input) {
        if (input == null) return new Cmd("", false);
        String s = input.trim();
        if (s.isEmpty()) return new Cmd("", false);
        String[] words = s.split("\\s+");
        int max = Math.min(words.length, 10);
        for (int n = 2; n <= max; n++) {
            StringBuilder tail = new StringBuilder();
            for (int i = words.length - n; i < words.length; i++) tail.append(words[i]).append(' ');
            String t = tail.toString();
            if (has(t, ENGLISH) && has(t, ACTION)) {
                int end = words.length - n;
                while (end > 0 && isFiller(words[end - 1])) end--;
                StringBuilder rest = new StringBuilder();
                for (int i = 0; i < end; i++) { if (i > 0) rest.append(' '); rest.append(words[i]); }
                return new Cmd(rest.toString().trim(), true);
            }
        }
        return new Cmd(s, false);
    }
}
