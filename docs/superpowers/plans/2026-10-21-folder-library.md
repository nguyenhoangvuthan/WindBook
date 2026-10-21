# Folder Library Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Scan remembered folders with extension filters and read linked EPUB/PDF books without permanently importing them.

**Architecture:** Separate book identity from owned/external sources. Persist tree grants and scan membership, then resolve external streams into session-owned resources. A cancellable scanner feeds the existing library while readers keep existing locators.

**Tech Stack:** Kotlin, Compose, Room 3, Android DocumentsContract, PdfRenderer and installed Readium 3.4.0; no new dependency in this plan.

**Spec:** `docs/superpowers/specs/2026-10-21-folder-library-design.md` (owner approved 2026-10-21).

**Status:** Proposed implementation plan; execution method and plan approval pending.

## Global Constraints

- Android 12+ (minSdk 31, targetSdk 36, compileSdk 37); four-module direction unchanged.
- No INTERNET or all-files permission; source folders/files are read-only.
- No new Gradle dependency without explicit approval. This part needs none.
- Eight discovery extensions: EPUB, PDF, MOBI, AZW3, FB2, CBZ, CBR and TXT; EPUB/PDF selected by default; recursion enabled by default.
- EPUB/PDF source opening is this plan's usable increment. Complete the additional reader plan before claiming the entire approved feature is finished.
- Preserve imported books, IDs, progress, preferences, any existing annotations and the exact PDF locator shape. No Reflow or shoulder-trigger work.
- Every new app string has English and Vietnamese resources. No private filenames/screenshots in git, no attribution, no push or merge without the owner.
- Execution branch: `folder-library`, based on the current approved local work. Creating a descendant branch does not merge `remove-pdf-reflow` into main.
- Run the full CI command below before **every code commit**, update AGENTS.md §8/9 in that commit, and record deviations in `.superpowers/sdd/folder-library/progress.md`.

## Review Focus

- Revoked grants and failed/interrupted enumeration must not mark unseen books missing (Tasks 3, 4, 7).
- A provider can expose one document through overlapping roots; identity must not depend on path text (Tasks 1, 2, 4).
- Same URI, new bytes, same size/mtime: do not reuse old positions/annotations (Task 6).
- Closing or switching readers during open must release every descriptor/session (Task 5).
- Hiding/disconnecting a folder book must preserve reading data and never touch its source (Tasks 2, 6, 7).

---

## Sequence, evidence and shared checks

Read `docs/research/2026-10-21-folder-reader-feasibility.md`. Pinned API inspection
found Readium content resources and URI retrieval, but no device result is
claimed. The source-opening device gate in Task 5 is required. Additional readers
are in `2026-10-21-ebook-format-readers.md`; their dependency gates are independent.

```bash
./gradlew :core:model:test :core:data:testDebugUnitTest :reader:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
```

This is **CI** below. PASS means all tests pass and lint adds no warnings to the
recorded baseline. A failing-test step means a behavior failure or missing new
symbol, not an unrelated environment/build failure. Test snippets below are test
bodies inside the named classes; use existing JUnit4, runTest and Robolectric
patterns. Test helpers belong to their named test file unless shared explicitly.

### Task 1: Source types and format discovery

**Files:**
- Modify `core/model/src/main/kotlin/dev/kent/windbook/core/model/Book.kt`.
- Update compile-time exhaustive/null handling in `reader/src/main/kotlin/dev/kent/windbook/reader/ReaderEngine.kt`, `ReadiumMetadataExtractor.kt`, and `app/src/main/kotlin/dev/kent/windbook/reader/ReaderViewModel.kt`; keep nullable paths from reaching File().
- Create `core/model/src/main/kotlin/dev/kent/windbook/core/model/BookSource.kt` and `FolderScan.kt` in that same package.
- Test `core/model/src/test/kotlin/dev/kent/windbook/core/model/FolderScanTest.kt`.

**Interfaces:**
- `sealed interface BookSource`: `Owned(val path: String)` and `External(val uri: String, val key: DocumentKey)`.
- `data class DocumentKey(val authority: String, val documentId: String)`; never store Android Uri in core:model.
- `SourceAvailability`: `UNVERIFIED`, `AVAILABLE`, `MISSING`, `ACCESS_DENIED`, `DISCONNECTED`, `UNREADABLE`. Types live beside BookSource.
- `data class ScanOptions(val extensions: Set<BookFormat> = setOf(EPUB, PDF), val recursive: Boolean = true)`.
- `fun candidateFormat(name: String): BookFormat?`; `fun ScanOptions.accepts(name: String): Boolean`.
- Add all eight enum values; change Book.filePath and Book.sha256 to nullable for external candidates. Update exhaustive format branches with explicit unavailable-reader behavior until adapters exist; do not route other ZIPs as EPUB.

