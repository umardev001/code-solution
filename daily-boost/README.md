# Daily Boost (Android app) – v1.2

Daily routine helper: hourly water / 5-minute walk reminders, project & task list, "search later" items, water counter.

- Animated PIN login (create a 4-digit PIN on first launch). "Designed and Developed by IronicXsoft".
- Settings: reminder tone (per app and per reminder), alarm mode, vibration, snooze length, quiet hours, PIN lock/auto-lock, water goal, reset reminders.
- Install: copy `DailyBoost.apk` to the phone, allow "Install unknown apps", open, allow notifications.
- Android 6.0+ (API 23). Signed with the included debug key (`debug.keystore`, password `android`).
- Rebuild without the Android SDK: `python3 tools/build_apk.py` (needs JDK 17+, `android-all.jar` and `dx.jar` in `DB_LIBS`).
