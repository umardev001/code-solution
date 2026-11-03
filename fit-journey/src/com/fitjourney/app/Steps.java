package com.fitjourney.app;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;

/**
 * Counts steps with the phone's hardware step counter (no permission needed on Android 9 and
 * below; granted automatically for this app on newer versions). The sensor value is "steps since
 * boot", so we keep the last value and add only the difference to today's record.
 */
public final class Steps {
    private Steps() { }

    public static Sensor sensor(Context c) {
        SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        return sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
    }

    public static boolean available(Context c) { return sensor(c) != null; }

    /** Adds the steps since the previous reading to today. Handles reboots (counter restarts at 0). */
    public static synchronized void onReading(Context c, long total) {
        Store.init(c);
        long last = Store.stepLast();
        if (last >= 0) {
            long d = total >= last ? total - last : total;
            if (d > 0 && d < 200000) Store.mut(Fit.today()).steps += (int) d;
        }
        Store.setStepLast(total);
        Store.save();
    }

    /** One quick reading (used by the background tick). Calls done once, always. */
    public static void snapshot(final Context c, final Runnable done) {
        final SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        final Sensor s = sensor(c);
        if (sm == null || s == null) { done.run(); return; }
        final Handler h = new Handler();
        final boolean[] finished = {false};
        final SensorEventListener l = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent e) {
                if (finished[0]) return;
                finished[0] = true;
                sm.unregisterListener(this);
                onReading(c, (long) e.values[0]);
                done.run();
            }
            @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
        };
        sm.registerListener(l, s, SensorManager.SENSOR_DELAY_NORMAL, h);
        h.postDelayed(new Runnable() {
            @Override public void run() {
                if (finished[0]) return;
                finished[0] = true;
                sm.unregisterListener(l);
                done.run();
            }
        }, 5000);
    }
}