- [ ] Write `FolderScanTest` covering defaults, final suffix and stable identity:
  ```kotlin
  assertTrue(ScanOptions().recursive)
  assertTrue(ScanOptions().accepts("sample.EpUb"))
  assertFalse(ScanOptions().accepts("sample.pdf.bak"))
  assertFalse(ScanOptions().accepts("sample.mobi"))
  assertEquals(BookFormat.CBR, candidateFormat("sample.CBR"))
  assertEquals(DocumentKey("provider", "42"), DocumentKey("provider", "42"))
  assertNotEquals(DocumentKey("provider", "42"), DocumentKey("other", "42"))
  ```
- [ ] Run `./gradlew :core:model:test --tests '*FolderScanTest'`; expect FAIL before implementation.
- [ ] Implement locale-independent extension matching and the types; empty extension sets accept nothing. Add tests for all eight suffixes, dotless names and Turkish default locale. Until later tasks, nullable paths return a source-unavailable error and non-EPUB/PDF reader/metadata branches return unsupported-format before opening anything.
- [ ] Repeat the targeted command, then CI; expect PASS.
- [ ] Commit the task files and AGENTS.md: `feat(model): define external book sources and folder filters`.

### Task 2: Persist sources, memberships and migration

**Files:**
- Modify `core/data/src/main/kotlin/dev/kent/windbook/core/data/db/Entities.kt`, `LibraryDao.kt`, `WindBookDatabase.kt`.
- Create `core/data/src/main/kotlin/dev/kent/windbook/core/data/db/FolderEntities.kt`, `FolderDao.kt`, `SourceDao.kt`, `FolderMigration.kt`.
- Modify `core/data/src/main/kotlin/dev/kent/windbook/core/data/LibraryRepository.kt` and `importer/BookImporter.kt`.
- Create `core/data/src/main/kotlin/dev/kent/windbook/core/data/BookSourcesRepository.kt`.
- Test `core/data/src/test/kotlin/dev/kent/windbook/core/data/db/FolderMigrationTest.kt`, `SourceDaoTest.kt`, and update existing `MigrationTest.kt`, `LibraryRepositoryTest.kt`, `importer/BookImporterTest.kt`.
- Export `core/data/schemas/dev.kent.windbook.core.data.db.WindBookDatabase/3.json` on this base.

**Interfaces and schema:**
- `folders`: id, tree_uri unique, display_location, extensions_json, recursive, disconnected_at, last_scan_at.
- `book_sources`: id, book_id FK, kind, local_path nullable, uri nullable, authority/document_id nullable unique pair, size/mtime nullable, verified_sha256 nullable, availability.
- `folder_memberships`: folder_id/source_id composite PK, relative_path, parent_document_id, last_seen_run. This permits one source in multiple roots.
- `scan_runs`: id, folder_id, options_json, state (`RUNNING`, `COMPLETE`, `INCOMPLETE`, `INTERRUPTED`), examined/matched/error counters. State persists even if the screen closes.
- `BookSourcesRepository.sources(bookId: String): List<BookSource>`; `hideExternalBook(bookId: String)`; `restoreExternalBook(bookId: String)`; `disconnectFolder(folderId: String)`.
- External books start with null file_path/hash; owned books retain both. Keep the nullable unique books.sha256 index for canonical hashes; conflicting already-read copies retain source-level hashes rather than violating that index.

- [ ] Create a v2 schema fixture containing two imported books (one tombstoned), progress and unknown preference keys. Open the migrated DB and assert:
  ```kotlin
  assertEquals(oldIds, db.libraryDao().allIdsIncludingHidden().toSet())
  assertEquals(oldLocator, db.libraryDao().progress("kept")!!.locatorJson)
  assertEquals(oldPrefs, db.bookPreferencesDao().json("kept"))
  assertEquals(BookSource.Owned("/owned/a.epub"), sources.sources("kept").single())
  assertEquals(2, db.sourceDao().countOwned())
  ```
  Add DAO tests inserting two unhashed external books and overlapping memberships.
