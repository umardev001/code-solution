package com.ironicxsoft.bolo;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static com.ironicxsoft.bolo.UI.*;

public class MainActivity extends Activity implements Voice.Listener {
    private static final int REQ_MIC = 21;

    private FrameLayout root;
    private int screen = 0;           // 0 home, 1 history, 2 settings

    // home state
    private EditText source;
    private TextView live, counter, resultText, resultMeta, hintAi, micLabel;
    private LinearLayout resultCard, toneRow, dock;
    private Widgets.MicView mic;
    private Widgets.WaveView wave;
    private Voice voice;
    private boolean translating = false;
    private String lastOut = "";
    private TextView transBtn;
    private ScrollView homeScroll;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(BG);
            getWindow().setNavigationBarColor(Color.WHITE);
        }
        if (Build.VERSION.SDK_INT >= 23) {
            int f = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) f |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(f);
        }
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        voice = new Voice(this, this);
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);
        showHome(Store.draft(this));
    }

    @Override protected void onPause() {
        if (source != null) Store.setDraft(this, source.getText().toString());
        if (voice != null && voice.isActive()) voice.stop();
        super.onPause();
    }

    @Override protected void onDestroy() { if (voice != null) voice.destroy(); super.onDestroy(); }

    @Override public void onBackPressed() {
        if (screen != 0) showHome(source != null ? source.getText().toString() : Store.draft(this)); else super.onBackPressed();
    }

    private int d(int v) { return dp(this, v); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private TextView roundBtn(String glyph, int bg, int fg, int size, int sp) {
        TextView v = label(this, glyph, sp, fg, true);
        v.setGravity(Gravity.CENTER);
        v.setIncludeFontPadding(false);
        v.setBackground(round(this, bg, size / 2));
        v.setLayoutParams(new LinearLayout.LayoutParams(d(size), d(size)));
        v.setClickable(true);
        return v;
    }

    private TextView chip(String text, boolean on) {
        TextView c = label(this, text, 14, on ? Color.WHITE : INK, true);
        c.setSingleLine(true);
        c.setEllipsize(TextUtils.TruncateAt.END);
        c.setGravity(Gravity.CENTER);
        c.setMinHeight(d(40));
        c.setPadding(d(10), d(8), d(10), d(8));
        c.setBackground(round(this, on ? GREEN : FIELD, 20));
        c.setClickable(true);
        return c;
    }

    /* ================================================================ HOME */
    private void showHome(String draft) {
        screen = 0;
        root.removeAllViews();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        // header
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(d(22), d(16), d(16), d(6));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        nameRow.addView(label(this, "Bolo", 30, INK, true));
        hintAi = label(this, Store.hasAi(this) ? "AI ✨" : "Basic", 11, Store.hasAi(this) ? GREEN_DARK : MUTED, true);
        hintAi.setBackground(round(this, Store.hasAi(this) ? alpha(GREEN, 38) : FIELD, 10));
        hintAi.setPadding(d(9), d(3), d(9), d(3));
        nameRow.addView(hintAi, lp(this, -2, -2, 10, 4, 0, 0));
        titles.addView(nameRow);
        titles.addView(label(this, "Speak Urdu. Send perfect English.", 14, MUTED, false));
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView hist = roundBtn("🕘", Color.WHITE, INK, 44, 18);
        hist.setElevation(d(2));
        hist.setOnClickListener(new Tap() { @Override public void tap() { stopVoice(); showHistory(); } });
        TextView set = roundBtn("⚙️", Color.WHITE, INK, 44, 18);
        set.setElevation(d(2));
        set.setOnClickListener(new Tap() { @Override public void tap() { stopVoice(); showSettings(); } });
        head.addView(hist);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(d(44), d(44));
        slp.setMargins(d(10), 0, 0, 0);
        head.addView(set, slp);
        page.addView(head, new LinearLayout.LayoutParams(-1, -2));

        // scroll area
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setFillViewport(true);
        homeScroll = sv;
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, d(8));
        sv.addView(col);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        // language chips
        LinearLayout langs = new LinearLayout(this);
        langs.setPadding(d(18), d(6), d(18), d(2));
        final TextView ur = chip("🇵🇰  Urdu", "ur-PK".equals(Store.lang(this)));
        final TextView en = chip("🇬🇧  English", "en-US".equals(Store.lang(this)));
        ur.setOnClickListener(new Tap() { @Override public void tap() { pickLang("ur-PK", ur, en); } });
        en.setOnClickListener(new Tap() { @Override public void tap() { pickLang("en-US", ur, en); } });
        langs.addView(ur, lp(this, 0, -2, 0, 0, 4, 0)); ((LinearLayout.LayoutParams) ur.getLayoutParams()).weight = 1f;
        langs.addView(en, lp(this, 0, -2, 4, 0, 0, 0)); ((LinearLayout.LayoutParams) en.getLayoutParams()).weight = 1f;
        TextView spk = label(this, "I’m speaking", 12, MUTED, true);
        spk.setPadding(d(24), d(8), 0, 0);
        col.addView(spk);
        col.addView(langs);

        // source card
        LinearLayout card = card(this);
        card.addView(caption(this, "YOUR WORDS"), lp(this, -2, -2, 0, 0, 0, 0));
        source = new EditText(this);
        source.setBackground(null);
        source.setHint("Tap the mic and speak, or type here…");
        source.setHintTextColor(Color.parseColor("#9AAABF"));
        source.setTextColor(INK);
        source.setTextSize(19);
        source.setLineSpacing(0, 1.2f);
        source.setMinLines(5);
        source.setGravity(Gravity.TOP);
        source.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        source.setPadding(0, d(4), 0, d(4));
        if (Build.VERSION.SDK_INT >= 17) source.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        source.setText(draft == null ? "" : draft);
        card.addView(source, new LinearLayout.LayoutParams(-1, -2));
        live = label(this, "", 15, MUTED, false);
        live.setPadding(0, 0, 0, d(4));
        if (Build.VERSION.SDK_INT >= 17) live.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        card.addView(live);
        LinearLayout foot = new LinearLayout(this);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        counter = label(this, "", 12, MUTED, false);
        foot.addView(counter, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView paste = textButton(this, "Paste", GREEN_DARK);
        paste.setOnClickListener(new Tap() { @Override public void tap() { pasteText(); } });
        foot.addView(paste);
        card.addView(foot);
        col.addView(card);
        source.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) { updateCounter(); }
        });

        // tone row (AI only)
        toneRow = new LinearLayout(this);
        toneRow.setOrientation(LinearLayout.VERTICAL);
        toneRow.setPadding(d(18), d(4), d(18), 0);
        buildToneRow();
        col.addView(toneRow);

        // result card
        resultCard = card(this);
        resultCard.setVisibility(View.GONE);
        resultMeta = caption(this, "ENGLISH");
        resultCard.addView(resultMeta);
        resultText = label(this, "", 19, INK, false);
        resultText.setLineSpacing(0, 1.25f);
        resultText.setTextIsSelectable(true);
        resultCard.addView(resultText);
        LinearLayout acts = new LinearLayout(this);
        acts.setPadding(0, d(14), 0, 0);
        TextView copy = button(this, "Copy", GREEN, Color.WHITE);
        TextView wa = button(this, "WhatsApp", Color.parseColor("#25D366"), Color.WHITE);
        TextView share = button(this, "Share", FIELD, INK);
        copy.setOnClickListener(new Tap() { @Override public void tap() { copy(lastOut, "Copied"); } });
        wa.setOnClickListener(new Tap() { @Override public void tap() { shareText(true); } });
        share.setOnClickListener(new Tap() { @Override public void tap() { shareText(false); } });
        acts.addView(copy, lp(this, 0, -2, 0, 0, 4, 0)); ((LinearLayout.LayoutParams) copy.getLayoutParams()).weight = 1f;
        acts.addView(wa, lp(this, 0, -2, 4, 0, 4, 0)); ((LinearLayout.LayoutParams) wa.getLayoutParams()).weight = 1.4f;
        acts.addView(share, lp(this, 0, -2, 4, 0, 0, 0)); ((LinearLayout.LayoutParams) share.getLayoutParams()).weight = 1f;
        resultCard.addView(acts);
        col.addView(resultCard);

        // dock (mic + actions)
        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.VERTICAL);
        dock.setGravity(Gravity.CENTER_HORIZONTAL);
        dock.setBackground(topRound(Color.WHITE, 28));
        dock.setElevation(d(14));
        dock.setPadding(d(20), d(10), d(20), d(10));
        wave = new Widgets.WaveView(this);
        dock.addView(wave, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(sideAction("🗑️", "Clear", FIELD, INK, new Tap() { @Override public void tap() { clearAll(); } }), new LinearLayout.LayoutParams(0, -2, 1f));
        mic = new Widgets.MicView(this);
        mic.setClickable(true);
        mic.setOnClickListener(new Tap() { @Override public void tap() { toggleMic(); } });
        row.addView(mic, new LinearLayout.LayoutParams(d(120), d(120)));
        LinearLayout tcol = sideAction("✨", "Translate", GREEN, Color.WHITE, new Tap() { @Override public void tap() { translateNow(); } });
        transBtn = (TextView) tcol.getChildAt(0);
        row.addView(tcol, new LinearLayout.LayoutParams(0, -2, 1f));
        dock.addView(row);
        micLabel = label(this, "Tap the mic to speak", 13, MUTED, true);
        micLabel.setGravity(Gravity.CENTER);
        dock.addView(micLabel, new LinearLayout.LayoutParams(-1, -2));
        page.addView(dock, new LinearLayout.LayoutParams(-1, -2));

        updateCounter();
        if (!lastOut.isEmpty()) showResult(lastOut, null);
    }

    private android.graphics.drawable.GradientDrawable topRound(int color, int r) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(color);
        float rr = d(r);
        g.setCornerRadii(new float[]{rr, rr, rr, rr, 0, 0, 0, 0});
        return g;
    }

    /** Round button with a caption underneath; child 0 is the button. */
    private LinearLayout sideAction(String glyph, String caption, int bg, int fg, Tap tap) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView b = roundBtn(glyph, bg, fg, 56, 22);
        b.setOnClickListener(tap);
        l.addView(b);
        TextView t = label(this, caption, 12, MUTED, true);
        t.setGravity(Gravity.CENTER);
        l.addView(t, lp(this, -2, -2, 0, 4, 0, 0));
        return l;
    }

    private void buildToneRow() {
        toneRow.removeAllViews();
        if (!Store.hasAi(this)) {
            TextView t = label(this, "✨ Add your AI key for perfect grammar & tones  ›", 12, GREEN_DARK, true);
            t.setPadding(d(6), d(10), d(6), d(2));
            t.setClickable(true);
            t.setOnClickListener(new Tap() { @Override public void tap() { stopVoice(); showSettings(); } });
            toneRow.addView(t);
            return;
        }
        TextView cap = label(this, "Reply tone", 12, MUTED, true);
        cap.setPadding(d(6), d(8), 0, d(4));
        toneRow.addView(cap);
        LinearLayout r = new LinearLayout(this);
        final TextView[] cs = new TextView[Translator.TONES.length];
        for (int i = 0; i < cs.length; i++) {
            final String tone = Translator.TONES[i];
            cs[i] = chip(tone, tone.equals(Store.tone(this)));
            cs[i].setTextSize(12.5f);
            cs[i].setOnClickListener(new Tap() { @Override public void tap() {
                Store.setTone(MainActivity.this, tone);
                for (int k = 0; k < cs.length; k++) {
                    boolean on = Translator.TONES[k].equals(tone);
                    cs[k].setTextColor(on ? Color.WHITE : INK);
                    cs[k].setBackground(round(MainActivity.this, on ? GREEN : FIELD, 20));
                }
            } });
            r.addView(cs[i], lp(this, 0, -2, i == 0 ? 0 : 3, 0, i == cs.length - 1 ? 0 : 3, 0));
            ((LinearLayout.LayoutParams) cs[i].getLayoutParams()).weight = 1f;
        }
        toneRow.addView(r);
    }

    private void pickLang(String code, TextView ur, TextView en) {
        Store.setLang(this, code);
        boolean isUr = "ur-PK".equals(code);
        ur.setTextColor(isUr ? Color.WHITE : INK); ur.setBackground(round(this, isUr ? GREEN : FIELD, 20));
        en.setTextColor(isUr ? INK : Color.WHITE); en.setBackground(round(this, isUr ? FIELD : GREEN, 20));
        if (voice.isActive()) { voice.stop(); }
    }

    private void updateCounter() {
        if (counter == null || source == null) return;
        String t = source.getText().toString().trim();
        int words = t.isEmpty() ? 0 : t.split("\\s+").length;
        counter.setText(words == 0 ? "" : words + (words == 1 ? " word" : " words"));
        if (transBtn != null) transBtn.setAlpha(words == 0 ? 0.45f : 1f);
    }

    /* ------------------------------------------------------------ voice */
    private void toggleMic() {
        if (voice.isActive()) { stopVoice(); if (Store.autoTranslate(this)) translateNow(); return; }
        if (!Voice.available(this)) { toast("Speech recognition is not available on this phone (install the Google app)."); return; }
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        voice.start(Store.lang(this));
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == REQ_MIC) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) voice.start(Store.lang(this));
            else toast("Microphone permission is needed to type with your voice.");
        }
    }

    private void stopVoice() { if (voice != null && voice.isActive()) voice.stop(); }

    @Override public void onPartial(String text) { if (live != null) live.setText(text); }

    @Override public void onFinal(String text) {
        if (source == null) return;
        String add = text;
        boolean cmd = false;
        if (Store.voiceCommands(this)) {
            Cmd c = Cmd.parse(text);
            add = c.text;
            cmd = c.translate;
        }
        if (!add.isEmpty()) {
            String cur = source.getText().toString();
            String sep = cur.isEmpty() || cur.endsWith(" ") || cur.endsWith("\n") ? "" : " ";
            source.setText(cur + sep + add);
            source.setSelection(source.getText().length());
        }
        if (cmd) {
            voice.stop();
            translateNow();
        }
    }

    @Override public void onLevel(float level) { if (mic != null) mic.setLevel(level); if (wave != null) wave.setLevel(level); }

    @Override public void onState(boolean listening) {
        if (mic == null) return;
        mic.setListening(listening);
        wave.setOn(listening);
        micLabel.setText(listening ? "Listening…  tap to stop" : "Tap the mic to speak");
        micLabel.setTextColor(listening ? RED : MUTED);
        if (!listening && live != null) live.setText("");
    }

    @Override public void onError(String message) { toast(message); }

    /* ------------------------------------------------------- translate */
    private void translateNow() {
        if (source == null) return;
        if (translating) return;
        final String text = source.getText().toString().trim();
        if (text.isEmpty()) { toast("Say or type something first"); return; }
        translating = true;
        resultCard.setVisibility(View.VISIBLE);
        resultMeta.setText("ENGLISH  •  working…");
        resultText.setText("Translating…");
        resultText.setTextColor(MUTED);
        final String tone = Store.tone(this);
        Translator.translate(this, text, tone, new Translator.Callback() {
            @Override public void done(String english, String engine) {
                translating = false;
                lastOut = english;
                showResult(english, engine);
                Store.addHistory(MainActivity.this, text, english);
            }
            @Override public void fail(String message) {
                translating = false;
                resultCard.setVisibility(View.GONE);
                toast(message);
            }
        });
    }

    private void showResult(String english, String engine) {
        if (resultCard == null) return;
        resultCard.setVisibility(View.VISIBLE);
        resultText.setTextColor(INK);
        resultText.setText(english);
        if (engine != null) resultMeta.setText("AI".equals(engine) ? "ENGLISH  •  AI polished ✨" : "ENGLISH  •  basic translation");
        resultCard.setAlpha(0f);
        resultCard.setTranslationY(d(18));
        resultCard.animate().alpha(1f).translationY(0).setDuration(320).start();
        if (homeScroll != null && engine != null) homeScroll.postDelayed(new Runnable() { @Override public void run() { homeScroll.smoothScrollTo(0, homeScroll.getChildAt(0).getHeight()); } }, 150);
    }

    private void clearAll() {
        stopVoice();
        source.setText("");
        live.setText("");
        lastOut = "";
        resultCard.setVisibility(View.GONE);
        Store.setDraft(this, "");
    }

    private void pasteText() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
            CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
            if (t != null && t.length() > 0) {
                String cur = source.getText().toString();
                source.setText(cur.isEmpty() ? t.toString() : cur + " " + t);
                source.setSelection(source.getText().length());
                return;
            }
        }
        toast("Nothing to paste");
    }

    private void copy(String text, String msg) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Bolo", text));
        toast(msg);
    }

    private void shareText(boolean whatsapp) {
        if (lastOut.isEmpty()) return;
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, lastOut);
        try {
            if (whatsapp) { i.setPackage("com.whatsapp"); startActivity(i); }
            else startActivity(Intent.createChooser(i, "Share reply"));
        } catch (Exception e) {
            if (whatsapp) { i.setPackage(null); try { startActivity(Intent.createChooser(i, "Share reply")); } catch (Exception ex) { toast("No app to share with"); } }
            else toast("No app to share with");
        }
    }

    /* =============================================================== HISTORY */
    private LinearLayout subPage(String title, String sub) {
        screen = title.equals("History") ? 1 : 2;
        root.removeAllViews();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(d(16), d(16), d(18), d(6));
        TextView back = roundBtn("←", Color.WHITE, INK, 44, 20);
        back.setElevation(d(2));
        back.setOnClickListener(new Tap() { @Override public void tap() { onBackPressed(); } });
        head.addView(back);
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(d(14), 0, 0, 0);
        t.addView(label(this, title, 26, INK, true));
        t.addView(label(this, sub, 13, MUTED, false));
        head.addView(t);
        page.addView(head, new LinearLayout.LayoutParams(-1, -2));
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, d(20));
        sv.addView(col);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        return col;
    }

    private void showHistory() {
        List<Store.Item> items = Store.history(this);
        LinearLayout col = subPage("History", items.size() + " saved replies");
        if (items.isEmpty()) {
            LinearLayout e = card(this);
            e.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView em = label(this, "🎙️", 36, INK, false);
            em.setGravity(Gravity.CENTER);
            e.addView(em);
            TextView et = label(this, "Your translated replies will appear here.", 15, MUTED, false);
            et.setGravity(Gravity.CENTER);
            e.addView(et);
            col.addView(e);
            return;
        }
        for (final Store.Item it : items) {
            LinearLayout c = card(this);
            TextView when = label(this, new SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(new Date(it.time)), 11, MUTED, true);
            c.addView(when);
            TextView src = label(this, it.src, 15, MUTED, false);
            src.setMaxLines(2);
            src.setEllipsize(TextUtils.TruncateAt.END);
            src.setPadding(0, d(6), 0, d(6));
            if (Build.VERSION.SDK_INT >= 17) src.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
            c.addView(src);
            TextView out = label(this, it.out, 16, INK, true);
            out.setMaxLines(4);
            out.setEllipsize(TextUtils.TruncateAt.END);
            c.addView(out);
            LinearLayout acts = new LinearLayout(this);
            acts.setGravity(Gravity.CENTER_VERTICAL);
            acts.setPadding(0, d(10), 0, 0);
            TextView cp = button(this, "Copy", GREEN, Color.WHITE);
            cp.setOnClickListener(new Tap() { @Override public void tap() { copy(it.out, "Copied"); } });
            TextView use = button(this, "Edit again", FIELD, INK);
            use.setOnClickListener(new Tap() { @Override public void tap() { lastOut = it.out; showHome(it.src); } });
            TextView del = roundBtn("🗑️", FIELD, INK, 46, 16);
            del.setOnClickListener(new Tap() { @Override public void tap() { Store.removeHistory(MainActivity.this, it.time); showHistory(); } });
            acts.addView(cp, lp(this, 0, -2, 0, 0, 4, 0)); ((LinearLayout.LayoutParams) cp.getLayoutParams()).weight = 1f;
            acts.addView(use, lp(this, 0, -2, 4, 0, 6, 0)); ((LinearLayout.LayoutParams) use.getLayoutParams()).weight = 1.2f;
            acts.addView(del);
            c.addView(acts);
            col.addView(c);
        }
    }

    /* ============================================================== SETTINGS */
    private LinearLayout group(LinearLayout col, String emoji, int tint, String title) {
        LinearLayout g = card(this);
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.addView(iconChip(this, emoji, tint, 38));
        TextView t = label(this, title, 13, MUTED, true);
        t.setPadding(d(12), 0, 0, 0);
        h.addView(t);
        g.addView(h);
        col.addView(g);
        return g;
    }

    private View switchRow(String title, String desc, boolean on, CompoundButton.OnCheckedChangeListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, d(10), 0, d(10));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(label(this, title, 16, INK, true));
        if (desc != null) info.addView(label(this, desc, 13, MUTED, false));
        row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(on);
        sw.setOnCheckedChangeListener(l);
        row.addView(sw, lp(this, -2, -2, 10, 0, 0, 0));
        return row;
    }

    private void showSettings() {
        LinearLayout col = subPage("Settings", "Make Bolo yours");

        // AI key
        LinearLayout ai = group(col, "✨", VIOLET, "AI – PERFECT ENGLISH");
        TextView why = label(this, "Add your Claude API key for natural, grammar-perfect replies in the tone you pick. Without it Bolo uses free basic translation.", 13, MUTED, false);
        why.setPadding(0, d(10), 0, d(8));
        ai.addView(why);
        final EditText key = input(this, "sk-ant-…", Store.apiKey(this), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setSingleLine(true);
        ai.addView(key);
        LinearLayout kr = new LinearLayout(this);
        kr.setPadding(0, d(10), 0, 0);
        TextView save = button(this, "Save key", GREEN, Color.WHITE);
        TextView test = button(this, "Test", FIELD, INK);
        save.setOnClickListener(new Tap() { @Override public void tap() { Store.setApiKey(MainActivity.this, key.getText().toString()); toast(Store.hasAi(MainActivity.this) ? "Key saved" : "AI turned off"); } });
        test.setOnClickListener(new Tap() { @Override public void tap() {
            Store.setApiKey(MainActivity.this, key.getText().toString());
            if (!Store.hasAi(MainActivity.this)) { toast("Enter your key first"); return; }
            toast("Testing…");
            Translator.translate(MainActivity.this, "میں کل آپ کو رپورٹ بھیج دوں گا", "Professional", new Translator.Callback() {
                @Override public void done(String english, String engine) { toast("AI".equals(engine) ? "✅ AI works: " + english : "⚠️ Key not used – using basic translation"); }
                @Override public void fail(String message) { toast("❌ " + message); }
            });
        } });
        kr.addView(save, lp(this, 0, -2, 0, 0, 4, 0)); ((LinearLayout.LayoutParams) save.getLayoutParams()).weight = 1.4f;
        kr.addView(test, lp(this, 0, -2, 4, 0, 0, 0)); ((LinearLayout.LayoutParams) test.getLayoutParams()).weight = 1f;
        ai.addView(kr);
        TextView get = textButton(this, "Get a key (console.anthropic.com)", GREEN_DARK);
        get.setOnClickListener(new Tap() { @Override public void tap() { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://console.anthropic.com/settings/keys"))); } catch (Exception e) { toast("No browser found"); } } });
        ai.addView(get, lp(this, -2, -2, 0, 4, 0, 0));
        TextView priv = label(this, "The key stays on this phone and is only sent to Anthropic when you translate.", 12, MUTED, false);
        ai.addView(priv);

        // voice
        LinearLayout v = group(col, "🎙️", TEAL, "VOICE");
        v.addView(switchRow("Translate automatically", "When you stop recording, translate straight away", Store.autoTranslate(this), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) { Store.setAutoTranslate(MainActivity.this, on); }
        }));
        View div = new View(this);
        div.setBackgroundColor(Color.parseColor("#EDF1F7"));
        v.addView(div, new LinearLayout.LayoutParams(-1, 1));
        v.addView(switchRow("Voice commands", "Say “… isay English mein translate kar do” at the end of your message and Bolo translates it for you", Store.voiceCommands(this), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) { Store.setVoiceCommands(MainActivity.this, on); }
        }));

        // data
        LinearLayout data = group(col, "🗂️", AMBER, "DATA");
        TextView clr = button(this, "Clear history", FIELD, INK);
        clr.setOnClickListener(new Tap() { @Override public void tap() {
            message(MainActivity.this, "Clear all history?", "All saved replies on this phone will be deleted.", "Clear", new Action() {
                @Override public boolean run() { Store.clearHistory(MainActivity.this); toast("History cleared"); return true; }
            }, "Cancel").show();
        } });
        data.addView(clr, lp(this, -1, -2, 0, 10, 0, 0));

        // about
        LinearLayout about = card(this);
        about.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView n = label(this, "Bolo  •  v1.0", 16, INK, true);
        n.setGravity(Gravity.CENTER);
        TextView cr = label(this, "Designed and Developed by IronicXsoft", 13, MUTED, false);
        cr.setGravity(Gravity.CENTER);
        cr.setPadding(0, d(4), 0, 0);
        about.addView(n);
        about.addView(cr);
        col.addView(about);
    }
}
