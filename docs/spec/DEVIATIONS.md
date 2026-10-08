# Deviations from the spec

Each entry: what the spec says, what reality requires, what was done instead.

## D-001 · Prefix 400 is not spam
- **Spec (03, F5; 02 rule 7):** "included rule: Spanish commercial range 400" as a spam-style `RULE` level.
- **Reality (BOE-A-2026-8409):** 400 is the range commercial callers *must* use, outbound only, operational around
  17 October 2026. Its presence identifies a legitimate commercial call.
- **Done:** the rule is shipped as "commercial call" information, default `WARN`, versioned in the data repo so it can
  change without an app release. See `08-investigacion-fuentes.md`.

## D-002 · Data repo layout aligned with UltimateMaps-data
- **Spec (04):** `manifest.json` signed with Ed25519; releases `data-YYYY.MM.DD`.
- **UltimateMaps-data:** stable `releases/latest/download/` URL, `SHA256SUMS` asset, tags `data-<version>`, only the
  two newest data releases kept, workflow syntax check in CI. It has no manifest signature.
- **Done:** adopt its URL scheme, `SHA256SUMS`, tag pruning and workflow check; keep the Ed25519 signature
  (`manifest.json` + `manifest.json.sig`) because the spec requires it.

## D-003 · CI follows UltimateGallery
- **Spec (06):** generic CI description. **UltimateGallery wins** (CLAUDE.md): same pinned actions, JDK 21, `UG_*`
  secret names, `appVersion` in `gradle.properties` driving `versionCode`, release notes from `CHANGELOG.md`.
- One adaptation: UltimatePhone needs the `INTERNET` permission (data packs, sources, WebDAV), so the "no network
  permission" CI check of UltimateGallery is replaced by a "no Google dependency" check. The Phase 0 spike also needs
  `INTERNET` for test 8.

## D-004 · Packs are compressed with xz, not zstd
- **Spec (04):** `.db.zst`.
- **Reality:** the app must decode packs on Android 12+ with no native code (F-Droid). Pure-Java zstd decoders either need
  JDK 22 or rely on `sun.misc.Unsafe`, and `zstd-jni` ships prebuilt native libraries. XZ is in Python's standard library
  and has a small pure-Java decoder (`org.tukaani:xz`, 0BSD).
- **Done:** packs are `<id>.db.xz` (LZMA2, 8 MiB dictionary). The manifest also carries `compression`,
  `uncompressedBytes` and `uncompressedSha256`, and the app refuses to unpack more than the manifest announces.

## D-005 · Ed25519 verification uses Bouncy Castle
- **Spec (04):** manifest signed with Ed25519, public key embedded in the app.
- **Reality:** `java.security` has no Ed25519 before Android 13 and the app supports Android 12.
- **Done:** only Bouncy Castle's lightweight `Ed25519Signer` is used (R8 removes the rest). The signature is detached
  (`manifest.json.sig`, base64 of the 64 raw bytes over the exact manifest bytes). The implementation is tested against
  the RFC 8032 test vector so it interoperates with the signer in the data pipeline.

## D-006 · Spam decision on the call screen does not go through the caller label resolver
- **Brief:** extend `TelecomCalls.labelResolver` so the in-call path warns when the screening role is not held.
- **Reality:** `CallerLabel` carries a name and photo only, and the resolver runs once per call in the telecom module,
  which must not know about spam. Changing it would also conflict with the business-name work in the same file.
- **Done:** the in-call view model asks `DecisionEngine.verdictForCall`, which reuses the decision the screening
  service just stored in `DecisionStore` (live map, 30 s) and otherwise decides and records it. Same decision, computed
  once per call, no change to `core/telecom`. Without the screening role the app can warn but cannot silence or reject.

## D-007 · Custom spam sources are stored as a sorted binary file, not SQLite
- **Spec (04):** every pack is a SQLite database.
- **Reality:** the writer for sources the user adds by URL had to be a pure, JVM-testable function, and a custom list only
  needs exact-number membership.
- **Done:** `custom-<id>.nums` (sorted numbers, memory-mapped, binary search). Labels are not stored: a hit is labelled
  with the source's display name and its `sourceId` is `custom-<id>`. Packs from the data repository stay SQLite.

