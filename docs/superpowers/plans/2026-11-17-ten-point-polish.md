# Ten-Point Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (inline, this session). Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the approved ten-point polish: format filter, paged-PDF tap zones, EPUB clean layout by default (with a "keep original formatting" switch), no text overflow, and the scorecard fixes.

**Architecture:** Pure logic goes in `:core:model` (filter, typography field) and in small testable classes (`PageTapClassifier`, `injectLayoutCss`). UI changes stay in `:app` Compose. EPUB layout uses Readium preferences plus one injected `<style>` whose rules are guarded by Readium's own `:root[style*=readium-advanced-on]` selector, so the switch needs no reopen.

**Tech Stack:** Kotlin, Compose Material3, Readium 3.4.0 (`EpubPreferences`, `TransformingContainer`), DataStore, Robolectric tests. No new dependency.

**Spec:** `docs/superpowers/specs/2026-11-17-ten-point-polish-design.md`

## Status board (update as tasks finish)

| # | Task | Status |
|---|---|---|
| 0 | Branch `ten-point-polish`, spec, plan committed | done |
| 1 | Format filter (EPUB/PDF/TXT) | done |
| 2 | Paged PDF tap zones (no accidental zoom) | done |
| 3 | EPUB clean-layout preferences + stored switch | done |
| 4 | Injected CSS: wrapping, links, image tiles | done |
| 5 | Aa sheet: "Keep original formatting" | done |
| 6 | Notes and Settings alignment, pill search | done |
| 7 | Dark-theme placeholder covers | done |
| 8 | Empty Library direct add buttons | done |
| 9 | One-time tap-zone hint | done |
| 10 | CI, owner-book checks on emulator, docs, 0.1.13 APK | done |

## Global Constraints

- Owner replies in Vietnamese; code, docs, commits in English. No AI attribution in commits (AGENTS.md rule 2 overrides trailers); this machine has no git identity, so commit with the repo author through env vars, no config change.
- Never push; no new Gradle dependency; no DB/schema change (typography JSON keeps unknown keys).
- Every new or changed string in `values/strings.xml` and `values-vi/strings.xml`; delete strings that become unused.
- CI: `./gradlew :core:model:test :core:data:testDebugUnitTest :reader:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug` with `ANDROID_HOME=$HOME/Android/Sdk`. Baseline 522 JVM tests, lint 0 errors / 14 warnings; must not get worse. One known flaky test: `ReaderViewModelPdfTest` (`UncaughtExceptionsBeforeTest`) — rerun once before treating it as a failure.
- 48 dp touch targets, EN + VI, 130 % font.
- Library `/home/kent/Library` is read-only; owner-book screenshots stay in git-ignored `.superpowers/` and out of commits (AGENTS.md rule 6).
- Supersedes the AGENTS.md decision "EPUB keeps publisher styles" (owner, 2026-11-17, option A); update §4 in Task 10.
- Existing tests that asserted old behaviour may be changed only where this plan says so.

## Review Focus

- Selected format disappears from the library (last PDF deleted): list must not stay empty behind hidden chips. (Task 1)
- Two quick taps at the right edge on page 3 give page 5 and no zoom; an edge tap is not delayed. (Task 2)
- Page already zoomed in: double tap in the middle zooms out; an edge tap still turns the page. (Task 2)
- A 400-character unbroken run (U+FFFD leaders, a URL) never overflows the column, in clean and original layout. (Task 4)
- Switching "Keep original formatting" while reading changes layout without losing the position or exact-text highlights. (Tasks 4–5, device check)
- Line height slider actually changes line spacing in clean layout; fixed-layout EPUB, PDF and TXT show no layout switch. (Tasks 3, 5)

---

### Task 0: Branch and docs

- [ ] **Step 1:** Branch `ten-point-polish` already exists (from `reader-polish`) with the spec committed. Commit this plan: `git add docs/superpowers/plans/2026-11-17-ten-point-polish.md && commit "docs: add ten-point polish plan"`.

### Task 1: Format filter

**Files:**
- Modify: `core/model/src/main/kotlin/dev/kent/windbook/core/model/LibraryQuery.kt`
- Modify: `app/src/main/kotlin/dev/kent/windbook/library/LibraryViewModel.kt`, `library/LibraryScreen.kt` (`LibraryActions`, `LibraryContent`), `library/components/LibraryHeader.kt`
- Test: `core/model/src/test/.../LibraryQueryTest.kt`, `app/src/test/.../library/LibraryViewModelTest.kt`, `LibraryScreenTest.kt`