- [ ] Run `./gradlew :core:data:testDebugUnitTest --tests '*FolderMigrationTest' --tests '*SourceDaoTest'`; expect FAIL.
- [ ] Implement a transactional explicit migration. Rebuild books with nullable columns while preserving FK dependents; test foreign_key_check and indices. Register migration in disk and test builders. Backfill owned sources for every legacy book; modify importer to register owned sources atomically and retain its explicit reimport behavior. Hide external books without deleting progress; owned import deletion retains existing behavior. Disconnect only updates connection state and releases an unused grant after checking other memberships; never delete a source file.
- [ ] Run targeted tests, v1→latest migration, importer/repository tests and CI. If notes schema v3 has been implemented on the chosen base, instead create v4 and test the real v2→v3→v4 chain with an annotation fixture; never overwrite a historical schema.
- [ ] Commit: `feat(data): persist folder sources without copying books`.

### Task 3: Read-only tree access and streaming enumeration

**Files:**
- Create `core/data/src/main/kotlin/dev/kent/windbook/core/data/folders/TreeAccess.kt`, `FolderScanner.kt`, `FolderGrants.kt`.
- Test `core/data/src/test/kotlin/dev/kent/windbook/core/data/folders/FolderScannerTest.kt`, `FolderGrantsTest.kt`.

**Interfaces:**
- `TreeDocument(key: DocumentKey, uri: String, name: String, isDirectory: Boolean, size: Long?, modifiedAt: Long?)`.
- `TreeAccess.children(treeUri: String, directory: DocumentKey): Flow<TreeDocument>`; use a cursor in the real implementation, not an eager full-tree list.
- `FolderScanner.scan(treeUri: String, root: DocumentKey, options: ScanOptions): Flow<ScanEvent>`.
- Events: `Found(document, relativePath, parentKey)`, `Examined(count)`, `DirectoryFailed(key, reason)`, `Finished(complete: Boolean)`.
- `FolderGrants.persistRead(treeUri: String, returnedFlags: Int)` and `hasRead(treeUri: String): Boolean`.

- [ ] Write a fake tree containing root PDF, nested EPUB, TXT, a repeated directory ID and an inaccessible sibling. Collect scanner events:
  ```kotlin
  assertEquals(setOf("root.pdf", "sub/nested.EPUB"), foundPaths)
  assertEquals(1, failures.size)
  assertFalse(completion.complete)
  assertEquals(1, tree.visitCount(repeatedDirectory))
  assertEquals(0, tree.openContentCount)
  assertEquals(0, tree.writeCount)
  ```
  Add recursion=false, cross-authority rejection, provider cursor failure mid-directory and cancellation-closes-cursor tests.
- [ ] Run `./gradlew :core:data:testDebugUnitTest --tests '*FolderScannerTest' --tests '*FolderGrantsTest'`; expect FAIL.
- [ ] Implement iterative traversal using DocumentsContract child queries and document IDs, read-only grant flags and IO dispatcher. Null names are counted/skipped; unknown size/mtime are valid. Propagate CancellationException; treat access failure as incomplete, not an empty success. Check cancellation between cursor rows; never guess a content URI from an absolute path. Check authority and tree membership before traversal.
- [ ] Repeat targeted tests and CI; expect PASS, with no book-content reads during enumeration.
- [ ] Commit: `feat(data): scan granted folders recursively`.

### Task 4: Scan reconciliation, cancellation and remembered folders

**Files:**
- Create `core/data/src/main/kotlin/dev/kent/windbook/core/data/folders/FolderRepository.kt`, `FolderScanCoordinator.kt`.
- Extend `db/FolderDao.kt`, `db/SourceDao.kt` from Task 2.
- Test `core/data/src/test/kotlin/dev/kent/windbook/core/data/folders/FolderRepositoryTest.kt`, `FolderScanCoordinatorTest.kt`.

**Interfaces:**
- `FolderRepository.save(treeUri: String, displayLocation: String, options: ScanOptions): String` returns folder ID; `observeFolders(): Flow<List<FolderSummary>>`; `observeEntries(folderId: String, query: String, formats: Set<BookFormat>): Flow<List<FolderEntry>>`.
- `FolderEntry(bookId, sourceId, name, relativePath, format, availability)` and `FolderSummary(id, displayLocation, options, connected, lastScanAt)`.
- App-singleton `FolderScanCoordinator.start(folderId: String): String`, `stop(runId: String)`, `observe(folderId: String): Flow<ScanProgress>`.
- `ScanProgress(runId, state, examined, matched, added, known, unreadable, directoryErrors, currentPath)`. Start serializes jobs globally; stop/change commits the old incomplete run before a new one can write.

