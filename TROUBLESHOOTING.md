# Troubleshooting
| Error | Fix |
|---|---|
| `SDK location not found` | `export ANDROID_HOME=$HOME/android-sdk` (build.sh does this) |
| `Failed to install the following Android SDK packages... licences` | `yes \| $HOME/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=$HOME/android-sdk --licenses` |
| `AAPT2 ... Daemon startup failed` / `Syntax error: "(" unexpected` | Gradle used the x86 aapt2. Pass `-Pandroid.aapt2FromMavenOverride=$PREFIX/bin/aapt2` and `pkg install aapt2` |
| `Unsupported class file major version` / Java errors | Need JDK 17: `pkg install openjdk-17`, `export JAVA_HOME=$PREFIX/lib/jvm/java-17-openjdk` |
| `OutOfMemoryError` / process killed | Close other apps, use `--no-daemon`, lower `-Xmx` (e.g. 1000m), keep Termux wake-locked |
| `Could not resolve com.android...` / timeout | Check internet; retry; Google Maven (dl.google.com) must be reachable |
| `Installed Build Tools revision 34.0.0 is corrupt` | `sdkmanager --sdk_root=$HOME/android-sdk "build-tools;34.0.0"` again |
| Kotlin daemon crash | build.sh already uses `kotlin.compiler.execution.strategy=in-process` |
| App says "speech service nahi mili" | Install/enable Google app speech services; use text input meanwhile |
| Hindi voice silent | Phone Settings -> Text-to-speech -> install Hindi voice data |
| "App nahi mila" | Use the exact name shown under the app icon |
| Accessibility actions fail | Service must be ON in Android Settings; target app must be on screen after the 5s delay |
If a compile error remains, send the first 20 lines of the error to Claude with the file name.
