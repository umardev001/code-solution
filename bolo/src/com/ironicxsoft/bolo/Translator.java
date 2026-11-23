package com.ironicxsoft.bolo;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Urdu / Roman-Urdu / mixed text -> clean English.
 * With an AI key (Claude) the result is a polished, grammar-perfect reply in the chosen tone.
 * Without a key it falls back to Google Translate, then MyMemory, plus local grammar tidy-up.
 */
public final class Translator {
    private Translator() { }

    public interface Callback {
        void done(String english, String engine);
        void fail(String message);
    }

    public static final String[] TONES = {"Professional", "Friendly", "Concise", "Formal"};
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String MODEL = "claude-sonnet-5-5";

    public static void translate(final Context ctx, final String text, final String tone, final Callback cb) {
        final String key = Store.apiKey(ctx);
        final boolean ai = Store.hasAi(ctx);
        new Thread(new Runnable() {
            @Override public void run() {
                String err = null;
                try {
                    if (ai) {
                        try {
                            final String out = claude(key, text, tone);
                            post(new Runnable() { @Override public void run() { cb.done(out, "AI"); } });
                            return;
                        } catch (ApiError e) {
                            err = e.getMessage();
                            if (e.fatal) { final String m = err; post(new Runnable() { @Override public void run() { cb.fail(m); } }); return; }
                        } catch (Exception e) { err = "AI service unreachable"; }
                    }
                    String out = null;
                    try { out = google(text); } catch (Exception ignored) { }
                    if (out == null || out.trim().isEmpty()) {
                        try { out = myMemory(text); } catch (Exception ignored) { }
                    }
                    if (out == null || out.trim().isEmpty()) {
                        final String m = err != null ? err + " – and basic translation failed too. Check your internet." : "Could not translate. Please check your internet connection.";
                        post(new Runnable() { @Override public void run() { cb.fail(m); } });
                        return;
                    }
                    final String fin = tidy(out);
                    post(new Runnable() { @Override public void run() { cb.done(fin, "Basic"); } });
                } catch (final Exception e) {
                    post(new Runnable() { @Override public void run() { cb.fail("Something went wrong. Please try again."); } });
                }
            }
        }).start();
    }

    private static void post(Runnable r) { MAIN.post(r); }

    /* --------------------------------------------------------------- Claude */
    static final class ApiError extends Exception {
        final boolean fatal;
        ApiError(String m, boolean fatal) { super(m); this.fatal = fatal; }
    }

    static String systemPrompt(String tone) {
        return "You are an expert bilingual editor helping a busy professional reply to clients. "
                + "The user dictated a message by voice in Urdu (Urdu script or Roman Urdu), English, or a mix of them. "
                + "Rewrite it as natural, fluent, grammatically perfect English that can be sent as-is. "
                + "Keep the meaning, names, numbers, dates, prices and every commitment exactly as said; never add new facts, promises or details. "
                + "Fix speech-recognition mistakes and remove filler words and repetition. "
                + "Use correct punctuation, capitalisation and natural paragraphs. "
                + "Tone: " + tone + ". "
                + "If the text is already English, just correct and polish it. "
                + "Output ONLY the final message text: no quotes, no preface, no explanation, no notes.";
    }

    static String claude(String key, String text, String tone) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", MODEL);
        body.put("max_tokens", 1500);
        body.put("system", systemPrompt(tone));
        JSONArray msgs = new JSONArray();
        msgs.put(new JSONObject().put("role", "user").put("content", text));
        body.put("messages", msgs);

        HttpURLConnection c = (HttpURLConnection) new URL("https://api.anthropic.com/v1/messages").openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(40000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("content-type", "application/json");
        c.setRequestProperty("x-api-key", key);
        c.setRequestProperty("anthropic-version", "2023-06-01");
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();
        int code = c.getResponseCode();
        String resp = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code == 401 || code == 403) throw new ApiError("Your AI key was rejected. Check it in Settings.", true);
        if (code == 429) throw new ApiError("AI rate limit reached", false);
        if (code >= 400) throw new ApiError("AI error " + code, false);
        return parseClaude(resp);
    }

    static String parseClaude(String resp) throws Exception {
        JSONArray content = new JSONObject(resp).getJSONArray("content");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            JSONObject b = content.getJSONObject(i);
            if ("text".equals(b.optString("type"))) sb.append(b.optString("text"));
        }
        String out = sb.toString().trim();
        if (out.length() > 1 && out.startsWith("\"") && out.endsWith("\"")) out = out.substring(1, out.length() - 1).trim();
        if (out.isEmpty()) throw new ApiError("Empty AI answer", false);
        return out;
    }

    /* ------------------------------------------------------ free fallbacks */
    static String google(String text) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=en&dt=t").openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
        OutputStream os = c.getOutputStream();
        os.write(("q=" + URLEncoder.encode(text, "UTF-8")).getBytes("UTF-8"));
        os.close();
        if (c.getResponseCode() != 200) throw new Exception("google " + c.getResponseCode());
        return parseGoogle(read(c.getInputStream()));
    }

    static String parseGoogle(String resp) throws Exception {
        JSONArray root = new JSONArray(resp);
        JSONArray parts = root.getJSONArray(0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONArray p = parts.optJSONArray(i);
            if (p != null && !p.isNull(0)) sb.append(p.getString(0));
        }
        return sb.toString();
    }

    static String myMemory(String text) throws Exception {
        String t = text.length() > 450 ? text.substring(0, 450) : text;
        HttpURLConnection c = (HttpURLConnection) new URL("https://api.mymemory.translated.net/get?langpair=ur%7Cen&q=" + URLEncoder.encode(t, "UTF-8")).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        if (c.getResponseCode() != 200) throw new Exception("mymemory");
        return new JSONObject(read(c.getInputStream())).getJSONObject("responseData").getString("translatedText");
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toString("UTF-8");
    }

    /* ---------------------------------------------- local grammar tidy-up */
    /** Spacing, capital letters, "i" -> "I" and final punctuation for plain machine translation. */
    public static String tidy(String s) {
        if (s == null) return "";
        s = s.replace("&#39;", "'").replace("&quot;", "\"").replace("&amp;", "&");
        s = s.replaceAll("[ \\t]+", " ").replaceAll(" +([,.!?;:])", "$1").replaceAll("([,.!?;:])(?=[A-Za-z])", "$1 ").trim();
        s = s.replaceAll("\\bi\\b", "I").replaceAll("\\bi'(m|ll|ve|d)\\b", "I'$1");
        StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (cap && Character.isLetter(ch)) { sb.append(Character.toUpperCase(ch)); cap = false; }
            else sb.append(ch);
            if (ch == '.' || ch == '!' || ch == '?' || ch == '\n') cap = true;
        }
        String out = sb.toString();
        if (!out.isEmpty() && "0123456789.!?\"')".indexOf(out.charAt(out.length() - 1)) < 0) out += ".";
        return out;
    }
}
