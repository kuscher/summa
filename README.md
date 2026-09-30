<p align="center">
  <img src="docs/images/icon.png" width="112" alt="Summa icon: a tangerine calculator with a display and plus, minus, times and divide keys">
</p>

<h1 align="center">Summa</h1>

<p align="center">
  <b>A notepad calculator for Googlebooks and Android.</b><br>
  Type maths the way you'd say it: <code>Hotel: 3 nights × $142 in EUR</code>. Answers line up on the right as you type.
</p>

<p align="center">
  <a href="../../releases/latest/download/Summa.apk"><b>⬇ Download Summa.apk</b></a>
  &nbsp;·&nbsp; <a href="#install">Install</a>
  &nbsp;·&nbsp; <a href="#what-you-can-type">What you can type</a>
  &nbsp;·&nbsp; <a href="#privacy">Privacy</a>
  &nbsp;·&nbsp; <a href="CHANGELOG.md">What's new</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Googlebook_OS-Android_17-FF7A31" alt="Googlebook OS, Android 17">
  <img src="https://img.shields.io/badge/Android-12%2B-2E2946" alt="Android 12 and up">
  <img src="https://img.shields.io/badge/Material_3-Expressive-3450C4" alt="Material 3 Expressive">
  <img src="https://img.shields.io/badge/license-MIT-555555" alt="MIT license">
  <img src="https://img.shields.io/badge/developed_entirely_on-a_Googlebook-F0520C" alt="Developed entirely on a Googlebook">
</p>

<p align="center"><sub>A personal hobby project by <a href="https://github.com/kuscher">Alexander Kuscher</a>, proudly developed entirely on a Googlebook.
Not affiliated with or endorsed by any employer (<a href="#about-this-project">more</a>). Inspired by Soulver and Numi.</sub></p>

<p align="center">
  <img src="docs/images/hero.png" width="880" alt="Summa on a Googlebook: a trip budget and a road trip with answers in a tonal column on the right">
</p>

## What it does

Summa is a sheet of paper that does the maths. Write a line in plain words and the answer appears
next to it, on the right. Change a number and everything that depends on it updates.

- **Words around numbers are fine.** `Lunch: $12 + 15% tip`, `Flights: 2 × €189`. Summa skips the
  words it doesn't know and works out the rest. A line that isn't maths simply has no answer.
- **Units and money.** `5 km in miles`, `72 °F in °C`, `€49 in USD`, `6.4 L/100 km in mpg`,
  `$25/hour × 14 hours`, `1 cm in px at 326 ppi`.
- **Percentages, every way.** `20% of $10`, `5% on $30`, `6% off 40 EUR`, `$50 as a % of $100`,
  `20 is 10% of what`, `50 to 75 is what %`.
- **Dates and time zones.** `days until Dec 25`, `next friday + 2 weeks`, `8:30 am + 3 h 20 min`,
  `3 pm Lisbon in Tokyo`, `time in New York`.
- **Your own names.** `rate = $85/hour`, then `rate × 6.5 hours`. Refer to a line with `line6`,
  the line above with `prev`, and add up a block with `sum`.
- **Formats.** `255 in hex`, `0.2 as fraction`, `1/3 to 2 dp`, `$490 rounded to nearest hundred`.

It's built for a laptop: a sheet list on the side, keyboard shortcuts, right-click menus and a
floating toolbar with a **display** that shows the current line's answer, or the sum of the lines
you select.

## What you can type

| You type | Summa answers |
|---|---|
| `Hotel: 3 nights × $142 in EUR` | `€375.17` |
| `$10 for lunch + 15% tip` | `$11.50` |
| `20% discount off $500` | `$400.00` |
| `5 feet 11 inches in cm` | `180.34 cm` |
| `313 km × 6.4 L/100 km` | `20.03 L` |
| `€30/day in €/month` | `€913.11/month` |
| `days until Dec 25` | `86 days` |
| `2:30 pm HKT in Berlin` | `8:30 am` |
| `0x9F31 to decimal` | `40,753` |
| `average of 36, 42, 19 and 81` | `44.5` |

Over 300 more examples, taken from the Soulver and Numi documentation, run as tests on every build
(`engine/src/test/resources/corpus.tsv`).

## Install

Summa is made for Googlebooks (Googlebook OS, Android 17) and also runs on Android 12 or newer
phones and tablets.

1. On your Googlebook or phone, download **[Summa.apk](../../releases/latest/download/Summa.apk)**
   from the latest release.
2. Open it from Chrome's downloads or the Files app. If Android asks, allow Chrome (or Files) to
   install apps, then tap **Install**.
3. Open **Summa**. It starts with a short welcome sheet and an example trip budget.

To update, install a newer `Summa.apk` over the old one. Your sheets stay.

## Keyboard

| Keys | Does |
|---|---|
| Ctrl+N | New sheet |
| Ctrl+K | Search sheets |
| Ctrl+Shift+C | Copy the current line's answer |
| Ctrl+/ | Turn lines into notes (and back) |
| Ctrl+D | Duplicate the line |
| Alt+↑ / Alt+↓ | Move the line up or down |
| Ctrl+\ | Insert `prev` (the answer above) |
| Ctrl+B | Show or hide the sheet list |
| Ctrl+, | Settings |

## Privacy

Summa keeps your sheets on your device, in the app's own storage, and Android's backup copies
them to your Google account if you have backup turned on. There are no accounts, ads or analytics.

## Made on a Googlebook

Everything here was written, built and tested on a Googlebook, in its built-in Linux Terminal:

- The calculation engine is plain Kotlin, tested with JUnit in the Terminal in a few seconds.
- The app is Kotlin and Jetpack Compose with Material 3 Expressive, built with Gradle in the same
  Terminal and installed on the Googlebook's own Android over adb.
- Screenshots in this README are the app's own window, captured on the device.

<sub>With a little help from Claude.</sub>

## About this project

Summa is my personal hobby project, made by me, [Alexander Kuscher](https://github.com/kuscher).
It has no affiliation with my employer: my employer didn't make, sponsor, review or endorse it,
and Summa doesn't endorse my employer or its products either. The views, choices and any
mistakes here are mine alone.

Summa is inspired by [Soulver](https://soulver.app) and [Numi](https://numi.app), two lovely Mac
apps that showed how good a notepad calculator can be. It's an independent project and isn't made
by or affiliated with Acqualia (Soulver), Numi's maker, or Google.

— Alexander ([@kuscher](https://github.com/kuscher))

## License

Summa is free software under the [MIT License](LICENSE). It includes:

- **Summa Sans** and **Summa Mono**: Google Sans Flex and Google Sans Code, subset and renamed as
  the [SIL Open Font License 1.1](app/src/main/assets/licenses/OFL-GoogleSans.txt) allows.
- Google's [Material Symbols](https://fonts.google.com/icons) (Apache License 2.0).
- Exchange rates from the European Central Bank (source: ECB), bundled for offline use.

It uses Jetpack Compose, AndroidX, Kotlin and kotlinx.serialization (Apache License 2.0).
