#!/data/data/com.termux/files/usr/bin/bash
# One-time setup: JDK 17, Android SDK (platform 34 + build-tools), Gradle 8.7, Termux aapt2.
set -e
pkg update -y && pkg upgrade -y
pkg install -y openjdk-17 wget unzip aapt2 git

export JAVA_HOME=$PREFIX/lib/jvm/java-17-openjdk
export ANDROID_HOME=$HOME/android-sdk
mkdir -p "$ANDROID_HOME/cmdline-tools" "$HOME/gradle"

if [ ! -d "$ANDROID_HOME/cmdline-tools/latest" ]; then
  wget -O "$HOME/cmdtools.zip" https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -q "$HOME/cmdtools.zip" -d "$ANDROID_HOME/cmdline-tools"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm "$HOME/cmdtools.zip"
fi

if [ ! -d "$HOME/gradle/gradle-8.7" ]; then
  wget -O "$HOME/gradle.zip" https://services.gradle.org/distributions/gradle-8.7-bin.zip
  unzip -q "$HOME/gradle.zip" -d "$HOME/gradle"
  rm "$HOME/gradle.zip"
fi

yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --licenses || true
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" "platforms;android-34" "build-tools;34.0.0"
echo "SETUP DONE"
