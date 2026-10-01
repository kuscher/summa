# Changelog

## 1.3.3 (2026-10-01)

- **On Google Play, Summa is now for Googlebooks and other desktop-class Android devices only**
  (devices that report themselves as a PC). Nothing changes in the app. The APK from GitHub still
  installs on Android 12 or newer phones and tablets.

## 1.3.2 (2026-10-01)

Small things that make Summa behave like a desktop app.

- **Start typing right away:** with a keyboard attached, the cursor is in the sheet as soon as it
  opens (a new sheet, another sheet from the list, back from Settings). On phones only a new, empty
  sheet takes the cursor, so reading a sheet doesn't pop up the keyboard.
- **Esc does what you'd expect:** in the search field it clears the search, and once more goes back
  to the sheet; in Settings it goes back to the sheet.
- Summa now says it doesn't need a touchscreen, so laptops and desktops with only a keyboard and
  mouse or trackpad can install it from Google Play.

## 1.3.1 (2026-09-30)

- Fixed: an answer could keep showing its previous value until something else redrew the sheet,
  for example `€3.00` after you finished typing `3 EUR in USD`. It now changes as you type.

## 1.3 (2026-09-30)

- **The mini calculator shows the sheet you're on**, not a separate scratch sheet. It's the same
  live sheet as the big window: what you type in one is there in the other. From the Quick
  Settings tile or the launcher it opens the sheet you had open last. The header shows its title.
- **Tooltips:** rest the pointer on a button for a moment (+, the sheet-list button, mini calculator,
  Share, More, Back, keep on top) and a small label appears, with its keyboard shortcut.
- Two windows on the same sheet now share it too, instead of saving over each other.

## 1.2.2 (2026-09-30)

- **Open-source licences in the app:** Settings › About › Open-source licences lists everything
  Summa includes (AndroidX, Compose, Kotlin, Material Symbols under Apache 2.0; the fonts under the
  SIL Open Font License; GeoNames under CC BY 4.0; ECB and CoinGecko data) with the full licence
  texts. The same list is in THIRD_PARTY_NOTICES.md.
- **Long answers fit on phones:** they shrink a little instead of being cut off with "…".
- A privacy policy (PRIVACY.md) and a Google Play submission kit (store-submission/).

## 1.2.1 (2026-09-30)

- The example sheet for new installs is now a **Munich weekend**: flights, a hotel paid in dollars,
  MVV day tickets, pretzels and beer, split three ways, and a day trip to Salzburg. The welcome
  sheet's time-zone line reads `3 pm Munich in Tokyo`.

## 1.2 (2026-09-30)

- **Big window and mini window take turns:** opening the mini calculator from the header (or
  Ctrl+Shift+M) closes the big window; the mini window's ⤢ button, or Ctrl+Shift+M again, closes
  it and brings the big window back on the sheet you had open.
- The new icon inside the app too: on the Calculate card and in Settings › About.

## 1.1.1 (2026-09-30)

- **A simpler icon:** one circle in four quadrants, each a slightly different tangerine, with
  + − × ÷ in white. The launch screen shows it too.

## 1.1 (2026-09-30)

Just the sheet. Summa is now much closer to Numi: you type on the left, answers appear on the right,
and nothing floats over the page.

- **Answers are plain text** on the right, in the theme's colour; totals are bolder. Click one to
  copy it (it says "Copied" for a moment). Right-click: Copy, Copy line with answer, Convert to.
- **Suggestions are grey text** after the cursor: Tab (or →) takes one, Esc hides it. No pop-up.
- **The sheet list is a history:** newest first, with search and +. Right-click a sheet to open it
  in a new window, duplicate or delete it; a delete shows Undo right in the list.
- **The header sits below the window's own title bar**, which is painted the same colour, so the
  two read as one bar and the system's handle and window buttons work as usual.
- **One Share button** for copy with answers, share as text or PDF (new), export and print.
  The ⋮ menu has New sheet, New window, Delete and Settings.
