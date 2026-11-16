# Cover speed and daily reading priorities: phased implementation plan

> Order amendment2026-11-16: the owner selected option B. Advance the already-approved EPUB selection/highlights/notes before backup/export, then trial single-book AI for EPUB/text PDF. See [the AI draft](../specs/2026-11-16-single-book-ai-design.md). Existing EPUB scope approval stands; AI details/network exception and the revised implementation plan remain pending review. Defer only D3 backup/export integration gates until backup/export exists. The historical order and AI exclusion below are superseded only by this scoped direction; no desktop book transmission is authorized by B alone.

Date: 2026-10-29
Status: approved by the owner on 2026-10-29 ("Tôi đã duyệt xong").
Execution starts on cover-speed; no increment is complete yet.
Amended: 2026-11-10 for the owner-approved toolbar, left contents drawer, position
slider, retained PDF double-tap zoom and explicitly requested embedded PDF contents.
Design: `../specs/2026-10-29-reading-priorities-design.md`.

## Execution contract

Implement sequentially: cover-speed → reading-controls → backup-export → epub-notes.
One implementer, independent whole-branch reviewer per increment under AGENTS6.5.
Do not spawn implementation agents for individual tasks. Review delegation is
only for the independent review explicitly required by the repo workflow.
After approval, use a branch per increment and retain ignored evidence under
`.superpowers/sdd/<branch>/progress.md` and `.superpowers/checks/<branch>/`.
Keep prior market-research changes; never include core/model/bin/ or private data.

Each task follows RED→GREEN→appropriate verification→commit. Run before each
code commit:

```sh
./gradlew :core:model:test :core:data:testDebugUnitTest :reader:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
```

No new Gradle dependency, INTERNET permission, source-file writes or push. New UI
text appears in both app/src/main/res/values/strings.xml and values-vi/strings.xml.
The existing9 lint warnings are the ceiling, not a target to expand.

Detailed signatures below describe WindBook-owned interfaces. Before adapter work,
verify the installed Readium3.4.0 API from its source/docs; do not copy signatures
from another toolkit version or introduce dependencies to make examples compile.
If a verified limitation changes user-visible scope, record it and seek a scoped
decision rather than silently dropping the behavior.

## A. Cover speed branch

### A1. Establish a repeatable baseline and split timings

Read only relevant existing sources/checklists:

- app/.../folders/FolderCoverLoader.kt
- core/data/.../folders/FolderScanCoordinator.kt and FolderRepository.kt
- reader/.../source/SourceMetadata.kt and SourceAccess.kt
- reader/.../ReadiumMetadataExtractor.kt, NoCoverPdfFactory.kt
- docs/testing/cover-import-pdf-fit-checklist.md

Create synthetic EPUB/PDF datasets through core:model testFixtures, with fixed
file order, nested/wide trees, ordinary/no-cover/large-raster EPUBs, malformed ZIPs,
and a large synthetic PDF. Add a reader device benchmark harness at
reader/src/androidTest/.../CoverPreparationDeviceTest.kt; collect raw timing files
outside git. A test or non-production observer splits stages; no private titles
or paths in timing output. Measure before implementation and retain the baseline.

RED contracts: first accepted cover can precede the end of discovery; unchanged
rescan performs no extraction; no production diagnostic logging survives delivery.
Do not turn a machine-dependent millisecond assertion into a flaky JVM unit test.

Environment note from this handoff: adb exists under Android/Sdk/platform-tools,
but no device was attached. The documented Neo3GT_API_36 AVD and old ignored
worktree reports are absent here; the listed AVD is Medium_Phone_API_37.0.
No local.properties or publish/ was present. Prepare the build/device environment
without claiming it matches the owner's phone until its configuration is checked.
Do not replay historical green suites solely for environment discovery.

### A2. Bounded progressive cover work

Files:

- core/data/.../folders/FolderScanCoordinator.kt
- core/data/.../folders/FolderRepository.kt
- core/data/.../folders/FolderBookEnricher.kt (locate current declaration first)
- core/data/.../db/SourceDao.kt
- app/.../folders/FolderCoverLoader.kt
- app/.../folders/FolderScreen.kt and FolderUiState.kt as needed for progress

