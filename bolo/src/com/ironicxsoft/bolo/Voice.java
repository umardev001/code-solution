package com.ironicxsoft.bolo;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

/**
 * Continuous speech-to-text: keeps listening (restarting after every pause) until stop() is called,
 * so long client replies can be dictated in one go.
 */
public final class Voice implements RecognitionListener {
    public interface Listener {
        void onPartial(String text);
        void onFinal(String text);
        void onLevel(float level);
        void onState(boolean listening);
        void onError(String message);
    }

    private final Context ctx;
    private final Listener l;
    private final Handler h = new Handler();
    private SpeechRecognizer rec;
    private String lang = "ur-PK";
    private boolean active = false;
    private int quiet = 0;

    public Voice(Context ctx, Listener l) { this.ctx = ctx.getApplicationContext(); this.l = l; }

    public static boolean available(Context c) { return SpeechRecognizer.isRecognitionAvailable(c); }

    public boolean isActive() { return active; }

    public void start(String lang) {
        this.lang = lang;
        if (active) return;
        h.removeCallbacksAndMessages(null);   // cancel a pending release from a previous stop()
        active = true;
        quiet = 0;
        l.onState(true);
        listen();
    }

    public void stop() {
        if (!active) return;
        active = false;
        h.removeCallbacksAndMessages(null);
        try { if (rec != null) rec.stopListening(); } catch (Exception ignored) { }
        // give the recognizer a moment to deliver the last result, then release it
        h.postDelayed(new Runnable() { @Override public void run() { release(); } }, 1500);
        l.onState(false);
    }

    public void destroy() {
        active = false;
        h.removeCallbacksAndMessages(null);
        release();
    }

    private void release() {
        try { if (rec != null) { rec.setRecognitionListener(null); rec.cancel(); rec.destroy(); } } catch (Exception ignored) { }
        rec = null;
    }

    private void listen() {
        if (!active) return;
        try {
            if (rec == null) {
                rec = SpeechRecognizer.createSpeechRecognizer(ctx);
                rec.setRecognitionListener(this);
            }
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.getPackageName());
            i.putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 2500L);
            i.putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 2500L);
            rec.startListening(i);
        } catch (Exception e) {
            active = false;
            l.onState(false);
            l.onError("Could not start the microphone.");
        }
    }

    private void again(long delay) {
        if (!active) return;
        h.postDelayed(new Runnable() { @Override public void run() { listen(); } }, delay);
    }

    /* ----------------------------------------------------- RecognitionListener */
    @Override public void onReadyForSpeech(Bundle params) { }
    @Override public void onBeginningOfSpeech() { quiet = 0; }
    @Override public void onRmsChanged(float rms) { l.onLevel(Math.max(0f, Math.min(1f, (rms + 2f) / 12f))); }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { }
    @Override public void onEvent(int type, Bundle params) { }

    @Override public void onPartialResults(Bundle b) {
        ArrayList<String> r = b == null ? null : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (r != null && !r.isEmpty()) l.onPartial(r.get(0));
    }

    @Override public void onResults(Bundle b) {
        ArrayList<String> r = b == null ? null : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (r != null && !r.isEmpty() && r.get(0) != null && !r.get(0).trim().isEmpty()) { quiet = 0; l.onFinal(r.get(0).trim()); }
        l.onPartial("");
        again(150);
    }

    @Override public void onError(int error) {
        l.onPartial("");
        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                if (++quiet >= 6) { // ~ a minute of silence
                    stop();
                    l.onError("Stopped listening because it was quiet.");
                } else again(120);
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                release();
                again(700);
                break;
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                fail("Internet is needed for Urdu voice typing. Please check your connection.");
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                fail("Microphone permission is needed.");
                break;
            case SpeechRecognizer.ERROR_AUDIO:
                fail("Microphone is not available right now.");
                break;
            default:
                release();
                again(500);
        }
    }

    private void fail(String msg) {
        active = false;
        h.removeCallbacksAndMessages(null);
        release();
        l.onState(false);
        l.onError(msg);
    }
}
