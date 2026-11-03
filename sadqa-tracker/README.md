# Sadqa Tracker (Android app) – v1.1 (premium dark redesign)

Freelancing revenue ↔ sadqa tracker. Default: **Rs 5,000 sadqa for every Rs 1,00,000** earned (editable in ⋮ menu).

- Design: night-sky login (twinkling stars, turning Islamic star pattern, crescent, glass keypad, golden burst on unlock); dashboard with gauge ring, 6-month chart, month pills and floating action buttons.
- Add revenue (amount, date, client/note) → sadqa due is calculated automatically.
- Give sadqa (amount, date, to whom) → it is subtracted from the pending total; every entry stays on record (edit / delete).
- Dashboard: all-time pending (or "advance" if you gave extra), progress bar, per-month Revenue / Due / Given / Pending, month-by-month history.
- Amounts use Pakistani grouping (Rs 12,34,567). Each revenue entry remembers the rate it was added with.

## Security
- All data is AES-256-GCM encrypted with a key held in the Android Keystore; the file never exists in plain text.
- 6-digit PIN (weak ones like 123456 rejected), stored only as salted PBKDF2 hash *inside* the encrypted data.
- Wrong-PIN lock-out: 5 tries, then 30 s, 1 min, 2 min … up to 1 hour (survives app restarts).
- No permissions at all (no INTERNET). Backups disabled. Screenshots / recent-apps preview blocked (FLAG_SECURE).
- Auto-lock 20 s after leaving the app; Back from the dashboard locks immediately.
- Forgotten PIN cannot be recovered: "Forgot PIN?" erases everything (must type DELETE).
- Uninstalling the app or erasing the phone deletes the records – keep your own note of the totals now and then.

## Install
Copy `SadqaTracker.apk` to the phone → allow "Install unknown apps" → open. Android 6.0+ (API 23).
Signed with `sadqa.keystore` (alias `sadqa`, password `android`). **Keep this keystore**: future versions must be signed with the same key to update without uninstalling (which would erase data).

## Rebuild (no Android SDK needed)
`python3 tools/build_apk.py` – needs JDK 17+, and in `ST_LIBS` (default `/tmp/claude-0/apk/dl`): `android-all.jar`
(org.robolectric:android-all:9-robolectric-4913185-2) and `dx.jar` (com.jakewharton.android.repackaged:dalvik-dx:11.0.0_r3).
Logic tests: `test/CalcTest.java` (pure Java).
