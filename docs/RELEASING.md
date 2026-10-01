# Releasing Summa

A release is a GitHub Release with the signed APK attached twice: as `Summa.apk` (the README's
download button points at `releases/latest/download/Summa.apk`, so this name is fixed) and as
`Summa-<version>.apk`, plus `SHA256SUMS`. The same tag also puts the Play bundle on Google Play
as a draft.

**The short version, for people and agents:** nobody needs the key file. Bump the version, write
the notes, push a tag `v<version>`, and GitHub builds, signs and publishes.

**Every release must be signed with the Summa release key** (alias `summa`, certificate SHA-256
`1D:EA:BA:8D:67:3F:B4:F1:00:C3:D9:74:78:D2:2E:EE:25:50:CD:E5:4D:C2:6B:65:99:C3:F3:4B:5F:F5:79:0F`).
Android only installs an update over an existing app when both are signed with the same key; a
release signed with anything else makes everyone uninstall first (and lose their sheets). The key
lives in the repo's `release` environment on GitHub and with the maintainer in `~/.config/summa/` (`keystore.jks`, `keystore.pass`), backed up with its password to private storage (folder a private folder). It replaced the original key on 30 September 2026, so
installs of 1.3.1 and earlier from GitHub have to be uninstalled once; it is never committed (`.gitignore` covers `*.jks`, `*.keystore`, `*.pass`).

## Steps

1. On `main`: bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`.
2. Add `docs/release-notes/<version>.md` (what's new, in plain words) and the same under a new
   heading in `CHANGELOG.md`. Put Google Play's "What's new" text (500 characters at most) in
   `store-submission/listing/en-US/release-notes.txt`.
3. Commit and push, then tag the commit and push the tag:

```bash
git tag v1.3.3 && git push origin v1.3.3
```

The tag runs `.github/workflows/release.yml`, which:
- checks that the notes file exists, that `versionName` matches the tag, and that the Play text fits;
- runs the engine tests and lint, and builds and signs the APK and the Play bundle (.aab);
- refuses to publish if either isn't signed with the Summa certificate;
- publishes the GitHub release with `Summa.apk`, `Summa-<version>.apk` and `SHA256SUMS`;
- uploads the bundle to Google Play's closed-testing track as a **draft** release
  (`tools/play-upload.mjs`), with the Play text. What is live there stays live.

**Google Play, the last step by hand:** a draft is not reviewed or served. In the Play Console, open
Summa › Test and release › Closed testing › the draft › Next › Save, then Publishing overview › Send
for review. Nothing goes to review on its own.

To test without publishing, press **Run workflow** on the Actions tab (Release), or run
`gh workflow run release.yml --ref main`: the same signed build and certificate checks, a check
that the Play key works, and nothing published.

If a tag's run fails after the GitHub release was made (for example at the Play step), fix the cause
and re-run the failed job; re-uploading a version code that is already on Play does nothing.

### Where the secrets are

Two GitHub environments that only `main` and `v*` tags can use: `release` holds the signing key
(`SIGNING_KEYSTORE_B64`, the keystore in base64, and `SIGNING_KEYSTORE_PASS`), and `play` holds
the Google Play key (`PLAY_SERVICE_ACCOUNT_JSON`). Workflows from forks and pull requests never get
them. Anyone with write access can push a tag, and so could also read the key through a workflow of
their own: give write access only to people you'd trust with the key. The job that holds the key
runs only GitHub's own actions, pinned to exact commits.

Restoring them (for example after a key restore from the backup):
- `base64 < ~/.config/summa/keystore.jks | tr -d '\n' | gh secret set SIGNING_KEYSTORE_B64 --env release`
- `gh secret set SIGNING_KEYSTORE_PASS --env release < ~/.config/summa/keystore.pass`
- `jq -c . play-service-account.json | gh secret set PLAY_SERVICE_ACCOUNT_JSON --env play` (the Play
  Console service account's key; the maintainer has it. One line, so the job log masks the whole
  value and not every brace)

### By hand (fallback, on a machine that has the key)

```bash
tools/release.sh            # engine tests, signed build, certificate check, checksums → executables/release-<version>/
tools/release.sh --publish  # … and push, then create the GitHub release v<version> with the notes
```

`tools/release.sh` refuses to continue if the notes file is missing, the key isn't there, the APK
isn't signed with the Summa certificate, or (with `--publish`) there are uncommitted changes.

## Google Play

Play takes an Android App Bundle instead of an APK. The release workflow builds and uploads it (see
above); by hand, `./gradlew :app:bundleRelease` builds
`app/build/outputs/bundle/release/app-release.aab`, signed with the same key (it serves as the
upload key), and `PLAY_SERVICE_ACCOUNT_FILE=<key.json> node tools/play-upload.mjs
io.github.kuscher.summa <app.aab> store-submission/listing/en-US/release-notes.txt --name <version>`
puts it on Play as a draft. The listing, graphics and policy answers are in `store-submission/` (see its README,
including the one-time app-signing choice).

## Checking a release by hand

```bash
apksigner verify --print-certs executables/release-<version>/Summa.apk | grep SHA-256
sha256sum -c executables/release-<version>/SHA256SUMS
```

If `apksigner` isn't on PATH, it's in `$ANDROID_HOME/build-tools/<version>/`.
