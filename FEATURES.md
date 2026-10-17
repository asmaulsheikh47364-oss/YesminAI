# Feature status (honest)
Legend: IMPLEMENTED = code written, UNTESTED = never compiled/run yet. Nothing here is verified on a device.

| Feature | Type | Status |
|---|---|---|
| 5-tab Compose UI, dark/light theme | Local | Implemented, untested |
| Gemini key save (Keystore-encrypted), real connection test, model list from API | Gemini | Implemented, untested |
| Model/temperature settings, sample test, clear error messages | Gemini | Implemented, untested |
| Chat with personalities (4) + Hindi/Hinglish/English | Gemini | Implemented, untested |
| Push-to-talk voice (SpeechRecognizer) + spoken replies (TextToSpeech), stop/cancel, text fallback | Local (+Gemini for answer) | Implemented, untested |
| Voice choice list | Local | Implemented; female voice depends on installed TTS engine |
| Open apps by name (ambiguity + not-installed handling) | Local | Implemented, untested |
| YouTube search via URL/intent | Local | Implemented; opens search only, NO autoplay |
| Battery, network, time/date, device info | Local | Implemented, untested |
| Calculator | Local | Implemented, unit tests written |
| Coding Assistant (generate/explain/bugs/error/tests/plan, copy + save snippet) | Gemini | Implemented, untested |
| Translate, writing, summary, study/quiz, ideas, analysis, troubleshooting | Gemini | Implemented, untested |
| Memory/notes/tasks (view, edit, delete, clear, disable), sensitive-text guard | Local | Implemented, untested |
| Optional chat history (off by default) | Local | Implemented, untested |
| Accessibility: back, home, scroll, tap by label, type text; confirmation for risky taps | Needs permission | Implemented, untested; runs 5s after command so you can switch apps |
| Privacy notice, delete-everything controls | Local | Implemented, untested |
| Unit tests (router, calculator, sensitive guard) | - | Written, not run |

## Not implemented
Image understanding, reminders/alarms (tasks are just notes), wake-word, always-on listening, cross-device sync,
running AI-generated code, Room/DataStore/DI (SQLite + SharedPreferences used instead), release signing config in Gradle.

## Known limits
- Accessibility cannot control password fields, system permission dialogs, or apps that block it.
- Gemini model names change over time; the app lists models from Google after a successful key test.