## D-008 · Business name search relies on FTS5 in the platform SQLite
- Dialer suggestions for businesses use an FTS5 `MATCH` over the pack's `numbers_fts` table. FTS5 is present in Android's
  SQLite on supported releases; if it is not, business suggestions are empty and nothing else is affected. Not unit tested
  (needs Android's SQLite); to verify on a device with an installed business pack.

## D-009 · Sync merge: duplicate numbers collapse into a tombstone that keeps its own stamp
- **Spec (03, F7):** merge by `id`, newest `updatedAt` wins, ties by `deviceId`.
- **Reality:** two devices can add the same number independently, giving two ids for one kind and value. Stamping the
  losing id with the winner's time makes the merge order-dependent.
- **Done:** the greatest of the live ids (`updatedAt`, `deviceId`, `id`) stays; the others become tombstones that keep
  their own `updatedAt`/`deviceId` and win a tie against their live form, so collapsing only moves an id up in the per-id
  order. The merge is commutative and idempotent, and every device reaches the same state. If one of the two ids is
  deleted at the same time, which one survives can depend on the sync order, but all devices still agree.

## D-010 · Settings export uses Argon2id from Bouncy Castle, and leaves device-specific values out
- **Spec (03, F8):** Argon2id if a suitable library exists, else PBKDF2.
- **Done:** Argon2id (32 MiB, 4 passes, 1 lane, parameters stored in the authenticated header, upper limits enforced when
  reading) through `Argon2BytesGenerator` of `bcprov`, which the app already ships for Ed25519; AES-256-GCM, header as AAD.
  The export holds theme and region, spam settings, both lists, and the Nextcloud account. It does not hold the
  per-number SIM choices or the default SIM (SIM keys belong to one phone's SIM cards) or the sync `deviceId` (it must
  stay unique per phone). Importing the lists merges (newest change wins) instead of replacing.

## D-011 · WebDAV with OkHttp
- **Reality:** `HttpURLConnection` cannot send PROPFIND or MKCOL. OkHttp 4.12 (Apache-2.0, no Google code) is used with
  redirects handled by the app because OkHttp turns a redirected PUT into a GET; `Authorization` never leaves the
  configured host and plain `http` is refused, including through redirects.

## D-012 · Call recording is microphone capture; line capture is detected at run time, not assumed
- **Spec (03, F9; 05, spike):** the scope of recording depends on the Phase 0 spike.
- **Reality (spike-results.md, realme RMX5210, Android 16):** `MIC` and `VOICE_RECOGNITION` capture sound,
  `VOICE_COMMUNICATION` captured silence, and `VOICE_CALL`, `VOICE_DOWNLINK` and `VOICE_UPLINK` failed with "start
  failed". Those three were only probed outside a call, so line capture is not ruled out on every phone.
- **Done:** the product does not promise to record the other person. Recording captures the microphone and, with the
  speaker on, the other person through the air (the speaker can be switched on automatically). On the first recording the
  app tries `VOICE_CALL`, then `VOICE_DOWNLINK`, inside a try/catch and uses a source only if it starts and returns
  non-silent audio within a few seconds. The result (`LINE_CAPTURE`, `MICROPHONE_ONLY`, `UNAVAILABLE`) is kept in
  DataStore together with the system build, so a system update re-checks. A source that starts but is silent may just
  have caught a quiet moment, so that is only believed after three recordings; a source that cannot start is final at
  once. Settings > Call recording states the result plainly ("On this phone the app can only record your microphone; use
  speaker to capture the other person").
- **Limits:** `VOICE_DOWNLINK` is recorded on its own (the other person only): mixing it with `VOICE_UPLINK` needs raw
  PCM mixing and an own encoder, which is not worth the risk for a source normal apps cannot open. The microphone service
  (`foregroundServiceType="microphone"`) can only be started while the call screen is visible or after a tap on a
  notification; an automatic start from the background is caught, reported in Settings, and replaced by a notification
  with a Record button. Files are written through `DocumentsContract` and the content resolver (the layer under
  `DocumentFile`, without an extra dependency) into the folder the user picked, and are created with a generic MIME type
  so providers keep the `.m4a`/`.ogg` name as given. File names hold the date, the direction and the last three digits of
  the number only; the contact name is added only if the user turns that on.
- **Not verified on a device:** everything that depends on the framework (`MediaRecorder` sources during a call,
  foreground service start rules, the SAF provider of a given phone) was reasoned from the documentation and is
  unit-tested only in its pure parts.
