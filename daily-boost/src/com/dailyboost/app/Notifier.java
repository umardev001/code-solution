package com.dailyboost.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import java.util.HashSet;
import java.util.Set;

public final class Notifier {
    private static final String PREFIX = "rem_";
    private Notifier() { }

    /** Effective sound for a reminder: its own tone, else the app tone, else the system default. */
    public static Uri soundFor(Context c, Store.Reminder r) {
        String s = r != null && r.tone != null ? r.tone : "";
        if (s.isEmpty()) s = Store.tone(c);
        if (s.isEmpty()) {
            return RingtoneManager.getDefaultUri(Store.alarmMode(c) ? RingtoneManager.TYPE_ALARM : RingtoneManager.TYPE_NOTIFICATION);
        }
        return Uri.parse(s);
    }

    private static String channelId(Uri sound, boolean alarm, boolean vibrate) {
        return PREFIX + Integer.toHexString((sound + "|" + alarm + "|" + vibrate).hashCode());
    }

    private static void ensureChannel(Context c, Uri sound) {
        if (Build.VERSION.SDK_INT < 26) return;
        boolean alarm = Store.alarmMode(c), vib = Store.vibrate(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        String id = channelId(sound, alarm, vib);
        if (nm.getNotificationChannel(id) != null) return;
        NotificationChannel ch = new NotificationChannel(id, alarm ? "Reminders (alarm sound)" : "Reminders", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Water, walk, project and other routine reminders");
        ch.enableVibration(vib);
        if (vib) ch.setVibrationPattern(new long[]{0, 300, 150, 300});
        ch.setLightColor(Color.GREEN);
        ch.setSound(sound, new AudioAttributes.Builder()
                .setUsage(alarm ? AudioAttributes.USAGE_ALARM : AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        nm.createNotificationChannel(ch);
    }

    /** Create the channels that are needed now and delete stale ones (called after settings change). */
    public static void sync(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        Set<String> keep = new HashSet<>();
        boolean alarm = Store.alarmMode(c), vib = Store.vibrate(c);
        Uri base = soundFor(c, null);
        ensureChannel(c, base);
        keep.add(channelId(base, alarm, vib));
        for (Store.Reminder r : Store.reminders(c)) {
            Uri u = soundFor(c, r);
            ensureChannel(c, u);
            keep.add(channelId(u, alarm, vib));
        }
        for (NotificationChannel ch : nm.getNotificationChannels()) {
            if (ch.getId().startsWith(PREFIX) && !keep.contains(ch.getId())) nm.deleteNotificationChannel(ch.getId());
        }
    }

    public static void createChannel(Context c) { sync(c); }

    public static void show(Context c, int id, String title, String text, boolean water) {
        show(c, id, title, text, water, Store.reminder(c, id));
    }

    public static void show(Context c, int id, String title, String text, boolean water, Store.Reminder r) {
        Uri sound = soundFor(c, r);
        ensureChannel(c, sound);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, channelId(sound, Store.alarmMode(c), Store.vibrate(c))) : new Notification.Builder(c);
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        b.setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER);
        if (Build.VERSION.SDK_INT < 26) {
            b.setPriority(Notification.PRIORITY_HIGH);
            if (Store.alarmMode(c)) b.setSound(sound, AudioManager.STREAM_ALARM); else b.setSound(sound);
            if (Store.vibrate(c)) b.setVibrate(new long[]{0, 300, 150, 300});
        }
        if (water) b.addAction(action(c, id, "water", "+1 glass"));
        b.addAction(action(c, id, "snooze", "Snooze " + Store.snoozeMin(c) + " min"));
        nm.notify(id, b.build());
    }

    private static Notification.Action action(Context c, int id, String what, String label) {
        Intent i = new Intent(c, ActionReceiver.class).putExtra("id", id).putExtra("what", what);
        PendingIntent p = PendingIntent.getBroadcast(c, id * 10 + (what.equals("water") ? 1 : 2), i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Action.Builder(0, label, p).build();
    }

    /** Human-readable name of a tone uri (empty uri = default). */
    public static String toneName(Context c, String uri) {
        try {
            Uri u = uri == null || uri.isEmpty() ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) : Uri.parse(uri);
            Ringtone r = RingtoneManager.getRingtone(c, u);
            if (r != null) {
                String t = r.getTitle(c);
                if (t != null && !t.isEmpty()) return uri == null || uri.isEmpty() ? "Default (" + t + ")" : t;
            }
        } catch (Exception ignored) { }
        return uri == null || uri.isEmpty() ? "Default" : "Custom tone";
    }

    private static Ringtone preview;

    public static void previewTone(Context c, String uri) {
        try {
            if (preview != null) preview.stop();
            Uri u = uri == null || uri.isEmpty() ? soundFor(c, null) : Uri.parse(uri);
            preview = RingtoneManager.getRingtone(c, u);
            if (preview == null) return;
            if (Build.VERSION.SDK_INT >= 21) {
                preview.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(Store.alarmMode(c) ? AudioAttributes.USAGE_ALARM : AudioAttributes.USAGE_NOTIFICATION).build());
            }
            preview.play();
        } catch (Exception ignored) { }
    }

    public static void stopPreview() {
        try { if (preview != null) preview.stop(); } catch (Exception ignored) { }
    }
}