Keep the enricher abstraction in core:data free of reader types. Extend the
existing writeBatch result with bounded committed source IDs, or an equivalent
core:data batch type. Proposed boundary:

```kotlin
data class CommittedScanBatch(val run: ScanRunEntity, val sourceIds: List<String>)
interface ProgressiveFolderEnricher {
    suspend fun enrichBatch(runId: String, sourceIds: List<String>)
    suspend fun finish(runId: String)
}
```

A coroutineScope owns discovery plus exactly one cover consumer. Queue capacity
is64 source IDs, with suspending backpressure. The consumer resolves a candidate
against the current RUNNING token at execution time. finish() drains a final
keyset pass after discovery closes; it reuses existing cache guards, not an
unbounded processed-ID set. A staged cover carries candidate/run identity through
the existing commitFolderMetadata transaction. Fatal worker errors terminate
conservatively as INCOMPLETE; cancellation still invalidates first and joins both.

Extend FolderScanCoordinatorTest and FolderCoverLoaderTest with:

- Gate the last directory; assert the first cover is committed while it is gated.
- Insert a later source whose UUID sorts before an earlier queued source; both
  obtain covers. A stale live keyset cursor must fail this test before the fix.
- Queue more than64 items; assert all eventually process, bounded acquisition,
  active lease/extractor maxima1 and no dropped IDs.
- Discovery updates a queued/in-flight row; final pass recovers valid candidates
  without overwriting a revision or re-extracting accepted unchanged covers.
- Stop/disconnect while producer is blocked and consumer is in a noncooperative
  native call; no late cover/metadata commit, all leases cleaned after join.
- Discovery errors preserve valid siblings and yield INCOMPLETE; optional corrupt
  book errors still allow later cover attempts under existing semantics.
- COMPLETE is impossible before discovery and the consumer/final pass finish.

Focused command: existing app FolderCoverLoaderTest and core:data
FolderScanCoordinatorTest / FolderCoverRepositoryTest; run mandatory CI to commit.

### A3. EPUB fast metadata and bounded raster decode

New files:

- reader/.../metadata/EpubMetadataFastPath.kt
- reader/.../metadata/CoverEncoder.kt (shared bounded cover conversion)
- reader/src/test/.../metadata/EpubMetadataFastPathTest.kt
- reader/src/androidTest/.../EpubCoverDecodeDeviceTest.kt

Modify ReadiumMetadataExtractor.kt / source/SourceMetadata.kt to choose the fast
path and fall back on the SAME pinned local snapshot. Keep PDF paths unchanged.
Own result type separates Success, Unsupported and Invalid; Unsupported may use
Readium, Invalid from archive guard must not bypass it.

Use existing JDK ZIP + Android XML/image APIs. Bound container/OPF XML and cover
entry reads (proposed XML1MiB each, encoded raster16MiB); values above a fast-path
limit use the legacy path rather than falsely declaring a valid book invalid.
Do not expand other ZIP entries. Honor namespaces, EPUB2 cover meta/manifest IDs,
EPUB3 cover-image, relative OPF paths and proper URI decoding. Reject traversal,
external URLs, duplicate/ambiguous metadata and external entity expansion. SVG,
XHTML-only or ambiguous cover declarations go to Readium.

Bounds-limit image decode before allocating the output; final image uses the
existing600px maximum, proportional scale and WebP80. Recycle only owned temporary
bitmaps after encoding; never leave scaled/native bitmaps alive after cancellation.
Preserve title/author normalization and cover orientation for supported fixtures.

RED tests: EPUB2/3 metadata parity, Unicode, namespaces, percent-encoded paths,
no-cover fallback, unsupported SVG/XHTML, malformed XML, path traversal, ZIP guard
failure, oversized fast-path entry fallback, cancellation propagation, decoder
dimension/allocation limits and exact one-source-acquisition identity. Confirm
source file bytes and optional-cover fallback behavior remain unchanged.

Compare legacy and fast-path title/author and visual cover content on approved
samples; use generated metadata in committed tests. Route unresolved parity cases
to fallback rather than silently returning an inferior result.

### A4. Measure, review and deliver the speed increment

Alternate baseline/candidate≥5 trials using identical restored fresh DB states;
separate cached runs and publish the stage metrics required by the design. Do not
overwrite an existing emulator user's library without an exact backup/restoration
procedure. Use synthetic libraries first; real files are read-only.

