# Releasing Summa

A release is a GitHub Release with the signed APK attached twice: as `Summa.apk` (the README's
download button points at `releases/latest/download/Summa.apk`, so this name is fixed) and as
`Summa-<version>.apk`, plus `SHA256SUMS`.

**Every release must be signed with the Summa release key** (alias `summa`, certificate SHA-256
`57:24:72:36:A8:E5:43:8C:38:F6:18:1B:F6:62:57:77:C2:C9:1D:B0:2B:16:F0:52:E1:07:74:5E:6B:5E:6D:32`).
Android only installs an update over an existing app when both are signed with the same key; a
release signed with anything else makes everyone uninstall first (and lose their sheets). The key
lives with the maintainer in `~/.config/summa/` (`keystore.jks`, `keystore.pass`), backed up to private storage; it is never committed (`.gitignore` covers `*.jks`, `*.keystore`, `*.pass`).

## Steps

1. On `main`: bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`.
2. Add `docs/release-notes/<version>.md` (what's new, in plain words) and the same under a new
   heading in `CHANGELOG.md`.
3. Commit, then run:

```bash
tools/release.sh            # engine tests, signed build, certificate check, checksums → executables/release-<version>/
tools/release.sh --publish  # … and push, then create the GitHub release v<version> with the notes
```

`tools/release.sh` refuses to continue if the notes file is missing, the key isn't there, the APK
isn't signed with the Summa certificate, or (with `--publish`) there are uncommitted changes.

## Google Play

Play takes an Android App Bundle instead of an APK: `./gradlew :app:bundleRelease` builds
`app/build/outputs/bundle/release/app-release.aab`, signed with the same key (it serves as the
upload key). The listing, graphics and policy answers are in `store-submission/` (see its README,
including the one-time app-signing choice).

## Checking a release by hand

```bash
apksigner verify --print-certs executables/release-<version>/Summa.apk | grep SHA-256
sha256sum -c executables/release-<version>/SHA256SUMS
```

If `apksigner` isn't on PATH, it's in `$ANDROID_HOME/build-tools/<version>/`.
