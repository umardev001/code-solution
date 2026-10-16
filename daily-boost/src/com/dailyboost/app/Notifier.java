package com.dailyboost.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;

public final class Notifier {
    public static final String CHANNEL = "reminders";
    private Notifier() { }

    public static void createChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Daily reminders", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Water, walk, project and other routine reminders");
        ch.enableVibration(true);
        ch.setLightColor(Color.GREEN);
        nm.createNotificationChannel(ch);
    }

    public static void show(Context c, int id, String title, String text, boolean water) {
        createChannel(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        b.setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
        if (water) b.addAction(action(c, id, "water", "+1 glass"));
        b.addAction(action(c, id, "snooze", "Snooze 10 min"));
        nm.notify(id, b.build());
    }

    private static Notification.Action action(Context c, int id, String what, String label) {
        Intent i = new Intent(c, ActionReceiver.class).putExtra("id", id).putExtra("what", what);
        PendingIntent p = PendingIntent.getBroadcast(c, id * 10 + (what.equals("water") ? 1 : 2), i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Action.Builder(0, label, p).build();
    }
}