**Interfaces:**
- Produces: `LibraryQuery(text, sort, filter, formats: Set<BookFormat> = emptySet())`; empty set = all formats.
- Produces: `LibraryUiState.formats: Set<BookFormat>` (effective, already intersected with available) and `availableFormats: List<BookFormat>` (formats present in the whole library, enum order).
- Produces: `LibraryViewModel.onFormatToggle(format: BookFormat)`; `LibraryActions.onFormatToggle: (BookFormat) -> Unit = {}` (default keeps existing tests compiling).
- Produces: `FormatFilterRow(available: List<BookFormat>, selected: Set<BookFormat>, onToggle: (BookFormat) -> Unit)` in `LibraryHeader.kt`, same `Pill` style and `edgeFade` as `FilterRow`; renders nothing when `available.size < 2`.

- [ ] **Step 1: Failing tests.**
  - `LibraryQueryTest.formatFilterKeepsOnlySelectedFormatsAndCombinesWithStatusAndText`: items EPUB/PDF/TXT; `formats={PDF}` → only PDF; `formats={EPUB,TXT}` → both; combined with `StatusFilter.READING` and a text needle; empty set → all.
  - `LibraryViewModelTest.formatToggleFiltersAndDroppedFormatDoesNotHideEverything`: import EPUB+PDF, toggle PDF → only PDF in `items`; delete the PDF → `formats` becomes empty and the EPUB is listed again.
  - `LibraryScreenTest.formatChipsShowOnlyForMixedLibrariesAndToggle`: library with EPUB+PDF shows "EPUB" and "PDF" pills and clicking "PDF" calls `onFormatToggle(PDF)`; EPUB-only library shows no format pills.
- [ ] **Step 2:** Run `--tests '*LibraryQueryTest' --tests '*LibraryViewModelTest' --tests '*LibraryScreenTest'` (modules `:core:model:test`, `:app:testDebugUnitTest`). Expected: FAIL (compile errors / missing behaviour).
- [ ] **Step 3: Implement** the signatures above. In the ViewModel keep `MutableStateFlow<Set<BookFormat>>`, compute `availableFormats` from `all`, and `effective = selected ∩ available` before building the query. Render `FormatFilterRow` in `LibraryContent` right under `FilterRow` (same grid item, spacing 8 dp). The continue-reading card ignores filters, as it does for status today.
- [ ] **Step 4:** Rerun the three classes. Expected: PASS.
- [ ] **Step 5:** Commit `feat(app): add an EPUB/PDF/TXT format filter to the library`.

### Task 2: Paged PDF tap zones

**Files:**
- Create: `app/src/main/kotlin/dev/kent/windbook/reader/pdf/PageTapClassifier.kt`
- Modify: `reader/pdf/PdfReaderView.kt` (paged branch ≈ lines 415–475), `reader/pdf/PdfAnnotationUi.kt` (`PdfTextPageOverlay`, ≈ lines 54–110)
- Test: `app/src/test/kotlin/dev/kent/windbook/reader/pdf/PageTapClassifierTest.kt` (create), `PdfReaderViewTest.kt`

**Interfaces:**
- Produces: `internal enum class TapAction { PREVIOUS_PAGE, NEXT_PAGE, TOGGLE_MENU, ZOOM_TOGGLE }`.
- Produces: `internal class PageTapClassifier(private val doubleTapTimeoutMs: Long = 300L, private val slopPx: Float = 48f)` with `fun onTap(x: Float, y: Float, viewportWidth: Float, timeMs: Long, zoomed: Boolean): TapAction?` (null = a middle tap that may still become a double tap) and `fun flushPending(): TapAction?` (returns `TOGGLE_MENU` once if a middle tap is waiting, else null).
- Rules: x < 25 % → `PREVIOUS_PAGE`, x > 75 % → `NEXT_PAGE`, both immediate, never part of a double tap. Middle tap → pending; a second middle tap within `doubleTapTimeoutMs` and `slopPx` returns `ZOOM_TOGGLE` and clears pending; `flushPending()` after the timeout returns `TOGGLE_MENU`. `zoomed` does not change the zones (edges still turn, middle double tap still toggles, so it zooms out).
- Consumes: `PdfTextPageOverlay` gets `onPageDoubleTap: ((Offset) -> Unit)?`; paged passes `null` so its `detectTapGestures` has no `onDoubleTap` (no wait). Vertical keeps passing its handler.

