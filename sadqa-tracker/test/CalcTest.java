import com.sadqa.tracker.Calc;

public class CalcTest {
    static int fails = 0;
    static void eq(Object a, Object b) { if (!a.equals(b)) { fails++; System.out.println("FAIL: " + a + " != " + b); } }
    public static void main(String[] x) {
        eq(Calc.money(0), "Rs 0");
        eq(Calc.money(999), "Rs 999");
        eq(Calc.money(1000), "Rs 1,000");
        eq(Calc.money(100000), "Rs 1,00,000");
        eq(Calc.money(1234567), "Rs 12,34,567");
        eq(Calc.money(123456789), "Rs 12,34,56,789");
        eq(Calc.money(-5000), "-Rs 5,000");
        eq(Calc.due(100000, 5000), 5000L);
        eq(Calc.due(250000, 5000), 12500L);
        eq(Calc.due(1, 5000), 0L);
        eq(Calc.due(10, 5000), 1L);           // 0.5 rounds up
        eq(Calc.due(999999999999L, 100000), 999999999999L);
        eq(Calc.parseAmount("1,00,000"), 100000L);
        eq(Calc.parseAmount(""), -1L);
        eq(Calc.parseAmount("12a"), -1L);
        eq(Calc.parseAmount("9999999999999"), -1L);
        eq(Calc.weakPin("111111"), true);
        eq(Calc.weakPin("123456"), true);
        eq(Calc.weakPin("654321"), true);
        eq(Calc.weakPin("121212"), true);
        eq(Calc.weakPin("483920"), false);
        eq(Calc.lockoutMs(4), 0L);
        eq(Calc.lockoutMs(5), 30000L);
        eq(Calc.lockoutMs(6), 60000L);
        eq(Calc.lockoutMs(30), 3600000L);
        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
        System.exit(fails);
    }
}
