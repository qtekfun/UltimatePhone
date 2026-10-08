# UltimatePhone

<img src="docs/logo.png" alt="UltimatePhone logo" width="128" />

An Android phone app with contacts and a **fully local spam filter**: it downloads lists, decides on the device and
warns you before you pick up. No phone number leaves the device, except towards the Nextcloud you configure yourself.

- Android 12+ (API 31), any manufacturer, with or without Google services. No Play Services, Firebase or ML Kit.
- Kotlin, Jetpack Compose, Material 3 Expressive. App in English and Spanish.
- GPL-3.0-or-later. Business and spam data live in [UltimatePhone-data](https://github.com/qtekfun/UltimatePhone-data).

**Status: release candidate (0.1.0-rc).** Phone, call screen, history, contacts, local spam filter, business data packs,
Nextcloud sync and encrypted backup are implemented. It has been tried on a Pixel 8 and a realme (Android 16) and is not
yet tested on a wide range of devices: see [`docs/spec/compat-matrix.md`](docs/spec/compat-matrix.md). The specification
lives in [`docs/spec/`](docs/spec/README.md) (written in Spanish), deviations from it in
[`docs/spec/DEVIATIONS.md`](docs/spec/DEVIATIONS.md).

Download: [releases](https://github.com/qtekfun/UltimatePhone/releases). Data packs come from
[UltimatePhone-data](https://github.com/qtekfun/UltimatePhone-data) and are signed; the app verifies them before use.

## Build

Requirements: JDK 21 and the Android SDK (platform 37, build-tools 36). Gradle is the wrapper in the repo.

```sh
./gradlew check assembleDebug     # ktlint, detekt, lint, unit tests, debug APK
scripts/check-no-google.sh        # fails on any Google dependency (also runs in CI)
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk` (package `com.qtekfun.ultimatephone.debug`).

## Reproduce the development environment

Tested on Fedora (x86_64). Any Linux with KVM works the same way.

```sh
# JDK 21 (Temurin) on PATH, JAVA_HOME set
java -version                                           # openjdk 21

# Android command-line tools in $ANDROID_HOME/cmdline-tools/latest
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator

yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;36.0.0" "emulator" \
  "system-images;android-34;default;x86_64"             # "default" = AOSP image, no Google apps or services

# Emulator without Google services, for the "no GMS" tests
avdmanager create avd -n aosp34 -k "system-images;android-34;default;x86_64" -d pixel_6
emulator -avd aosp34 -no-snapshot                       # needs /dev/kvm

adb devices -l                                          # physical phones: enable USB debugging
```

A second emulator with Google APIs (`system-images;android-34;google_apis;x86_64`) is used for the "with GMS" half of the
compatibility matrix. The emulator needs about 2 GB of free RAM on top of Gradle.

## Releases and signing

Releases follow UltimateGallery: see [`RELEASING.md`](RELEASING.md). Tags `vX.Y.Z` build a signed APK in CI.

To verify an APK you downloaded, compare its signing certificate with the published fingerprint:

```sh
apksigner verify --print-certs UltimatePhone-<version>.apk | grep SHA-256
# expected release certificate SHA-256 (no colons, lower case):
# 5aec737a127944f96e5bde920b83d392c02765df823e827cd3eb0de5a32268ef
```

(`keytool -list -v -keystore <jks> | grep SHA256` prints the same value with colons.)

## Privacy

No analytics, no crash reporting, no remote logging. The app logs only the last three digits of a number. The network is
used for three things, all configurable: data packs from `UltimatePhone-data`, the spam sources you enable, and your own
Nextcloud (WebDAV).

## License

GPL-3.0-or-later. Business data derived from OpenStreetMap is © OpenStreetMap contributors, ODbL 1.0
(see the data repository).
