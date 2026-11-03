package com.fitjourney.app;

public class LogicTest {
    static int fails = 0;
    static void ck(boolean b, String m) { if (!b) { fails++; System.out.println("FAIL " + m); } else System.out.println("ok   " + m); }
    static String mem;
    public static void main(String[] a) {
        Store.use(new Store.Backend() { public String read() { return mem; } public void write(String s) { mem = s; } });
        ck(!Store.hasProfile(), "no profile at first");
        Store.Profile p = new Store.Profile(); p.name = "Umar"; p.heightCm = 175; p.startKg = 90; p.goalKg = 75; p.stepGoal = 8000; p.waterGoal = 2500; p.workoutGoal = 30;
        Store.setProfile(p);
        String t = Fit.today();
        // step counter: first reading only sets the baseline
        Steps.onReading(null, 10000);
        ck(Store.get(t).steps == 0, "first reading = baseline");
        Steps.onReading(null, 10500);
        Steps.onReading(null, 11000);
        ck(Store.get(t).steps == 1000, "deltas added: " + Store.get(t).steps);
        Steps.onReading(null, 300);                       // phone rebooted
        ck(Store.get(t).steps == 1300, "reboot handled: " + Store.get(t).steps);
        Steps.onReading(null, 300);
        ck(Store.get(t).steps == 1300, "no change, no steps");
        // goals & streak
        for (int i = 1; i <= 4; i++) { Store.Day d = Store.mut(Fit.addDays(t, -i)); d.steps = 9000; d.water = 2600; }   // 4 good days before today
        Store.save();
        ck(Store.streak() == 4, "streak skips unfinished today: " + Store.streak());
        Store.Day td = Store.mut(t); td.steps = 8100; td.water = 2500; Store.save();
        ck(Store.good(Store.get(t)) && Store.streak() == 5, "today counts when done: " + Store.streak());
        Store.mut(Fit.addDays(t, -10)).steps = 9000; Store.mut(Fit.addDays(t, -10)).water = 3000; Store.save();
        ck(Store.bestStreak() == 5, "best streak " + Store.bestStreak());
        ck(Store.hits(Store.get(Fit.addDays(t, -2))) == 2, "hits counts goals");
        // weight
        Store.mut(Fit.addDays(t, -6)).weight = 90; Store.mut(Fit.addDays(t, -3)).weight = 89; Store.mut(t).weight = 88.2; Store.save();
        ck(Store.weighIns().size() == 3 && Store.currentKg() == 88.2, "weigh-ins & current weight");
        // persistence round trip
        savedCopy = mem;
        Store.use(new Store.Backend() { public String read() { return saved(); } public void write(String s) { mem = s; } });
        ck(Store.hasProfile() && Store.profile().name.equals("Umar") && Store.profile().heightCm == 175, "profile reload");
        ck(Store.get(t).steps == 8100 && Store.get(t).weight == 88.2, "days reload");
        ck(Store.stepLast() == 300, "step baseline persisted");
        ck(!Store.get(Fit.addDays(t, -20)).equals(null) && Store.get(Fit.addDays(t, -20)).steps == 0, "blank day for missing key");
        Store.wipe();
        ck(!Store.hasProfile() && Store.all().isEmpty(), "wipe");
        System.out.println(fails == 0 ? "ALL LOGIC OK" : fails + " FAILED");
        System.exit(fails);
    }
    static String saved() { return savedCopy; }
    static String savedCopy;
    static { }
}
