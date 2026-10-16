package com.dailyboost.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SnoozeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Store.Reminder r = Store.reminder(c, intent.getIntExtra("id", -1));
        if (r != null) Notifier.show(c, r.id, r.title, r.message, r.water);
    }
}
