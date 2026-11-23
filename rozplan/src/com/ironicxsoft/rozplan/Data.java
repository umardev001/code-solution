package com.ironicxsoft.rozplan;

/** Static plan content (routine, habits, meals, exercises). Offsets are minutes after wake-up time. */
public final class Data {
    private Data() { }

    public static final class Task {
        public final String id; public final int off; public final String title; public final String desc;
        Task(String id, int off, String title, String desc) { this.id = id; this.off = off; this.title = title; this.desc = desc; }
    }

    public static Task[] routine(boolean workoutDay) {
        return new Task[]{
                new Task("wake", 0, "Uth jao + 1 glass paani", "Roz isi waqt, weekend par bhi. Yehi neend fix karega."),
                new Task("str", 15, "5–10 min stretching", "Ya ghar/chhat par halki walk."),
                new Task("nas", 45, "Nashta", "Khana tab se option chuno. Chai bina cheeni."),
                new Task("wrk", 90, "Kaam shuru", "Har 45–60 min baad 3–5 min utho, paani piyo."),
                new Task("lun", 330, "Dopahar ka khana", "1 roti, salan kam oil, salad, raita."),
                new Task("wk1", 360, "15 min walk", "Khane ke baad. Call chalte hue bhi le sakte ho."),
                new Task("caf", 540, "Aakhri chai/coffee", "Is ke baad caffeine nahi, raat ko neend achi aayegi."),
                new Task("snk", 570, "Shaam ka snack", "Chana chaat, fruit ya bhunay chanay."),
                new Task("ex", 660, workoutDay ? "Workout (20–30 min)" : "30 min tez walk",
                        workoutDay ? "Exercise tab kholo aur 5 exercises ke 3 set." : "Aaj workout nahi, bas tez walk. Steps 6,000–10,000."),
                new Task("din", 750, "Raat ka khana", "Sonay se kam az kam 3 ghantay pehle."),
                new Task("wk2", 810, "10 min halki walk", "Khana hazam hoga, craving kam hogi."),
                new Task("scr", 900, "Screen band", "Phone door rakho, lights dim karo."),
                new Task("slp", 960, "So jao", "7–8 ghantay ki neend. Kal phir isi waqt uthna.")};
    }

    public static final String[][] HABITS = {{"sug", "Cheeni nahi"}, {"cd", "Cold drink nahi"}, {"oil", "Oil kam"}, {"stp", "6,000+ steps"}};

    public static final String[] DAYS = {"Itwar", "Peer", "Mangal", "Budh", "Jumeraat", "Juma", "Hafta"};

    public static final class Meal {
        public final String key, name; public final Object[][] options;
        Meal(String key, String name, Object[][] o) { this.key = key; this.name = name; this.options = o; }
    }

    public static final Meal[] MEALS = {
            new Meal("nas", "Nashta", new Object[][]{
                    {"2 ublay anday + 1 roti + chai (bina cheeni)", 315, 18}, {"3 anday ka omelette (1 tsp oil) + 1 brown bread slice", 340, 21},
                    {"Oats (40g) doodh mein + 1 kela", 370, 13}, {"1 katori dahi + 2 ublay anday", 305, 21}}),
            new Meal("lun", "Dopahar", new Object[][]{
                    {"1 roti + chicken salan (1 bara piece) + salad + raita", 560, 40}, {"1 roti + daal (1 katori) + salad + raita", 450, 22},
                    {"Chicken pulao (chhoti plate) + raita", 560, 30}, {"1 roti + mix sabzi + 2 anday", 425, 20}}),
            new Meal("snk", "Shaam", new Object[][]{
                    {"Chana chaat (1 katori)", 220, 10}, {"1 fruit + 10 badaam", 150, 3}, {"Bhunay chanay (1 mutthi) + chai", 160, 7}, {"1 glass doodh", 150, 8}}),
            new Meal("din", "Raat", new Object[][]{
                    {"Tawa/grilled chicken (200g) + salad", 360, 55}, {"Chicken karahi (kam oil) + 1 roti", 500, 39},
                    {"Daal + sabzi + 1 roti", 490, 18}, {"Tawa machli (150g) + salad", 330, 35}})};

