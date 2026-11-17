# Reader Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (inline, this session). Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the five approved UI fixes from the reference comparison (reader footer, Aa in top bar, clearer Aa sheet, warm placeholder covers, aligned Library header).

**Architecture:** Presentation-only changes in `:app` Compose code. No data-model, DB, dependency or navigation change. Each task is test-first (Robolectric Compose tests already used in `app/src/test`).

**Tech Stack:** Kotlin, Jetpack Compose Material3, Robolectric compose tests, string resources EN+VI.

**Spec:** `docs/superpowers/specs/2026-11-17-reader-polish-design.md`

## Status board (update as tasks finish)

| # | Task | Status |
|---|---|---|
| 0 | Branch `reader-polish` from main | done |
| 1 | Warm placeholder cover tints | done |
| 2 | Library header alignment | done |
| 3 | Reader footer: gutter + chapter title | done |
| 4 | Aa button in top bar | done |
| 5 | Reading appearance sheet redesign | done |
| 6 | CI + emulator evidence + docs | done |

## Global Constraints

- Owner replies in Vietnamese; code, docs, commits in English.
- No AI attribution in commits (AGENTS.md rule 2 overrides the default trailer).
- Never push; no new Gradle dependency; no DB/schema change.
- Every user-visible string in `values/strings.xml` and `values-vi/strings.xml`; delete strings that become unused.
- CI command: `./gradlew :core:model:test :core:data:testDebugUnitTest :reader:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug` — baseline is 0 lint errors / 14 warnings, must not increase.
- 48 dp minimum touch targets; TalkBack names preserved.

## Review Focus

- Chapter title very long or Vietnamese with stacked diacritics: must ellipsize and never push the percentage off screen. (Task 3)
- Chapter title null or blank: no empty gap, percentage still right-aligned. (Task 3)
- PDF footer: `Page n / N` button still shown, no chapter/percent regression. (Task 3)
- 130 % font scale: `Aa` button and footer texts not clipped. (Tasks 3, 4)
- Switching scope in the sheet must show that scope's values; unset line height must not show a reset button. (Task 5)
- PDF books must not get an `Aa` button. (Task 4)

---

### Task 0: Branch

- [ ] **Step 1:** `git switch -c reader-polish` (from `main`, clean tree). Commit spec and plan first: `git add docs/superpowers && git commit -m "docs: add reader polish spec and plan"`.

### Task 1: Warm placeholder cover tints

**Files:**
- Modify: `app/src/main/kotlin/dev/kent/windbook/ui/components/BookCover.kt:24-27`
- Test: `app/src/test/kotlin/dev/kent/windbook/ui/components/BookCoverTest.kt`

**Interfaces:**
- Produces: `internal val GeneratedCoverTints: List<Color>` (was `private`), same name.