- [ ] **Step 1: Failing tests.**
  - `PageTapClassifierTest` (plain JUnit): left edge → `PREVIOUS_PAGE` immediately; right edge twice 100 ms apart → two `NEXT_PAGE`, no `ZOOM_TOGGLE`; middle tap → null then `flushPending()` = `TOGGLE_MENU`, second `flushPending()` = null; two middle taps 150 ms apart → null then `ZOOM_TOGGLE` and `flushPending()` = null; two middle taps 400 ms apart → null, null (first flushed separately); two middle taps farther apart than `slopPx` are not a double tap.
  - `PdfReaderViewTest.pagedDoubleTapOnRightEdgeTurnsTwoPagesWithoutZoom`: `doubleClick(centerRight.copy(x = width * .9f))` from page 0 → page 2, `zooms` unchanged.
  - `PdfReaderViewTest.pagedEdgeTapTurnsWithoutWaitingForTheDoubleTapTimeout`: click at 90 % width, advance the clock only 50 ms, page is already 1.
  - `PdfReaderViewTest.pagedMiddleDoubleTapStillZoomsAndMiddleSingleTapStillTogglesTheMenu`: existing zoom behaviour kept (reuse the assertions of the existing paged double-tap test around `PdfReaderViewTest.kt:114`).
- [ ] **Step 2:** Run `--tests '*PageTapClassifierTest' --tests '*PdfReaderViewTest'`. Expected: classifier test fails to compile; edge double tap test FAILS (zooms today).
- [ ] **Step 3: Implement** `PageTapClassifier` as a pure class; in the paged `Box` replace `detectTapGestures(onTap = turnAt, onDoubleTap = zoomAt)` by `detectTapGestures(onTap = …)` that feeds the classifier (`SystemClock.uptimeMillis()`), runs immediate actions, and for a pending middle tap launches `delay(doubleTapTimeoutMs)` then `flushPending()`, cancelling that job when a second tap arrives. `ZOOM_TOGGLE` runs the existing `zoomAt(tap)`; `TOGGLE_MENU` runs `onCenterTap()`; turns reuse `turnAt` logic. Route the annotation overlay's `onPageTap` through the same function (tap coordinates are converted with the existing `viewportPoint`) and pass `null` for `onPageDoubleTap` in the paged call. Keep `gesturesLocked` behaviour (no turn/zoom while locked).
- [ ] **Step 4:** Rerun both classes, then `*PdfAnnotation*`/`*PdfSelection*` tests in `reader/pdf`. Expected: PASS.
- [ ] **Step 5:** Commit `fix(app): page-turn taps no longer count as double-tap zoom in paged PDF`.

### Task 3: EPUB clean-layout preferences and stored switch

**Files:**
- Modify: `core/model/.../ReaderTypography.kt`, `core/data/.../ReaderPreferencesRepository.kt` (`TypographyCodec`), `reader/src/main/kotlin/dev/kent/windbook/reader/ReaderTypographyAdapter.kt`
- Test: `core/model/src/test/.../ReaderTypographyTest.kt`, `core/data/src/test/.../` codec/repository test that already covers `TypographyCodec`, `reader/src/test/.../ReaderTypographyAdapterTest.kt`

**Interfaces:**
- Produces: `ReaderTypography.originalLayout: Boolean? = null` (null inherits global; global null = clean layout); `resolveOver` returns `originalLayout ?: global.originalLayout`; `normalized()` unchanged.
- Produces: `TypographyCodec` reads/writes key `originalLayout` (absent = null); unknown keys still survive `merge`.
- Produces: `typographyPreferences(value, darkTheme)` — when `originalLayout != true` returns `publisherStyles = false`, `textAlign = TextAlign.JUSTIFY`, `paragraphSpacing = 0.5`, `paragraphIndent = 0.0`, `hyphens = true`, `lineHeight = value.lineHeight?.takeIf { it > 0 } ?: 1.5`; when `originalLayout == true` behaves exactly like today (no `publisherStyles`, `textAlign`, `paragraphSpacing`, `paragraphIndent`, `hyphens`, default line height only from the user value).

