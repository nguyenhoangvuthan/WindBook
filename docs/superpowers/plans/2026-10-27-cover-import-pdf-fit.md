# Cover Import Speed and PDF Fit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce redundant first-import I/O, show whole shelf covers and provide repeatable whole-page PDF fitting.

**Architecture:** Hash bounded external snapshots during staging and carry that digest on their source lease. Use proportional cover/page images. A single PDF preferences write selects PAGED without crop; a transient request counter resets the existing paged transform without resetting reading position.

**Tech Stack:** Existing Kotlin, coroutines, Compose, Coil 3, Room and framework PdfRenderer. No dependency additions.

**Spec:** `docs/superpowers/specs/2026-10-27-cover-import-pdf-fit-design.md` (owner-approved 2026-10-27).

**Status:** owner-approved 2026-10-27; execution in progress. Execution style: one implementer here, one independent final review.

## Global Constraints

- Keep one folder source snapshot and one extractor active at a time; covers remain at most 600 px.
- Keep all existing byte/space limits, cancellation, identity/revision guards and guarded cover publication; COMPLETE follows the cover pass.
- Cache a digest only for its immutable external snapshot; owned sources hash actual current bytes.
- Fit full covers in centered 2:3 frames. Whole-page PDF fitting preserves page, notes, safe insets and stored vertical zoom/zoomX.
- All new user-visible strings go in English and Vietnamese resources. No schema/permission changes, source-library writes or paused-format work.
- Export APK/checksum to `/home/kent/KentProject/WindBook/publish/`; keep artifacts/private books/screenshots untracked. No automatic merge/push.
- Use TDD, required CI before code commits, actual API36 visual/performance checks, independent final review and updated AGENTS sections 8/9.

## Review Focus

- Unknown-length or blocked provider streams: no digest/partial snapshot escapes cancellation or quota rejection (Task 1).
- Repeated fingerprints and closed leases: cached digest honors cancellation/closed state; changed owned files do not reuse it (Task 1).
- Tall/square covers and load completion: edge text remains visible without a Crop-to-Fit jump; fallback/menu/accessibility unchanged (Task 2).
- Fit invoked while already paged or while layout changes: reset every time, no current-page reset, single combined preferences write (Task 3).
- Mixed-size PDF pages, rotation and notes: no bitmap stretched into first-page geometry, all corners and annotation targets share the measured page rectangle (Tasks 3/4).

---

### Task 1: Hash external snapshots during their existing bounded copy

**Files:** modify `reader/src/main/kotlin/dev/kent/windbook/reader/source/{SessionWorkspace,SourceAccess}.kt`; test `reader/src/test/kotlin/dev/kent/windbook/reader/source/SessionWorkspaceTest.kt` and `reader/src/androidTest/kotlin/dev/kent/windbook/reader/ExternalSourceDeviceTest.kt`.

**Interfaces:** add internal `StagedSource(val file: File, val sha256: String)` and `SessionLease.stageSource(input: InputStream, declaredSize: Long? = null): StagedSource`. Preserve existing `stage(...): File`/artifact callers. Extend internal SourceLease construction with optional `snapshotSha256: String? = null`; retain `suspend fun fingerprint(): String`.

- [x] Before changes, prepare an isolated branch/worktree `cover-import-pdf-fit` from `2ae7699` with the approved docs. Save baseline evidence under ignored `.superpowers/checks/cover-import-pdf-fit/`. Use the existing 24 real-book manifest plus a larger-file subset, read-only. On the test emulator, restore the same isolated pre-import app DB/files fixture while the app is stopped between trials, so existing SHA/cover dedup cannot turn a fresh trial into a rescan. Never clear phone data. Measure first-cover latency, cover-pass duration and memory; separate provider staging/hash reads from parser I/O with temporary diagnostics removed before delivery. Use matched cache conditions and at least three alternating baseline/candidate trials; report medians and range, not a speed guarantee.
- [x] Write failing tests: staged digest equals SHA-256 of all copied bytes for empty/nonempty and unknown-length inputs; cancellation/oversize/space failure cleans partial files and reservations. Test repeated fingerprints after provider original replacement still match the snapshot, closed lease rejects fingerprint, cancelled caller gets cancellation, and an owned file changed between calls yields the new hash.
- [x] Run `./gradlew :reader:testDebugUnitTest --tests '*SessionWorkspaceTest'`; capture RED attributable to the missing hashed staging contract. Run new device-source regressions on API36 to capture their RED before the corresponding fix.
- [x] Implement SHA updates inside the existing successful copy loop and finalize only after complete write/cancellation checks. Reuse its bounded buffer, lock and reservation cleanup. SourceAccess consumes stageSource for external sources and passes the digest to that lease; fingerprint checks active context/closed state before returning it. Owned leases retain byte hashing.
- [x] Run the focused JVM tests and `./gradlew :reader:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.kent.windbook.reader.ExternalSourceDeviceTest`; expect PASS, including existing pipe/open-count/cleanup gates. Confirm the second full snapshot hash read is removed in matched diagnostics.
- [x] Run required CI below, update the execution ledger/AGENTS checkpoint, then commit `perf(reader): hash external snapshots while staging` with only this task and its approved docs.

### Task 2: Fit every shelf cover without cropping

**Files:** modify `app/src/main/kotlin/dev/kent/windbook/ui/components/BookCover.kt`; test `app/src/test/kotlin/dev/kent/windbook/ui/components/BookCoverTest.kt`.

**Interfaces:** keep public `BookCover(...)` unchanged; use constant `ContentScale.Fit` and center alignment from the first image. Remove obsolete dimension-dependent mutable scale behavior; update/remove `coverScaleFor` consistently with tests.

