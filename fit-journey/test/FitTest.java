import com.fitjourney.app.Fit;

public class FitTest {
    static int fails = 0;
    static void eq(Object a, Object b) { if (!a.equals(b)) { fails++; System.out.println("FAIL: " + a + " != " + b); } }
    public static void main(String[] x) {
        eq(Fit.bmiLabel(Fit.bmi(70, 175)), "Healthy");
        eq(Fit.kg(Fit.bmi(70, 175)), "22.9");
        eq(Fit.bmiLabel(Fit.bmi(95, 170)), "Obese");
        eq(Fit.bmiLabel(Fit.bmi(45, 170)), "Underweight");
        eq(Fit.kg(Fit.progress(90, 85, 80) * 100), "50.0");
        eq(Fit.progress(90, 95, 80), 0.0);          // moved the wrong way -> 0
        eq(Fit.progress(90, 70, 80), 1.0);          // overshoot -> clamp
        eq(Fit.progress(60, 63, 66), 0.5);          // weight-gain goal
        eq(Fit.num(1234567), "1,234,567");
        eq(Fit.num(999), "999");
        eq(Fit.num(1000), "1,000");
        eq(Fit.water(250), "250 ml");
        eq(Fit.water(2500), "2.5 L");
        eq(Fit.water(1250), "1.25 L");
        eq(Fit.water(3000), "3 L");
        eq(Fit.parse("72,5"), 72.5);
        eq(Fit.parse("abc"), -1.0);
        eq(Fit.parse(""), -1.0);
        eq(Fit.parse("-4"), -1.0);
        // slope: lose 0.1 kg / day
        double[] xs = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, ys = new double[10];
        for (int i = 0; i < 10; i++) ys[i] = 90 - 0.1 * i;
        eq(Fit.kg(Fit.slope(xs, ys) * 100), "-10.0");
        eq(Fit.etaDays(89.1, 80, Fit.slope(xs, ys)), 91);
        eq(Fit.etaDays(89.1, 95, Fit.slope(xs, ys)), -1);   // trending away
        eq(Fit.etaDays(80, 80, -0.1), 0);
        eq(Double.isNaN(Fit.slope(new double[]{1}, new double[]{2})), true);
        eq(Double.isNaN(Fit.slope(new double[]{1, 1.5}, new double[]{2, 3})), true);  // spread < 2 days
        eq(Fit.km(10000, 170), 7.055);
        eq(Fit.kcalWalk(10000, 80), 400);
        eq(Fit.addDays("2026-12-31", 1), "2027-01-01");
        eq(Fit.addDays("2026-03-01", -1), "2026-02-28");
        eq(Fit.dayNo("2026-11-04") - Fit.dayNo("2026-11-03"), 1L);
        eq(Fit.shortDate("2026-11-03"), "3 Nov");
        eq(Fit.dow("2026-11-03"), "Tue");
        eq(Fit.mondayIndex("2026-11-02"), 0);
        eq(Fit.mondayIndex("2026-11-08"), 6);
        eq(Fit.key(Fit.ts("2026-02-09")), "2026-02-09");
        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
        System.exit(fails);
    }
}
