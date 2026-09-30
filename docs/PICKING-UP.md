# Picking up Summa

State and next steps, for whoever continues (human or Claude). Keep this current at every milestone.
Read CLAUDE.md first (layout, dev loop, gotchas).

## Where things are (v1.1, 2026-09-30)
- 1.1 simplified the app to the approved Numi-like design (canvas
  https://claude.ai/artifact/3JoM2SnRZyabq32f2XaFmt): no floating UI, plain answers, ghost-text
  autocomplete, sheet history on the left, Share + ⋮ menus, 11 settings. The engine didn't change.

## Before 1.1 (v1.0)
- Releases v0.1, v0.2, v0.3 and v1.0 are on GitHub (`tools/release.sh --publish`), all signed with
  the key in ~/.config/summa (backed up to a private folder).
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
