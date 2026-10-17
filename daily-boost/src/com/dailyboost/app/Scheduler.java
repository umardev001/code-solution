package com.dailyboost.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;

/** Chains one exact alarm per reminder; each alarm schedules the following one. */
public final class Scheduler {
    private Scheduler() { }

    public static long nextTrigger(Store.Reminder r, long from) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(from);
        for (int day = 0; day < 2; day++) {
            Calendar c = (Calendar) now.clone();
            c.add(Calendar.DAY_OF_YEAR, day);
            if (r.intervalMin <= 0) {
                long t = at(c, r.startMin);
                if (t > from) return t;
            } else {
                for (int m = r.startMin; m <= r.endMin; m += r.intervalMin) {
                    long t = at(c, m);
                    if (t > from) return t;
                }
            }
        }
        return from + 24L * 60 * 60 * 1000;
    }

    private static long at(Calendar day, int minutes) {
        Calendar c = (Calendar) day.clone();
        c.set(Calendar.HOUR_OF_DAY, minutes / 60);
        c.set(Calendar.MINUTE, minutes % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static PendingIntent pi(Context c, int id, int flags) {
        Intent i = new Intent(c, AlarmReceiver.class).putExtra("id", id);
        return PendingIntent.getBroadcast(c, id, i, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void schedule(Context c, Store.Reminder r) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent p = pi(c, r.id, PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(p);
        if (!r.enabled) { Notifier.updateCountdown(c); return; }
        long when = nextTrigger(r, System.currentTimeMillis());
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, p);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, p);
        }
        Notifier.updateCountdown(c);
    }

    public static void cancel(Context c, int id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi(c, id, PendingIntent.FLAG_UPDATE_CURRENT));
        Notifier.updateCountdown(c);
    }

    public static void scheduleAll(Context c) {
        for (Store.Reminder r : Store.reminders(c)) schedule(c, r);
    }

    /** The soonest enabled reminder, or null. */
    public static Store.Reminder nextUp(Context c) {
        Store.Reminder best = null;
        long bestT = Long.MAX_VALUE;
        for (Store.Reminder r : Store.reminders(c)) {
            if (!r.enabled) continue;
            long t = nextTrigger(r, System.currentTimeMillis());
            if (t < bestT) { bestT = t; best = r; }
        }
        return best;
    }
}
