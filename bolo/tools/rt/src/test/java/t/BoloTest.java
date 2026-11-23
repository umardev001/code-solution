package t;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.ironicxsoft.bolo.*;
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
@Config(sdk = 34, manifest = Config.NONE, qualifiers = "w360dp-h780dp-xxhdpi")
public class BoloTest {
    static void shot(Activity a, String name) throws Exception {
        View v = a.getWindow().getDecorView();
        int w = a.getResources().getDisplayMetrics().widthPixels, h = a.getResources().getDisplayMetrics().heightPixels;
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(b));
        new java.io.File("/tmp/claude-0/apk/shots").mkdirs();
        try (FileOutputStream o = new FileOutputStream("/tmp/claude-0/apk/shots/bolo-" + name + ".png")) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
        System.out.println("SHOT " + name);
    }
    static void collect(View v, List<View> out) {
        if (v.isClickable() && v.getVisibility() == View.VISIBLE) out.add(v);
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), out); }
    }
    static View byText(View root, String t) {
        List<View> st = new ArrayList<>(); st.add(root);
        while (!st.isEmpty()) { View x = st.remove(0); if (x instanceof TextView && ((TextView) x).getText().toString().startsWith(t)) return x; if (x instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) x).getChildCount(); i++) st.add(((ViewGroup) x).getChildAt(i)); }
        return null;
    }

    @Test public void commands() {
        String[][] cases = {
            {"میں کل آپ کو رپورٹ بھیج دوں گا اسے انگلش میں ٹرانسلیٹ کر دو", "میں کل آپ کو رپورٹ بھیج دوں گا", "true"},
            {"mein kal report bhej dunga isay english mein translate kar do", "mein kal report bhej dunga", "true"},
            {"please send the invoice today translate this to english", "please send the invoice today", "true"},
            {"اسے انگلش میں ترجمہ کر دو", "", "true"},
            {"مجھے انگلش سیکھنی ہے", "مجھے انگلش سیکھنی ہے", "false"},
            {"آپ کا کام اچھا ہے", "آپ کا کام اچھا ہے", "false"},
        };
        for (String[] c : cases) { Cmd r = Cmd.parse(c[0]); System.out.println("CMD [" + r.text + "] " + r.translate + (r.text.equals(c[1]) && String.valueOf(r.translate).equals(c[2]) ? "  OK" : "  <<< MISMATCH expected [" + c[1] + "] " + c[2])); }
        System.out.println("TIDY " + Translator.tidy("hello  , i will send the report tomorrow . please confirm"));
    }

    @Test public void ui() throws Exception {
        Activity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);
        shot(a, "home-empty");
        android.widget.EditText et = null;
        List<View> st = new ArrayList<>(); st.add(a.getWindow().getDecorView());
        while (!st.isEmpty()) { View x = st.remove(0); if (x instanceof android.widget.EditText) { et = (android.widget.EditText) x; break; } if (x instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) x).getChildCount(); i++) st.add(((ViewGroup) x).getChildAt(i)); }
        et.setText("میں کل صبح آپ کو رپورٹ بھیج دوں گا۔ براہ کرم تصدیق کر دیں کہ آپ کو ملی یا نہیں۔");
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);
        shot(a, "home-text");
        { java.lang.reflect.Method m = MainActivity.class.getDeclaredMethod("showResult", String.class, String.class); m.setAccessible(true);
          m.invoke(a, "I will send you the report tomorrow morning. Kindly confirm whether you have received it.", "AI");
          ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);
          android.view.ViewGroup rt = (android.view.ViewGroup) a.findViewById(android.R.id.content);
          java.util.List<View> q = new ArrayList<>(); q.add(rt); android.widget.ScrollView sv = null;
          while (!q.isEmpty()) { View x = q.remove(0); if (x instanceof android.widget.ScrollView) { sv = (android.widget.ScrollView) x; break; } if (x instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) x).getChildCount(); i++) q.add(((ViewGroup) x).getChildAt(i)); }
          sv.scrollTo(0, 2000); shot(a, "home-result"); sv.scrollTo(0, 0); }
        // history + settings
        Store.addHistory(a, "میں کل صبح آپ کو رپورٹ بھیج دوں گا", "I will send you the report tomorrow morning.");
        Store.addHistory(a, "قیمت دس ہزار روپے ہے", "The price is 10,000 rupees.");
        View hist = null; List<View> all = new ArrayList<>(); collect(a.getWindow().getDecorView(), all);
        for (View v : all) if (v instanceof TextView && "🕘".equals(String.valueOf(((TextView) v).getText()))) hist = v;
        hist.performClick(); ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS); shot(a, "history");
        a.onBackPressed(); ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);
        all.clear(); collect(a.getWindow().getDecorView(), all);
        View set = null; for (View v : all) if (v instanceof TextView && "⚙️".equals(String.valueOf(((TextView) v).getText()))) set = v;
        set.performClick(); ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS); shot(a, "settings");
        // click everything on settings
        all.clear(); collect(a.getWindow().getDecorView(), all);
        for (View v : all) { try { v.performClick(); } catch (Throwable e) { System.out.println("CLICK FAIL " + e); throw e; } }
        System.out.println("UI OK");
    }
}
