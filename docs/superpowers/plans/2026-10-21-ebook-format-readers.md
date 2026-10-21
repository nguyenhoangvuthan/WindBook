# Additional Ebook Readers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete in-app offline reading for TXT, FB2, CBZ, CBR, MOBI and AZW3 alongside EPUB/PDF in the approved folder library.

**Architecture:** Share source leases and revision identity with the folder plan. Normalize text formats to deterministic session-only EPUB publications and expose comics through a bounded image-page session. Preserve original formats and source files; derived assets are disposable.

**Tech Stack:** Existing Kotlin/Readium/Compose, JDK ZIP/XML, Android image decoding; proposed Junrar 8.1.2 and native libmobi 0.12 only after explicit approval.

**Spec:** `docs/superpowers/specs/2026-10-21-folder-library-design.md` (owner approved 2026-10-21).

**Status:** Proposed implementation plan. New dependencies, native build tooling and execution method are not approved.

## Global Constraints

- Execute after the source contracts and migrations in `2026-10-21-folder-library.md`; keep the four-module direction and API 31 minimum.
- Target all eight formats in the approved spec. Intermediate unavailable adapters are not final completion.
- No new Gradle dependency without explicit approval. Do not vendor libmobi or install an NDK based only on design approval.
- No network permission, source modifications, permanent library conversion, PDF Reflow, Pdfium rendering or external-reader handoff.
- Preserve existing EPUB/PDF locators. Original source format stays in Book and ReaderSession even when rendered through a derived EPUB.
- Use the bounded preparation policy in `docs/research/2026-10-21-folder-reader-feasibility.md`; all readers release their SessionLease.
- English/Vietnamese app strings, no private fixture titles/screenshots, no attribution or unapproved push/merge.
- TDD task commits, full repository CI before every code commit, AGENTS.md §8/9 in the same commit; ledger `.superpowers/sdd/ebook-format-readers/progress.md`.

## Review Focus

- Rebuilding normalized content must not move locators after cache deletion or rotation (Tasks 1–3, 6).
- Archive ordering and solid-RAR random page access must stay deterministic (Tasks 4–5).
- Malformed text/XML, image dimensions and false archive sizes must not cause unbounded allocation (Tasks 2–6).
- Native failure or cancellation must not kill the main reader process or leak scratch files (Task 6).
- An extension match must not enable a broken, encrypted or unsupported-format book (Tasks 1, 5–7).

---

## Dependency approval and execution gates

Read the feasibility note for inspected pins and actual evidence. This proposal is:

1. `com.github.junrar:junrar:8.1.2`, including runtime `org.slf4j:slf4j-api:2.0.17`, for CBR. No logging binding, Bouncy Castle or Jackson dependency.
2. libmobi v0.12 at `85dcfe803fc2a21020ddcf15c3eb66b93d388add`, as an explicitly approved native dependency for MOBI/AZW3, with upstream notices/COPYING retained.
3. Android NDK `29.0.14206865` for all four existing APK ABIs; CMake 4.1.2 is already present. NDK is currently absent. Installing it into the owner's SDK outside the repo needs approval under AGENTS.md §2.9. This plan does not trigger installation automatically.

The Java and native integrations must each pass their device gates before
enabling the associated capability. Record resolved dependency graph, exact
source hashes, licenses/notices, stripped APK size delta and PSS. Those sizes
are not known from source inspection. If a proposed library fails compatibility,
bounds or fidelity tests, keep that adapter disabled and prepare a concrete
replacement proposal; never silently add a replacement dependency.

TXT/FB2/CBZ tasks need no added library and can proceed once their implementation
plan is approved. The CBR and MOBI/AZW3 tasks additionally require the respective
dependency/tool approvals. Branch `ebook-format-readers` descends from the tested
folder-library branch; this is not authorization to merge either into main.

**CI** and the red/green convention are defined in the folder plan. Each test
snippet is an assertion body in the specified test class. Fixtures must be
synthetic/publicly redistributable with provenance, never owner library books.

