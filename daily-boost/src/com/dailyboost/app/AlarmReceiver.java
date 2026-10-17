package com.dailyboost.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Store.Reminder r = Store.reminder(c, intent.getIntExtra("id", -1));
        if (r == null || !r.enabled) return;
        if (!Store.inQuietHours(c)) Notifier.show(c, r.id, r.title, r.message, r.water, r);
        Scheduler.schedule(c, r);   // chain the next one (quiet hours skip the notification)
    }
}
