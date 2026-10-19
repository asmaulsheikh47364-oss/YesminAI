# Yesmin AI — feature status (honest)
Nothing here has been verified on a device by the author; the code is written by Claude and is checked only by the GitHub build and your phone.

| Area | What it does | Notes |
|---|---|---|
| Design | Neon theme, 5 tabs (Home, Chat, Voice, Apps, More), onboarding with permissions | |
| Gemini brain | Key (Keystore-encrypted), real connection test, model list, temperature, retries on 503 | Needs internet + free quota |
| Chat | Hindi/Hinglish/English, 4 modes, 4 personalities + custom, temporary chat | Messages go to Google Gemini |
| Voice | Push-to-talk, live waveform, phone voice or Gemini natural voice with fallback | Female voice on phone depends on installed engine |
| Phone control | Open apps, battery/network/device/storage/RAM/CPU, torch, volume, Wi-Fi/Bluetooth/DND panels, call (dialer), action history with undo | Android 10 does not let apps toggle Wi-Fi/Bluetooth; call and send are pressed by the user |
| YouTube | Search page; with an optional YouTube Data API key the first video opens directly | Auto-play is decided by YouTube |
| Messaging | Contacts lookup, WhatsApp/SMS draft opened with text ready, AI draft, read incoming messages and reply (Notification Access), confirm before reply | Messages stay in memory only |
| Reminders | Real alarms + notifications, repeat, snooze, done, edit, boot restore, voice "10 minute baad ... yaad dilao" | Exact alarm permission may be needed on Android 12+ |
| Memory | Categories, search, edit/delete, on/off, sensitive-text guard | Local only, no sync |
| Security | PIN lock (salted PBKDF2), optional fingerprint, hide reminder text | Forgotten PIN = clear app data |
| Photo AI | Gallery/camera photo + question via Gemini Vision | Photo sent to Google |
| Tools | Coding assistant (6), translate, writing, summary, study, ideas, analysis, troubleshooting, calculator | |
| Accessibility | Optional: back, home, scroll, tap by label, type; confirm for risky taps | Never reads passwords |
| Help | FAQ and optional support number | |

## Not implemented
"Hey Yesmin" wake word, always-on listening, cross-device sync, running AI-generated code, reading screen content.
