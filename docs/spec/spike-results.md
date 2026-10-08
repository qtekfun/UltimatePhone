# Phase 0 spike results

Fill this file in after testing the spike APK on the two phones and the emulator. The test definitions come from
`05-fase0-spike-oppo.md`. Numbers are always masked in the app log (last three digits), so the log can be pasted here.

## Devices

| | OPPO Find X9 Ultra (global ROM) | Pixel 8 | AOSP emulator (no GApps) |
|---|---|---|---|
| Android / ColorOS version | | | Android 14 (`uk34`) |
| Build number | | | |
| Previous default phone app | | | |
| SIMs (physical / eSIM) | | | |
| Date tested | | | |

## Install the APK

Build (or take the `app-debug.apk` the CI uploads):

```sh
./gradlew assembleDebug
adb devices -l                       # note the serial of each phone
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

The package is `com.qtekfun.ultimatephone.debug`, so it can live next to any other app. Launch **UltimatePhone Spike**.

Before the tests, in the app, in this order:

1. **Grant permissions** (allow everything, including notifications and microphone).
2. **Request phone app role** and **Request call screening role**; accept both system dialogs.
3. **Run capability probe**, which writes the device capabilities to the log.
4. Under **Screening test list** add the number of the phone you will call from (6+ digits) with the action to test:
   `REJECT`, `SILENCE`, or `WARN`. Change it between runs of test 5.
5. On ColorOS (and any OEM with its own battery manager): allow auto-start, allow background activity, set the app's
   battery usage to *unrestricted*, and allow full-screen notifications / pop-ups on the lock screen. Note exactly
   which switches you had to change in the *Notes* column of test 8.

## Not yet verified (author's machine)

The emulator column was not exercised by the author: the AOSP emulator (`uk34`) was killed repeatedly while the machine
was low on free memory, so no automated incoming-call run was possible. Everything in the table is therefore pending,
including the emulator column. Build, lint, detekt and unit tests do pass.

## Where to write results

Write each result in the table below (cells for OPPO and Pixel; add the emulator column if you test it). For every
test also paste the relevant lines of the app's **Event log** into the *Evidence* section at the end. To get the log
off a phone:

- In the app: **Copy log**, then paste it into this file or an issue.
- Or from the computer (works on the debug build):

```sh
adb -s <serial> exec-out run-as com.qtekfun.ultimatephone.debug cat files/spike-log.txt > spike-log-<device>.txt
```

## Test matrix

Result values: `PASS`, `FAIL`, `PARTIAL` (explain), `N/A`.

| # | Test | How | Expected | OPPO Find X9 Ultra | Pixel 8 | Emulator |
|---|---|---|---|---|---|---|
| 1 | Phone role | Request the role, accept, reboot the phone, check **Run capability probe** | `role.dialer held=true` after the reboot; ColorOS did not restore its own app | | | |
| 2 | Incoming call, phone locked | Call the phone from another phone, screen off | Own screen over the lock screen; answer without unlocking | | | |
| 3 | Incoming call, phone in use | Same with the screen on and unlocked | Heads-up notification, not full screen | | | |
| 4 | Pre-ring screening | Call from a number on the test list | `[screen] ... decided_in=Nms` before the phone rings. Record N and `since_call_created` | | | |
| 5 | Silence and reject | Repeat 4 with `SILENCE` and `REJECT` | Honoured; the call appears in the call log (use **Read last calls**) with type `REJECTED`/`BLOCKED` or equivalent | | | |
| 6 | Dual SIM | Pick each SIM in the *Dial* section and call; receive a call on each SIM | Log shows `sim=slot0` / `slot1` for each call | | | |
| 7 | Contacts | **Create test contact**; look for "UP Spike Test" in the system Contacts app; **Delete test contacts** | Visible in the system app, then removed | | | |
| 8 | Background | **Schedule daily job (Wi-Fi only)** and **Schedule 15-minute job**; leave the screen off 24 h, once with the app's battery usage restricted and once unrestricted | `[bg] ran ok` lines at the expected cadence. Note which OEM switches were needed | | | |
| 9 | App killed | Swipe the app out of recents, then call the phone | A new `[app] process started` line, and the call is handled | | | |
| 10 | No Google dependency | `google_mobile_services_feature` in the probe. On the emulator repeat roles, incoming call, screening, contacts. On the phones, if possible, disable Google Play services and repeat the essentials | Nothing fails | | | |
| 11 | Capability probe | **Run capability probe** | The listed capabilities agree with what the manual tests showed | | | |
| 12 | Current default phone app | Before installing, note the default phone app and what the user loses by replacing it (recording, caller ID, spam warnings, smart dialling) | List of what is lost | | | |
| 13 | Coexistence with Google Phone | With the spike as default, check the Google Phone app does not interfere with warnings or screening, and switch back | No interference; reversible | | | |

### Recording tests (decide the scope of F9)

Run during a real call (answer it, then start a source, speak for 5+ seconds, stop). Try with the speaker on and off.
`peak=0` or `nonzero_samples=0/N` means the source produced silence. "Heard the other side" is a manual listening
check: the files are in the app cache (`adb exec-out run-as com.qtekfun.ultimatephone.debug cat cache/probe-MIC.m4a > mic.m4a`).

| Source | Starts? | Speaker off: your voice / other side | Speaker on: your voice / other side | Peak amplitude | Notes |
|---|---|---|---|---|---|
| `MIC` — OPPO | | | | | |
| `MIC` — Pixel 8 | | | | | |
| `VOICE_COMMUNICATION` — OPPO | | | | | |
| `VOICE_COMMUNICATION` — Pixel 8 | | | | | |
| `VOICE_CALL` — OPPO | | | | | |
| `VOICE_CALL` — Pixel 8 | | | | | |
| `VOICE_DOWNLINK` / `VOICE_UPLINK` (probe button, no call) — OPPO | | | | | |
| `VOICE_DOWNLINK` / `VOICE_UPLINK` (probe button, no call) — Pixel 8 | | | | | |

Also note (from doc 05):

- Does the phone's current dialer offer call recording or call screening in your region, and is anything reusable?
- Elevated-privilege routes (debug-permission tools): only document whether they are viable and what they need.
  They are not implemented in v1 without an explicit decision. Accessibility-service tricks are ruled out.

Decision (filled in after the tests): `full line capture` / `microphone + speaker only` / `drop F9 from v1`.

## Exit criteria (from doc 05)

- [ ] Tests 1 to 7 work, or there is a documented alternative.
- [ ] Screening decision time is within the system limit with margin (record the measured milliseconds).
- [ ] List of ColorOS settings needed for background work, for the onboarding guide.
- [ ] Recording scope decided.

If tests 1 to 3 fail in a way that stops the app being usable as the main phone, stop and report before Phase 1.

## Evidence

Paste the event-log excerpts per device and test below.

### OPPO Find X9 Ultra

```
```

### Pixel 8

```
```

### Emulator

```
```
