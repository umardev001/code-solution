# Fit Journey (Android app) – v1.1 (light theme)

Weight-loss companion: daily weight, steps, water, sleep, workout and calories – with beautiful charts.

- **Today**: three animated activity rings (steps · water · workout), a wavy water bottle with +250 / +500 ml buttons, live step count, weight / sleep / workout / calorie cards, streak 🔥 and a coaching line.
- **Progress**: current weight, progress to goal, smooth touch-to-inspect weight curve (7D/30D/90D/All), weekly pace, estimated finish date, BMI scale + healthy range, weigh-in history.
- **History**: 7-day step and water bar charts with goal line, 5-week consistency heat-map, streaks, daily log (tap to edit any day).
- **Me**: profile, daily goals, water reminders (every 1–4 h, 9 am–9 pm), CSV share, reset.
- **Steps are automatic**: uses the phone's hardware step counter (no internet, no account). A half-hourly background tick saves them even when the app is closed. Phones without the sensor can type steps in.
- All data stays on the phone (no INTERNET permission). Permissions: activity recognition (steps) and boot (re-arm reminders).

## Install
Copy `FitJourney.apk` to the phone → allow "Install unknown apps" → open. Android 6.0+.
Signed with `fit.keystore` (alias `fitjourney`, password `android`) – keep it to be able to update without uninstalling.

## Rebuild (no Android SDK)
`python3 tools/build_apk.py` – needs JDK 17+, and in `FJ_LIBS` (default `/tmp/claude-0/apk/dl`): `android-all.jar`
(org.robolectric:android-all:9-robolectric-4913185-2) and `dx.jar` (com.jakewharton.android.repackaged:dalvik-dx:11.0.0_r3).
Logic tests: `test/FitTest.java` (pure maths), `test/LogicTest.java` (store / steps / streaks).