- [ ] **Step 1: Failing test** `generatedCoverTintsAreAllWarm`: for every colour in `GeneratedCoverTints`, assert `red >= blue` and `size >= 6`.
- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest --tests '*BookCoverTest'`. Expected: FAIL on `0xFFD9E8E2` and `0xFFDCE3EC` (compile error first because the list is private — acceptable RED).
- [ ] **Step 3:** Make the list `internal`; replace `0xFFD9E8E2` with sand `0xFFE9D8B8` and `0xFFDCE3EC` with clay `0xFFEBD6C8`. Keep the other four.
- [ ] **Step 4:** Rerun the test class. Expected: PASS.
- [ ] **Step 5:** Commit `fix(app): keep generated cover tints in the warm palette`.

### Task 2: Library header alignment

**Files:**
- Modify: `app/src/main/kotlin/dev/kent/windbook/library/LibraryScreen.kt:160-163` (the "Book folders" `TextButton`)
- Test: `app/src/test/kotlin/dev/kent/windbook/library/LibraryScreenTest.kt`

- [ ] **Step 1: Failing test** `bookFoldersTextAlignsWithLibraryTitle`: render a non-empty library state the way sibling tests in this file do; read `getBoundsInRoot().left` of the node with text "Book folders" and of the heading "Library"; assert `abs(diff) <= 1.dp`. Note: `TextButton` adds 12 dp internal padding, the header column adds 16 dp, so the button needs `Modifier.padding(start = 4.dp)`.
- [ ] **Step 2:** Run `--tests '*LibraryScreenTest'`. Expected: FAIL, difference ≈ 4 dp.
- [ ] **Step 3:** Add `modifier = Modifier.padding(start = 4.dp)` to that `TextButton`.
- [ ] **Step 4:** Rerun. Expected: PASS (whole class green).
- [ ] **Step 5:** Commit `fix(app): align Book folders button with the Library title`.

### Task 3: Reader footer — gutter and chapter title

**Files:**
- Modify: `app/src/main/kotlin/dev/kent/windbook/reader/ReaderScreen.kt:315-337` (footer inside `ReaderOverlay`)
- Test: `app/src/test/kotlin/dev/kent/windbook/reader/ReaderOverlayTest.kt`

**Interfaces:** `ReaderOverlay` signature unchanged; it already receives `chapterTitle: String?`.

- [ ] **Step 1: Failing tests** (edit the first test and add three):
  - Update `visibleOverlayShowsTitleMinimalProgressAndBack`: assert `"Book One"` and `"37%"` are both displayed and `"Book One · 37%"` does not exist.
  - `sliderKeepsASideGutter`: with `onSeek` set, `onNodeWithContentDescription("Reading position").getBoundsInRoot().left >= 16.dp`.
  - `blankChapterShowsOnlyThePercentage`: `chapterTitle = "  "` → `"37%"` displayed, no node whose text is blank-only needed; assert `onNodeWithText("37%")` exists.
  - `longVietnameseChapterKeepsPercentageVisible`: a 200-character Vietnamese title; assert `"37%"` is displayed.
- [ ] **Step 2:** Run `--tests '*ReaderOverlayTest'`. Expected: FAIL (no chapter text, slider at left 0).
- [ ] **Step 3:** In the footer: wrap `ReaderPositionSlider` with `Modifier.padding(horizontal = 16.dp)` (add a `modifier` parameter to `ReaderPositionSlider` if it lacks one, applied before `heightIn`). In the bottom row render, in order: PDF page button (unchanged), return button (unchanged), chapter `Text` (`chapterTitle?.takeIf { it.isNotBlank() }`, `weight(1f)`, 1 line, ellipsis, `labelLarge`, `onSurfaceVariant`), a `Spacer(weight(1f))` only when there is no chapter text, then the percentage `Text` (only when `pdfPage == null`), then mark-finished.
- [ ] **Step 4:** Rerun the class and `*ReaderControlsUiTest`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(app): show chapter title and keep a side gutter on the reader slider`.

### Task 4: Aa button in the top bar

**Files:**
- Modify: `ReaderScreen.kt` — `ReaderOverlay` top row (≈line 304) and `ReaderMoreMenu` (≈line 350-353)
- Test: `app/src/test/kotlin/dev/kent/windbook/reader/ReaderControlsUiTest.kt`, `ReaderOverlayTest.kt`

**Interfaces:** `onAppearance: () -> Unit` stays on `ReaderOverlay`; `ReaderMoreMenu` loses its `onAppearance` parameter.

- [ ] **Step 1: Failing tests**:
  - Update `contentsAndOverflowActionsAreReachable`: after opening ⋮, `"Reading appearance (Aa)"` text must **not** exist in the menu; Bookmark and Search still displayed.
  - Add `appearanceIsOneTapAwayForReflowableBooks`: `ReaderOverlay(..., onAppearance = { taps++ })` with `pdfDisplay = null`; `onNodeWithContentDescription("Reading appearance (Aa)").performClick()`; `assertEquals(1, taps)`.
  - Add `pdfHasNoAppearanceButton`: `pdfDisplay = EffectiveDisplay(PdfLayout.PAGED, false)`; the node does not exist.
- [ ] **Step 2:** Run both classes. Expected: FAIL.
- [ ] **Step 3:** In the top row, when `pdfDisplay == null`, add before Contents an `IconButton(onClick = onAppearance)` whose content is `Text("Aa", style = titleSmall)` with `semantics { contentDescription = stringResource(R.string.reader_appearance) }`. Remove the Appearance `DropdownMenuItem` and the unused parameter.
- [ ] **Step 4:** Rerun both classes. Expected: PASS.
- [ ] **Step 5:** Commit `feat(app): move Aa to the reader top bar`.

