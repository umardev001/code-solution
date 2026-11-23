package t;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import com.ironicxsoft.rozplan.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;
import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, manifest = Config.NONE, qualifiers = "w320dp-h620dp-night-xhdpi", fontScale = 1.3f)
public class PlanSmallDarkTest {
    static void shot(Activity a, String name, int scrollY) throws Exception {
        View v = a.getWindow().getDecorView();
        int w = a.getResources().getDisplayMetrics().widthPixels, h = a.getResources().getDisplayMetrics().heightPixels;
        List<View> st = new ArrayList<>(); st.add(v);
        while (!st.isEmpty()) { View x = st.remove(0); if (x instanceof ScrollView) { ((ScrollView) x).scrollTo(0, scrollY); } if (x instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) x).getChildCount(); i++) st.add(((ViewGroup) x).getChildAt(i)); }
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(b));
        new java.io.File("/tmp/claude-0/apk/shots").mkdirs();
        try (FileOutputStream o = new FileOutputStream("/tmp/claude-0/apk/shots/plansd-" + name + ".png")) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
        System.out.println("SHOT " + name);
    }
    static void collect(View v, List<View> out) {
        if (v.isClickable() && v.getVisibility() == View.VISIBLE) out.add(v);
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), out); }
    }
    static void press(Activity a, String digits) {
        for (char ch : digits.toCharArray()) {
            List<View> all = new ArrayList<>(); collect(a.findViewById(android.R.id.content), all);
            for (View v : all) if (v instanceof TextView && ((TextView) v).getText().toString().equals(String.valueOf(ch))) { v.performClick(); break; }
        }
        ShadowLooper.idleMainLooper(3, TimeUnit.SECONDS);
    }

    @Test public void tabs() throws Exception {
        Activity a0 = Robolectric.buildActivity(LoginActivity.class).get();
        Lock.setPin(a0, "2580"); Lock.unlock();
        Store.saveDay(a0, Store.today(), dayWith());
        Store.setWeightToday(a0, 102.5);
        Activity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);
        ViewGroup root = (ViewGroup) a.findViewById(android.R.id.content);
        ViewGroup bar = (ViewGroup) ((ViewGroup) root.getChildAt(0)).getChildAt(2);
        String[] names = {"aaj", "khana", "exercise", "progress"};
        for (int i = 0; i < 4; i++) {
            bar.getChildAt(i).performClick(); ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS);
            shot(a, names[i], 0);
            shot(a, names[i] + "-2", 1500);
            shot(a, names[i] + "-3", 3000);
        }
        // click everything in every tab
        for (int i = 0; i < 4; i++) {
            bar.getChildAt(i).performClick(); ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);
            List<View> all = new ArrayList<>(); collect(root, all);
            int n = 0;
            for (View v : all) { try { v.performClick(); n++; } catch (Throwable e) { System.out.println("CLICK FAIL tab " + i + " " + e); throw e; } bar.getChildAt(i).performClick(); }
            System.out.println("TAB " + i + " clicks " + n);
        }
        System.out.println("TABS OK");
    }

    static Store.Day dayWith() {
        Store.Day d = new Store.Day();
        d.done.add("wake"); d.done.add("str"); d.done.add("nas"); d.habits.add("sug"); d.water = 5; d.meals.put("nas", 0); d.meals.put("lun", 1); d.pct = 40;
        return d;
    }

}
