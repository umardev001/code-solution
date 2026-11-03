package com.sadqa.tracker;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Dashboard: total pending sadqa, month view, add revenue / give sadqa, history. */
public class MainActivity extends Activity {
    private static final int REQ_PIN = 7;
    private static long lastShownPending = Long.MIN_VALUE;

    private ScrollView sv;
    private LinearLayout col;
    private int selYm;
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);

    /* ------------------------------------------------------------ lifecycle */
    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(Color.parseColor("#06281F"));
            getWindow().setNavigationBarColor(UI.BG);
        }
        selYm = Store.nowYm();
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(UI.BG);
        root.setFilterTouchesWhenObscured(true);
        sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setFillViewport(true);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col, new ViewGroup.LayoutParams(-1, -2));
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!Lock.isUnlocked()) { goLogin(); return; }
        render();
    }

    @Override protected void onStop() { Lock.onLeave(); super.onStop(); }

    @Override public void onBackPressed() { Lock.lockNow(); super.onBackPressed(); }

    private void goLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private int dp(int v) { return UI.dp(this, v); }

    /* --------------------------------------------------------------- render */
    private void render() {
        final int keepY = sv.getScrollY();
        col.removeAllViews();
        addHero();
        addActions();
        addMonthBar();
        addMonthStats();
        addEntries();
        addHistory();
        TextView foot = UI.label(this, "🔒  Encrypted on this phone • no internet access", 12, UI.MUTED, false);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(dp(16), dp(22), dp(16), dp(26));
        col.addView(foot, new LinearLayout.LayoutParams(-1, -2));
        sv.post(new Runnable() { @Override public void run() { sv.scrollTo(0, keepY); } });
    }

    private TextView section(String t) {
        TextView v = UI.label(this, t, 13, UI.MUTED, true);
        v.setPadding(dp(22), dp(18), dp(22), dp(2));
        return v;
    }

    private void addHero() {
        long[] all = Store.sums(-1);
        final long pending = all[1] - all[2];
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#06281F"), Color.parseColor("#0B5D45"), Color.parseColor("#138A63")});
        float r = dp(32);
        g.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        h.setBackground(g);
        h.setPadding(dp(22), dp(14), dp(22), dp(56));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(UI.label(this, "Sadqa Tracker", 19, Color.WHITE, true));
        names.addView(UI.label(this, "Allah qabool farmaye 🤲", 12, Color.parseColor("#BDE8D4"), false));
        top.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView menu = UI.label(this, "⋮", 26, Color.WHITE, true);
        menu.setGravity(Gravity.CENTER);
        menu.setPadding(dp(14), dp(4), dp(6), dp(4));
        menu.setClickable(true);
        menu.setOnClickListener(new UI.Tap() { @Override public void tap() { showMenu(); } });
        top.addView(menu);
        h.addView(top);

        TextView cap = UI.label(this, pending >= 0 ? "SADQA PENDING  •  ALL TIME" : "ADVANCE GIVEN  •  EXTRA", 12, UI.GOLD, true);
        cap.setLetterSpacing(0.08f);
        h.addView(cap, UI.lp(this, -2, -2, 0, 22, 0, 0));

        final TextView amt = UI.label(this, Calc.money(Math.abs(pending)), 38, Color.WHITE, true);
        amt.setSingleLine(true);
        amt.setEllipsize(TextUtils.TruncateAt.END);
        h.addView(amt, UI.lp(this, -1, -2, 0, 2, 0, 0));
        if (lastShownPending != pending) {
            final long from = lastShownPending == Long.MIN_VALUE ? 0 : Math.abs(lastShownPending);
            final long to = Math.abs(pending);
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(700);
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator v) {
                    float f = (Float) v.getAnimatedValue();
                    amt.setText(Calc.money(from + Math.round((to - from) * (double) f)));
                }
            });
            a.start();
            lastShownPending = pending;
        }

        Widgets.BarView bar = new Widgets.BarView(this);
        h.addView(bar, UI.lp(this, -1, dp(8), 0, 14, 0, 0));
        bar.setProgress(all[1] > 0 ? (float) all[2] / all[1] : 0f);
        int pct = all[1] > 0 ? (int) Math.min(999, all[2] * 100 / all[1]) : 0;
        TextView sub = UI.label(this, "Given " + Calc.money(all[2]) + " of " + Calc.money(all[1]) + " due  •  " + pct + "%", 12, Color.parseColor("#BDE8D4"), false);
        h.addView(sub, UI.lp(this, -2, -2, 0, 8, 0, 0));
        TextView sub2 = UI.label(this, "Total revenue tracked: " + Calc.money(all[0]), 12, Color.parseColor("#8FCFB3"), false);
        h.addView(sub2, UI.lp(this, -2, -2, 0, 2, 0, 0));
        col.addView(h, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addActions() {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(14), 0, dp(14), 0);
        TextView rev = UI.button(this, "＋  Add Revenue", UI.GREEN, Color.WHITE);
        rev.setElevation(dp(6));
        rev.setOnClickListener(new UI.Tap() { @Override public void tap() { entryDialog(true, null); } });
        TextView give = UI.button(this, "🤲  Give Sadqa", UI.GOLD, Color.parseColor("#3A2A00"));
        give.setElevation(dp(6));
        give.setOnClickListener(new UI.Tap() { @Override public void tap() { entryDialog(false, null); } });
        row.addView(rev, UI.lp(this, 0, -2, 4, 0, 4, 0));
        ((LinearLayout.LayoutParams) rev.getLayoutParams()).weight = 1f;
        row.addView(give, UI.lp(this, 0, -2, 4, 0, 4, 0));
        ((LinearLayout.LayoutParams) give.getLayoutParams()).weight = 1f;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = -dp(26);
        col.addView(row, lp);
    }

    private void addMonthBar() {
        List<Integer> ms = Store.months();           // newest first
        final int max = ms.get(0), min = ms.get(ms.size() - 1);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(18), dp(14), 0);
        bar.addView(arrow("‹", selYm > min, -1));
        TextView t = UI.label(this, Store.monthName(selYm), 18, UI.INK, true);
        t.setGravity(Gravity.CENTER);
        bar.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        bar.addView(arrow("›", selYm < max, 1));
        col.addView(bar, new LinearLayout.LayoutParams(-1, -2));
    }

    private View arrow(String s, boolean on, final int dir) {
        TextView v = UI.label(this, s, 28, on ? UI.INK : Color.parseColor("#C5CDD8"), true);
        v.setGravity(Gravity.CENTER);
        v.setMinWidth(dp(48));
        v.setMinHeight(dp(44));
        v.setIncludeFontPadding(false);
        if (on) {
            v.setClickable(true);
            v.setOnClickListener(new UI.Tap() {
                @Override public void tap() {
                    selYm += dir;
                    render();
                }
            });
        }
        return v;
    }

    private void addMonthStats() {
        long[] m = Store.sums(selYm);
        long pend = m[1] - m[2];
        LinearLayout card = UI.card(this);
        LinearLayout r1 = new LinearLayout(this);
        r1.addView(tile("REVENUE", Calc.money(m[0]), UI.BLUE), UI.lp(this, 0, -2, 0, 0, 6, 0));
        r1.addView(tile("SADQA DUE", Calc.money(m[1]), UI.VIOLET), UI.lp(this, 0, -2, 6, 0, 0, 0));
        ((LinearLayout.LayoutParams) r1.getChildAt(0).getLayoutParams()).weight = 1f;
        ((LinearLayout.LayoutParams) r1.getChildAt(1).getLayoutParams()).weight = 1f;
        LinearLayout r2 = new LinearLayout(this);
        r2.addView(tile("GIVEN", Calc.money(m[2]), UI.GREEN), UI.lp(this, 0, -2, 0, 0, 6, 0));
        r2.addView(tile(pend >= 0 ? "PENDING" : "ADVANCE", Calc.money(Math.abs(pend)), pend > 0 ? UI.AMBER : UI.TEAL), UI.lp(this, 0, -2, 6, 0, 0, 0));
        ((LinearLayout.LayoutParams) r2.getChildAt(0).getLayoutParams()).weight = 1f;
        ((LinearLayout.LayoutParams) r2.getChildAt(1).getLayoutParams()).weight = 1f;
        card.addView(r1);
        card.addView(r2, UI.lp(this, -1, -2, 0, 12, 0, 0));
        col.addView(card);
    }

    private View tile(String label, String value, int color) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setBackground(UI.round(this, UI.alpha(color, 30), 16));
        t.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView l = UI.label(this, label, 11, color, true);
        l.setLetterSpacing(0.06f);
        t.addView(l);
        TextView v = UI.label(this, value, 17, UI.INK, true);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(v, UI.lp(this, -2, -2, 0, 3, 0, 0));
        return t;
    }

    private void addEntries() {
        col.addView(section("ENTRIES THIS MONTH"));
        List<Store.Entry> all = new ArrayList<Store.Entry>();
        for (Store.Entry e : Store.income) if (Store.ym(e.ts) == selYm) all.add(e);
        for (Store.Entry e : Store.paid) if (Store.ym(e.ts) == selYm) all.add(e);
        Collections.sort(all, Store.NEWEST_FIRST);
        if (all.isEmpty()) {
            LinearLayout c = UI.card(this);
            TextView t = UI.label(this, "Nothing recorded for " + Store.monthName(selYm) + " yet.\nTap “Add Revenue” when a payment arrives – sadqa is calculated automatically.", 14, UI.MUTED, false);
            t.setLineSpacing(0, 1.15f);
            c.addView(t);
            col.addView(c);
            return;
        }
        for (Store.Entry e : all) col.addView(entryRow(e, Store.income.contains(e)));
    }

    private View entryRow(final Store.Entry e, final boolean isIncome) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(UI.round(this, Color.WHITE, 18));
        row.setElevation(dp(2));
        row.setPadding(dp(12), dp(10), dp(14), dp(10));
        row.setClickable(true);
        row.setOnClickListener(new UI.Tap() { @Override public void tap() { entryOptions(e, isIncome); } });
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.setMargins(dp(16), dp(5), dp(16), dp(5));

        TextView chip = UI.iconChip(this, isIncome ? "💰" : "🤲", isIncome ? UI.GREEN : UI.GOLD, 44);
        row.addView(chip, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        TextView t = UI.label(this, e.note.isEmpty() ? (isIncome ? "Revenue" : "Sadqa given") : e.note, 15, UI.INK, true);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(t);
        String sub = dateFmt.format(new Date(e.ts)) + (isIncome ? "  •  Sadqa " + Calc.money(e.due()) : "");
        TextView s = UI.label(this, sub, 12, UI.MUTED, false);
        s.setSingleLine(true);
        s.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(s);
        row.addView(mid, UI.lp(this, 0, -2, 12, 0, 8, 0));
        ((LinearLayout.LayoutParams) mid.getLayoutParams()).weight = 1f;

        TextView a = UI.label(this, (isIncome ? "+ " : "– ") + Calc.money(e.amount), 15, isIncome ? UI.GREEN : UI.AMBER, true);
        a.setSingleLine(true);
        row.addView(a);
        row.setLayoutParams(rl);
        return row;
    }

    private void addHistory() {
        col.addView(section("ALL MONTHS"));
        LinearLayout card = UI.card(this);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        List<Integer> ms = Store.months();
        for (final int ym : ms) {
            long[] m = Store.sums(ym);
            long pend = m[1] - m[2];
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            if (ym == selYm) row.setBackground(UI.round(this, UI.alpha(UI.GREEN, 28), 14));
            row.setClickable(true);
            row.setOnClickListener(new UI.Tap() {
                @Override public void tap() { selYm = ym; render(); sv.post(new Runnable() { @Override public void run() { sv.smoothScrollTo(0, 0); } }); }
            });
            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.addView(UI.label(this, Store.shortMonth(ym), 15, UI.INK, true));
            TextView sub = UI.label(this, "Revenue " + Calc.money(m[0]), 12, UI.MUTED, false);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            mid.addView(sub);
            row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout right = new LinearLayout(this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.END);
            String top = m[1] == 0 && m[2] == 0 ? "–" : (pend > 0 ? Calc.money(pend) : (pend == 0 ? "✓ Settled" : "+" + Calc.money(-pend)));
            TextView st = UI.label(this, top, 15, pend > 0 ? UI.AMBER : UI.GREEN, true);
            st.setSingleLine(true);
            right.addView(st);
            right.addView(UI.label(this, pend > 0 ? "pending" : (pend < 0 ? "advance" : ""), 11, UI.MUTED, false));
            row.addView(right);
            card.addView(row);
        }
        col.addView(card);
    }

    /* -------------------------------------------------------------- dialogs */
    private long defaultTs() {
        Calendar c = Calendar.getInstance();
        if (selYm != Store.nowYm()) { c.set(selYm / 12, selYm % 12, 1); }
        c.set(Calendar.HOUR_OF_DAY, 12); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private void entryDialog(final boolean isIncome, final Store.Entry edit) {
        final long[] when = {edit != null ? edit.ts : defaultTs()};
        final int rate = edit != null ? edit.rate : Store.rate();
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);

        form.addView(UI.caption(this, isIncome ? "REVENUE RECEIVED (Rs)" : "SADQA GIVEN (Rs)"));
        final EditText amt = UI.input(this, "e.g. 100000", edit != null ? Long.toString(edit.amount) : "", InputType.TYPE_CLASS_NUMBER);
        amt.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
        amt.setTextSize(20);
        amt.setTypeface(Typeface.DEFAULT_BOLD);
        form.addView(amt);

        final TextView hint = UI.label(this, "", 13, UI.GREEN_DARK, true);
        hint.setPadding(dp(2), dp(8), 0, 0);
        form.addView(hint);
        if (isIncome) {
            amt.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void afterTextChanged(Editable s) {
                    long v = Calc.parseAmount(s);
                    hint.setText(v > 0 ? "Sadqa on this: " + Calc.money(Calc.due(v, rate)) + "   (" + Calc.money(rate) + " per lakh)" : "Sadqa is calculated automatically");
                }
            });
            hint.setText(edit != null ? "Sadqa on this: " + Calc.money(edit.due()) : "Sadqa is calculated automatically");
        } else {
            long[] all = Store.sums(-1);
            final long pend = all[1] - all[2] + (edit != null ? edit.amount : 0);
            hint.setText("Total pending now: " + Calc.money(Math.max(0, pend)));
            if (pend > 0) {
                TextView all1 = UI.textButton(this, "Fill full pending " + Calc.money(pend), UI.GREEN);
                all1.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                all1.setPadding(dp(2), dp(6), dp(2), dp(2));
                all1.setOnClickListener(new UI.Tap() { @Override public void tap() { amt.setText(Long.toString(pend)); amt.setSelection(amt.getText().length()); } });
                form.addView(all1);
            }
        }

        form.addView(UI.caption(this, "DATE"));
        final TextView dateBtn = UI.label(this, dateFmt.format(new Date(when[0])) + "   ▾", 16, UI.INK, true);
        dateBtn.setBackground(UI.round(this, UI.FIELD, 12));
        dateBtn.setPadding(dp(14), dp(12), dp(14), dp(12));
        dateBtn.setMinHeight(dp(48));
        dateBtn.setGravity(Gravity.CENTER_VERTICAL);
        dateBtn.setClickable(true);
        dateBtn.setOnClickListener(new UI.Tap() {
            @Override public void tap() {
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(when[0]);
                DatePickerDialog dp = new DatePickerDialog(MainActivity.this, new DatePickerDialog.OnDateSetListener() {
                    @Override public void onDateSet(DatePicker view, int y, int m, int d) {
                        Calendar n = Calendar.getInstance();
                        n.set(y, m, d, 12, 0, 0);
                        n.set(Calendar.MILLISECOND, 0);
                        when[0] = n.getTimeInMillis();
                        dateBtn.setText(dateFmt.format(new Date(when[0])) + "   ▾");
                    }
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
                if (dp.getWindow() != null) dp.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                dp.show();
            }
        });
        form.addView(dateBtn);

        form.addView(UI.caption(this, isIncome ? "CLIENT / NOTE (optional)" : "GIVEN TO / NOTE (optional)"));
        final EditText note = UI.textInput(this, isIncome ? "e.g. Fiverr – logo project" : "e.g. Masjid, orphan fund", edit != null ? edit.note : "", false);
        form.addView(note);

        String title = (edit == null ? "" : "Edit ") + (isIncome ? (edit == null ? "Add revenue" : "revenue") : (edit == null ? "Give sadqa" : "sadqa"));
        Dialog d = UI.dialog(this, title, form, "Save", new UI.Action() {
            @Override public boolean run() {
                long v = Calc.parseAmount(amt.getText());
                if (v <= 0) { Toast.makeText(MainActivity.this, "Enter an amount", Toast.LENGTH_SHORT).show(); return false; }
                Store.Entry e = edit != null ? edit : new Store.Entry();
                long oldAmount = e.amount, oldTs = e.ts; String oldNote = e.note;
                e.amount = v; e.ts = when[0]; e.note = note.getText().toString().trim();
                if (edit == null) { e.id = Store.newId(); e.rate = rate; (isIncome ? Store.income : Store.paid).add(e); }
                try {
                    Store.save(MainActivity.this);
                } catch (Exception ex) {
                    if (edit == null) (isIncome ? Store.income : Store.paid).remove(e);
                    else { e.amount = oldAmount; e.ts = oldTs; e.note = oldNote; }
                    Toast.makeText(MainActivity.this, "Could not save securely – nothing changed", Toast.LENGTH_LONG).show();
                    return true;
                }
                selYm = Store.ym(e.ts);
                render();
                Toast.makeText(MainActivity.this, isIncome ? "Revenue added • sadqa " + Calc.money(e.due()) : "Sadqa recorded ✓ جزاک اللہ", Toast.LENGTH_SHORT).show();
                return true;
            }
        }, "Cancel", null, null);
        if (d.getWindow() != null) d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        d.show();
        amt.requestFocus();
    }

    private void entryOptions(final Store.Entry e, final boolean isIncome) {
        String body = (isIncome ? "Revenue " : "Sadqa given ") + Calc.money(e.amount) + "\n" + dateFmt.format(new Date(e.ts))
                + (isIncome ? "\nSadqa due " + Calc.money(e.due()) + "  (" + Calc.money(e.rate) + " per lakh)" : "")
                + (e.note.isEmpty() ? "" : "\n\n" + e.note);
        TextView m = UI.label(this, body, 15, UI.INK, false);
        m.setLineSpacing(0, 1.2f);
        UI.dialog(this, isIncome ? "Revenue entry" : "Sadqa entry", m, "Edit", new UI.Action() {
            @Override public boolean run() { entryDialog(isIncome, e); return true; }
        }, "Close", "Delete", new UI.Action() {
            @Override public boolean run() {
                UI.message(MainActivity.this, "Delete this entry?", "This will change your pending sadqa total. This cannot be undone.", "Delete", new UI.Action() {
                    @Override public boolean run() {
                        List<Store.Entry> l = isIncome ? Store.income : Store.paid;
                        int idx = l.indexOf(e);
                        l.remove(e);
                        try { Store.save(MainActivity.this); } catch (Exception ex) {
                            if (idx >= 0) l.add(Math.min(idx, l.size()), e);
                            Toast.makeText(MainActivity.this, "Could not save securely – nothing changed", Toast.LENGTH_LONG).show();
                            return true;
                        }
                        render();
                        return true;
                    }
                }, "Cancel").show();
                return true;
            }
        }).show();
    }

    private void showMenu() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] ref = new Dialog[1];
        body.addView(menuRow("💸", "Sadqa rate", Calc.money(Store.rate()) + " per 1 lakh", new Runnable() { @Override public void run() { rateDialog(); } }, ref));
        body.addView(menuRow("🔑", "Change PIN", "Replace your " + Lock.PIN_LEN + "-digit PIN", new Runnable() {
            @Override public void run() { startActivityForResult(new Intent(MainActivity.this, LoginActivity.class).putExtra(LoginActivity.EXTRA_MODE, LoginActivity.MODE_CHANGE), REQ_PIN); }
        }, ref));
        body.addView(menuRow("🔒", "Lock now", "Close the app and require the PIN", new Runnable() {
            @Override public void run() { Lock.lockNow(); goLogin(); }
        }, ref));
        body.addView(menuRow("🛡️", "How your data is protected", "Encryption, lock-out, no internet", new Runnable() { @Override public void run() { securityInfo(); } }, ref));
        ref[0] = UI.dialog(this, "Menu", body, null, null, "Close", null, null);
        ref[0].show();
    }

    private View menuRow(String emoji, String title, String sub, final Runnable go, final Dialog[] ref) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(8), 0, dp(8));
        r.setClickable(true);
        r.addView(UI.iconChip(this, emoji, UI.GREEN, 40), new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(UI.label(this, title, 15, UI.INK, true));
        TextView s = UI.label(this, sub, 12, UI.MUTED, false);
        t.addView(s);
        r.addView(t, UI.lp(this, 0, -2, 12, 0, 0, 0));
        ((LinearLayout.LayoutParams) t.getLayoutParams()).weight = 1f;
        r.setOnClickListener(new UI.Tap() { @Override public void tap() { if (ref[0] != null) ref[0].dismiss(); go.run(); } });
        return r;
    }

    private void rateDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        TextView m = UI.label(this, "Sadqa = this many rupees for every Rs 1,00,000 of revenue. Applies to NEW revenue entries only – old entries keep the rate they were added with.", 14, UI.MUTED, false);
        m.setLineSpacing(0, 1.15f);
        form.addView(m);
        form.addView(UI.caption(this, "RUPEES PER 1 LAKH"));
        final EditText ed = UI.input(this, "5000", Integer.toString(Store.rate()), InputType.TYPE_CLASS_NUMBER);
        ed.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        form.addView(ed);
        UI.dialog(this, "Sadqa rate", form, "Save", new UI.Action() {
            @Override public boolean run() {
                long v = Calc.parseAmount(ed.getText());
                if (v < 1 || v > 100000) { Toast.makeText(MainActivity.this, "Enter 1 to 1,00,000", Toast.LENGTH_SHORT).show(); return false; }
                try { Store.setRate(MainActivity.this, (int) v); } catch (Exception ex) {
                    Toast.makeText(MainActivity.this, "Could not save securely", Toast.LENGTH_LONG).show();
                    return true;
                }
                render();
                return true;
            }
        }, "Cancel", null, null).show();
    }

    private void securityInfo() {
        String t = "• Everything is encrypted (AES-256-GCM) with a key kept inside the phone’s secure Keystore.\n\n"
                + "• Your PIN is never stored – only a salted PBKDF2 hash, inside the encrypted data.\n\n"
                + "• Wrong PINs trigger growing lock-outs (30 s, 1 min, 2 min … up to 1 hour).\n\n"
                + "• The app has NO internet permission – nothing can leave this phone.\n\n"
                + "• Screenshots and the recent-apps preview are blocked.\n\n"
                + "• Auto-locks 20 seconds after you leave the app.\n\n"
                + "• Cloud/ADB backup is switched off. If you uninstall the app or erase the phone, the records are gone.";
        TextView m = UI.label(this, t, 14, UI.INK, false);
        m.setLineSpacing(0, 1.1f);
        UI.dialog(this, "How your data is protected", m, null, null, "Close", null, null).show();
    }
}
