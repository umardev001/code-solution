# Bolo – speak Urdu, send perfect English (Android)

Voice → Urdu text (Android speech recognition, runs until you stop it) → clean English.
- Voice command: end a message with "… isay English mein translate kar do" and it translates by itself.
- Translation engines: Claude (add your API key in Settings: perfect grammar + tone: Professional / Friendly / Concise / Formal), otherwise free Google Translate → MyMemory fallback with local grammar tidy-up.
- History, copy, WhatsApp/share, draft is kept. Designed and Developed by IronicXsoft.
- Android 6.0+, needs internet + microphone. Build: `python3 tools/build_apk.py`; UI tests: `tools/rt` (Robolectric).
