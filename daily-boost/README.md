# Daily Boost (Android app)

Daily routine helper: hourly water / 5-minute walk reminders, project & task list, "search later" items, water counter.

- Install: copy `DailyBoost.apk` to the phone, open it, allow "Install unknown apps", then allow notifications.
- Android 6.0+ (API 23). Sideload build signed with the included debug key (`debug.keystore`, password `android`).
- Rebuild without the Android SDK: `python3 tools/build_apk.py` (needs JDK 17+, `android-all.jar` and `dx.jar` in `DB_LIBS`).