- **Settings trimmed** to eleven rows. Line numbers are off unless you turn them on.
- **The mini calculator** is just the sheet and its keep-on-top pin.
- **Gone:** the floating toolbar and display, the tonal answer column, the current-line highlight,
  the autocomplete pop-up, the phone keypad, the exchange-rate chip, pop-up messages, folders,
  pins, the trash view, and the settings for answer column width, monospaced text, slashed zero,
  thousands separators, trigonometry mode and what $ means (they're back to their defaults).
  Deleted sheets are removed for good after a week. Everything Summa can calculate stays.

## 1.0 (2026-09-30)

Parity with Soulver and Numi, and polish.

- **Money over time:** compound interest (`$10,000 at 5% for 10 years compounded monthly`, or
  daily, quarterly, continuously…), `interest on …`, `simple interest on …`, and loan payments
  (`loan of $300k at 6% for 30 years` → `$1,798.65/month`; also `mortgage of`, `monthly payment on`).
  Rates can be per month (`1% a month`) or a variable (`at rate`).
- **Tax both ways:** `€1,350 incl 19% VAT` adds it, `€120 without 20% VAT` (or `excl`, `ex`,
  `net of`) takes an included tax out. `19% VAT` stays 19% even if you've defined `vat`.
- **Public holidays** for 22 countries (nationwide days, with substitute and observed days), picked
  from your region or in Settings › Dates. Workday maths skips them: `workdays until Dec 18`,
  `5 workdays after Dec 22`, `workdays between Oct 1 and Oct 31` (both ends count), and `next holiday`.
- **Definitions sheet** (in the sheet list, and Settings › Definitions): its variables, units and
  functions work in every sheet, the mini calculator and Calculate.
- **Your own units:** `1 watermelon = 20 lb`, `1 coffee = $4.50` (follows the exchange rates),
  `1 sprint = 2 weeks`, `1 box = 12`; plurals work (`3 watermelons`, `$20 in coffees`).
- **Your own functions:** `tip(bill) = bill × 18%`, `area(w, h) = w × h`, even recursive ones with `if … then … else`.
- **More functions:** `npr`/`permutations`, `secant`, `csc`, `cot`, `asinh`, `acosh`, `atanh`.
- **Export as PDF, web page (HTML) or CSV**, next to Markdown and text, and **Print** (Ctrl+P; the
  system dialog can also save a PDF). PDFs and pages keep the sheet's colours.
- **Long sheets are fast:** a 2,000-line sheet re-evaluates in about 50 ms, and typing stays smooth
  (answers and colours are drawn for the lines near the screen).
- **Accessibility:** TalkBack reads each answer with its line, the answer and sheet menus are
  available as actions, keys are buttons, and comments meet 4.5:1 contrast in every theme.
- Fixed: the embedded font names no longer mention Google Sans (they're Summa Sans and Summa Mono throughout).

## 0.3 (2026-09-30)

At home on the Googlebook.

- **Title-bar header:** the sheet title, rates chip and actions draw into the window's caption bar,
  clear of the system's window buttons (and they follow the buttons when you resize).
- **Mini calculator** with its own scratch sheet. On Android 17 desktops it stays on top of other
  windows (pin button; on by default, Settings › Mini calculator). Opens from a new **Quick Settings
  tile**, the launcher, the header or **Ctrl+Shift+M**.
- **"Calculate"** in every app's text-selection menu: a card with the answer, Copy, Insert answer
  and Open in Summa.
- **Open in new window** for any sheet, and the taskbar's New window (Ctrl+Shift+N).
- **Launcher shortcuts:** New sheet, Mini calculator. **Share text** to Summa to start a sheet.
- **Drag answers** out into other apps.
- **Shortcut helper:** Summa's shortcuts appear in the system's list (Search+/).
- **Phone keypad** in Material 3 Expressive: keys that squash as you press them, a row of units and
  words, and a cookie-shaped return key.
- **Handoff** of the open sheet between devices (Android 17).
- Fixed: answers kept the old colour after switching between light and dark.

## 0.2 (2026-09-30)

Units, money and time.

- **Live exchange rates:** 166 currencies and metals from Frankfurter (central-bank data), with the
  ECB's rates as a fallback and a snapshot built in. At most twice a day, only while the switch in
  Settings › Money is on. A chip on sheets that use money shows the source and date; click it to update.
- **Crypto prices** (Bitcoin, Ether, Solana and more) from CoinGecko, if you turn them on.
- **Over 6,000 cities offline** (GeoNames), every country by its capital, and zone abbreviations:
  `3 pm Munich in San Francisco`, `time in Japan`, `9am Bangalore in NYC` (→ Yesterday 11:30 pm).
- **Autocomplete** for units, currencies, functions and your own names. Tab inserts, Esc hides.
- **Convert to…** on an answer's right-click menu: pick a unit or currency and the line is rewritten.
- Named dates: `days until Christmas`, `easter`, `thanksgiving`, `new year`, and `week number`.

## 0.1 (2026-09-30)

The first release: a notepad calculator you can use every day.

- Plain-language maths with live answers in a column on the right, aligned with each line.
- Units, money (bundled ECB rates), percentages in every form, dates, times and time zones,
  variables, `line6` references, `prev`, `sum`/`average` over a block, and hex/binary/fraction formats.
- A sheet library with pinning, folders, search and trash. Sheets save as you type.
- Keyboard shortcuts, right-click menus on answers and sheets, and a floating toolbar whose display
  shows the current answer or a statistic over the selected lines.
- Tangerine, Cobalt, Lime and Berry themes, or your wallpaper's colours; light and dark.
- Import and export plain text, Markdown with answers, and Numi's `.numi` files.