### Task 5: Reading appearance sheet redesign

**Files:**
- Modify: `app/src/main/kotlin/dev/kent/windbook/reader/ReaderTypographySheet.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-vi/strings.xml`
- Test: `app/src/test/kotlin/dev/kent/windbook/reader/ReaderControlsUiTest.kt`

**Interfaces:** `ReaderTypographySheet(...)` signature unchanged (Settings → reading defaults still uses it with `globalOnly = true`).

Strings: add `reader_scope_hint_book` ("Only this book changes.") and `reader_scope_hint_global` ("Every book changes, except books you set separately."), `reader_text_size_value` (`%1$s` for "Text size · …"), `reader_line_height_use_publisher` ("Use the book's own line height"); VI equivalents. Reuse `reader_font_publisher` as the "unset" value. Delete `reader_scope_hint` and `reader_inherit_hint` in both locales.

- [ ] **Step 1: Failing tests** (reuse the sheet harness of `twoAppearanceFieldsSurviveBeforeObservedValueCatchesUp`):
  - `scopeSelectorShowsSelectedSegment`: "This book" `assertIsSelected()`, "All books" `assertIsNotSelected()`; click "All books" → states swap and the global hint text is displayed.
  - `textSizeShowsItsNumber`: book typography `size = 120` → `"Text size · 120%"` displayed.
  - `lineHeightResetOnlyWhenSet`: with `lineHeight = null` the node "Use the book's own line height" does not exist; with `lineHeight = 1.6` it exists and clicking it emits `lineHeight = 0.0`.
  - `colorChipsHaveSwatches`: the Paper, Sepia and Dark chips each contain a node tagged `swatch:<name>` (use `Modifier.testTag("swatch:PAPER")` etc.).
  - Keep the existing two-field test green.
- [ ] **Step 2:** Run `--tests '*ReaderControlsUiTest'`. Expected: the four new tests FAIL.
- [ ] **Step 3:** Implement: replace the scope `TextButton` row with `SingleChoiceSegmentedButtonRow` (two `SegmentedButton`s, `selected = editingGlobal == …`); one hint `Text` chosen by scope; size label via `reader_text_size_value` with `"${size}%"` or `reader_font_publisher`; size slider `valueRange = 80f..200f, steps = 11` rounding to the nearest 10; line-height reset `TextButton` rendered only when `effective.lineHeight?.let { it > 0 } == true`; each colour `FilterChip` gets `leadingIcon = { Box(Modifier.size(16.dp).clip(CircleShape).background(swatch).border(1.dp, outline, CircleShape).testTag("swatch:$name")) }` with swatch colours PAPER `0xFFFFFFFF`, SEPIA `0xFFFAF4E8`, DARK `0xFF000000`, SYSTEM transparent (outline ring only).
- [ ] **Step 4:** Rerun the class. Expected: PASS. Also run `*Settings*` tests (the sheet is reused there).
- [ ] **Step 5:** Commit `feat(app): clearer reading appearance sheet with scope selector, values and swatches`.

### Task 6: CI, emulator evidence, docs

**Files:** `docs/testing/reader-polish-checklist.md` (create), `AGENTS.md` (§8, §9), this plan's status board.

- [ ] **Step 1:** Run the full CI command. Expected: BUILD SUCCESSFUL, 0 failures, lint 0 errors and warnings ≤ 14.
- [ ] **Step 2:** `./gradlew :app:installDebug` on the running emulator; with the synthetic EPUB capture screenshots into `.superpowers/checks/ui-compare/after/`: reader HUD, Aa sheet (EN), Aa sheet (VI via per-app locale), Library header; also repeat the HUD at font scale 1.3 (`adb shell settings put system font_scale 1.3`, then reset to 1.0).
- [ ] **Step 3:** Write the manual checklist (items: gutter, chapter text, Aa tap, sheet, swatches, cover tints, header alignment, 130 % font) with the observed results.
- [ ] **Step 4:** Update `AGENTS.md` §8 (new entry, commit/CI numbers, no push, no APK) and §9; set the status board to done; commit `docs: record reader polish status`.
