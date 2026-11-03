package com.fitjourney.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

/** Half-hourly background tick: saves steps, and nudges you to drink water. */
public final class Reminders {
    private static final String CHANNEL = "water";
    private Reminders() { }

    public static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(c, 1, new Intent(c, TickReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60000L, AlarmManager.INTERVAL_HALF_HOUR, pi);
    }

    public static void maybeNotify(Context c) {
        Store.init(c);
        Store.Profile p = Store.profile();
        if (!Store.hasProfile() || p.remindHours <= 0) return;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 9 || hour > 21) return;
        Store.Day d = Store.get(Fit.today());
        if (d.water >= p.waterGoal) return;
        long now = System.currentTimeMillis();
        if (now - Store.lastRemind() < p.remindHours * 3600000L - 300000L) return;
        Store.setLastRemind(now);

        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Water reminders", NotificationManager.IMPORTANCE_DEFAULT));
            b = new Notification.Builder(c, CHANNEL);
        } else {
            b = new Notification.Builder(c);
        }
        PendingIntent open = PendingIntent.getActivity(c, 2, new Intent(c, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
        int left = p.waterGoal - d.water;
        b.setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("💧 Time for some water")
                .setContentText(Fit.water(d.water) + " of " + Fit.water(p.waterGoal) + " so far – " + Fit.water(left) + " to go")
                .setContentIntent(open)
                .setAutoCancel(true);
        nm.notify(10, b.build());
    }
}