- [ ] With an in-memory DB and fake TreeAccess, scan twice, overlap a second root, hide one book, fail one child, and cancel another run. Assert:
  ```kotlin
  assertEquals(2, sourceCountAfterSecondScan)
  assertEquals(2, bookCountAfterOverlappingRoot)
  assertFalse(hiddenBookWasRevived)
  assertEquals(0, missingCountAfterFailedScan)
  assertEquals(0, missingCountAfterCancelledScan)
  assertEquals("INTERRUPTED", reloadedRunningRun.state)
  ```
- [ ] Run `./gradlew :core:data:testDebugUnitTest --tests '*FolderRepositoryTest' --tests '*FolderScanCoordinatorTest'`; expect FAIL.
- [ ] Implement incremental bounded transactions (100 entries maximum per batch), indexed filenames without extraction, canonical source upsert, per-run tokens and durable summaries. Mark missing only after a fully successful run and only for that run's selected extensions/recursion coverage. Any directory failure conservatively suppresses missing reconciliation for the whole run. Do not erase entries excluded by new filters. On startup mark stale RUNNING runs INTERRUPTED. Stop discards no previously committed entries.
- [ ] Repeat targeted tests and CI; add concurrent double-start, late-emission-after-stop and unknown-source-name assertions before PASS.
- [ ] Commit: `feat(data): reconcile cancellable folder scans`.

### Task 5: External EPUB/PDF sessions with bounded ownership

**Files:**
- Create `reader/src/main/kotlin/dev/kent/windbook/reader/source/SourceAccess.kt`, `SessionWorkspace.kt`.
- Modify `reader/src/main/kotlin/dev/kent/windbook/reader/ReaderApi.kt`, `ReaderEngine.kt`, `ReadiumServices.kt`, `pdf/PdfDocumentHandle.kt`.
- Test `reader/src/test/kotlin/dev/kent/windbook/reader/source/SessionWorkspaceTest.kt`.
- Create `reader/src/androidTest/kotlin/dev/kent/windbook/reader/ExternalSourceDeviceTest.kt`, `TestBookProvider.kt`, and register the synthetic provider in `reader/src/androidTest/AndroidManifest.xml`.

**Interfaces:**
- `BookOpener.open(source: BookSource, format: BookFormat): Result<ReaderSession>`; keep a File overload delegating to Owned for compatibility while migrating callers.
- `SourceAccess.acquire(source: BookSource): SourceLease` (suspend IO); SourceLease is AutoCloseable and supplies `openStream(): InputStream`, `openDescriptor(): ParcelFileDescriptor`, and `suspend fingerprint(): String`. Descriptor-backed reads use duplicated/repositioned descriptors without losing the acquired source identity; a provider unable to supply a stable seekable handle uses the bounded staging path. ReaderSession owns both the source and workspace leases.
- `SessionWorkspace.create(): SessionLease`; lease implements AutoCloseable and exposes `directory: File`, `stage(source: BookSource): File`. Lease lives until its ReaderSession closes; no preparation cleanup while resources are in use.
- `ReadiumServices.openPublication(uri: Uri): Result<Publication>` via pinned content resource APIs; existing File overload remains.
- `PdfDocumentHandle.open(fd: ParcelFileDescriptor): PdfDocumentHandle` consumes descriptor ownership even on failure. Keep `open(File)` delegating through the same path.

- [ ] Write tests that open tiny synthetic EPUB/PDF through a local DocumentsProvider and verify content/pageCount without any permanent book copy. Inject a non-seekable pipe for PDF and an open failure:
  ```kotlin
  assertEquals(0, permanentBooksDir.listFiles().orEmpty().size)
  assertEquals(1, pdf.pageCount)
  session.close()
  assertEquals(0, workspace.activeLeaseCount)
  assertEquals(0, provider.openDescriptorCount)
  ```
  Add cancellation immediately after open and oversized/unknown-length input/low-space staging tests with a small injected quota.
