# Changelog

All notable changes to UltimatePhone. Versions follow [SemVer](https://semver.org/); the
same text is kept under `fastlane/metadata/android/*/changelogs` for F-Droid.

## [0.1.0-rc4]

- Faster start: heavy work moved off the main thread; the first call after opening the app is still handled.
- Accessibility: TalkBack reads rows, keys and states properly; the call screen and keypad work with large text; recording and spam states no longer rely on colour alone.
- Spanish and English wording reviewed, plurals fixed.

## [0.1.0-rc3]

- Business names on the keypad now work: the data packs use an index that Android supports (the previous one needed a SQLite module Android does not include). Data packs are updated automatically.
- Contacts: find and merge duplicates (nothing is lost), groups, vCard import and export.
- Call recording (microphone; it records the other person only through the speaker unless the phone allows line capture), with a legal notice and a folder of your choice.
- Emulator smoke tests in CI, on Android with and without Google apps.

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
