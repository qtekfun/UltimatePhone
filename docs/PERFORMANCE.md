# Startup and performance audit (phase 6)

Targets (docs/spec/03-funcionalidades.md, non-functional requirements):

| Target | Budget |
| --- | --- |
| Cold start to the keypad | under 1 s on the target phone |
| Incoming-call screening decision, all packs installed | under 50 ms |
| Network during a call | none |
| Heavy work on the main thread | none |

This audit was done by reading the code and with JVM tests. **No device or emulator was available, so no number below is a
phone measurement.** The laptop numbers only show orders of magnitude and catch regressions; the real budgets still have to be
confirmed on the phone (see "How to profile on a device").

## What was found and changed

### 1. Application start (`UltimatePhoneApp.onCreate`)

Before, `onCreate` field-injected six singletons on the main thread (contacts, businesses, lookups, data startup, sync
scheduler, recording coordinator). Building them pulled in, on the main thread:

* `SpamModule.spamLists` called `runBlocking { settings.deviceId() }`, a DataStore read (disk) that blocked the main thread
  because `SyncScheduler` needs the lists repository. This was the suspect named in the brief, and it was real.
* `PackStore` and `DataPackRepository` create their directories in `init` (disk writes on the main thread).
* The decision engine was built from the screening service's `onCreate` (main thread) and compiled the built-in prefix rules
  there, which also loads libphonenumber.

Now:

* The application injects `dagger.Lazy<...>` for everything. The main thread only installs the (debug-only) StrictMode policy,
  wires the caller-label resolver (itself lazy) and launches one coroutine on the application scope (`Dispatchers.Default`).
* The warm-up runs there, each step isolated (a failure is logged by class name and never crashes the app): libphonenumber
  metadata for the SIM region and the short-number (emergency) data, the installed packs, the decision engine (built-in rules,
  lists database, stored settings), the sync scheduler, the recording coordinator and the data startup.
* `RoomSpamListsRepository` takes the device id as a `suspend` provider and reads it when a list is changed (the writes are
  suspending anyway). Nothing blocks while the graph is built. The old `String` constructor is kept for tests.
* The built-in prefix rules are a `LazyPrefixRules`: parsed and compiled on first use or by the warm-up, once.

A call that arrives right after the process starts is still handled correctly, because nothing depends on the warm-up having
run, it only makes the first use cheap:

* `DefaultInstalledPackLookups.current()` used to build a second snapshot when a call beat the warm-up (two builds, two sets of
  open databases). It now waits for the build that is running (it takes the same mutex `reload()` holds) and builds one itself
  only if nobody is. It always runs on the calling screening thread, never on the main thread (the label lookup is on IO, the
  screening runs on `Dispatchers.Default`).
* `PhoneNumberUtil` initialises under its own lock, so a call during the warm-up waits for the same load instead of loading twice.
* `TelecomCalls.labelResolver` is set synchronously in `onCreate`; the repositories behind it are created on the first lookup,
  on the background scope.

### 2. Call screening path

Read: `SpamScreeningService`, `DecisionEngine`, `DecisionStore`, `PackLookups`, `SpamDecider`, `PrefixRuleSet`.

Confirmed:

* No network: contacts (ContactsContract), two indexed queries on the lists, in-memory/indexed pack lookups. `HttpFetcher`,
  `SourceFetcher` and WorkManager are only reachable from the data and sync features, never from the decision.
* Not on the main thread: the service launches on `Dispatchers.Default` and the whole decision is under a 2 s timeout
  (`BUDGET_MS`); on timeout or failure the call is allowed. The answer is sent before the decision is written to disk.
* Bounded: one live-verdict map capped at 64 entries with a 30 s TTL; no state is rebuilt per call (the snapshot of packs is
  swapped only on a reload).

Fixed:

* `PrefixRuleSet.match` built `listOf(e164) + PrefixCandidates.of(e164)` (about 12 strings and two lists) per call. It now walks
  the prefixes by length and only creates the substrings it looks up, same order and same result.
* `DecisionEngine.verdictForCall` normalised the number with libphonenumber, then `decide` normalised it again on a miss.
  It is now normalised once.
