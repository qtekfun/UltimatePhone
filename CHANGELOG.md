# Changelog

All notable changes to UltimatePhone. Versions follow [SemVer](https://semver.org/); the
same text is kept under `fastlane/metadata/android/*/changelogs` for F-Droid.

## [0.1.0-rc2]

- Fixed a crash when opening the app for the first time (the contacts observer was registered before the permission was granted).
- New launcher icon, centred.

## [0.1.0-rc1]

First release candidate. Not tested on a wide range of devices yet.

- Phone app: keypad with contact and business search, call screen (answer, decline, mute, keypad, speaker and Bluetooth, hold, merge), dual SIM, call history with filters, contacts.
- Local spam filter: your own list, allowed numbers, community sources by URL, prefix rules (Spain 400 is shown as a commercial call, not spam). Decided on the device, before the call rings.
- Data packs from UltimatePhone-data: businesses for Spain, Germany and Austria (OpenStreetMap), signed and verified.
- Nextcloud sync of the spam list and allowed numbers, and encrypted settings export and import.
- Setup guide with battery and auto-start help for each manufacturer. English and Spanish.

## [0.0.1]

Phase 0 spike (not a usable phone yet).

- Test app for the phone and call-screening roles, the in-call screen, pre-ring screening timing, dual SIM,
  contacts, call log, call-recording sources and background work, with an event log that masks phone numbers.
