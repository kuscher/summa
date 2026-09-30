# Google Play submission kit

Everything the Play Console asks for when publishing Summa, ready to copy or upload. Requirements
were checked against Google's Play Console Help on 30 September 2026 (store listing assets,
Data safety, testing requirements for new personal accounts).

## What's here

| Play Console field | File | Limit / spec |
|---|---|---|
| App name | [listing/en-US/title.txt](listing/en-US/title.txt) | 30 characters (25 used) |
| Short description | [listing/en-US/short-description.txt](listing/en-US/short-description.txt) | 80 characters (68 used) |
| Full description | [listing/en-US/full-description.txt](listing/en-US/full-description.txt) | 4,000 characters (about 2,400 used) |
| Release notes ("What's new") | [listing/en-US/release-notes.txt](listing/en-US/release-notes.txt) | 500 characters |
| App icon | [graphics/icon-512.png](graphics/icon-512.png) | 512 × 512, 32-bit PNG with alpha, full square (Play rounds the corners), under 1 MB |
| Feature graphic | [graphics/feature-graphic.png](graphics/feature-graphic.png) | 1024 × 500, 24-bit PNG, no alpha |
| Phone screenshots | [graphics/phone/](graphics/phone) (6) | 1080 × 1920, 9:16, 24-bit PNG (meets the "4+ at 1080 px" promotion bar) |
| Chromebook / large-screen screenshots | [graphics/large-screen/](graphics/large-screen) (4) | 1920 × 1080, 16:9 |
| 10-inch tablet screenshots | reuse [graphics/large-screen/](graphics/large-screen) | same spec as large screens |
| 7-inch tablet screenshots | optional; the phone set also fits the spec | |
| Store settings, contact, category | [forms/store-settings.md](forms/store-settings.md) | the support email is yours to fill in |
| Privacy policy | [../PRIVACY.md](../PRIVACY.md) → https://github.com/kuscher/summa/blob/main/PRIVACY.md | required for every app |
| Data safety | [forms/data-safety.md](forms/data-safety.md) | "No data collected" |
| Content rating (IARC) | [forms/content-rating.md](forms/content-rating.md) | expected: Everyone / PEGI 3 |
| Other App content declarations | [forms/app-content.md](forms/app-content.md) | ads, audience, financial features… |

The listing text avoids what Play's metadata policy rules out: rankings or superlatives ("best",
"#1"), promotional words ("new", "sale"), testimonials, emoji and calls to action, and other
companies' app names. Every screenshot is Summa's real UI (see "Remaking the graphics").

## Steps

1. **Create the app** in the Play Console (personal developer account): name *Summa: Notepad
   Calculator*, default language English (US), App, Free, accept the declarations.
2. **App signing (decide once, it can't be undone).** Recommended: *Use existing app signing key*
   and upload the Summa key with Google's PEPK tool, as the Console explains. Then the Play build
   and the APKs on GitHub have the same signature, and people can move between them without
   uninstalling. (If you let Google generate a new key instead, Play installs and GitHub installs
   can't update each other.) The same keystore is used as the upload key. Key:
   `~/.config/summa/keystore.jks` (alias `summa`), backup in a private folder.
3. **Build the bundle** (Play only takes .aab files):
   ```bash
   ./gradlew :app:bundleRelease     # app/build/outputs/bundle/release/app-release.aab, signed with the Summa key
   ```
   Each upload needs a higher `versionCode` than the last one (in `app/build.gradle.kts`).
4. **Closed test first.** New personal developer accounts (created after 13 November 2023) must run
   a closed test with at least 12 testers, opted in for 14 days in a row, before applying for
   production access. Google also checks that testers actually used the app.
5. **Store listing:** paste the texts from `listing/en-US/`, upload the icon, feature graphic and
   screenshots from `graphics/`.
6. **App content:** answer the forms as in `forms/`, set the privacy policy URL.
7. **Release:** add the bundle to the track, paste `release-notes.txt`, roll out.

## Remaking the graphics

The screenshots are Summa's own UI, rendered at store resolution on a Googlebook with a debug
build (a private virtual display, so nothing else on the screen is captured), from the sheets in
[screenshot-sheets/](screenshot-sheets):

```bash
./summa render phone 1080 1920 420 0 store-submission/screenshot-sheets/p1.txt shots/phone-1.png   # p1–p4, then d1 = p1 in dark (last arg 1)
./summa render list  1080 1920 420 0 store-submission/screenshot-sheets/p1.txt shots/phone-6.png
./summa render large 1920 1080 240 0 store-submission/screenshot-sheets/l1.txt shots/large-1.png   # large-2: focus + dark with l2; large-3: large with l3
./summa shot mini-pinned    # the real mini window, pinned; ./summa shot calc-card for the Calculate card
python3 tools/store_assets.py shots/
```

`tools/store_assets.py` writes everything in `graphics/` (and the extra README images).
