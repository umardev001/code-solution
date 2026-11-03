package com.fitjourney.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TickReceiver extends BroadcastReceiver {
    @Override public void onReceive(final Context c, Intent i) {
        final PendingResult pr = goAsync();
        Steps.snapshot(c, new Runnable() {
            @Override public void run() {
                try { Reminders.maybeNotify(c); } finally { pr.finish(); }
            }
        });
    }
}
