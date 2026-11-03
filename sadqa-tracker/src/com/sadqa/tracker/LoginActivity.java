package com.sadqa.tracker;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Animated PIN screen: login, first-time PIN creation, and PIN change. */
public class LoginActivity extends Activity {
    public static final String EXTRA_MODE = "mode";
    public static final String MODE_CHANGE = "change";
    private static final int LEN = Lock.PIN_LEN;
    private static final int STEP_VERIFY = 0, STEP_NEW = 1, STEP_CONFIRM = 2;
    private static final int SUB = Color.parseColor("#BDE8D4");

    private boolean changeMode;
    private boolean broken;           // vault could not be opened
    private int step;
    private String first = "";
    private String afterMsg = null;
    private final StringBuilder entry = new StringBuilder();
    private boolean busy = false;

    private AuroraView aurora;
    private LogoView logo;
    private TextView title, subtitle;
    private LinearLayout dotsRow;
    private View[] dots = new View[LEN];
    private View content;
    private final Handler handler = new Handler();
    private Runnable ticker;

    /* ------------------------------------------------------------ lifecycle */
    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);   // no screenshots / recents preview
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(Color.parseColor("#06281F"));
            getWindow().setNavigationBarColor(Color.parseColor("#138A63"));
        }
        changeMode = MODE_CHANGE.equals(getIntent().getStringExtra(EXTRA_MODE)) && Lock.isUnlocked();
        if (!changeMode && Lock.isUnlocked()) { openApp(false); return; }

        try { Store.open(this); } catch (Exception e) { broken = true; }

        if (broken) step = STEP_VERIFY;
        else step = Lock.hasPin() ? STEP_VERIFY : STEP_NEW;

        build();
        refreshTexts();
        playEntrance();
        if (!broken && step == STEP_VERIFY && Lock.waitMs() > 0) startCountdown();
    }

    @Override
    public void onBackPressed() {
        if (changeMode) { super.onBackPressed(); return; }
        moveTaskToBack(true);
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) { press(String.valueOf(code - KeyEvent.KEYCODE_0)); return true; }
        if (code == KeyEvent.KEYCODE_DEL) { backspace(); return true; }
        return super.onKeyDown(code, e);
    }

    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }

    private int dp(int v) { return UI.dp(this, v); }

    /* --------------------------------------------------------------- layout */
    private void build() {
        FrameLayout root = new FrameLayout(this);
        root.setFilterTouchesWhenObscured(true);          // ignore taps through overlay windows
        aurora = new AuroraView(this);
        root.addView(aurora, new FrameLayout.LayoutParams(-1, -1));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(24), dp(28), dp(24), dp(14));
        content = col;

        int sh = getResources().getDisplayMetrics().heightPixels;
        int key = Math.max(dp(50), Math.min(dp(72), (sh - dp(540)) / 4));
        int logoSize = sh < dp(620) ? dp(70) : dp(96);

        View top = new View(this);
        col.addView(top, new LinearLayout.LayoutParams(1, 0, 1f));

        logo = new LogoView(this);
        col.addView(logo, new LinearLayout.LayoutParams(logoSize, logoSize));

        title = UI.label(this, "", 27, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        col.addView(title, UI.lp(this, -2, -2, 0, 14, 0, 0));
        subtitle = UI.label(this, "", 14, SUB, false);
        subtitle.setGravity(Gravity.CENTER);
        col.addView(subtitle, UI.lp(this, -2, -2, 0, 4, 0, 0));

        dotsRow = new LinearLayout(this);
        dotsRow.setGravity(Gravity.CENTER);
        for (int i = 0; i < LEN; i++) {
            View d = new View(this);
            dots[i] = d;
            dotsRow.addView(d, UI.lp(this, dp(14), dp(14), 7, 0, 7, 0));
        }
        col.addView(dotsRow, UI.lp(this, -2, -2, 0, 22, 0, 20));
        paintDots(0, Color.WHITE);

        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setGravity(Gravity.CENTER_HORIZONTAL);
        String[][] rows = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"", "0", "⌫"}};
        for (int r = 0; r < 4; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER);
            for (int k = 0; k < 3; k++) {
                final String label = rows[r][k];
                View b = label.isEmpty() ? new View(this) : keyButton(label);
                row.addView(b, UI.lp(this, key, key, 8, 5, 8, 5));
            }
            pad.addView(row);
        }
        col.addView(pad);

        if (!changeMode && (broken || Lock.hasPin())) {
            TextView forgot = UI.textButton(this, "Forgot PIN?", Color.parseColor("#9FDCC0"));
            forgot.setOnClickListener(new UI.Tap() { @Override public void tap() { confirmReset(); } });
            col.addView(forgot, UI.lp(this, -2, -2, 0, 6, 0, 0));
        }

        View bottom = new View(this);
        col.addView(bottom, new LinearLayout.LayoutParams(1, 0, 1f));

        TextView credit = UI.label(this, "🔒  Encrypted • Offline • Only on this phone", 12, Color.parseColor("#8FCFB3"), false);
        credit.setGravity(Gravity.CENTER);
        credit.setPadding(0, dp(10), 0, dp(4));
        col.addView(credit);

        sv.addView(col, new ViewGroup.LayoutParams(-1, -2));
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    private View keyButton(final String label) {
        TextView t = new TextView(this);
        boolean back = label.equals("⌫");
        t.setText(label);
        t.setTextColor(Color.WHITE);
        t.setTextSize(back ? 24 : 28);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        t.setIncludeFontPadding(false);
        StateListDrawable sl = new StateListDrawable();
        GradientDrawable pressed = new GradientDrawable(); pressed.setShape(GradientDrawable.OVAL); pressed.setColor(Color.parseColor("#66F5D78A"));
        GradientDrawable normal = new GradientDrawable(); normal.setShape(GradientDrawable.OVAL); normal.setColor(back ? Color.parseColor("#14FFFFFF") : Color.parseColor("#2AFFFFFF"));
        sl.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sl.addState(new int[]{}, normal);
        t.setBackground(sl);
        t.setClickable(true);
        t.setFilterTouchesWhenObscured(true);
        t.setOnClickListener(new UI.Tap() {
            @Override public void tap() { if (label.equals("⌫")) backspace(); else press(label); }
        });
        t.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(70).start();
                else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL)
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).setInterpolator(new OvershootInterpolator(3f)).start();
                return false;
            }
        });
        return t;
    }

    /* ----------------------------------------------------------- PIN logic */
    private void refreshTexts() {
        subtitle.setTextColor(SUB);
        if (broken) {
            title.setText("Vault unavailable");
            subtitle.setText("Your data could not be unlocked on this phone");
        } else if (step == STEP_VERIFY) {
            title.setText(changeMode ? "Enter current PIN" : "Welcome back");
            subtitle.setText(changeMode ? "Confirm it’s you first" : "Enter your " + LEN + "-digit PIN");
        } else if (step == STEP_NEW) {
            title.setText(Lock.hasPin() || changeMode ? "Create new PIN" : "Create your PIN");
            subtitle.setText("Choose " + LEN + " digits only you know");
        } else {
            title.setText("Confirm your PIN");
            subtitle.setText("Enter the same " + LEN + " digits again");
        }
    }

    private void press(String d) {
        if (busy || broken || entry.length() >= LEN) return;
        entry.append(d);
        paintDots(entry.length(), Color.WHITE);
        pop(dots[entry.length() - 1]);
        if (entry.length() == LEN) {
            busy = true;
            handler.postDelayed(new Runnable() { @Override public void run() { submit(); } }, 160);
        }
    }

    private void backspace() {
        if (busy || entry.length() == 0) return;
        entry.setLength(entry.length() - 1);
        paintDots(entry.length(), Color.WHITE);
    }

    private void submit() {
        String pin = entry.toString();
        try {
            if (step == STEP_VERIFY) {
                if (Lock.verify(this, pin)) {
                    if (changeMode) { step = STEP_NEW; resetEntry(); slideTexts(); }
                    else success();
                } else if (Lock.waitMs() > 0) {
                    wrong("", true);
                } else {
                    int left = Lock.triesLeftBeforeLock();
                    wrong("Wrong PIN" + (left > 0 ? " • " + left + (left == 1 ? " try" : " tries") + " left before lock" : ""), false);
                }
            } else if (step == STEP_NEW) {
                if (Calc.weakPin(pin)) { wrong("Too easy to guess – choose another PIN", false); return; }
                first = pin;
                step = STEP_CONFIRM;
                resetEntry();
                slideTexts();
            } else {
                if (pin.equals(first)) {
                    Lock.setPin(this, pin);
                    if (changeMode) { Toast.makeText(this, "PIN updated", Toast.LENGTH_SHORT).show(); setResult(RESULT_OK); successThen(false); }
                    else success();
                } else {
                    first = "";
                    step = STEP_NEW;
                    wrong("PINs didn’t match – start again", false);
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Secure storage error – please try again", Toast.LENGTH_LONG).show();
            resetEntry();
        }
    }

    private void resetEntry() { entry.setLength(0); paintDots(0, Color.WHITE); busy = false; }

    private void wrong(String msg, final boolean lockout) {
        afterMsg = lockout ? null : msg;
        paintDots(LEN, Color.parseColor("#FF8A80"));
        subtitle.setText(msg);
        subtitle.setTextColor(Color.parseColor("#FFB4AB"));
        try { ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(70); } catch (Exception ignored) { }
        ObjectAnimator a = ObjectAnimator.ofFloat(dotsRow, "translationX", 0, -dp(14), dp(14), -dp(10), dp(10), -dp(5), dp(5), 0);
        a.setDuration(420);
        a.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                resetEntry();
                refreshTexts();
                if (lockout) startCountdown();
                else if (afterMsg != null) { subtitle.setText(afterMsg); subtitle.setTextColor(Color.parseColor("#FFB4AB")); }
            }
        });
        a.start();
    }

    /** Disables the keypad and counts down the lock-out. */
    private void startCountdown() {
        busy = true;
        if (ticker != null) handler.removeCallbacks(ticker);
        ticker = new Runnable() {
            @Override public void run() {
                long ms = Lock.waitMs();
                if (ms <= 0) { busy = false; ticker = null; refreshTexts(); return; }
                long sec = (ms + 999) / 1000;
                title.setText("Locked");
                subtitle.setText("Too many wrong tries. Try again in " + (sec / 60) + ":" + (sec % 60 < 10 ? "0" : "") + (sec % 60));
                subtitle.setTextColor(Color.parseColor("#FFB4AB"));
                handler.postDelayed(this, 500);
            }
        };
        ticker.run();
    }

    private void slideTexts() {
        refreshTexts();
        View[] vs = {title, subtitle, dotsRow};
        for (int i = 0; i < vs.length; i++) {
            vs[i].setAlpha(0f);
            vs[i].setTranslationY(dp(18));
            vs[i].animate().alpha(1f).translationY(0).setStartDelay(40L * i).setDuration(260).start();
        }
    }

    private void success() { successThen(true); }

    private void successThen(final boolean openMain) {
        busy = true;
        paintDots(LEN, Color.parseColor("#F5D78A"));
        for (int i = 0; i < LEN; i++) pop(dots[i]);
        logo.celebrate();
        content.animate().alpha(0f).setStartDelay(520).setDuration(280).start();
        handler.postDelayed(new Runnable() {
            @Override public void run() { if (openMain) openApp(true); else finish(); }
        }, 820);
    }

    private void openApp(boolean animate) {
        Lock.unlock();
        startActivity(new Intent(this, MainActivity.class));
        finish();
        if (animate) overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void confirmReset() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        TextView m = UI.label(this, "The PIN cannot be recovered. Resetting permanently erases ALL revenue and sadqa records on this phone, then lets you start fresh.\n\nType DELETE to confirm.", 15, UI.INK, false);
        m.setLineSpacing(0, 1.15f);
        body.addView(m);
        final EditText ed = UI.input(this, "DELETE", "", android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        ed.setSingleLine(true);
        body.addView(ed, UI.wrapRow(this, 12));
        Dialog d = UI.dialog(this, "Forgot your PIN?", body, "Erase everything", new UI.Action() {
            @Override public boolean run() {
                if (!ed.getText().toString().trim().equals("DELETE")) {
                    Toast.makeText(LoginActivity.this, "Type DELETE exactly to confirm", Toast.LENGTH_SHORT).show();
                    return false;
                }
                Store.wipe(LoginActivity.this);
                Lock.lockNow();
                recreate();
                return true;
            }
        }, "Cancel", null, null);
        d.show();
    }

    /* ------------------------------------------------------------ animation */
    private void paintDots(int filled, int color) {
        for (int i = 0; i < LEN; i++) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            if (i < filled) g.setColor(color);
            else { g.setColor(Color.TRANSPARENT); g.setStroke(dp(2), Color.parseColor("#99FFFFFF")); }
            dots[i].setBackground(g);
        }
    }

    private void pop(final View v) {
        v.animate().cancel();
        v.setScaleX(0.6f); v.setScaleY(0.6f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(4f)).start();
    }

    private void playEntrance() {
        ViewGroup col = (ViewGroup) content;
        int n = 0;
        for (int i = 0; i < col.getChildCount(); i++) {
            View v = col.getChildAt(i);
            if (v.getLayoutParams() instanceof LinearLayout.LayoutParams && ((LinearLayout.LayoutParams) v.getLayoutParams()).weight > 0) continue;
            v.setAlpha(0f);
            v.setTranslationY(dp(34));
            v.animate().alpha(1f).translationY(0).setStartDelay(60L * n++).setDuration(420).setInterpolator(new AccelerateDecelerateInterpolator()).start();
        }
    }

    /* ---------------------------------------------------------- custom views */
    /** Slowly drifting emerald gradient with soft golden bubbles. */
    static final class AuroraView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bg = new Paint();
        private float t;
        private ValueAnimator anim;
        private final float[][] b = {{0.15f, 0.2f, 0.34f, 0.7f, 0.0f}, {0.85f, 0.15f, 0.26f, 1.1f, 1.7f}, {0.3f, 0.75f, 0.42f, 0.9f, 3.1f},
                {0.9f, 0.7f, 0.3f, 0.6f, 4.4f}, {0.55f, 0.45f, 0.2f, 1.3f, 2.2f}};

        AuroraView(Context c) { super(c); }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            bg.setShader(new LinearGradient(0, 0, w * 0.4f, h, new int[]{Color.parseColor("#06281F"), Color.parseColor("#0B5D45"), Color.parseColor("#138A63")}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            anim = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
            anim.setDuration(14000);
            anim.setInterpolator(new LinearInterpolator());
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) { t = (Float) a.getAnimatedValue(); invalidate(); }
            });
            anim.start();
        }

        @Override protected void onDetachedFromWindow() { if (anim != null) anim.cancel(); super.onDetachedFromWindow(); }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            c.drawRect(0, 0, w, h, bg);
            for (int i = 0; i < b.length; i++) {
                float[] k = b[i];
                float x = (k[0] + 0.06f * (float) Math.sin(t * k[3] + k[4])) * w;
                float y = (k[1] + 0.05f * (float) Math.cos(t * k[3] * 0.8f + k[4])) * h;
                float r = k[2] * Math.min(w, h) * (1f + 0.06f * (float) Math.sin(t * 2 + k[4]));
                boolean gold = i % 2 == 1;
                p.setColor(gold ? Color.argb(22, 245, 215, 138) : Color.argb(22, 255, 255, 255));
                c.drawCircle(x, y, r, p);
                p.setColor(gold ? Color.argb(14, 245, 215, 138) : Color.argb(12, 255, 255, 255));
                c.drawCircle(x, y, r * 0.62f, p);
            }
        }
    }

    /** Golden coin with a heart (sadqa), floating, with sparkles orbiting it. */
    static final class LogoView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path heart = new Path();
        private ValueAnimator pulse;
        private float phase;
        private float boost = 0f;

        LogoView(Context c) { super(c); }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            pulse = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
            pulse.setDuration(3200);
            pulse.setInterpolator(new LinearInterpolator());
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    phase = (Float) a.getAnimatedValue();
                    setTranslationY((float) Math.sin(phase) * UI.dp(getContext(), 4));
                    invalidate();
                }
            });
            pulse.start();
        }

        @Override protected void onDetachedFromWindow() { if (pulse != null) pulse.cancel(); super.onDetachedFromWindow(); }

        void celebrate() {
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(520);
            a.setInterpolator(new OvershootInterpolator(2.5f));
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator v) { boost = (Float) v.getAnimatedValue(); setScaleX(1f + 0.25f * boost); setScaleY(1f + 0.25f * boost); invalidate(); }
            });
            a.start();
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), cx = w / 2f, cy = getHeight() / 2f;
            float glow = 1f + 0.07f * (float) Math.sin(phase);
            p.setStyle(Paint.Style.FILL);
            p.setShader(null);
            p.setColor(Color.argb(40, 245, 215, 138));
            c.drawCircle(cx, cy, w * 0.5f * glow, p);
            // coin
            p.setShader(new LinearGradient(cx - w * 0.36f, cy - w * 0.36f, cx + w * 0.36f, cy + w * 0.36f, Color.parseColor("#FBE7A1"), Color.parseColor("#E0A526"), Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, w * 0.36f, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(w * 0.025f);
            p.setColor(Color.argb(120, 255, 255, 255));
            c.drawCircle(cx, cy, w * 0.30f, p);
            // heart
            float s = w * 0.17f;
            heart.reset();
            heart.moveTo(cx, cy + s * 0.95f);
            heart.cubicTo(cx - s * 1.7f, cy - s * 0.1f, cx - s * 0.95f, cy - s * 1.25f, cx, cy - s * 0.35f);
            heart.cubicTo(cx + s * 0.95f, cy - s * 1.25f, cx + s * 1.7f, cy - s * 0.1f, cx, cy + s * 0.95f);
            heart.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.parseColor("#0B5D45"));
            c.drawPath(heart, p);
            // orbiting sparkles
            p.setColor(Color.argb(230, 255, 241, 190));
            for (int i = 0; i < 3; i++) {
                double a = phase * 1.0 + i * 2.0944;
                float r = w * 0.47f;
                float sz = w * (0.028f + 0.012f * (float) Math.sin(phase * 3 + i));
                c.drawCircle(cx + r * (float) Math.cos(a), cy + r * 0.92f * (float) Math.sin(a), sz, p);
            }
        }
    }
}
