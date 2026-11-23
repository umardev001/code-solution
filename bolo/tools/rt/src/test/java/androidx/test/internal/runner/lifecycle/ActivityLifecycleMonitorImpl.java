package androidx.test.internal.runner.lifecycle;
import androidx.test.runner.lifecycle.*;
import android.app.Activity;
import java.util.*;
public class ActivityLifecycleMonitorImpl implements ActivityLifecycleMonitor {
  public ActivityLifecycleMonitorImpl() {}
  public ActivityLifecycleMonitorImpl(boolean b) {}
  public void addLifecycleCallback(ActivityLifecycleCallback c) {} public void removeLifecycleCallback(ActivityLifecycleCallback c) {}
  public Stage getLifecycleStageOf(Activity a) { return Stage.RESUMED; }
  public Collection<Activity> getActivitiesInStage(Stage s) { return new ArrayList<Activity>(); }
  public void signalLifecycleChange(Stage s, Activity a) {}
}
