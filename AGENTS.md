# AGENTS.md — WindBook shared context for AI coding agents

Read this file first. It is shared by every AI agent working in this repo (Claude Code, Codex, …) and is the single place for project context, rules, current status and the task queue. **Keep it current:** when you finish or hand off work, update §8 "Current state" and §9 "Task queue" in the same commit as the work.

Last updated: 2026-10-27 (daily feedback implemented, verified and independently reviewed; phone validation next).

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
| Device tests (`:reader` androidTest) | `./gradlew :reader:connectedDebugAndroidTest` (32 tests) |
| One device test class | `./gradlew :reader:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<fqcn>` |

Last green JVM counts (2026-10-27): 385 (model 55, data 120, reader 47, app 163). Device suite: 17 passed during Reflow removal verification. Lint: 0 errors, 10 pre-existing warnings (SDK/Gradle/Kotlin/serialization/DataStore version notices, `PluralsCandidate` in strings.xml:17, `allowBackup`, monochrome icon). Refreshed unchanged baseline `5510307` also reports the identical 10 warnings; the increase from the historical 9 is an upstream DataStore version notice. A change must not add lint warnings.

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
| `:core:data` | Android lib | Persistence and import | Room 3 (`androidx.room3`) DB **v7**: `books`, `reading_progress`, `book_preferences`, `folders`, `book_sources`, `folder_memberships`, `scan_runs`, `text_reading_states`, `annotations` (AutoMigration 1→2, explicit 2→3 preserving dependent rows, additive 3→4 for membership presence and durable run paths/timestamps, 4→5 for nonunique durable revision hashes, 5→6 for TXT encoding history, 6→7 for PDF annotations). `LibraryRepository`, `DisplayPreferencesRepository` + `BookPreferencesCodec` (JSON, keeps unknown keys), `importer/BookImporter` (copies into app storage, sha256 dedupe, revives tombstoned rows), `BookFileStore`, `ZipGuard`, `LibraryPreferences` (DataStore). |
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