Acceptance: early cover appears before final discovery, actual elapsed cover-pass
gain exceeds trial spread, no loss in cover success/quality, no unexplained PSS
increase or cached-rescan regression, reading data identical for unchanged files.
If unmet, record failure and diagnose before claiming delivery as a speed fix.

Add docs/testing/cover-speed-checklist.md. Independent review, failing regression
for every Important/Critical fix, mandatory CI, assembleDebug, version/hash/signature
verification. Proposed version0.1.6/code7, subject to actual baseline at execution.
Export publish/WindBook-0.1.6-cover-speed-debug.apk and adjacent SHA256, untracked.
Update AGENTS8/9 and roadmap in the same commits as the changes.

## B. Reading controls branch

The November10 decisions extend this increment; they do not reorder it ahead of
the cover-speed delivery gate. Apply sections3.2–3.3 of the amended design. The
approval is already recorded; do not ask again about the same layout or PDF zoom.

### B1. Atomic reading preference model and persistence

Add core/model/.../ReaderTypography.kt with nullable per-field overrides,
validated ranges and pure resolution; tests for inherit/reset/clamp/nonfinite
values, publisher default and fixed-layout capability behavior.
Add core/data/.../ReaderPreferencesRepository.kt for global DataStore settings.
Extend the existing book JSON codec with typography fields, preserving unknown
keys and every PDF field. Replace shared read-modify-write operations with
transactional field transforms; migrate existing PDF call sites to that API.

RED tests in ReaderTypographyTest, ReaderPreferencesRepositoryTest and
DisplayPreferencesRepositoryTest: defaults leave untouched books unchanged;
global→book resolution, reset only intended fields, concurrent PDF/typography
updates cannot erase one another, rapid edits and book switching preserve final
values, legacy unknown/reflow keys remain inert/preserved. No schema change yet.

### B2. Real navigator settings and Aa UI

Files: reader/.../ReaderHostFragment.kt, ReaderTheme.kt; app/.../reader/
ReaderViewModel.kt, ReaderScreen.kt and new ReaderTypographySheet.kt;
app/.../settings/SettingsScreen.kt / SettingsViewModel.kt for defaults.

Introduce a WindBook-owned applyTypography(settings) boundary and capabilities
for reflowable/fixed-layout sessions. Register existing Literata through the
installed toolkit API, not CSS URL guesses or a second font download.
Collect newest resolved preferences and apply in place; preserve the latest
locator while layout changes. Keep default sepia/dark page backgrounds consistent
between Compose and the navigator. PDF View behavior stays on the PDF branch.

RED tests: changing settings after initial bind reaches the current navigator;
rebinding the same session does not accidentally ignore updated settings; stale
results cannot apply to a replacement session. Sheet/global scopes, reset, empty
states, localized labels and48dp controls receive focused app tests.
Device gate: real Vietnamese EPUB/TXT text reflows; same sentence after resizing,
font switch, rotation, restart and returning to publisher styles. Validate a
fixed-layout EPUB without offering fake font changes. System font130/200% UI checks.

### B3. Bookmarks and additive v7→v8 migration

New model Bookmark.kt; data db/BookmarkDao.kt, BookmarkMigration.kt and
BookmarkRepository.kt. Register migration/entities in WindBookDatabase.kt and
export schema8. Fields include id, book_id, fingerprint, locator_json,
progression, label, text_encoding, created_at, updated_at, deleted_at.
Bookmarks count as durable data in BookSourcesRepository/SourceRevisionPolicy.
In the same migration add db/PdfContentsEntity.kt and PdfContentsDao.kt for the
derived cache used by B4a: book_id, fingerprint, payload_version, status and
contents_json, with a book foreign key. Cache identity includes book/revision and
SHA; it must not assume SHA is globally unique. It does not count as durable data.

RED tests: exact preservation of all v7 tables, duplicate-toggle idempotency,
CRUD/tombstones, PDF locator unchanged, TXT encoding-separated anchors,
source-revision split when only bookmarks exist, stale-session rejection,
hidden/restored book references and invalid locators. Do not use page percentage
as a substitute for an EPUB semantic locator.
Verify cache absence on migration, identity isolation between revisions, and that
a contents-only cache does not change the durable-data revision policy.

