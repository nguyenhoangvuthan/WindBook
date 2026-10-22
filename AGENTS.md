# AGENTS.md — WindBook shared context for AI coding agents

Read this file first. It is shared by every AI agent working in this repo (Claude Code, Codex, …) and is the single place for project context, rules, current status and the task queue. **Keep it current:** when you finish or hand off work, update §8 "Current state" and §9 "Task queue" in the same commit as the work.

Last updated: 2026-10-22 (bounded source sessions verified; revision integration next).

---

## 1. Project in one paragraph

WindBook is a private, **offline** EPUB/PDF reader for Android 12+ (minSdk 31, targetSdk 36, compileSdk 37), built with Kotlin, Jetpack Compose and Readium. It has one user: the owner (Kent), who reads on a **Nubia Neo 3 GT** (Android 16, 1080×2400, 420 dpi, 4 GB RAM). The library is large and mixed: 672 EPUBs (368 Vietnamese) and 703 PDFs (460 typeset books, 91 calibre conversions, 49 scans, 16 slide decks, 87 comics). Release path: personal use first, Play Store later if it sticks. Package root: `dev.kent.windbook`. The app has **no INTERNET permission** on purpose.

## 2. Owner rules (non-negotiable)

1. **Reply to the owner in Vietnamese.** Docs, code, comments and commit messages are in English.
2. **No AI attribution anywhere.** No `Co-Authored-By: Claude/Codex…`, no "Generated with …" in commits or PR descriptions.
3. **Never push** to GitHub (`origin`) and never push tags without asking first. Local commits and merges are fine when the owner asked for the work.
4. **No new Gradle dependency** without explicit approval (`gradle/libs.versions.toml` is the full list).
5. **Every user-visible string** goes in both `app/src/main/res/values/strings.xml` (English) and `values-vi/strings.xml` (Vietnamese).
6. **Never commit** `metadata_books.json` or anything that lists the owner's book titles. Screenshots that show book pages stay out of git too.
7. **The external library drive is read-only** for agents: `/run/media/kent/Document Stock/Library/` (catalog: `Library/00_Catalog/metadata.json`, 2 376 records; find books by `current_path`).
8. **Shoulder-trigger page turns and PDF Reflow were removed** by owner decision. Do not propose them again.
9. Ask before anything destructive or outward-facing (pushing, deleting branches with unmerged work, touching the owner's files outside the repo).

## 3. Build, test, run

| What | Command |
|---|---|
| CI check (run before every commit that touches code) | `./gradlew :core:model:test :core:data:testDebugUnitTest :reader:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug` |
| Debug APK | `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` (all 4 ABIs) |
| Install on emulator/phone | `./gradlew :app:installDebug` or `adb install -r <apk>` |
| Device tests (`:reader` androidTest) | `./gradlew :reader:connectedDebugAndroidTest` (17 tests) |
| One device test class | `./gradlew :reader:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<fqcn>` |

Last green JVM counts (2026-10-22): 278 (model 51, data 91, reader 27, app 109). Device suite: 17 passed during Reflow removal verification. Lint: 0 errors, 10 pre-existing warnings (SDK/Gradle/Kotlin/serialization/DataStore version notices, `PluralsCandidate` in strings.xml:17, `allowBackup`, monochrome icon). Refreshed unchanged baseline `5510307` also reports the identical 10 warnings; the increase from the historical 9 is an upstream DataStore version notice. A change must not add lint warnings.

**Emulator** (AVD `Neo3GT_API_36`, matches the phone). Start it detached; `-gpu swiftshader_indirect` is required, otherwise it dies with a gfxstream "null ctx" error:

```bash
QT_QPA_PLATFORM=xcb setsid nohup ~/Android/Sdk/emulator/emulator -avd Neo3GT_API_36 -no-snapshot-load -cores 4 -gpu swiftshader_indirect > /dev/null 2>&1 &
adb wait-for-device; until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 3; done
```

Sample books on the emulator in `/sdcard/Download/`: `epub_tech.epub`, `pdf_latex.pdf` (title "Ubuntu Unleashed 2019 Edition"), `pdf_indesign.pdf`, `pdf_scan_vi.pdf` (24 MB scan, 2654×4329 pt pages, "Cẩm Nang Tư Duy…", the memory/speed benchmark), `pdf_calibre_vi.pdf`, `pdf_comic.pdf`, `pdf_slides.pdf`, `vi-lh12-font.epub` (links a missing stylesheet; it used to crash the app), `vi-lh12-nofont.epub`, `vi-lh15.epub`, `vi-unset.epub`. If they are missing, copy them again from the library drive via `metadata.json`.

## 4. Architecture

Four Gradle modules. Dependencies point downward only: `:app` → `:reader` → `:core:data` → `:core:model`.

| Module | Kind | Responsibility | Key files |
|---|---|---|---|
| `:core:model` | Pure Kotlin/JVM | Domain types and pure algorithms, all JVM-tested | `Book.kt`, `LibraryQuery.kt`, `FormatSniffer.kt`, `SearchText.kt`, `MarginDetector.kt` (PDF crop boxes), `PdfDisplay.kt` (`PdfLayout`, `BookDisplayPrefs`, `EffectiveDisplay`, `resolve`). testFixtures: `TestBooks` (tiny EPUB/PDF generators). |
| `:core:data` | Android lib | Persistence and import | Room 3 (`androidx.room3`) DB **v4**: `books`, `reading_progress`, `book_preferences`, `folders`, `book_sources`, `folder_memberships`, `scan_runs` (AutoMigration 1→2, explicit 2→3 preserving dependent rows, additive 3→4 for membership presence and durable run paths/timestamps). `LibraryRepository`, `DisplayPreferencesRepository` + `BookPreferencesCodec` (JSON, keeps unknown keys), `importer/BookImporter` (copies into app storage, sha256 dedupe, revives tombstoned rows), `BookFileStore`, `ZipGuard`, `LibraryPreferences` (DataStore). |
| `:reader` | Android lib | Readium 3.4.0 integration and WindBook's own PDF engine | `ReadiumServices` (uses `OfflineHttpClient` and `NoCoverPdfFactory`), `ReaderEngine` (EPUB → Readium; **PDF → `PdfDocumentHandle`, never Readium**), `ReaderHostFragment` (EPUB navigator only), `ReadiumMetadataExtractor` (import metadata; PDF cover via `pdfCoverWebp`), `LocatorCodec`, `pdf/PdfDocumentHandle` (framework `PdfRenderer` on one thread), `pdf/PdfPages` (48 MB bitmap LRU, crop plan), `pdf/PdfReaderSession` (locators). |
| `:app` | Android app | Compose UI, Hilt DI, Navigation3 | `MainActivity`, `navigation/*` (4 tabs: Library, Reading, Notes, Settings), `library/*`, `reading/*`, `settings/*`, `reader/ReaderScreen.kt` (route, overlay, View menu), `reader/ReaderViewModel.kt`, `reader/pdf/PdfReaderView.kt` (Vertical / Paged), `input/KeyEventDispatcher` (volume and page keys), `ui/theme/*` (cream paper and brass, Literata), `di/AppModule.kt`. |

**Key decisions (do not undo without a spec change):**
- **PDF rendering** uses `android.graphics.pdf.PdfRenderer` on one dedicated thread: open page, render through a crop `Matrix`, close page. PdfiumAndroid 1.9.8 (Readium's engine) leaks ~11 MB per page and must never render.
- **Readium never opens a PDF for reading.** Its PDF parser renders a cover at full device-pixel size (≈ 1.5 GB for the scan). Import uses `NoCoverPdfFactory` (cover = null) and renders its own 600 px cover.
- **`OfflineHttpClient`.** Readium's `DefaultHttpClient` throws `SecurityException` without INTERNET when an EPUB links a missing file (~100 EPUBs do). Every HTTP request fails softly instead.
- **PDF locators** keep the M1 shape: `{"href":"publication.pdf","type":"application/pdf","locations":{"fragments":["page=N"],"position":N,"progression":p,"totalProgression":p}}` with p = (N−1)/pageCount. Old saved positions must keep working.
- **Per-book display prefs** are JSON in `book_preferences.json`: `pdfLayout` (`VERTICAL`|`PAGED`|null), `cropMargins`, `pdfZoom`, `pdfZoomX`. `resolve()` clamps them. Unknown keys survive writes, so new fields need no migration.
- **Reflow was removed** by owner decision on 2026-10-20. Legacy `pdfLayout: "REFLOW"` decodes to `VERTICAL` (including landscape books); locators, crop and zoom survive. `pdfReflowSize` remains inert under the unknown-key policy. See `docs/superpowers/specs/2026-10-20-remove-pdf-reflow-design.md`.
- **EPUB** keeps publisher styles. The reader only adds top/bottom spacing and cutout safety. Typography controls belong to M2.

## 5. Docs map

| Path | What |
|---|---|
| `docs/superpowers/specs/2026-10-13-windbook-design.md` | Main product spec (features F1–F8 by milestone) |
| `docs/superpowers/plans/2026-10-13-windbook-roadmap.md` | Milestone index with status |
| `docs/superpowers/specs/*-design.md` + `plans/*.md` | One spec and one plan per milestone (look-and-feel, pdf-display, pdf-comfort) |
| `docs/research/2026-10-15-library-analysis.md` | Aggregate stats of the owner's library and emulator measurements (no titles) |
| `docs/research/2026-10-13-*.md` | Feature benchmark vs other readers; validation |
| `docs/testing/m1-manual-checklist.md` | Manual device checklist (M1, Look & feel, PDF display P1–P8, PDF comfort C1–C7) |
| `README.md` | Requirements, build, signing, tests, device setup, manual steps |
| `docs/WindBook_Technical_Blueprint.md` | Early technical blueprint (historical) |

## 6. How work is done here

1. **Brainstorm → spec.** Agree on intent with the owner, then write `docs/superpowers/specs/YYYY-MM-DD-<topic>-design.md`. The owner approves the written spec.
2. **Plan.** Write `docs/superpowers/plans/YYYY-MM-DD-<topic>.md`: global constraints, review focus, tasks with exact files, interfaces, test code and commands. The owner approves it and picks the execution style.
3. **Execute on a branch** named after the plan (e.g. `pdf-comfort`), task by task, strictly **TDD**: write the test, watch it fail for the right reason, implement, watch it pass, commit (conventional prefix: `feat(app):`, `fix(reader):`, `docs:` …). Deviations from the plan are recorded as rulings in the execution ledger (`.superpowers/sdd/<plan>/progress.md`, git-ignored) and summarised to the owner.
4. **Verify on the emulator** (screenshots, timings, `dumpsys meminfo … TOTAL PSS`) for anything visual or performance-related. Robolectric cannot measure real text layout.
5. **Whole-branch review** by an independent reviewer, then one fix pass where every Critical/Important fix gets a failing test first.
6. **The owner decides** to merge, push or keep. Then update the roadmap, the checklist and this file.

## 7. Gotchas (learned the hard way)

- **adb input:** `adb shell input text` mistypes `_`, so search UI by words without underscores. For a double-tap use `adb shell "input tap X Y & sleep 0.08; input tap X Y"` (two separate taps are too slow). The system file picker opened by "Add books" selects and returns on a **single tap**, so start recordings or measurements before tapping the file. `uiautomator dump` nodes can lack a `text` attribute.
- **Library delete is a soft delete.** Re-importing the same file revives the row and re-runs metadata extraction.
- **Gradle glitches:** if a test compile suddenly cannot resolve classes that clearly exist, rerun with `--rerun-tasks`. On "Failed to store cache entry … Could not get file mode", run `./gradlew --stop` and retry. Never `pkill -f <pattern>` with a pattern that also matches your own shell command.
- **Emulator image is production:** no `adb root` and no `debuggerd -j`. Use temporary `Log.d("WBDBG", …)` lines (never commit them) and `adb logcat`.
- `run-as dev.kent.windbook cat databases/windbook.db{,-wal,-shm}` copies the app DB out for `sqlite3` inspection.
- **Kotlin:** `list += someIntRange` on a `MutableList<Any>` picks the `Iterable` overload (IntRange is Iterable), so it adds the numbers, or fails to compile on a `val`. Use `list.add(range)`.
- `core/model/bin/` and `.vscode/` are untracked IDE output. Do not commit them.
- The scratchpad/tmp area of an agent session can be wiped mid-task. Keep anything important in the repo or under `.superpowers/` (git-ignored).

## 8. Current state (update on every hand-off)

- **Branch:** `folder-library` in `.superpowers/worktrees/folder-library`, based on `remove-pdf-reflow` at `5510307`. Reflow removal is awaiting the owner's merge decision; neither this branch nor the local `v0.1.0` tag has been pushed.
- **Done milestones:** M1 core reading loop (v0.1.0; the roadmap row still says "Planned" and should read Done), Look & feel, PDF display, PDF comfort.
- **Owner is testing** `~/Downloads/WindBook-0.1.0-pdf-comfort-debug.apk` (built from `32525b1`) on the Neo 3 GT. This older APK still includes Reflow; the removal build is `app/build/outputs/apk/debug/app-debug.apk`. Record phone results in `docs/testing/m1-manual-checklist.md` (C1–C3 and R1–R3, Neo 3 GT column).
- **Owner feedback (2026-10-20/21):** missing notes, inadequate margins/layout, and missing folder loading/access to all files. Page-turning feedback is provisionally interpreted as positive. Notes must attach to selected text in **both EPUB and PDF** (confirmed). PDF scans use a selected image region with an attached note (confirmed). The format affected by the margin problem still needs clarification. Folder behavior was clarified below on 2026-10-21. See `docs/research/2026-10-20-daily-reading-feedback.md`; this is a discussion brief, not an approved scope change.
- **Reflow removal:** code, menu and exclusive analysis tests removed; legacy preferences reopen vertically without changing saved locators. Independent code review found no correctness/regression issues. Debug APK builds; JVM/lint checks pass. Local verification artifacts are under `.superpowers/checks/remove-reflow/`.
- **Selection notes design:** `docs/superpowers/specs/2026-10-21-selection-notes-design.md` is drafted and independently reviewed, awaiting owner approval. It covers EPUB/PDF text selection and PDF scan region notes. The owner specified the same compact popup above EPUB text, PDF text and PDF scan regions with Highlight color and Note actions; standalone colored highlights are included for all three, with a translucent fill and outline for scan regions. The owner requested a popup about one-third of the previous area, larger labels, and clicks directly on text instead of padded button boxes. Both actions must stay on one horizontal row (owner correction). API 31–34 PDF region-only support and Markdown export are proposed defaults. Interactive mockup: `.superpowers/brainstorm/27897-1792599917/content/selection-notes-v5.html` (ignored, throwaway). Notes are not implemented in the APK.
- **Folder library design:** `docs/superpowers/specs/2026-10-21-folder-library-design.md` was approved by the owner on 2026-10-21 after independent review. The owner requested folder location input, extension filters, recursive scans, discovery AND in-app reading for EPUB/PDF/MOBI/AZW3/FB2/CBZ/CBR/TXT, and direct source-folder reading without a permanent library copy. Proposed defaults: one-time Android tree grant, remembered folders and manual rescans; bounded per-book temporary preparation where engines require it. Raw typed paths do not grant access. New reader adapters require feasibility checks and concrete dependency proposals before approval; no dependencies added. Interactive mockup: `.superpowers/brainstorm/27897-1792599917/content/folder-library-v1.html` (ignored, synthetic). Design approval does not approve the implementation plans, new dependencies or branch merges.
- **Folder implementation plans:** `docs/superpowers/plans/2026-10-21-folder-library.md` (8 tasks, no new dependencies) and `docs/superpowers/plans/2026-10-21-ebook-format-readers.md` (7 tasks, six additional readers) were approved on 2026-10-22 for subagent-driven execution. Full completion still requires all eight formats. Source/API inspection is recorded in `docs/research/2026-10-21-folder-reader-feasibility.md`; proposed additions are Junrar 8.1.2 (plus slf4j-api 2.0.17), native libmobi 0.12 and NDK 29.0.14206865 (not installed). The owner approved these additions and NDK installation on 2026-10-22. Runtime compatibility, APK delta and memory remain execution gates. Deliver a debug APK when the complete feature is verified.
- **Folder Task 1:** source/discovery model seams are implemented: all eight formats, nullable owned path/hash, provider/document identity and locale-independent final-suffix filters. Reader and metadata opening explicitly reject the six pending adapters before accessing files; a null owned path temporarily produces the existing cannot-open state. Full CI passes 219 tests; lint matches the refreshed baseline (0 errors, 10 warnings). Persistence/source resolution and the six new readers remain later tasks.
- **Folder Task 2:** v3 persistence supports nullable external book paths/hashes, canonical provider/document sources, overlapping folder memberships and durable scan runs. Migration backfills owned sources for visible and hidden v2 books while preserving IDs, progress and unknown preferences; v1→v3 is tested. Importer commits books and owned sources atomically. External hide/restore and disconnect retain reading data. Grant release is a required injected callback, now wired to real FolderGrants by Task 3; disconnect conservatively retains a grant while a shared canonical URI still needs it. Task 4 now sweeps retained grants once unused without dropping disconnected rows. Full CI passes 228 tests and unchanged lint (0 errors, 10 warnings). Null-path reader regression is covered; ReaderViewModelTest now clears ViewModels and joins their jobs before closing the DB.
- **Folder Task 3:** read-only persisted tree grants and DocumentsContract metadata enumeration are implemented. The scanner traverses iteratively, filters extensions, detects repeated directory IDs, validates authority/tree membership, counts/skips unnamed rows and marks cursor/access/loading failures incomplete. Cancellation propagates and closes cursors; enumeration never opens book content. Actual FolderGrants release is bound to BookSourcesRepository in app DI. Full CI passes 246 tests with unchanged lint (0 errors, 10 warnings). Task 4 now supplies the held-read-grant sweep over disconnected folder rows during lifecycle initialization and after reconciliation/disconnect; grants still used by a connected canonical source URI are retained. No URI rebasing was introduced. Robolectric provider tests are verified; no Task 3 device run is claimed.
- **Folder Task 4:** remembered folders, filename/format result flows and a singleton app-owned scan coordinator are implemented. Canonical document upserts commit at most 100 discovered entries per batch; hidden rows stay hidden. Durable run tokens, serialized start/stop and per-root presence prevent late writes and suppress missing reconciliation for interrupted, failed or unnamed-row scans. Complete runs only mark missing within their frozen extension/recursion coverage; overlapping roots retain independent presence. Additive v4 preserves v3 and the v1→latest chain. Grant cleanup runs under the repository lifecycle mutex; a disconnected root's canonical URI grant stays held while any connected membership may use it, including a temporarily missing source. Picker connections/reconnections now use `connect(treeUri, displayLocation, options, returnedFlags)`, which initializes stale cleanup, acquires the real read grant and saves the connected row under one lifecycle lock; `save()` rejects reconnecting disconnected rows. Full CI passes 267 tests with unchanged lint. **Task 7 must route picker results through atomic `connect()` and call `FolderRepository.initialize()` from app startup using the existing lifecycle/app scope, even if the Folder screen is never visited.** Initialization is currently deterministic and lazy before every folder operation; no UI/app-start hook is claimed yet.
- **Folder Task 5:** external EPUB/PDF acquisition uses one bounded session snapshot (500 MiB source, 1 GiB scratch, 64 MiB reserve, 64 KiB buffers). Readium's direct URI resource reopens the provider during random reads, so fingerprinting and reading must share the acquired `SourceLease`; `BookOpener.open(SourceLease, format)` consumes it even on failure. Owned files remain direct. Sources, PDF descriptors and unpublished sessions release on failure/cancellation; blocked pipes are interrupted, close is idempotent and main-thread cleanup is dispatched to IO. Process-wide workspace ownership protects active leases from other managers' orphan cleanup. The prior importer EPUB ZIP guard is shared through core:model and applied before Readium to preserve archive limits/path checks; no module/dependency edge was added. Synthetic API 31 and API 36 source gates each pass 10 tests; full CI passes 278 tests with unchanged lint. **Task 6 must use the same acquired lease for fingerprint, verification, metadata and open; Task 7 must call `SourceAccess.initialize()` alongside folder initialization at app startup, even without opening a book.** The explicit suspend entry point sweeps orphans on IO and preserves active leases; actual app startup wiring is not yet claimed. No source-reading UI is connected yet.
- **Historical emulator results for PDF comfort (before Reflow removal):**
  - Scan import ~0.75 s (+15 MB PSS).
  - Zoom and horizontal position restored on reopen.
  - Reflow text ≈ 2.5× the uncropped page; body pages analysed in 153–236 ms (front matter up to 627 ms).
  - Reflow at the largest size: 339 MB PSS settled.

## 9. Task queue

**Next (approved execution):**
1. Review Folder Task 5 source sessions, then continue Task 6 revision integration and the remaining approved folder/format tasks (Task 6 must retain the acquired source lease through metadata/verification/open; Task 7 must wire `FolderRepository.initialize()` and `SourceAccess.initialize()` at app startup and use `connect()` for picker grants) using subagents, independent task reviews and final verification; export the resulting APK for the owner. No further plan/dependency/NDK confirmation is needed. The separate selection-notes design still awaits approval; no notes implementation is implied. The owner separately decides merge/push; margins/layout remain a separate discussion.
2. Ask before pushing the `v0.1.0` tag.
3. Start **M2 Reading comfort** (brainstorm → spec → plan): themes, fonts, typography (line height 1.2–2.0, a publisher-styles toggle), per-book EPUB prefs. The Vietnamese EPUB check (2026-10-15) found tone marks render fine at publisher line height 1.2, so line height is a preference, not a fix.

**Known limitations, recorded and deferred:**
- Double-tap zoom always zooms at the left edge (`hScroll.maxValue` is still 0 when `scrollTo` runs).
- Volume keys at the bottom-right of the last page while zoomed cycle sideways.
- A zoom change is lost if the reader is left within 500 ms. `ReaderViewModel.updatePrefs` builds on `currentPrefs`, so very fast successive changes can overwrite each other.
- Import: the cover width is uncapped for banner-shaped pages and `OutOfMemoryError` is not caught.
- From the PDF display review:
  - `PdfDocumentHandle.close()` blocks the main thread in `onCleared` while a render finishes.
  - `runCatching` swallows `CancellationException` in a few places.
  - Leaving while a PDF is still opening leaks the session (fd + thread).
  - Paged-mode pan is unclamped.
  - Weak tests: mixed page heights, password PDF, cascade after migration.
- Readium still parses PDFs at import (metadata only); OK now that it no longer renders the cover.

**Housekeeping:**
- Roadmap: mark M1 Done.
- README §4 (device setup) was flagged for an update on 2026-10-14 and is not done yet.
- Release keystore not created yet (README §2).
- Run the M1 manual checklist on the Neo 3 GT.
