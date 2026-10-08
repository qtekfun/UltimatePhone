# Releasing

Releases are tagged by hand; there is no release automation beyond the tag workflow.

1. Set `appVersion` in `gradle.properties` (for example `0.2.0`, or `1.0.0-rc1`).
2. Add a `## [x.y.z]` section to `CHANGELOG.md` and save the same text (500 bytes max) as
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and `es-ES/...`.
   The version code is `(MAJOR*10000 + MINOR*100 + PATCH) * 100 + 99` (or `+ N` for `-rcN`).
3. Commit, push to `main`, wait for CI to be green.
4. `git tag vX.Y.Z && git push origin vX.Y.Z`. The *Release* workflow builds the APK and
   creates the GitHub pre-release with it attached.

## Signing

Set these repository secrets for a release-signed APK:
`UG_KEYSTORE_BASE64` (the keystore, base64), `UG_KEYSTORE_PASSWORD`, `UG_KEY_ALIAS`, `UG_KEY_PASSWORD`.
Without them the workflow signs with the debug key and says so in the release notes.
Never commit a keystore.

## Signing key and F-Droid

The release key was created once with:

```sh
keytool -genkeypair -v -keystore ultimatephone-release.jks -alias ultimatephone \
  -keyalg RSA -keysize 4096 -validity 10000
```

It lives outside the repository (the owner keeps it and its passwords backed up) and in the four `UG_*` repository
secrets. Never commit a keystore. Certificate SHA-256 of the release key (give it to F-Droid as `AllowedAPKSigningKeys`):

```
5A:EC:73:7A:12:79:44:F9:6E:5B:DE:92:0B:83:D3:92:C0:27:65:DF:82:3E:82:7C:D3:EB:0D:E5:A3:22:68:EF
```

F-Droid builds the same source, compares its APK with the one published on GitHub (the build is reproducible) and ships
ours. Metadata goes in `fdroid/` and `fastlane/metadata/android/<locale>/` (Phase 6).