### Task 1: Adapter dispatch and deterministic normalized publications

**Files:**
- Create `reader/src/main/kotlin/dev/kent/windbook/reader/formats/FormatAdapter.kt`, `NormalizedPublicationWriter.kt`, `FormatCapabilities.kt`.
- Modify `reader/src/main/kotlin/dev/kent/windbook/reader/ReaderApi.kt`, `ReaderEngine.kt`, `ReaderHostFragment.kt`.
- Modify `app/src/main/kotlin/dev/kent/windbook/reader/ReaderScreen.kt`.
- Test `reader/src/test/kotlin/dev/kent/windbook/reader/formats/NormalizedPublicationWriterTest.kt`, `FormatDispatchTest.kt`.

**Interfaces:**
- `FormatAdapter.format: BookFormat`; `suspend open(source: BookSource, lease: SessionLease): Result<ReaderSession>`; returned session owns the lease, failed open releases it.
- `FormatCapabilities.canRead(format: BookFormat): Boolean` is true only for registered validated adapters, not all enum entries.
- `NormalizedPublicationWriter.write(document: NormalizedDocument, directory: File): File` returns an EPUB file. `NormalizedDocument(id: String, title: String, sections: List<NormalizedSection>, resources: List<NormalizedResource>)`; sections reference staged XHTML files, resources reference bounded staged image/style files (never full book byte arrays).
- `NormalizedSection(href: String, title: String?, xhtmlFile: File)`; `NormalizedResource(href: String, mediaType: String, file: File)`.
- `NavigationSurface` enum `PUBLICATION`, `PDF`, `IMAGES`; add `ReaderSession.surface` and use it for dispatch. ReadiumReaderSession may have a non-EPUB original format but always has PUBLICATION surface.

- [ ] Write a two-chapter normalized fixture twice in different temporary directories. Assert:
  ```kotlin
  assertEquals(first.spineHrefs, second.spineHrefs)
  assertEquals(listOf("text/0001.xhtml", "text/0002.xhtml"), first.spineHrefs)
  assertEquals(first.elementIds, second.elementIds)
  assertFalse(capabilities.canRead(BookFormat.CBR))
  assertEquals(BookFormat.TXT, normalizedSession.format)
  assertEquals(NavigationSurface.PUBLICATION, normalizedSession.surface)
  ```
  Add escaping/local-resource resolution and failed-open-closes-lease tests.
- [ ] Run `./gradlew :reader:testDebugUnitTest --tests '*NormalizedPublicationWriterTest' --tests '*FormatDispatchTest'`; expect FAIL.
- [ ] Implement deterministic EPUB3 packaging: uncompressed first mimetype entry, OPF/nav/spine, stable section/paragraph IDs and relative resource references independent of temp paths/timestamps. Disable script/external resources in generated publications. ReaderHostFragment arguments identify the EPUB navigation surface rather than falsely requiring original format EPUB; still reject PDF/image sessions. Enable adapters individually, keeping PDF entirely outside Readium.
- [ ] Repeat targeted tests and CI; verify imported EPUB/PDF dispatch remains unchanged.
- [ ] Commit: `feat(reader): dispatch format adapters with stable derived publications`.

### Task 2: TXT decoding and reading

**Files:**
- Create `reader/src/main/kotlin/dev/kent/windbook/reader/formats/TxtAdapter.kt`, `TextEncoding.kt`.
- Modify `core/data/src/main/kotlin/dev/kent/windbook/core/data/BookSourcesRepository.kt` to persist a per-source TXT encoding override; modify the source schema during this unshipped feature branch with an explicit new migration if its previous schema has shipped.
- Modify `app/src/main/kotlin/dev/kent/windbook/reader/ReaderScreen.kt`, both strings.xml files.
- Test `reader/src/test/kotlin/dev/kent/windbook/reader/formats/TxtAdapterTest.kt` and `reader/src/androidTest/kotlin/dev/kent/windbook/reader/TextFormatsDeviceTest.kt`.

