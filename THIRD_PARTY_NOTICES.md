# Third-party notices

Summa's own code is released under the [MIT License](LICENSE). Summa includes or uses
the following, under their own licences.

## Apache License 2.0

Full text: [app/src/main/assets/licenses/Apache-2.0.txt](app/src/main/assets/licenses/Apache-2.0.txt).

- Android Jetpack (AndroidX): Core, Activity, Lifecycle, Compose UI, Foundation,
  Material 3, and the libraries they include. Copyright The Android Open Source Project.
- Kotlin standard library. Copyright JetBrains s.r.o. and Kotlin Programming Language contributors.
- kotlinx.coroutines and kotlinx.serialization. Copyright JetBrains s.r.o. and contributors.
- Material Symbols Rounded (icons, subset). Copyright Google LLC.
  https://github.com/google/material-design-icons
- Material Color Utilities (used when building Summa to generate its colour
  schemes; not included in the app). Copyright Google LLC.

## SIL Open Font License 1.1

Full text: [app/src/main/assets/licenses/OFL-GoogleSans.txt](app/src/main/assets/licenses/OFL-GoogleSans.txt).

- "Summa Sans" is a modified (subset and renamed) version of Google Sans Flex.
  Copyright 2015 The Google Sans Flex Authors
  (https://github.com/googlefonts/googlesans-flex).
- "Summa Mono" is a modified (subset and renamed) version of Google Sans Code.
  Copyright 2025 The Google Sans Code Project Authors
  (https://github.com/googlefonts/googlesans-code).
  The fonts were renamed because Google's trademark notes ask that modified
  versions not use the Google Sans name. "Google" and "Google Sans" are
  trademarks of Google LLC.

## Creative Commons Attribution 4.0
- City names and time zones: GeoNames (https://www.geonames.org), licensed under
  CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/). Summa uses a subset
  (cities of 100,000 people or more, and capitals), reduced to name, country and
  time zone.

## Data

- Exchange rates: European Central Bank euro foreign exchange reference rates
  (source: ECB, https://www.ecb.europa.eu), bundled for offline use and
  downloaded as a fallback. Frankfurter (https://frankfurter.dev, open source,
  MIT) is the main source of live rates. Crypto prices, only if you turn them on:
  data provided by CoinGecko (https://www.coingecko.com).
- Unit definitions follow the international definitions as published in the
  Unicode CLDR (unit factors are facts; no CLDR files are included).
- Time zones come from Android's copy of the IANA time zone database.

In the app: Settings › About › Open-source licences shows all of the above with the full licence texts.