- [x] Create portrait, landscape, square and narrow cover fixtures with colored corners/edge labels. Capture the current clipping on API36 as RED. Replace the test expecting portrait Crop with whole-image behavior assertions; preserve tint/accessibility checks.
- [x] Run `./gradlew :app:testDebugUnitTest --tests '*BookCoverTest'`; capture relevant RED before implementation. If the pixel regression is only reproducible on device, retain that device RED rather than adding a test that merely repeats a constant.
- [x] Implement proportional Fit centered in the existing shaped 2:3 frame, preserving paper tint and generated fallback/title inset.
- [x] Run focused tests; verify all four fixture borders/labels appear from first display and after restart, with unchanged shelf alignment and reachable menus. Save only synthetic visual evidence in the ledger.
- [x] Run required CI, update checkpoint, commit `fix(app): show complete shelf cover images`.

### Task 3: Add repeatable Fit page and align PDF pixels to measured geometry

**Files:** modify `app/src/main/kotlin/dev/kent/windbook/reader/{ReaderViewModel,ReaderScreen}.kt`, `app/src/main/kotlin/dev/kent/windbook/reader/pdf/{PdfNotesReader,PdfReaderView}.kt`, and `app/src/main/res/{values,values-vi}/strings.xml`. Test existing `ReaderViewModelPdfTest.kt`, `ReaderOverlayTest.kt` and `reader/pdf/PdfReaderViewTest.kt` under `app/src/test/kotlin/dev/kent/windbook/reader/`.

**Interfaces:** add `ReaderViewModel.fitPdfPage(): Unit` and read-only `pdfFitRequestId: StateFlow<Long>` (starts at 0, increases per successful fit). Add defaulted `onFitPdfPage: () -> Unit = {}` to ReaderOverlay and defaulted `fitRequestId: Long = 0L` to PdfReaderView. PdfNotesReader forwards the current request. No persisted schema field.

- [ ] Write failing ViewModel tests: begin with VERTICAL/crop=true/zoom=2/zoomX=.5 and a saved page; fitting persists exactly PAGED/crop=false while keeping zoom=2/zoomX=.5 and locator. Observe one combined preferences upsert, not intermediate layout/crop writes. Fitting an already-fitted PDF increments request again. Preserve unknown JSON keys via the existing codec.
- [ ] Write failing UI regressions: View menu invokes Fit page callback; paged zoom/drag followed by request resets to original centered bounds, repeated request works and page does not change. Vertical-to-paged fit keeps page. Delayed measure on mixed portrait/landscape pages never displays pixels stretched to the fallback page aspect; overlay geometry uses the same measured rectangle.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests '*ReaderViewModelPdfTest' --tests '*ReaderOverlayTest' --tests '*PdfReaderViewTest'`; capture RED before implementation for each changed behavior.
- [ ] Implement one `displayPrefs.set(bookId, currentPrefs.copy(pdfLayout=PAGED, cropMargins=false))` per fit followed by the transient request increment. Do not call the two existing setters sequentially. Wire View menu action with `fit_page` strings `Fit page` / `Vừa trang`. Preserve current page through layout transition; reset paged zoom/pan on each request without keying/recreating the whole reader or note reveal request.
- [ ] Keep current proportional page-fit calculation, but render/show page bitmap only with its matching measured geometry. Use proportional image rendering instead of FillBounds, keeping full-page overlay bounds aligned and vertical detail tiles unchanged. If geometry needs a helper, keep it internal to PdfReaderView rather than adding another public model API.
- [ ] Run focused tests; on API36 verify fit after zoom/pan/crop, repeat fit, both layout directions, rotation, mixed-size/tall pages, all four corner markers, page keys and saved-text highlight/handle alignment. Status bar remains visible. Expect PASS without progress/note changes.
- [ ] Run required CI, update checkpoint, commit `fix(app): fit PDF pages without stretching content`.

### Task 4: Review, measure and deliver the feature APK

**Files:** modify `app/build.gradle.kts`, `AGENTS.md`, `README.md`, milestone roadmap; create `docs/testing/cover-import-pdf-fit-checklist.md`. Keep benchmark manifests, instrumentation, private screenshots and APKs ignored.

- [ ] Finish matched fresh-import trials from Task 1 on the same source bytes, recording first cover/total time, stage/hash byte counts and memory. Verify all expected covers persist across restart/rescan, source SHA unchanged, reading data unchanged and no workspace/cover-file leak. Report actual improvement and remaining parsing/rendering cost; do not expand scope silently if hashing is not the dominant cost.
- [ ] Request one independent whole-change review against the approved spec. Every Important/Critical fix receives a failing regression before implementation; rerun affected checks after fixes. Retain the review and exact commit/evidence references.
- [ ] Set versionCode **6**, versionName **0.1.5**. Run required CI and `./gradlew :app:assembleDebug`; expect all tests PASS and lint zero errors with no warnings above the existing 9. Repeat device checks only for changes introduced by review/version finalization that affect behavior.
- [ ] Export `publish/WindBook-0.1.5-cover-fit-debug.apk` and adjacent `.apk.sha256` at the main checkout root. Verify exported hash, package version and signature. No new APK is delivered if required verification fails.
- [ ] Update the checklist, roadmap, README, AGENTS sections 8/9 and resume checkpoint with completed tasks, measured results and exact remaining limitations. Commit `chore(app): prepare cover and PDF fit build` locally. Do not merge/push automatically.

## Required CI before each code commit

`./gradlew :core:model:test :core:data:testDebugUnitTest :reader:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug`

Reuse existing unaffected historical device evidence. Checkpoint after each task with commit ID and precise test/log paths; resume from the first unchecked step.