### B4. Toolbar, left contents drawer, seek, search and return

New core/model/.../ReaderNavigation.kt for immutable TOC/search/jump records and
bounded jump history; reader/.../ReaderNavigationController.kt adapts existing
publication/navigator APIs. Add app/.../reader/ReaderContentsDrawer.kt,
ReaderPositionSlider.kt, ReaderPageJumpDialog.kt and ReaderSearchViewModel.kt.
Connect through ReaderHostFragment, ReaderViewModel and ReaderScreen.
PDF page jump goes through PdfSession and existing PDF locator/page callbacks;
ReaderEngine must never open PDFs via Readium. B4a provides cached PDF contents
from a separate metadata-only preparation task.

ReaderOverlay gets Back/title/Contents/overflow in that order. Overflow exposes
bookmark creation/list plus EPUB/TXT Search/Aa or PDF View. Keep existing encoding
and Mark finished actions reachable. The left drawer owns its scrim and Back
handling; only the toolbar button opens it, avoiding a new edge/double-tap gesture.
Close overflow before opening another surface. After a valid contents jump close
the drawer and return focus to the reader; preserve an invalid entry's current
page and explain why it cannot navigate.

Use a draft slider value scoped to the session. onValueChange changes only the
preview; onValueChangeFinished submits one validated target. PDF pageIndex is
round(fraction * (pageCount - 1)), with explicit zero/one-page and nonfinite guards.
Do not replace saved PDF progression with this UI fraction. EPUB/TXT use stable
publication positions/locators rather than guessed page counts. Disable seeking
until targets exist, retain encoding/revision identity and discard stale drafts
on book/layout change. Accessibility adjustments use the same commit boundary.

Proposed owned interface: contents:StateFlow<ContentsState>,
search(query):Flow<SearchBatch>, suspend goTo(locatorJson):NavigationResult.
ContentsState distinguishes preparation, ready, empty, unavailable and failure.
NavigationResult distinguishes completed, unchanged, stale and failed requests.
Keep streaming search
bounded/paged; do not collect the entire book/library into memory. History has10
origins, pushes only successful deliberate jumps, and retains full locators.
One completed slider gesture is one origin. Failed/same-location jumps and history
returns add none; resolve asynchronous navigator completion before saving history.

RED tests in ReaderNavigationTest, ReaderContentsDrawerTest, ReaderOverlayTest,
ReaderPositionSliderTest and ReaderPageJumpDialogTest: nested/empty TOC; drawer
opens from the button and Back closes it first; no background page turn through
the scrim; per-format menu entries and localized accessibility labels; slider
drag produces no jump/progress save until release, cancel has no effect, rapid
drags/session replacement cannot navigate stale targets, first/last/single-page
PDF mapping, invalid/empty page input, no render for every preview; history records
one origin per successful gesture. Also cover newest query cancelling older work;
bounded result pages; Vietnamese query/snippet handling; stale results after book close; invalid
destinations leave the current page intact; PDF page bounds; same TXT encoding;
back history capacity and page-turn exclusion. Device tests prove exact passage
after TOC/search/bookmark jumps and Back, including typography changes and API31 PDF.
Add actual API31/API36 device gates for the left drawer/slider, PDF physical page
labels, center-tap overlay, double-tap zoom in paged/vertical modes, annotation
handle drags, system Back, rotation and EN/VI at130/200% font size. Measure PDF
navigation on a large synthetic document; dragging must not queue native renders.

### B4a. PDF contents capture, cache and existing-library backfill

Reuse the installed Readium3.4.0 adapter; no dependency change. Inspection of the
installed AARs confirms PdfDocument.outline, PdfParser conversion to contents
links and Pdfium's zero→one-based page conversion. Evidence is retained under
.superpowers/checks/reading-controls-layout/. Prove actual destinations with a
synthetic PDF outline fixture before wiring production navigation.

