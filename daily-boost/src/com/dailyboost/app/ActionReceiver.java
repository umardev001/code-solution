package com.dailyboost.app;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/** Handles the "+1 glass" and "Snooze" buttons on notifications. */
public class ActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        int id = intent.getIntExtra("id", -1);
        String what = intent.getStringExtra("what");
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(id);
        if ("water".equals(what)) {
            int n = Store.water(c) + 1;
            Store.setWater(c, n);
            Toast.makeText(c, "Water: " + n + " / " + Store.waterGoal(c) + " glasses", Toast.LENGTH_SHORT).show();
        } else if ("snooze".equals(what)) {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(c, SnoozeReceiver.class).putExtra("id", id);
            PendingIntent p = PendingIntent.getBroadcast(c, id + 500000, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            long when = System.currentTimeMillis() + 10 * 60 * 1000L;
            try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, p); }
            catch (SecurityException e) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, p); }
            Toast.makeText(c, "Snoozed for 10 minutes", Toast.LENGTH_SHORT).show();
        }
    }
}
