#!/data/data/com.termux/files/usr/bin/bash
# Builds the debug APK in Termux. Run termux_setup.sh once before this.
export JAVA_HOME=$PREFIX/lib/jvm/java-17-openjdk
export ANDROID_HOME=$HOME/android-sdk
export PATH=$HOME/gradle/gradle-8.7/bin:$PATH
cd "$(dirname "$0")" || exit 1

gradle assembleDebug --no-daemon --stacktrace \
  -Pandroid.aapt2FromMavenOverride=$PREFIX/bin/aapt2 \
  -Pkotlin.compiler.execution.strategy=in-process \
  -Dorg.gradle.jvmargs=-Xmx1200m || { echo "BUILD FAILED - see TROUBLESHOOTING.md"; exit 1; }

APK=app/build/outputs/apk/debug/app-debug.apk
ls -lh "$APK" || exit 1
if [ -d "$HOME/storage/downloads" ]; then
  cp "$APK" "$HOME/storage/downloads/YesminAI-debug.apk" && echo "Copied to Downloads/YesminAI-debug.apk"
fi