- [ ] **Step 1: Failing tests.**
  - `ReaderTypographyTest`: `originalLayout` inherits (`null` over global `true` → `true`; `false` over `true` → `false`).
  - Codec test: round trip `originalLayout=true/false/null` and an unknown key survives.
  - `ReaderTypographyAdapterTest`: default → `publisherStyles == false`, `textAlign == JUSTIFY`, `lineHeight == 1.5`, `hyphens == true`; `originalLayout=true` → `publisherStyles == null`, `textAlign == null`, `lineHeight == null`; explicit `lineHeight=1.8` wins in clean layout; existing `fontSize` assertions stay green.
- [ ] **Step 2:** Run `:core:model:test :core:data:testDebugUnitTest :reader:testDebugUnitTest` for those classes. Expected: FAIL.
- [ ] **Step 3: Implement** the field, codec and adapter as specified.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5:** Commit `feat(reader): clean EPUB layout by default with a stored original-layout switch`.

### Task 4: Injected CSS (wrapping, links, image tiles)

**Files:**
- Create: `reader/src/main/kotlin/dev/kent/windbook/reader/LayoutCss.kt`
- Modify: `reader/src/main/kotlin/dev/kent/windbook/reader/ReadiumServices.kt` (`openAsset`)
- Test: `reader/src/test/kotlin/dev/kent/windbook/reader/LayoutCssTest.kt` (create), `ReadiumEngineTest.kt`

**Interfaces:**
- Produces: `internal fun injectLayoutCss(html: ByteArray): ByteArray` — inserts one `<style id="wb-layout">…</style>` immediately before the first `</head>` (case-insensitive); no `</head>` → inserts after the opening `<body…>` tag, else returns input; idempotent (second call changes nothing); never touches non-HTML.
- CSS contents: always `html,body,p,li,td,th,a,span,div,h1,h2,h3,h4,h5,h6{overflow-wrap:anywhere}`; only under `:root[style*=readium-advanced-on]`: `a,a:link,a:visited{color:inherit!important;text-decoration:none!important}` and `p:has(>img:only-child){margin-block:0!important}`.
- Consumes: `publicationOpener.open(asset, allowUserInteraction = false, onCreatePublication = { container = TransformingContainer(container, ::wrapHtml) })` where `wrapHtml(url, resource)` wraps resources whose path ends in `.html`, `.xhtml` or `.htm` in a `TransformingResource` calling `injectLayoutCss` on success bytes (read errors pass through).

- [ ] **Step 1: Failing tests.**
  - `LayoutCssTest`: inserts before `</head>`; uppercase `</HEAD>`; no head → after `<body class="x">`; idempotent; non-HTML bytes (PNG header) untouched; Vietnamese UTF-8 body bytes survive byte-for-byte outside the insertion; CSS text contains `readium-advanced-on` guard and `overflow-wrap:anywhere`.
  - `ReadiumEngineTest.openedEpubServesChaptersWithTheLayoutStyle`: open the synthetic test EPUB through `ReadiumServices` and read a chapter resource; its text contains `id="wb-layout"`, and the book's own text is unchanged (compare after removing the style element). An image resource is not altered.
- [ ] **Step 2:** Run `:reader:testDebugUnitTest --tests '*LayoutCssTest' --tests '*ReadiumEngineTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** per the interfaces; keep serial/offline behaviour of `ReadiumServices` unchanged.
- [ ] **Step 4:** Rerun. Expected: PASS. Then run the full `:reader:testDebugUnitTest` (anchor/search tests must stay green: the style element adds no body text).
- [ ] **Step 5:** Commit `feat(reader): wrap unbreakable runs and tidy links and image tiles in EPUB chapters`.

### Task 5: Aa sheet — "Keep original formatting"

**Files:**
- Modify: `app/src/main/kotlin/dev/kent/windbook/reader/ReaderTypographySheet.kt`; strings EN+VI
- Test: `app/src/test/kotlin/dev/kent/windbook/reader/ReaderControlsUiTest.kt`

**Interfaces:**
- Consumes: `ReaderTypography.originalLayout` (Task 3). Switch shown only when `reflowable` (same condition as the font controls) and also in the global-only (Settings) sheet.
- Strings: `reader_keep_original_layout` = "Keep original formatting" / "Giữ định dạng gốc của sách"; `reader_keep_original_layout_hint` = "Off: the app sets spacing, alignment and line height." / "Tắt: app tự chỉnh giãn dòng, căn lề và khoảng cách đoạn."; rename `reader_line_height_use_publisher` → `reader_line_height_reset` = "Reset line height" / "Đặt lại giãn dòng".
- Behaviour: switch state = `effective.originalLayout == true`; toggling calls `change { it.copy(originalLayout = on) }`. The line-height label shows the number always in clean layout (`effective.lineHeight?.takeIf { it > 0 } ?: 1.5`) and `reader_value_publisher` only in original layout; slider and reset button (`reader_line_height_reset`, only when a value is set) are disabled in original layout.

- [ ] **Step 1: Failing tests** (update the two line-height tests from the reader-polish round to the new label/string):
  - `keepOriginalFormattingSwitchTogglesTheField`: switch off by default; click → `latest.originalLayout == true`.
  - `lineHeightShowsNumberInCleanLayoutAndPublisherInOriginal`: clean + unset → "Line height · 1.5"; original → "Line height · Publisher default" and the slider is disabled.
  - `lineHeightResetOnlyWhenSet` renamed text "Reset line height".
  - `noLayoutSwitchForFixedLayoutBooks`: `reflowable = false` book scope → switch does not exist.
- [ ] **Step 2:** Run `*ReaderControlsUiTest`. Expected: FAIL.
- [ ] **Step 3: Implement** switch row (Material3 `Switch`, `Role.Switch` semantics, 48 dp) above the font section, strings in both locales, remove the old string.
- [ ] **Step 4:** Rerun `*ReaderControlsUiTest` and `*Settings*`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(app): add a keep-original-formatting switch to the reading appearance sheet`.