Files: core/model/.../MetadataExtractor.kt (optional typed contents result), new
PdfContents.kt; core/data/.../PdfContentsRepository.kt, importer/BookImporter.kt,
folders/FolderRepository.kt and BookSourcesRepository.kt; reader/.../
ReadiumMetadataExtractor.kt, source/SourceMetadata.kt and new metadata/
PdfContentsExtractor.kt and MetadataWorkCoordinator.kt; app/.../reader/
ReaderViewModel.kt, folders/FolderCoverLoader.kt and di/AppModule.kt. Wire a shared
coordinator into import, source enrichment and backfill so metadata/cover extraction
remains serial; the current SourceMetadata constructs its own extractor and must
receive the shared coordinator too. Acquire the coordinator once per operation,
avoiding nested acquisition by the common extractor. Reuse the B3 cache schema.

Owned result separates Ready(entries), Empty, Unavailable/TooLarge and Failure;
absence means not prepared. Each entry contains a stable tree ID, parent/order,
label and optional validated one-based page, not an arbitrary external URL.
Keep grouping nodes and valid descendants when a parent lacks a destination.
Proposed normalization limits:10000 nodes,32 levels,1024 characters per label and
1MiB encoded cache. Exceeding a bound yields an explicit unavailable result,
not a truncated tree presented as complete. Iterate WindBook normalization with
bounds; native parsing still needs device memory/time verification.

Capture contents from the existing publication before metadata extraction closes
it; persist only against the same verified book/SHA as the metadata commit.
For pre-existing PDFs with covers, Contents can enqueue metadata-only backfill
through the same serialized coordinator; rescan can request missing caches too.
This path uses NoCoverPdfFactory and does not call pdfCoverWebp, perform another
cover pass, reopen the reader or change reading data. Deduplicate in-flight work,
retain known-empty results, close leases/publications on failure/cancellation,
and reject late results after Stop/disconnect/source or session change.

RED tests in PdfContentsTest, PdfContentsRepositoryTest and existing importer/
source/loader suites: nested Unicode labels, first/last pages, grouping nodes,
invalid/out-of-range/external destinations, empty versus not-prepared/failure,
normalization limits, exact immutable-SHA cache identity, changed-source rejection,
one preparation for repeated opens, existing-cover backfill without cover render,
serial extraction during scans, retry, cancellation and resource cleanup. Preserve
all book IDs, source guards, notes/progress/preferences and legacy PDF locators.

Add reader/src/androidTest/.../PdfContentsDeviceTest.kt using synthetic PDFs with
nested/no/invalid outline destinations. On API31/36, verify exact framework page
after selecting first/nested/last entries, cache reuse after restart, existing PDF
backfill, and unchanged native-render counts during metadata preparation. Capture
sampled PSS/timings; retain the large-scan PDF safety and cover-speed regression gates.

### B5. Review and deliver

Add docs/testing/reading-controls-checklist.md, exact schema/reading-data evidence,
independent review and fix pass, mandatory CI/device gates and debug build.
Proposed0.1.7/code8, APK/checksum in main-root publish/. Record the delivered M2/M3
subset; do not mark all reading-comfort/navigation features complete.

## C. Backup/export branch

### C1. Versioned portable snapshot and deterministic additive import

New core/model/.../backup/WindBookSnapshot.kt, SnapshotValidation.kt and
RestoreMapping.kt using the existing serialization dependency. Data layer adds
backup/SnapshotRepository.kt to capture rows consistently without copying the live
SQLite/WAL. Represent raw preference and annotation payloads losslessly.

Owned contracts: capture():WindBookSnapshot, preview(snapshot):RestorePreview,
restore(validated, mapping):RestoreResult. Archive identity is stable across a
single exported file. Deterministic conflict IDs and remapped FKs preserve current
data; same-archive repeat does not overwrite later edits. Do not merge books by
SHA alone. Backups include all durable rows, including hidden/revisions and TXT
encoding history, but not active scan jobs, OS grants or private diagnostics.

RED tests: full snapshot round trip, unknown JSON keys, row consistency under
writes, same-ID conflicting SHA, nonunique SHA revisions, bookmark/annotation FK
mapping, identical-archive repeat, local edits preserved on repeat and unsupported
future versions rejected before mutation.

### C2. Bounded ZIP, staged file restore and Markdown

