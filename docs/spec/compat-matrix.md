# Compatibility matrix

Results per device or emulator. The app must install, start and complete its core flows on any Android 12+ (API 31+),
with or without Google services, from any manufacturer. See `02-arquitectura.md` (universal compatibility).

| Device / emulator | Android | GMS | Roles (phone / screening) | Incoming call | Outgoing call | Contacts | Notes |
|---|---|---|---|---|---|---|---|
| realme RMX5210 | 16 (SDK 36) | no | phone yes / screening yes | not tested | OK, SIM slot detected | not tested | Phase 0 spike, see `spike-results.md` |
| OPPO Find X9 Ultra (global) | | | | | | | pending |
| Pixel 8 | 16 | yes | not tested (the system phone app was left as default) | not tested | not tested | list loads, permission gate OK | 0.1.0-rc2 (release APK): installs, opens without permissions, onboarding, 4 tabs, Settings > Data downloads, verifies and installs the Spain pack. Found: no `fts5` module in Android's SQLite (fixed, D-008) |
| AOSP emulator, no GApps (API 34, CI) | 14 | no | not covered | not covered | not covered | not covered | CI smoke tests: app starts and stays up with and without permissions; business name search on the platform SQLite works (FTS4) |
| Emulator with Google APIs (API 34, CI) | 14 | yes | not covered | not covered | not covered | not covered | Same smoke tests, passing |