### Task 6: Notes and Settings alignment

**Files:**
- Modify: `app/src/main/kotlin/dev/kent/windbook/notes/NotesScreen.kt` (≈ lines 43–45), `settings/SettingsScreen.kt` (≈ line 108)
- Test: `app/src/test/.../notes/` (existing Notes UI test class, else create `NotesScreenTest`), `settings/SettingsScreenTest`

- [ ] **Step 1: Failing tests.**
  - `NotesScreenTest.vocabularyButtonAlignsWithTheTitle`: button's left edge = 4 dp (its text then sits at the 16 dp title gutter; same reasoning as `LibraryScreenTest.bookFoldersTextAlignsWithLibraryTitle`).
  - `NotesScreenTest.searchFieldIsAPill`: tag the field `NOTES_SEARCH_TAG`; assert it exists and uses `PillShape` through a small `internal val notesSearchShape = PillShape` constant referenced by the test (compose tests cannot read shapes otherwise).
  - `SettingsScreenTest.readingAppearanceRowTextAlignsWithOtherRows`: the Aa row's text starts at the same x as the folder-scanning rows' text (compare unmerged text bounds of "Reading appearance (Aa)" and "Include subfolders").
- [ ] **Step 2:** Run those classes. Expected: FAIL.
- [ ] **Step 3: Implement:** `Modifier.padding(start = 4.dp)` on the Vocabulary `TextButton`; search field `shape = PillShape`, leading search icon like Library's; Aa row `TextButton` gets `contentPadding = PaddingValues(0.dp)` and the row uses the same 16 dp horizontal padding the other rows use.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5:** Commit `fix(app): align Notes and Settings controls with the shared gutters`.

### Task 7: Dark-theme placeholder covers

**Files:** `app/src/main/kotlin/dev/kent/windbook/ui/components/BookCover.kt`; test `BookCoverTest.kt`.

**Interfaces:** `internal fun generatedCoverTint(bookId: String, dark: Boolean = false): Color`; dark variant = same hue with lightness scaled to ≈ 35 % (blend with `0xFF17130F` at 62 %), red channel still ≥ blue. `BookCover` passes `isSystemInDarkTheme()`.

- [ ] **Step 1: Failing test** `darkTintsAreDimmedButStillWarm`: for every id sample, `dark` tint luminance < light tint luminance × 0.6 and `red >= blue`.
- [ ] **Step 2:** Run `*BookCoverTest`. Expected: FAIL.
- [ ] **Step 3: Implement** with `androidx.compose.ui.graphics.lerp` and `luminance()`.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5:** Commit `fix(app): dim placeholder cover tints in the dark theme`.

### Task 8: Empty Library direct add buttons

**Files:** `ui/components/EmptyState.kt` (optional `secondaryActionLabel`/`onSecondaryAction`), `library/LibraryScreen.kt` (empty branch ≈ line 179); test `LibraryScreenTest.kt`.