**Interfaces:**
- `TextEncoding` values `UTF8`, `UTF16_LE`, `UTF16_BE`, `WINDOWS_1258`.
- `decodeText(input: InputStream, override: TextEncoding?, output: Writer)` streams with malformed-input reporting.
- TxtAdapter produces Task 1 NormalizedDocument, then opens it through Readium with original format TXT.

- [ ] Test UTF-8 Vietnamese, both UTF-16 BOMs, CRLF/LF equivalence, HTML-looking plain text and invalid UTF-8. Assert:
  ```kotlin
  assertEquals("Tiếng Việt\nDòng hai", decoded)
  assertTrue(generatedXhtml.contains("&lt;script&gt;"))
  assertEquals("text/0001.xhtml", firstHrefAfterRebuild)
  assertEquals(TextEncoding.UTF16_LE, detectedBomEncoding)
  assertEquals("ENCODING_REQUIRED", invalidUtf8.errorCode)
  ```
- [ ] Run `./gradlew :reader:testDebugUnitTest --tests '*TxtAdapterTest'`; expect FAIL.
- [ ] Decode BOM first; otherwise strict UTF-8 unless owner selects an encoding override. Present the four explicit encodings on failure; do not silently replace Vietnamese bytes. Normalize newlines, stream escaped paragraphs, split at <=64 Ki UTF-16 code units without splitting a surrogate, retain stable element IDs and enforce 64 MiB normalized text cap. Font/theme use the existing publication surface. Encoding changes are a derived-content revision; preserve old progress under its prior encoding rather than applying incompatible offsets.
- [ ] Run targeted tests, TextFormatsDeviceTest on API 31/36 and CI. Navigate, kill/reopen and delete temporary assets before reopening; exact passage must restore from deterministic output.
- [ ] Commit: `feat(reader): read linked text books with explicit encoding`.

### Task 3: FB2 structure, images and references

**Files:**
- Create `reader/src/main/kotlin/dev/kent/windbook/reader/formats/Fb2Adapter.kt`, `Fb2Parser.kt`.
- Test `reader/src/test/kotlin/dev/kent/windbook/reader/formats/Fb2ParserTest.kt`; extend `TextFormatsDeviceTest.kt`.
- Create synthetic fixture helpers in `core/model/src/testFixtures/kotlin/dev/kent/windbook/testing/TextBookFixtures.kt`.

**Interfaces:**
- `Fb2Parser.parse(input: InputStream, directory: File): NormalizedDocument` streams XML into the writer's staged inputs.
- Preserve section order, paragraph semantics, IDs, description/title-info metadata, binary images and local footnote links. Only `.fb2` is selected; `.fb2.zip` is not implicitly added.

- [ ] Test nested sections, Vietnamese XML encoding, binary image, link to a later footnote, and forbidden DOCTYPE/external entity:
  ```kotlin
  assertEquals(listOf("Chapter one", "Chapter two"), document.sections.map { it.title })
  assertEquals(1, document.resources.size)
  assertEquals("text/0002.xhtml#note-1", resolvedFootnote)
  assertEquals(0, externalResourceRequests)
  assertEquals("UNSAFE_XML", doctypeResult.errorCode)
  ```
- [ ] Run `./gradlew :reader:testDebugUnitTest --tests '*Fb2ParserTest'`; expect FAIL.
- [ ] Use Android's pull parser with external entity/DOCTYPE rejection, XML-declared encoding and bounded streaming base64 decoding. Map source IDs deterministically to XHTML; retain local links, notes-body sections and supported images. Reject broken XML with a readable error; do not show a successful partial book. Apply Task 1/2 text/resource quotas and bounded cover generation.
- [ ] Repeat targeted tests, TextFormatsDeviceTest and CI; verify images, footnote return, typography changes and restored passage after cache recreation.
- [ ] Commit: `feat(reader): read FictionBook chapters and embedded resources`.