    public static final String[] AVOID = {"Cold drinks / juice", "Cheeni wali chai", "Paratha, puri", "Samosay, pakoray", "Bakery, biscuits", "Mithai", "Raat ko snacking", "Double roti / naan"};

    public static final class Exercise {
        public final String name, reps, query, warn; public final String a, b; public final String[] steps;
        Exercise(String name, String reps, String query, String a, String b, String[] steps, String warn) {
            this.name = name; this.reps = reps; this.query = query; this.a = a; this.b = b; this.steps = steps; this.warn = warn;
        }
    }

    /** Poses: joint:x,y;... in a 160x125 box starting at (20,10). Hd head, S shoulder, Hp hip, K knee, F foot, E elbow, W wrist (1 = near side, 2 = far side). */
    public static final Exercise[] EXERCISES = {
            new Exercise("Squats", "3 set × 10–12", "bodyweight squat proper form beginners",
                    "Hd:100,24;S:100,40;Hp:100,74;K1:101,100;F1:100,124;E1:114,46;W1:130,46",
                    "Hd:90,48;S:90,62;Hp:76,92;K1:106,94;F1:100,124;E1:110,62;W1:128,60",
                    new String[]{"Pair kandhon jitne khol kar khade ho.", "Kamar seedhi, aisay neeche baitho jaise kursi par.", "Ghutnay panjon ki seedh mein, aerian zameen par.", "Wapas upar aao aur hips ko tight karo."},
                    "Ghutnay andar ki taraf na mudein."),
            new Exercise("Knee pushups", "3 set × 8–10", "knee push up proper form beginners",
                    "Hd:154,84;S:140,92;Hp:95,108;K1:68,122;F1:44,108;E1:140,108;W1:140,124",
                    "Hd:156,106;S:140,111;Hp:95,114;K1:68,122;F1:44,108;E1:124,108;W1:140,124",
                    new String[]{"Ghutnay zameen par, haath kandhon se thora khule.", "Sar se ghutnon tak jism seedhi line mein.", "Chhati zameen ke qareeb le jao.", "Haathon se dhakel kar wapas upar."},
                    "Mushkil lagein to diwar par wall pushups karo."),
            new Exercise("Glute bridge", "3 set × 12–15", "glute bridge exercise beginners",
                    "Hd:38,114;S:56,119;Hp:100,120;K1:124,94;F1:140,124;E1:76,123;W1:96,123",
                    "Hd:38,114;S:56,119;Hp:100,96;K1:130,88;F1:140,124;E1:76,123;W1:96,123",
                    new String[]{"Kamar ke bal leto, ghutnay moro, pair zameen par.", "Aerian daba kar hips upar uthao.", "Upar 2 second ruko, hips tight.", "Aahista neeche aao."},
                    "Kamar ko zyada mat moro, hips se uthao."),
            new Exercise("Reverse lunges", "3 set × 8 har taang", "bodyweight reverse lunge beginners",
                    "Hd:100,24;S:100,40;Hp:100,74;K1:101,100;F1:100,124;K2:100,100;F2:100,124;E1:104,58;W1:106,74",
                    "Hd:99,42;S:99,58;Hp:98,92;K1:120,96;F1:120,124;K2:84,118;F2:60,124;E1:103,74;W1:106,90",
                    new String[]{"Seedhe khade ho, chaho to diwar pakar lo.", "Ek pair peeche le jao aur dono ghutnay moro.", "Peechay wala ghutna zameen ke qareeb.", "Agle pair se dhakel kar wapas khade ho."},
                    "Agla ghutna panjon se aage na jaye."),
            new Exercise("Plank", "3 set × 20–30 second", "forearm plank proper form beginners",
                    "Hd:154,90;S:140,98;Hp:92,108;K1:68,115;F1:44,122;E1:140,124;W1:162,124",
                    "Hd:154,90;S:140,98;Hp:92,104;K1:68,114;F1:44,122;E1:140,124;W1:162,124",
                    new String[]{"Kohniyan kandhon ke neeche, pair peeche.", "Sar se aerion tak seedhi line.", "Pait andar kheencho, saans lete raho.", "Upar 30s plank timer chalao."},
                    "Hips na latkein aur na zyada upar hon.")};
}