- [ ] Run `./gradlew :reader:testDebugUnitTest --tests '*SessionWorkspaceTest'` and `./gradlew :reader:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.kent.windbook.reader.ExternalSourceDeviceTest`; expect FAIL before the seam exists.
- [ ] Implement direct URI EPUB and descriptor PDF first. If a provider cannot support random access, stage only the active book under cacheDir/reader-sessions. Enforce the feasibility note's 500 MiB source, 1 GiB total scratch, 64 MiB reserve and 64 KiB buffers. Reject oversized content before rendering. Session close is idempotent; close work runs off the main thread and cancellation must release unpublished sessions. Clean orphan workspace directories on startup without touching active leases. Do not fall back from corrupt or permission-denied files to copying.
- [ ] Repeat device tests on API 36 and API 31; record URI EPUB open latency and cache usage. If direct Readium URI access cannot satisfy lifetime/size bounds, use the explicit temporary-file path under the same lease and document that evidence. Run CI; expect PASS before later UI work.
- [ ] Commit: `feat(reader): open folder books through owned source sessions`.

### Task 6: Revision verification, reconnect and metadata

**Files:**
- Extend `core/data/src/main/kotlin/dev/kent/windbook/core/data/BookSourcesRepository.kt`.
- Create `core/data/src/main/kotlin/dev/kent/windbook/core/data/SourceRevisionPolicy.kt`.
- Modify `app/src/main/kotlin/dev/kent/windbook/reader/ReaderViewModel.kt`, `ReaderScreen.kt`, `di/AppModule.kt`, `navigation/WindBookNavHost.kt`.
- Create `reader/src/main/kotlin/dev/kent/windbook/reader/source/SourceMetadata.kt`.
- Test `core/data/src/test/kotlin/dev/kent/windbook/core/data/SourceRevisionPolicyTest.kt`, `BookSourcesRepositoryTest.kt`, and extend `app/src/test/kotlin/dev/kent/windbook/reader/ReaderViewModelTest.kt`.

**Interfaces:**
- `verify(bookId: String, source: BookSource, sha256: String): Verification` where `Verification` is `Ready(bookId, source)`, `Redirect(bookId)`, `Changed(newBookId)`, or `HiddenMatch(bookId)`.
- `reconnect(bookId: String, replacement: BookSource, sha256: String): Verification`; different bytes are never a silent relink.
- `SourceMetadata.read(source: BookSource, format: BookFormat): Result<ExtractedMetadata>` enriches only opened books, with no full-resolution PDF cover and cover longest edge <=600 px.

- [ ] Assert same URI/size/mtime with changed fingerprint creates a separate book revision and retains original progress. Assert verified duplicate connects to a canonical book only when the candidate has no reading data; already-read conflicts stay separate. Assert hidden matches require Restore. In ViewModel tests:
  ```kotlin
  assertEquals(oldLocator, repository.savedLocator(oldBookId))
  assertNotEquals(oldBookId, changed.newBookId)
  assertNull(repository.savedLocator(changed.newBookId))
  assertEquals(0, opener.callsBeforeVerification)
  assertEquals(1, cancelledOpenSession.closeCalls)
  ```
- [ ] Run `./gradlew :core:data:testDebugUnitTest --tests '*SourceRevisionPolicyTest' --tests '*BookSourcesRepositoryTest' :app:testDebugUnitTest --tests '*ReaderViewModelTest'`; expect FAIL.
- [ ] Stream a fingerprint on external open before using saved locators, then check it against known revision and available metadata. Keep the resolved descriptor/resource lease across verification and open rather than discarding it and resolving a raw path again. Recheck metadata after opening; handle changed/unavailable input rather than applying stale anchors. Surface Changed with an explicit action to open the new revision via replacement ReaderKey; show Reconnect on missing/revoked sources. Try a healthy owned/other verified source if available. Make duplicate decisions transactional, retaining all sources and annotation/progress-bearing rows. Metadata failure leaves filename fallback and never blocks a valid reader. Avoid source writes during all reconnect/hide operations.
- [ ] Repeat tests and CI, including absent size/mtime, changed during fingerprint, same-content relink, conflicting read copies, library hide and folder disconnect with reading data retained. Update English/Vietnamese reader messages.
- [ ] Commit: `feat(reader): preserve reading identity across source changes`.

### Task 7: Folder setup, results and library integration

**Files:**
- Create `app/src/main/kotlin/dev/kent/windbook/folders/FolderScreen.kt`, `FolderViewModel.kt`, `FolderUiState.kt`.
- Modify `app/src/main/kotlin/dev/kent/windbook/library/LibraryScreen.kt`, `LibraryViewModel.kt`, `navigation/Routes.kt`, `navigation/WindBookNavHost.kt`, `di/AppModule.kt`.
- Modify `app/src/main/res/values/strings.xml` and `app/src/main/res/values-vi/strings.xml`.
- Test `app/src/test/kotlin/dev/kent/windbook/folders/FolderViewModelTest.kt`, `FolderScreenTest.kt`; update library/navigation tests.

