# Acceptance criteria: status

Status of every acceptance criterion (AC) of `03-funcionalidades.md`, as of 0.1.0-rc4. "Unit test" means a JVM test that runs in CI on every
change; "emulator" means the instrumented smoke tests that CI runs on an AOSP image without Google apps and on one with Google APIs
(`.github/workflows/instrumented.yml`); "device" means it still needs to be tried by hand on a phone. Nothing marked "device" has been
verified on a physical phone except where stated.

| | AC | Status |
|---|---|---|
| **F1** | Dialing places the call on the chosen SIM | Implemented; SIM choice logic: unit test (`SimChooser`). **Device:** the call itself. |
| F1 | Typing "far" suggests contacts and businesses whose name starts or contains it | T9 matching: unit tests. Business search on the platform SQLite: emulator test (FTS4). Pack download, verification and install: seen working on a Pixel 8 (Spain pack). **Device:** end-to-end typing with an installed pack. |
| F1 | Results in under 100 ms with 10,000 contacts | Unit test (10,000-contact index). |
| **F2** | Locked phone: rings, answer without unlocking | Implemented (full-screen intent, show-when-locked). **Device.** |
| F2 | Spam warning appears before answering | Implemented (banner from the stored decision). Decision path: unit tests. **Device.** |
| F2 | The call can be ended from the notification | Implemented (notification action). **Device.** |
| **F3** | Marking a history entry as spam adds it to the own list and the next call from it warns | Unit test of the decision path after marking. **Device** for the UI flow. |
| **F4** | A contact created with two numbers and a photo shows up in the system contacts app | Implemented (ContactsContract batch). **Device.** |
| F4 | Export then import a `.vcf` keeps fields and photo | Round-trip unit tests with photos. **Device:** the file picker flows. |
| F4 | Merging duplicates loses no phone or email | Unit test of the merge; the platform write keeps every raw contact (aggregation). **Device.** |
| **F5** | A number from an installed source triggers the warning before ringing | Decision logic: unit tests incl. precedence matrix. Screening service: **device.** |
| F5 | A contact number is never flagged | Unit test (contact beats everything but emergency). |
| F5 | Everything keeps working offline with downloaded data | By design (local lookups); update worker tolerates no network. **Device.** |
| F5 | No network traffic during a call | Screening path has no network code (code review, `docs/PERFORMANCE.md`). The call screen shows only stored data. |
| **F6** | With a region pack installed, a known number shows the business when ringing and in history | Implemented. Pack format and search: pipeline tests, emulator test. **Device.** |
| F6 | Without a pack nothing is shown and no request is made | By design; no request unless the user installs data. |
| **F7** | A number added on phone A appears on B after sync; deletes propagate | Unit tests with two and three simulated devices against a fake ETag server. **Real Nextcloud: not tested.** |
| F7 | Simultaneous changes to one number lose no other change | Unit tests (merge properties, concurrent edits). |
| F7 | Credentials are not visible in backups or logs | Keystore-encrypted, backups disabled, tests that exports never contain them without the password. |
| **F8** | Export on one phone, import on another leaves the app configured | Unit tests of the round trip and sections. **Device:** file picker. |
| F8 | A tampered file or wrong password fails and applies nothing | Unit tests (every bit flip, truncation, wrong password). |
| **F9** | Recording scope follows what the device allows; microphone-only is stated clearly | Capability detected at run time (D-012), policy unit tests. **Device:** real recording during a call. |
| **F10** | Onboarding completes with no account and no network; skippable steps can be done later; downloaded data can be removed | Flow state machine: unit tests. Walked step by step on a Pixel 8 (skipping every step). Data removal: Settings > Data. |
| **NFR** | Cold start to the keypad under 1 s | **Not measured.** Start-up work was moved off the main thread (`docs/PERFORMANCE.md`). |
| NFR | Screening decision under 50 ms | JVM benchmark: about 20 microseconds per decision with 10,000-entry lists and a 500,000-number pack; **phone figure not measured** (SQLite and ContactsContract add to it). |
| NFR | TalkBack, large fonts, contrast | Audited by code reading, string consistency test (`docs/ACCESSIBILITY.md`); **manual TalkBack pass pending.** |
| NFR | Spanish and English complete | Test fails on missing or untranslated strings. |
| NFR | No trackers or analytics | None present; `scripts/check-no-google.sh` runs in CI. |
| NFR | Universal compatibility: installs, starts and completes the onboarding with and without Google apps | Emulator tests: starts and stays up with and without permissions on AOSP (no Google apps) and Google APIs images. The onboarding completes on a real device without Google services (realme). |
