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