- [ ] **Step 1: Failing tests:** `emptyLibraryOffersFilesAndFolderDirectly`: empty state shows "Choose files" and "Choose folder" buttons; clicking each calls `onImportClick` / `onFoldersClick` without opening the dialog; the old `emptyLibraryShowsAddBooks` test is replaced (the "Add books" button/dialog remains for a non-empty library via the FAB).
- [ ] **Step 2:** Run `*LibraryScreenTest`. Expected: FAIL.
- [ ] **Step 3: Implement:** keep primary filled button = Choose files, secondary `OutlinedButton` = Choose folder; VI strings already exist (`add_books_files`, `add_books_folder`).
- [ ] **Step 4:** Rerun. Expected: PASS (adjust `emptyLibraryShowsVietnameseAddBooks` to the new VI labels).
- [ ] **Step 5:** Commit `feat(app): offer file and folder choices directly on the empty library`.

### Task 9: One-time tap-zone hint

**Files:**
- Modify: `core/data/.../LibraryPreferences.kt` (key `tap_hint_seen`), `app/src/main/kotlin/dev/kent/windbook/reader/ReaderScreen.kt` / `ReaderControlsScreen.kt`; strings EN+VI
- Create: `app/src/main/kotlin/dev/kent/windbook/reader/TapHint.kt`
- Test: `core/data` preferences test, `app/src/test/.../reader/TapHintTest.kt`

**Interfaces:**
- Produces: `LibraryPreferences.tapHintSeen: Flow<Boolean>` and `suspend fun setTapHintSeen()`.
- Produces: `@Composable internal fun TapHint(visible: Boolean, onDismiss: () -> Unit)` — a bottom card above the slider area, text `reader_tap_hint` = "Tap the edges to turn pages · tap the middle for the menu" / "Chạm mép trái/phải để lật trang · chạm giữa để mở menu", a "Got it"/"Đã hiểu" button (existing string reuse if one exists), auto-dismiss after 8 s, 48 dp target. Shown for EPUB and paged PDF only, only on the first reader open per install.

- [ ] **Step 1: Failing tests:** preferences flow false → `setTapHintSeen()` → true; `TapHintTest.showsTextAndDismissesOnButton`, `autoDismissesAfterEightSeconds` (mainClock), `invisibleShowsNothing`.
- [ ] **Step 2:** Run the two classes. Expected: FAIL.
- [ ] **Step 3: Implement** the composable and wire it in `ReaderReadyScreen` (state from a small ViewModel method that reads/writes `LibraryPreferences`; mark seen on dismiss or timeout).
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5:** Commit `feat(app): show the tap-zone hint once on the first reading session`.

### Task 10: CI, owner-book checks, docs, APK

**Files:** `docs/testing/ten-point-polish-checklist.md` (create), `AGENTS.md` (§4 key decision, §8, §9, header date), `app/build.gradle.kts` (code 14 / `0.1.13`), this plan's status board.

- [ ] **Step 1:** Run the full CI command. Expected: BUILD SUCCESSFUL, 0 failures (rerun once if only the known flaky test fails), lint ≤ 0 errors / 14 warnings.
- [ ] **Step 2:** `./gradlew :app:installDebug`; copy the three owner EPUBs (book A, both editions of book B) from `/home/kent/Library/03_Cloud_DevOps/Cloud_AWS_Azure/` to the emulator's `/sdcard/Download/`; import via the picker; capture to `.superpowers/checks/ten-point-polish/` (not committed): book A TOC (leaders wrap inside the column), book B TOC (tight, justified, no blue underline), book B cover tiles (no gaps — report honestly whether the grid still looks wrong), Aa sheet switch on/off on one chapter with the position kept, a paged PDF edge double tap (two pages, no zoom) and middle double tap (zoom), library format chips (EN, VI, 130 % font), dark library with warm dimmed covers, empty library, tap hint.
- [ ] **Step 3:** Write the checklist with pass/fail per item and honest notes; update `AGENTS.md` (§4: EPUB now clean by default with a per-book/global original-layout switch; §8 entry with CI numbers and deliverable; §9; header); bump version; commit `docs: record ten-point polish status and checklist`.
- [ ] **Step 4:** `./gradlew :app:assembleDebug`; export `publish/Updraft-0.1.13-ten-point-polish-debug.apk` (repo-root `publish/`, git-ignored) with a `.sha256`; verify version/signature with `aapt2`/`apksigner`; commit `chore: bump version to 0.1.13`.