**Interfaces:**
- `FolderKey(folderId: String?) : NavKey`; null means setup/saved-folder list.
- `FolderViewModel.onLocationEdited(String)`, `onTreeGranted(uri: Uri, flags: Int)`, `setOptions(ScanOptions)`, `scan()`, `stop()`, `setQuery(String)`, `disconnect()`.
- `FolderUiState(locationHint, resolvedFolderId, options, progress, entries, query, message)`; use Task 4 state, not a second scan job in Compose.
- `FolderScreen(state: FolderUiState, actions: FolderActions)`; actions expose the above commands plus picker/open-book/restore callbacks.

- [ ] Write UI tests for default EPUB/PDF and recursion, disabled Scan without grant/extension, typed-path invalidation, All/Clear, state after rotation, Stop and incomplete result text. Open a result by stable book ID:
  ```kotlin
  compose.onNodeWithText("Quét thư mục").assertIsNotEnabled()
  grantSyntheticFolder()
  compose.onNodeWithText("Quét cả thư mục con").assertIsOn()
  compose.onNodeWithText("Tất cả").performClick()
  assertEquals(8, viewModel.state.value.options.extensions.size)
  clickResult(bookId)
  assertEquals(ReaderKey(bookId), navigator.lastKey)
  ```
  Test bodies use the existing locale setup/test navigator pattern; helpers dispatch real UI actions.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests '*FolderViewModelTest' --tests '*FolderScreenTest'`; expect FAIL.
- [ ] Implement the approved folder-library-v1 mockup in Compose. Launch ACTION_OPEN_DOCUMENT_TREE through StartActivityForResult to retain returned flags. Reuse saved grants by canonical identity, never by guessed raw-path conversion. Include actual selected breadcrumb, per-folder filters, saved folders, aggregate counters, filename/title search, virtualized results and explicit Reconnect/Restore. Disabled adapters show a clear format-unavailable state in intermediate builds. Preserve existing individual-file import. All mutations affect app records only; disconnect shows affected count and confirms locally.
- [ ] Repeat UI tests and CI, then emulator-check narrow portrait, landscape, font scaling, keyboard/TalkBack focus and Vietnamese labels. Check Scan/Stop responsiveness with 2,500 synthetic documents; no snackbar-per-file flood.
- [ ] Commit: `feat(app): browse and scan saved ebook folders`.

### Task 8: End-to-end source validation and handoff

**Files:**
- Create `docs/testing/folder-library-checklist.md`.
- Update the test classes from Tasks 2–7 for regressions, `README.md`, roadmap status and AGENTS.md.

**Interfaces:** consumes the public folder/source/reader UI; produces recorded emulator evidence and a usable EPUB/PDF folder increment.

- [ ] Add any missing end-to-end regression first: scan→open→position→restart→reconnect exact content, imported-book regression, hidden book staying hidden, and interrupted scans preserving unseen files. Demonstrate failure before a required code fix.
- [ ] Run CI, `./gradlew :reader:connectedDebugAndroidTest :app:assembleDebug`; all pass with no added lint warnings.
- [ ] On API 36 and API 31 check first/second scan of 2,500+ synthetic entries, revoked grant, detached source, Stop mid-cursor, full-disk preparation and exit during open. Record elapsed times, source-open count during scan (zero), descriptor count returning to baseline, temp-disk cleanup and `adb shell dumpsys meminfo dev.kent.windbook` TOTAL PSS. A >1 s UI freeze, uncancelled job or retained session resources is a release blocker, not a documentation-only limitation.
- [ ] Obtain the independent whole-branch review required by AGENTS.md; fix Important/Critical issues with failing regressions, rerun affected checks and CI. Mark this increment complete while leaving the six new readers explicitly outstanding.
- [ ] Commit `docs: verify direct folder reading`, then hand off APK evidence and the additional-reader plan. Do not merge or push.

## Coverage and execution handoff

Folder choice/grants → 3/7; recursive filters → 1/3; persisted scan/results → 2/4/7;
direct reading/cache → 5; identity/revision/reconnect/hide → 2/6; migration and
existing imports → 2/8; performance/accessibility/offline → 7/8. Full-format
reading → the companion plan, not an omitted requirement.

Choose execution style with the owner after plan review: subagent-driven for
per-task independent checks, or native execution followed by whole-branch review.
This plan recommends subagent-driven because migration, revision identity and
descriptor ownership cross module boundaries and can lose user data if mistaken.