### Task 4: CBZ and shared comic navigation

**Files:**
- Create `reader/src/main/kotlin/dev/kent/windbook/reader/comic/ComicArchive.kt`, `CbzArchive.kt`, `ComicSession.kt`, `ComicPages.kt`.
- Create `core/model/src/main/kotlin/dev/kent/windbook/core/model/ComicOrder.kt` and `core/model/src/test/kotlin/dev/kent/windbook/core/model/ComicOrderTest.kt`.
- Create `app/src/main/kotlin/dev/kent/windbook/reader/comic/ComicReaderView.kt`; modify ReaderScreen.kt and ReaderViewModel.kt in the reader UI package.
- Test `reader/src/test/kotlin/dev/kent/windbook/reader/comic/CbzArchiveTest.kt`, `ComicLocatorTest.kt`; create `reader/src/androidTest/kotlin/dev/kent/windbook/reader/comic/ComicPagesDeviceTest.kt`, `app/src/test/kotlin/dev/kent/windbook/reader/comic/ComicReaderViewTest.kt`.

**Interfaces:**
- `ComicEntry(id: String, name: String, expandedSize: Long?)`; `ComicArchive.entries: List<ComicEntry>`, `open(entryId: String): InputStream`, `close()`.
- `ComicSession : ReaderSession` with `pageCount`, `pages: ComicPages`, `locatorJsonFor(page: Int): String`, `pageOf(locatorJson: String?): Int`.
- `ComicPages.render(page: Int, widthPx: Int): Bitmap` and `close()`, called on one worker; IMAGE surface, no PDF locator masquerading.
- Locator JSON version 1 stores entry href and zero-based page index; restore by entry ID first. `naturalComicOrder(names: List<String>): List<String>` sorts numeric runs without integer overflow, then a deterministic codepoint tie-break.

- [ ] Assert `page1.jpg`, `page2.jpg`, `page10.jpg` ordering regardless of ZIP insertion order; ignore directories and __MACOSX. Reject duplicate normalized entry paths, traversal, excessive entries/expanded bytes and no-image archives. Assert:
  ```kotlin
  assertEquals(listOf("page1.jpg", "page2.jpg", "page10.jpg"), ordered)
  assertEquals(1, reopened.pageOf(savedSecondPage))
  assertTrue(cacheBytes <= 48L * 1024 * 1024)
  assertTrue(decodedBitmapBytes <= 16L * 1024 * 1024)
  ```
- [ ] Run `./gradlew :core:model:test --tests '*ComicOrderTest' :reader:testDebugUnitTest --tests '*CbzArchiveTest' --tests '*ComicLocatorTest' :app:testDebugUnitTest --tests '*ComicReaderViewTest'`; expect FAIL.
- [ ] Use JDK ZipFile on a leased seekable source, inspect entry names/sizes before access, stream pages without extracting the archive. Support JPEG/PNG/WebP (case-insensitive); report corrupt/unsupported image entries rather than silently changing saved page numbering. Decode bounds before sampling. Implement paged forward/back navigation, center overlay, pinch zoom and existing volume/page-key support, keeping PDF-specific crop controls hidden. Persist page progress through existing library APIs. Layout/rotation must retain page ID; no simultaneous whole-archive bitmap loading.
- [ ] Repeat targeted tests, ComicPagesDeviceTest and CI. Verify 100+ image pages, rapid navigation, giant image sampling, last page and process restart on emulator.
- [ ] Commit: `feat(reader): read comic ZIP pages with bounded image caching`.

### Task 5: CBR adapter and Java dependency gate

**Prerequisite:** explicit approval for Junrar 8.1.2 and its slf4j-api runtime dependency.

**Files:**
- Modify `gradle/libs.versions.toml`, `reader/build.gradle.kts`.
- Create `reader/src/main/kotlin/dev/kent/windbook/reader/comic/CbrArchive.kt`.
- Test `reader/src/test/kotlin/dev/kent/windbook/reader/comic/CbrArchiveTest.kt`, `reader/src/androidTest/kotlin/dev/kent/windbook/reader/comic/CbrDeviceTest.kt`.
- Add only licensed synthetic archives under `reader/src/androidTest/assets/formats/` with `PROVENANCE.md`.

