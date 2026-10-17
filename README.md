# Yesmin AI — Android personal assistant (Kotlin + Jetpack Compose + Gemini)

> STATUS: source code written by Claude. It has NOT been compiled or run by Claude.
> The first real build on your phone/GitHub may show small errors — send me the error text and I will fix it.

## What it is
Gemini-powered Hindi/Hinglish/English assistant with push-to-talk voice, app launching, YouTube search,
coding assistant, local memory, and an optional Accessibility Service. See FEATURES.md for exact status.

## Project layout
```
YesminAI/
  settings.gradle.kts  build.gradle.kts  gradle.properties
  termux_setup.sh      build.sh          .github/workflows/build.yml
  app/build.gradle.kts
  app/src/main/AndroidManifest.xml
  app/src/main/res/{values,xml}/...
  app/src/main/java/com/yesmin/ai/*.kt      (UI, ViewModel, Gemini, voice, router, phone actions)
  app/src/test/java/com/yesmin/ai/*.kt      (unit tests)
```
Versions: AGP 8.5.0, Gradle 8.7, Kotlin 2.0.0, JDK 17, compileSdk/targetSdk 34, minSdk 26, Compose BOM 2024.06.00.
No Room/KSP/Hilt: plain SQLite + SharedPreferences keep the build light for a phone.

## Option A — Build in Termux (no PC)
Termux limitation: Gradle's downloaded `aapt2` is an x86 binary and does not run on ARM phones.
`build.sh` passes Termux's own `aapt2` (`-Pandroid.aapt2FromMavenOverride`). This is a known workaround,
but I could NOT verify it on your device. It also needs ~2 GB free RAM and a good while (10-30+ min first time).

1. `termux-setup-storage`
2. `unzip -o ~/storage/downloads/YesminAI.zip -d ~`
3. `cd ~/YesminAI && bash termux_setup.sh`   (one-time, downloads ~1 GB)
4. `bash build.sh`
5. APK: `~/YesminAI/app/build/outputs/apk/debug/app-debug.apk` (also copied to Downloads/YesminAI-debug.apk)
6. Install: open the APK from the Downloads folder (allow "install unknown apps").
7. Run unit tests (optional): `gradle test --no-daemon -Pandroid.aapt2FromMavenOverride=$PREFIX/bin/aapt2`

## Option B — Build on GitHub (most reliable, still no PC)
Push this folder to a GitHub repo (branch `main`). `.github/workflows/build.yml` builds the APK;
download it from Actions -> latest run -> Artifacts -> YesminAI-debug-apk.

## First run
1. Accept the privacy notice. 2. AI Brain tab -> paste Gemini API key (Google AI Studio) -> Save & Test.
3. "Connected" appears only after a real Gemini reply. 4. Chat tab -> type or tap the mic.

## Optional: signed release APK
```
keytool -genkeypair -v -keystore ~/yesmin.jks -alias yesmin -keyalg RSA -keysize 2048 -validity 10000
gradle assembleRelease --no-daemon -Pandroid.aapt2FromMavenOverride=$PREFIX/bin/aapt2
pkg install apksigner
apksigner sign --ks ~/yesmin.jks --out YesminAI-release.apk app/build/outputs/apk/release/app-release-unsigned.apk
```
Keep the .jks file and its password safe and never commit them.

## Security notes
API key: Android Keystore AES-GCM, never logged, sent only in an HTTPS header. A key typed into a personal build is fine;
a key embedded inside a distributed APK can be extracted — use a backend for production.
