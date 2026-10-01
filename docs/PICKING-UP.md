# Picking up Summa

State and next steps, for whoever continues (human or Claude). Keep this current at every milestone.
Read CLAUDE.md first (layout, dev loop, gotchas).

## Where things are (v1.3.5 in main, 2026-10-01)
- 1.3.5 (code 15; released 2026-10-01 with the tag `v1.3.5`: on GitHub, and on Google Play's
  closed-testing track as a draft that still has to be sent for review in the Play Console): the user reported "opening a new tab doesn't let me edit anything inside it and no
  cursor is seen" (Play install). Cause: Compose (ui 1.13 alpha, `AutoClearFocusBehavior.CursorBased`
  by default) clears the focus when a mouse or trackpad is pressed outside the focused node, and the
  sheet's text field was only as tall as its text, so one click on the empty page of a new sheet took
  the cursor away and no click there brought it back. Touch taps never did this, which is why it
  went unnoticed. Three changes: `setSummaContent` (ui/Window.kt) turns the auto-clear off for the
  big and the mini window; the text field is at least as tall as the page (`heightIn(min = pageHeight)`
  in `SheetEditor`); and a tap on the rest of the page (margins, line numbers, around the answers)
  puts the cursor at the nearest place in the text and focuses the sheet.
- 1.3.4 (code 14): `android.hardware.type.pc` is back to `required="false"`. The user, the same day: "make
  summa actually available on phones too since that works". Only the manifest changed again. On Play the
  ChromeOS devices excluded by hand for 1.3.3 were included again (Device catalog).
- 1.3.3 (code 13): only the manifest changed. `android.hardware.type.pc` is `required="true"`, so Google
  Play offers Summa only to PC-type devices (Googlebooks report it; the user asked for Play to target
  Googlebooks, or at least desktop Android devices, before production). Android doesn't enforce the
  feature at install time, so GitHub's APK still installs on phones. To offer phones on Play again,
  set it to `required="false"`. Released with the tag `v1.3.3` (the first release through the tag
  workflow).
- 1.3.2 (code 12; on Google Play's closed-testing track, sent for review 2026-10-01 from the Mac with the new key; NOT yet
  released on GitHub: push the tag `v1.3.2` and the release workflow does it, see docs/RELEASING.md): the sheet takes
  focus on open when a hardware keyboard is present (`SheetEditor` LaunchedEffect; phones only for
  an empty sheet), Esc clears/leaves search and leaves Settings, and the manifest declares
  `android.hardware.touchscreen` not required. These came from checking Summa against the adaptive
  app quality guidelines for Play's "Desktop optimized" badge; the user declined 48dp targets,
  Ctrl+wheel zoom, scrollbars, file handlers and requestFullscreenMode ("makes little sense").
- 1.3: the mini window shows the current sheet via the shared `Sessions` hub (the old scratch
  sheet file stays hidden and unused); hover tooltips (`HoverTip`) on icon buttons.
- 1.2.2: licence texts in the app (assets/licenses + Settings › About › Open-source licences,
  THIRD_PARTY_NOTICES.md), PRIVACY.md, the Google Play kit in store-submission/ (texts, graphics,
  policy answers; `ui/Shots.kt` + `tools/store_assets.py` remake the screenshots). Answers auto-size.
  Not yet on Play: the user needs a developer account, the app-signing choice and a 12-tester closed test.
- 1.2.1: example sheet is a Munich weekend.
- 1.2: the big window and the mini calculator take turns (`MainActivity.switchToMini`,
  `MiniActivity.backToBig`); the icon (1.1.1: four tangerine quadrants with + − × ÷, `tools/logo.py`)
  also appears in the app via `SummaMark`. Release steps: docs/RELEASING.md.
- 1.1 simplified the app to the approved Numi-like design (canvas
  https://claude.ai/artifact/3JoM2SnRZyabq32f2XaFmt): no floating UI, plain answers, ghost-text
  autocomplete, sheet history on the left, Share + ⋮ menus, 11 settings. The engine didn't change.

## Before 1.1 (v1.0)
- Releases v0.1, v0.2, v0.3 and v1.0 are on GitHub (`tools/release.sh --publish`), all signed with
  the key in ~/.config/summa (a new key since 2026-09-30, also Google Play's; backed up with its password to a private folder).
- Engine: 386-case golden corpus (`corpus.tsv`), holiday tests, fuzz, and a 2,000-line timing test
  all pass (`./summa test`). ~18 ms cached / ~32 ms after an edit for 2,000 lines on the VM,
  ~50 ms on the HP (release build). `PerfProbe` times any sheet: `SUMMA_PERF=file ./gradlew :engine:test --tests '*PerfProbe*' -i`.
- App (as of 1.1): editor with plain answers (virtualized to the viewport), sheet history, settings,
  caption-bar header, pinned mini window, Calculate card, QS tile, Handoff, exports (text,
  Markdown, CSV, HTML, PDF), PDF sharing and printing. Folders/pins/trash data may still exist in
  index.json from 1.0 but aren't shown; trashed sheets are purged after 7 days.
- Plan and research: artifact SGXCvaPbcSA7n6digX4Hsy, sources in ~/calc-plan.

## How the v1.0 features hang together
- Finance: `Finance` node (Parser) ← "at … for …" after any amount, or the prefixes loan/interest/
  simple interest. Keywords `for`, `loan`, `interest`, `compounded X` only count in lines with "at"
  and a percentage (`Tokenizer.financeContext`), so "$20 for lunch" is untouched.
- Tax: `incl`/`with` → PctApply("on"), `excl`/`without`/`ex`/`net of` → PctOfWhat("on"); they need a
  percentage or a variable right after them.
- Holidays: `Holidays.kt` rules per country (fixed dates, nth weekdays, Easter offsets, US observed,
  UK-style substitute days). `EngineSettings.holidays` is the country code; the app maps
  Settings › Dates (auto = device region).
- Definitions: `Library.DEFINITIONS` is a normal sheet file hidden from the list (opened from Settings). Its session
  publishes `SheetResult.definitions` to `SummaApp.definitions`; every other session and the
  Calculate card pass that into `SheetEngine.definitions`. At start `SummaApp.loadDefinitions()`
  evaluates the saved file.
- Custom units (`1 NAME = value`, Sheet.parseUnitDef) become `UnitDef`s; money-based ones keep
  `via` so rates stay live. User functions (`name(a, b) = …`, Sheet.parseFunction) keep body tokens
  with the parameters as variables; `Evaluator.callUser` binds arguments (depth limit 48).
- Parse cache key = (line, 64-bit hash of the names in scope), see `Scope.mix`.
- Exports: `ui/Export.kt` (`SheetSnapshot` → CSV/HTML/PDF; printing uses the same PDF renderer
  through a `PrintDocumentAdapter`).

## Ideas after 1.0 (not promised)
- German keywords (plan said "German after v1.0"), per-sheet number format override.
- Regional holidays (states, cantons), Japan/India/China calendars.
- Charts of a column of answers; a Glance/widget for the mini sheet.
- Stocks and weather were "later, if ever" in the plan.