**Interfaces:** CbrArchive implements Task 4 ComicArchive and uses the same ComicSession; same IDs, ordering, quotas and navigation.

- [ ] Before enabling the adapter, write tests for unencrypted RAR4 and RAR5 image archives, solid archive page 1→last→1, truncated streams, encrypted/multipart rejection and false declared output size:
  ```kotlin
  assertEquals(expectedImageHashes, extractedHashes)
  assertEquals(firstPageHash, rereadFirstPageHash)
  assertEquals("PROTECTED_ARCHIVE", encrypted.errorCode)
  assertEquals("MULTIPART_ARCHIVE", multipart.errorCode)
  assertTrue(observedOutputBytes <= outputQuota)
  ```
- [ ] Add only the approved pinned dependencies to compile the tests; run `./gradlew :reader:testDebugUnitTest --tests '*CbrArchiveTest'`; expect the adapter behavior to FAIL before implementation.
- [ ] Use one Junrar Archive per worker. Extract selected regular-file entries to a quota-counting stream in SessionWorkspace; no general extraction helper, links, directories or source writes. For solid archives, replay safely and cache bounded extracted pages; propagate extraction/close errors. Reject encryption/multipart before reading content. If archive enumeration is partial or malformed, fail explicitly. Register CBR only after device tests pass.
- [ ] Run CbrDeviceTest on API 31/36, CI and `./gradlew :app:assembleDebug`. Record dependency tree using `./gradlew :reader:dependencies --configuration debugRuntimeClasspath`, APK delta and solid-archive memory/time. Failure blocks CBR; it does not justify silently shipping RAR4-only coverage.
- [ ] Commit: `feat(reader): read RAR4 and RAR5 comic archives`.

### Task 6: MOBI/AZW3 native adapter with process containment

**Prerequisite:** explicit approval for libmobi v0.12 and installing NDK 29.0.14206865 in the Android SDK. This task cannot start from design approval alone.

**Files:**
- Create `reader/src/main/cpp/CMakeLists.txt`, `mobi_bridge.c`, `mobi_budget.c`, `mobi_budget.h`; approved vendored source and notices in `reader/src/main/cpp/vendor/libmobi/`.
- Create `reader/src/main/kotlin/dev/kent/windbook/reader/formats/MobiAdapter.kt`, `MobiParserClient.kt`, `MobiParserService.kt`.
- Modify `reader/src/main/AndroidManifest.xml`, `reader/build.gradle.kts`, `app/proguard-rules.pro` if required by the verified JNI surface.
- Test `reader/src/androidTest/kotlin/dev/kent/windbook/reader/formats/MobiDeviceTest.kt`; include synthetic MOBI7/KF8 fixtures with provenance and expected text/resource hashes under `reader/src/androidTest/assets/formats/`.

**Interfaces:**
- `MobiParserClient.parse(input: File, outputDirectory: File): NormalizedDocument` is suspend/cancellable; both paths must be in the current validated SessionLease.
- Non-exported bound service runs in `:ebook-parser`. Messenger requests contain request ID and leased paths; replies contain typed success/error plus manifest path. Never transfer book payloads over Binder.
- JNI `nativeParse(inputPath: String, outputPath: String, allocationLimitBytes: Long): Int`; native return codes map to corrupt/protected/unsupported/size-limit failures. One active request; 128 MiB counted parser allocation ceiling and the common output quota.