Add core/data/.../backup/BackupArchive.kt, BackupRestoreCoordinator.kt;
core/model/.../notes/MarkdownExporter.kt. Use existing java.util.zip and SAF,
no archive dependency. Quiesce scans/readers before actual restore publication.
Validate complete manifest/entries before transaction; verify included owned
files' SHA, stage private files, remap paths, commit all rows, clean on failure.
Missing files remain reconnectable; external grants are never synthesized.

RED tests: truncated/duplicate/traversal ZIP, decompression/size budgets,
insufficient disk, cancellation before/during commit, corrupt included-book hash,
failed final output stream, unknown entry policy, preexisting file collision,
transaction rollback and no orphan artifacts. Import stage files have unique
ownership so cleanup cannot delete existing books/covers.
Markdown tests cover Vietnamese, multi-paragraph quotes, punctuation/escaping,
empty bodies, deterministic book/location order and stable PDF page references.
Protect test backups containing personal data from git.

### C3. Settings flows and delivery

Add app/.../backup/BackupViewModel.kt, BackupScreen.kt; wire Settings and file
picker launchers. Backup defaults data-only with explicit owned-files option.
Restore preview explains counts, conflicts/duplicates and files needing reconnect;
confirmation happens in the app before import, Cancel leaves current data intact.
Notes gains Save Markdown for a book/all. All strings EN/VI.

Device gates: saved archive opens in the system picker, data-only and owned-file
round trips into a separate synthetic app state, unavailable grants explained,
cancel/failure retains original library, same archive twice is idempotent, exported
Vietnamese Markdown readable. No owner-library replacement to demonstrate restore.
Independent review, mandatory CI, build and docs/testing/backup-export-checklist.md.
Proposed0.1.8/code9 APK/checksum in publish/.

## D. EPUB notes branch

### D1. Typed anchors and additive v8→v9 migration

Add core/model/.../EpubTextAnnotation.kt and shared annotation-kind/anchor models.
Add AnnotationKindMigration.kt; annotate existing rows as PDF_TEXT without changing
PDF anchor_json/quote/color/body/timestamps/IDs. Extend AnnotationRepository,
AnnotationDao, NotesViewModel/NotesScreen and route models to support both kinds.
Do not force legacy PDF JSON through an EPUB decoder or drop invalid visible notes.

RED tests: migration preserves all v8 tables exactly apart from the additive kind;
PDF behavior unchanged; malformed/unknown anchor kinds visible but unrevealable;
EPUB locator/quote/context validation and stale-session SHA rejection;
durable-data revision/hide/reconnect guards for both kinds.

### D2. EPUB selection, decoration and exact passage revisit

ReaderHostFragment exposes bounded current-resource selection and decoration
events. Add app/.../reader/epub/EpubNotesReader.kt plus a shared note editor if the
existing PDF editor can be reused without geometry coupling. Selection callback
carries a session generation and fingerprint of the pinned lease. Save rechecks
book identity, clears ephemeral selection and applies the decoration. Native
selection handles stay accessible and do not collide with page-edge navigation.

RED tests: quote/context bounds, duplicate Save, draft retry/rotation, cancellation
on resource change, old session events, jump returns to the exact quote after
font-size/line-height changes, annotation tapping opens the correct editor,
PDF pages/selection remain unchanged. No cross-chapter or fixed-layout annotation.

### D3. Extend portability and deliver

Snapshot and Markdown exporter round-trip both annotation kinds; retain previous
snapshot reader compatibility and unknown payload fields. Device gates on API31
and36: synthetic Vietnamese EPUB highlight→note→search→edit→restart→source revisit,
layout changes, duplicate quote occurrences, missing/changed source, both kinds in
Notes, backup/restore, correct Markdown. Preserve existing PDF gates.

Independent review, mandatory CI, build and docs/testing/epub-notes-checklist.md.
Proposed0.1.9/code10 APK/checksum in publish/. Update AGENTS/roadmap/checklists with
actual results and owner phone-test status. Future scan notes, sync, additional
formats and full-M2/M3/M4 features remain outside this plan.

## Approval and execution checkpoint

The owner approved the written design, this plan and sequential execution with
independent review per increment on 2026-10-29 ("Tôi đã duyệt xong").
Cover-speed environment/harness work has started; baseline measurement,
production changes and release gates are tracked in the ignored execution ledger.
Routine implementation choices within approved constraints need no repeated
permission. Material scope/dependency changes and any push remain separate decisions.