* `LibEmergencyNumbers` allocated a list and a distinct set per call; replaced by two direct comparisons.
* `LibPhoneNormalizer.parse` counted digits with `filter { }.length`; now `count { }`.
* `TelecomCalls` re-formatted the display number on every call-state change (each change is a libphonenumber parse and
  format, on the main thread because `InCallService` callbacks run there). It is now formatted once per call.

Not changed on purpose: `SqlitePackDatabase.lookup` does `SELECT *` and reads columns by name; it is one primary-key lookup
per pack and cannot be tested on the JVM, so it is left alone. If device traces show it, project only the four columns.

### 3. libphonenumber

* There was one `PhoneNumberUtil` (the library's own singleton), but `TelecomCalls` and `CoreModule` each constructed a
  `LibPhoneNormalizer`. Both now use `LibPhoneNormalizer.shared`, so there is one normaliser per process.
* The library loads metadata per region lazily from `com/google/i18n/phonenumbers/data/*`. `LibPhoneNormalizer.warmUp(region)`
  loads the SIM region (validate and format an example number) and the short-number data on a background thread.
  `PrefixRuleSet` uses the same `getInstance()`.
* R8 does not touch these files (they are Java resources). The release APK holds 541 of them: 254 region metadata files,
  241 short-number files and 46 alternate-format files, 334 KB raw and 127 KB compressed. Trimming them would mean forking
  the library, so it is not done; the alternate formats (23 KB raw) are only used by `PhoneNumberMatcher`, which the app
  does not call, but excluding them is not worth the risk for 23 KB.

### 4. Dialer typing path

* Suggestions are `number.debounce(60 ms).mapLatest { ... }`: a burst of keys searches once and a newer key cancels the search
  in flight. The empty number is not debounced, so clearing is immediate.
* The whole state pipeline now runs on `flowOn(Dispatchers.Default)`: before, `SuggestionMatcher` ran on the main thread
  (`viewModelScope`) and the combine step normalised and formatted the typed number there on every key.
* `refresh()` (SIM accounts and the dialer role are binder round trips) runs on `Dispatchers.Default`; it used to run in the
  ViewModel `init`, on the first frame of the first screen.
* Already right and left alone: the contacts index is built once on IO (`SystemContactsRepository.currentIndex`, behind a
  mutex), invalidated by a `ContentObserver` and registered lazily after the permission is granted; results are bounded by
  `limit` (contacts) and `MAX_BUSINESS_SUGGESTIONS = 5` (businesses, FTS with at most 256 prefix terms, over-fetch of 6 times
  the limit per pack).

### 5. R8 and size

`./gradlew :app:assembleRelease` (R8 and resource shrinking on, debug-signed because no release key is configured here):

| Item | Size |
| --- | --- |
| `app-release.apk` before the resource excludes | 5,609,532 bytes (5.35 MiB) |
| `app-release.apk` after them (final) | 5,594,748 bytes (5.34 MiB) |
| `classes.dex` | 4,507,056 bytes |
| `resources.arsc` | 592,724 bytes |
| libphonenumber metadata (compressed) | about 127 KB |
| Bouncy Castle resources (compressed) | about 13.5 KB |

* Bouncy Castle: R8 keeps 20 classes (Ed25519 signer and points, Argon2 generator, Blake2b, SHA-512, a few parameter classes,
  `CryptoServicesRegistrar` and `Properties`). The rest of the 4 MB library is gone. What remained were two message
  tables (`org/bouncycastle/x509/CertPathReviewerMessages*.properties`, 96 KB raw); `packaging.resources.excludes` now drops
  them, together with `DebugProbesKt.bin`.
* Keep rules: the seeds file shows `SpamDatabase_Impl` (Room), `SyncWorker`, `DataUpdateWorker`, the services and the Hilt
  entry points are kept by the libraries' consumer rules (Room, WorkManager, Hilt, kotlinx.serialization, OkHttp). Nothing was
  added to `proguard-rules.pro`; it now documents why. The XZ library is plain Java without reflection.
* Not verified: that the release APK runs. It builds, and the keep checks above are by reading `seeds.txt` and the mapping, but
  nobody has installed it. The CI emulator smoke test (AOSP, no GApps) installs the debug APK; running it against the release
  build would close this gap.

### 6. StrictMode

`DebugStrictMode.install()` (called first thing in `onCreate`, a no-op unless `BuildConfig.DEBUG`) sets:

* thread policy: disk reads, disk writes, network and custom slow calls on any thread that is checked (the main thread),
  `penaltyLog` only;
* VM policy: leaked closeables, leaked SQLite objects, activity leaks and leaked registrations, `penaltyLog` only.

Look for violations with `adb logcat -s StrictMode` during development. Penalties never crash. StrictMode logs the stack, not
the data, so no phone number is involved.

## Budgets and the tests that guard them

`core/spam/.../ScreeningBenchmarkTest` (JVM, runs in `check`): 10,000 list entries, a fake pack of 500,000 numbers, 3,001
prefix rules, real libphonenumber normalisation and the real `SpamDecider`.

| Check | Budget (test fails above) | Measured on the dev laptop |
| --- | --- | --- |
| 1,000 consecutive decisions | 2,000 ms (2 ms each) | 20 ms (20 microseconds each) |
| slowest single decision of those 1,000 | 150 ms (3 times the product target) | under 1 ms |
| 100,000 prefix matches against 3,001 rules | 2,000 ms | 40 ms |
| 1,000 hidden/short-number checks | 2,000 ms | not printed (well under the budget) |
| built-in rules built once | exactly 1 build | 1 |

The budgets are about 100 times the laptop figures because CI machines are shared and noisy; they exist to catch an accidental
linear scan, an O(rules) match or a rebuild per call, not to prove the phone figure. The phone adds what the test leaves
out: the contacts content-provider query, the two list queries and the pack queries (all single indexed lookups), and a much
slower CPU. 50 ms on the phone is **not proven** by these tests.

`RepositoryTest.deviceIdIsReadWhenAChangeIsMadeNotWhenTheRepositoryIsBuilt` guards the removal of the blocking device-id read.

## What could not be measured

* Cold start time, time to first frame, time to the keypad: needs a device.
* The real cost of loading libphonenumber metadata on a phone, and how much of it the warm-up hides. By reading, loading one
  region plus the short-number data is small compared with the process start, but this is an estimate.
* The decision latency on the phone with real packs (SQLite file lookups, ContactsContract IPC).
* Whether the release APK starts (R8 is configured by consumer rules and looks right in `seeds.txt`).
* The effect of the 60 ms suggestion debounce on how typing feels; it is easy to change (`SUGGEST_DEBOUNCE_MS`).
* Baseline APK size before this phase (the build was not repeated on the old commit); the only size change made here is the
  14,784 bytes of excluded resources (5,609,532 to 5,594,748).

## How to profile on a device

All with a **release-like** build (`assembleRelease`; the debug build is much slower and has StrictMode logging).

* **Cold start time:** `adb shell am force-stop com.qtekfun.ultimatephone && adb shell am start -W -n com.qtekfun.ultimatephone/.MainActivity`.
  `TotalTime` is the number to compare against the 1 s target; `WaitTime` includes the system. Repeat 10 times and take the median,
  after a reboot for a truly cold run. `adb shell dumpsys gfxinfo com.qtekfun.ultimatephone framestats` shows the first frames.
* **Perfetto:** record `sched`, `freq`, `am`, `wm`, `view` and `binder_driver` (`adb shell perfetto -o /data/misc/perfetto-traces/trace
  -t 20s sched freq am wm view binder_driver`), start the app, open the trace in ui.perfetto.dev and look at the main thread of the
  app process between `bindApplication` and the first `Choreographer#doFrame`. Anything long there (disk, binder, class loading) is
  a regression. Add `androidx.tracing` sections only if you need finer detail.
* **Call screening latency:** the service already logs `Screened <masked number> in N ms` (tag `SpamScreening`, debug level) and
  keeps the slowest of the last 50 in `ScreeningStats`. Place test calls from another phone with all packs installed and read
  `adb logcat -s SpamScreening`. Run it once right after a force stop to see the cold case.
* **StrictMode:** `adb logcat -s StrictMode` on a debug build while using the keypad, the contacts and an incoming call.
* **Later: Macrobenchmark.** Add a `:benchmark` module with `StartupTimingMetric` and `FrameTimingMetric` for `MainActivity`
  (cold start, `CompilationMode.Partial` and `None`) and a Baseline Profile generator for the keypad path. The release build
  already includes the library baseline profiles; an app profile would improve the first launch after an install. It needs a
  device or emulator, so it was not added in this phase.