- [ ] Write tests for legacy MOBI and KF8/AZW3 paragraphs, Vietnamese diacritics, spine order, cover and internal links. Rebuild derived assets and compare href/element IDs. Add corrupt headers, protected files, allocation exhaustion, worker crash and cancel mid-parse:
  ```kotlin
  assertEquals(expectedText, renderedText)
  assertEquals(expectedResourceHashes, parsedResourceHashes)
  assertEquals(first.spineHrefs, rebuilt.spineHrefs)
  assertTrue(mainProcessSurvivedWorkerFailure)
  assertEquals(0, scratchBytesAfterCancel)
  assertTrue(nativePeakAllocations <= 128L * 1024 * 1024)
  ```
- [ ] After explicit approvals, pin/install the NDK, add only approved libmobi sources with hash verification and notices, and compile tests. Run `./gradlew :reader:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.kent.windbook.reader.formats.MobiDeviceTest`; expect missing adapter behavior to FAIL, not an unrelated toolchain issue.
- [ ] Cross-build libmobi+bundled miniz/internal writer through CMake for armeabi-v7a, arm64-v8a, x86 and x86_64. Count native allocations via compile-unit allocator wrappers; exercise failure returns, not unchecked malloc sizes. Parse through the pinned API and stream bounded reconstructed resources to the writer. Reject protected/unsupported content explicitly. Preserve deterministic original section IDs/links in derived EPUB. Keep the parser out of the main process; cancellation/timeout terminates only this app's parser process and its lease, while a new request starts a fresh worker. Default timeout 120 s with visible preparing/cancel UI; make timeout an explicit failure.
- [ ] Run MobiDeviceTest on API 31/36, CI and debug/release compilation without publishing. Verify four ABI payloads, Android 16 KB page-size compatibility, no unresolved JNI symbol, resource limits, cleanup, all note/progress-independent source IDs and no INTERNET permission. Record native APK/PSS delta and distribution notice/source packaging. If bounds or fidelity fail, leave both capabilities disabled and resolve the failing gate before claiming completion.
- [ ] Commit: `feat(reader): read MOBI and AZW3 through bounded native preparation`.

### Task 7: All-format integration and owner verification build

**Files:**
- Extend `app/src/test/kotlin/dev/kent/windbook/folders/FolderViewModelTest.kt`, reader tests above and both strings.xml files.
- Update `docs/testing/folder-library-checklist.md`, `README.md`, roadmap and AGENTS.md.

**Interfaces:** consumes all adapter capabilities and folder/source APIs; produces the eight-format acceptance record.

- [ ] Add an end-to-end matrix that scans mixed nested fixtures, filters one extension at a time, opens each format, navigates, restarts and restores. Pin any discovered defect with a failing regression. Assert all eight readable capabilities only after their adapter gates pass; wrong magic, encrypted or corrupt files show errors without removing library records.
- [ ] Run CI and `./gradlew :reader:connectedDebugAndroidTest :app:assembleDebug`. All tests pass; no additional lint warnings.
- [ ] On emulator verify Vietnamese text, images/links, comic page order/zoom, cache deletion/rebuild, source replacement, low disk and parser cancellation. Repeat the 2,500-file folder test with all eight suffixes. Compare source checksums before/after reading and verify no permanent imported copies; capture screenshots outside git and collect TOTAL PSS.
- [ ] Complete an independent whole-branch review; Critical/Important fixes start with failing tests. Document concrete unsupported protected/multipart/size-limit cases and remaining device evidence honestly. Owner tests the final APK on Neo 3 GT; no claim of physical-device results before that occurs.
- [ ] Commit `docs: verify folder reading across eight ebook formats`; request the owner's merge decision separately. No push/tag action is included.

## Coverage and self-review

All format adapters have explicit tasks, source leases and identity come from
the folder plan, and each Review Focus item has a regression owner. TXT/FB2
share stable publication output; CBZ/CBR share page IDs and navigation; MOBI/AZW3
share a native decoder and output contract. No new-format notes feature is
implied. A dependency refusal or failed gate leaves the corresponding work
outstanding, rather than downgrading the approved eight-format goal.

Plan approval and execution style are separate from dependency approval. For
these tasks, subagent-driven execution is recommended because parser boundaries,
data preservation and platform lifetime behavior benefit from per-task review.