- **Active branch/worktree:** `daily-reading-feedback` in `.superpowers/worktrees/folder-library`, BASE `b2e5910`. The outer checkout `remove-pdf-reflow` is stale. No merge/push authorized; local `v0.1.0` remains unpushed.
- **Resume without rediscovery:** read the outer repo `.superpowers/RESUME.md`, then check active-worktree HEAD/short status. Current per-domain reports/logs are `.superpowers/checks/daily-feedback/`. Do not reread all old plans or rerun green suites solely because a turn was interrupted. CodeGraph is initialized here; CLI `/home/kent/.local/bin/codegraph` (not on shell PATH), use before code searches/reads.
- **Completed base:** M1 core reading loop, look-and-feel, PDF display/comfort; Reflow removed. Folder/source Tasks1–8 and their whole-branch fix-wave review are Approved at `1d64653`. Additional-reader shared preparation and TXT increments are independently Approved at `ab77179`/`98425b3`. Status-bar fix is complete/reviewed at `b2e5910`; fullscreen reading always retains the status bar and respects real system-bar/cutout bounds.
- **Paused scope:** on 2026-10-24 the owner paused FB2/CBZ/CBR/MOBI/AZW3; EPUB/PDF/TXT suffice. Partial FB2 parser/tests are preserved outside build in ignored additional-reader ledger `paused-task3/`. Do not resume paused readers without a new request. Junrar/slf4j/libmobi/NDK approvals remain historical; no such runtime dependency has been added.
- **Current owner-authorized feedback (2026-10-26):** folder-as-root scanning of all selected EPUB/PDF/TXT, visible Settings choices, Paged PDF default, title/time-added sort, and explicitly confirmed PDF text selection → save/edit in Notes → exact source revisit. Scope/plan: `docs/superpowers/specs/2026-10-26-daily-reading-feedback-design.md` and companion plan. EPUB/scan notes, OCR and export remain deferred; the broader October21 notes design is not blanket implementation approval.
- **Folder implementation:** only three selectable extensions; persistent scan defaults in Settings; per-folder overrides remain explicit. Typed device paths reuse an exact existing grant or launch a prepositioned SAF picker, then auto scan. The visible options at Scan time are authoritative across grant/queue delays. Unsupported host paths cannot grant Android access. Invalid provider rows no longer discard valid siblings; scans still report INCOMPLETE and retain identity checks. Compact saved-folder selector stays reachable with thousands of results. Folder/settings focused41 tests and selector regression10 tests pass; source uses actual tree grants, not raw filesystem bypass.
- **PDF implementation:** unset per-book layout now resolves to PAGED for all page sizes, with explicit choices/legacy decoding preserved. PDF text annotations use guarded API35+ framework selection on the existing PDF worker, full-page normalized rectangles, immutable session SHA, bounded newest-pending selection, DB6→7 additive annotations and durable-data revision checks. Notes supports highlights/colors, optional notes, CRUD/search, draft retry/rotation and repeatable note-aware navigation. Focused model/data/queue/app tests and API36 native Vietnamese/multiline/reversed/empty selection gates pass. Final mandatory CI passes385 JVM tests (55model/120data/47reader/163app), lint0errors/10baseline warnings, and debug APK0.1.1 builds. Installed v6→v7 migration preserves exact rows/hashes for books, reading progress, preferences and TXT history. Actual API36 MainActivity gates pass for root scan24→25, three-format reading, PDF selection/handles/colors/CRUD/restart/repeated passage revisit and crop/zoom/pan/rotation. API31 reads/pages correctly and explains unavailable text selection. Independent whole-change and narrow-fix review are Approved. Device testing found popup outside-touch cleared selection before handle drag; actual failing gate is GREEN after disabling outside-dismiss (page taps still clear).
- **Build identification:** current work bumps the debug app to versionCode2/versionName0.1.1; the previously delivered status-bar APK is `.superpowers/builds/WindBook-0.1.0-epub-pdf-txt-statusbar-debug.apk`. Do not mistake that older build for the current feature build. Never commit local DB backups or page screenshots.
- **Evidence retained:** prior folder API31/36 2,500-document scan and exact-content reconnect tests are in the folder checklist/reports; TXT CI345 and real passage restore on API31/36 are in its report; status-bar CI346 and actual API36 EPUB/PDF/TXT visual matrix are in `docs/testing/reader-system-bars-checklist.md`. Reuse unaffected evidence. The current UI gate uses 24 synthetic EPUB/PDF/TXT documents, 16 ignored files and a three-page synthetic Vietnamese PDF, not owner books.

## 9. Task queue

1. Current0.1.1 integration is complete: mandatory385-test CI/lint/build, installed v6→v7 row preservation, actual API36 folder/settings/sort and PDF-note gates, API31 guard, independent whole-change/narrow-fix approval. Final checklist: `docs/testing/daily-reading-feedback-checklist.md`. Resume from checkpoint; do not repeat historical checks without new changes or evidence.
2. Owner tests `.superpowers/builds/WindBook-0.1.1-daily-reading-feedback-debug.apk` on the Neo 3 GT. Merge/push and pushing `v0.1.0` remain separate owner decisions.
3. M2 typography, EPUB/scan notes and paused readers remain future work; do not resume automatically.

**Known limitations retained, not part of current fixes:**
- Vertical PDF double-tap zoom starts at the left edge; bottom-right last-page volume panning may cycle sideways.
- Leaving within500ms can lose a zoom write; rapid display-pref changes may overwrite one another.
- `PdfDocumentHandle.close()` can block the main thread during a native operation; older `runCatching` sites may swallow cancellation. Paged pan remains unclamped.
- Import does not catch `OutOfMemoryError`; covers are capped600px. Readium still parses PDF metadata at import, but never renders PDF reading/cover pages.
- Physical-phone manual checks, release keystore and broader notes/typography milestones remain open. TXT is currently available through folder sources; the Add-books importer still accepts EPUB/PDF.
