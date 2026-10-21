# Folder library and additional ebook formats

Date: 2026-10-21
Status: owner approved on 2026-10-21. Implementation plans and new dependencies
require separate approval; no feature code has been implemented.

Plans: `../plans/2026-10-21-folder-library.md` and
`../plans/2026-10-21-ebook-format-readers.md`.
Planning evidence: `../../research/2026-10-21-folder-reader-feasibility.md`.

Independent builder review: no important unanswered planning questions. Engine
feasibility, concrete cache limits and dependency proposals belong to planning.

Interactive mockup (ignored, synthetic files only):
`.superpowers/brainstorm/27897-1792599917/content/folder-library-v1.html`.
It simulates directory access, filters, recursive scanning, Stop and opening a
result; it does not access real folders or implement any reader engine.

## Intent and confirmed requirements

The owner wants to provide a folder location, scan its ebooks with selectable
extensions, and read the results in WindBook. The owner confirmed recursive
scanning of subfolders, both discovery and reading of additional formats, and
reading directly from the source folder to avoid duplicating the library.

The target set discussed with the owner is EPUB, PDF, MOBI, AZW3, FB2, CBZ,
CBR and TXT. Discovery alone does not complete support for a format. The scope
is larger than the earlier EPUB/PDF batch-import proposal: it changes source
storage and adds reader adapters. Existing privately imported books keep working.

The defaults below are proposals, not additional claims of owner approval.
This work and selection notes have separate designs; neither determines the
other's execution order or authorizes merging the Reflow-removal branch.

## Folder selection and access

Add a Folders entry from Library's Add books menu, alongside the existing
individual-file import. The setup screen shows a folder-location field,
Choose folder action, extension filters and Include subfolders enabled by
default. Remember selected folders and their individual filter settings.

A typed path is a location hint, not an access grant. For a new location,
the system directory picker grants access once. Persist the granted read
permission and the returned tree URI; show a human-readable location breadcrumb.
Do not manufacture a content URI from an arbitrary filesystem string. Reuse
an already granted location when identified unambiguously; otherwise open the
picker and let the owner select it. Show the actual selected location before
scanning. Editing the location invalidates a previous selection until resolved.

Use Android's Storage Access Framework. No all-files permission, root access,
new network permission or direct-path bypass is proposed. Some locations are
restricted by Android, including the storage root, the Download directory
itself, and Android/data and Android/obb. Offer another folder when access
is refused. Never promise that every typed path is accessible.

Initially propose manual Scan and Scan again, with saved folders available
after restart. Periodic/background watching is separate from this increment.
Removing a saved folder disconnects it; it never deletes source files. Show
affected books and retain their reading data for reconnection.

## Filters and scan interaction

Offer one multi-select checkbox for each extension: .epub, .pdf, .mobi,
.azw3, .fb2, .cbz, .cbr and .txt. Default to EPUB and PDF, with All and Clear
shortcuts. Require at least one selected extension. Match case-insensitively
against the final filename extension, so BOOK.EPUB matches and book.pdf.bak
does not. General ZIP files and unlisted extensions are not silently included.
Additional extensions can be added explicitly later; this is not a claim of
support for every ebook format, protected Kindle variant or arbitrary archive.

Scan the granted folder and, by default, its descendants. Discover files
without copying them or extracting every book's content. Show files examined,
matches found, current relative folder and a Stop action. Use indeterminate
progress until a total is known. Results update incrementally in a virtualized
list showing filename, relative path, format and availability. An extension
match is a candidate, not proof of a valid readable book.

Completed scans save discovered entries to Library, linked to the folder. The
owner can open them there or in the folder's results, search filenames/titles
and filter by format. The first display can use filenames; enrich metadata
and covers lazily with bounded work. For comics without metadata use the
filename as title and first readable page as a bounded thumbnail.

Summarize found, newly added, already known, unreadable and inaccessible
subfolder counts. An individual failure does not stop other folders. Stopping
keeps already indexed entries and labels the run incomplete. Changing filters
starts a new scan; stale results cannot enter the new run. Rotation preserves
the active scan. Process death leaves an interrupted state with a manual
rescan action, not a claim that the job finished.

Changing filters never deletes existing library entries. Only a complete,
successful enumeration of a relevant subtree may mark formerly indexed files
there missing. An interrupted scan, permission error or excluded extension
must not mark unseen entries deleted or missing. Do not follow links outside
the granted tree; track visited document IDs to avoid provider cycles.

## Reading directly and retaining identity

Persist source references separately from books: an imported local file or an
external document URI with its folder reference, document identity, display
name, size and last-modified metadata when available. Never put content://
strings into the existing filePath field and then pass them to File().
Migrate existing books to owned-local sources without changing book IDs,
progress, display preferences or annotations.

Opening an external book resolves read access to its current URI. PDF stays
on framework PdfRenderer with a seekable descriptor, on its dedicated worker.
EPUB uses a verified Readium asset/resource bridge. Close descriptors/resources
on success, cancellation, failure and reader exit. Source files are never
modified, renamed, annotated in place or deleted by WindBook.

Direct reading means no permanent duplicate of every source book. Some engines
or providers may require a seekable temporary source, decompressed pages or
normalized publication assets. Proposed default: create these only for the
book being opened, show preparation status, allow cancellation, bound their
size and remove session-only material after close or interrupted-session
cleanup. The plan must document actual limits and validate disk requirements.
Do not silently convert or cache the whole library. A provider that cannot
supply an offline readable stream reports unavailable; no cloud download
integration is promised.

Use canonical provider/document identity to avoid duplicate rows on rescans
and when authorized roots overlap. The same filename in different folders is
not the same book. Keep distinct source locations for content duplicates;
compute content hashes when reading/verifying, rather than reading the full
library just to enumerate it. On a verified hash match with an existing book,
attach the new source to that book, preserving its ID and reading data. Do not
silently combine conflicting progress/annotations if two existing books have
already been read; retain them until a deliberate reconciliation is designed.

Changed content at the same URI is a new revision. When available, size and
mtime are hints; verify a content fingerprint before reusing content-dependent
positions or annotations. Preserve the old revision's data and explain that
the source changed; never place old notes on unrelated replacement content.
A moved/deleted source or revoked permission leaves a readable library record
with Reconnect/Locate file. Reconnecting the same verified content preserves
its ID and progress. A different file cannot silently inherit old anchors.
Deleting a library entry remains a soft delete; rescans do not automatically
revive hidden books. Explicit Restore is required for a folder source.

## Reader coverage and feasibility work

All eight target formats must support opening, page/section navigation,
remembering position and reopening offline before this feature is called
complete. Format-specific discovery and reader capabilities are separate.
During staged development, indicate unavailable adapters honestly; do not
present a format as readable just because its extension can be scanned.
The separate notes design currently covers EPUB/PDF; notes on the additional
formats require their own anchor mapping and are not promised by this spec.

| Format | Proposed approach | Evidence or check required before implementation |
|---|---|---|
| EPUB | Existing Readium navigator, external source bridge | Verify pinned 3.4.0 URI/random access and resource lifetime without permanent import |
| PDF | Existing PdfRenderer engine and locator shape | External seekable descriptors; optional bounded session staging for non-seekable sources |
| TXT | Decode and expose a paginated/scrollable text publication | UTF-8 and UTF-16 BOM handling, explicit encoding fallback, Vietnamese text and stable offsets |
| FB2 | Stream-parse XML into a normalized text publication | Chapter order, embedded images, links, encoding; bounded parsing without external entities |
| CBZ | ZIP image sequence with an image navigator | Stable natural filename ordering, image decoding limits, zoom and restored page position |
| CBR | RAR image sequence with the same image navigator | Evaluate both RAR4 and RAR5; parser choice is not approved yet |
| MOBI / AZW3 | Offline parser to normalized publication resources | Test legacy MOBI and KF8, links/images, stable positions, native integration and memory costs |

Readium 3.4.0 documents partial CBZ support and no planned CBR support. Its
format table does not establish MOBI, AZW3, FB2 or TXT support. The existing
WindBook BookFormat enum and ReaderEngine currently accept only EPUB/PDF.
Adding extensions to a picker is insufficient.

libmobi is a candidate for MOBI/KF8 investigation, not a selected dependency.
A RAR implementation must demonstrate the promised variants; a RAR4-only
library is insufficient for unqualified CBR support. The plan must present
candidate/version, integration effort, ABI/APK footprint and applicable
distribution requirements before asking for any new dependency approval.
Do not install, vendor or add a Gradle/native library through this design.

Proposed coverage excludes encrypted/DRM-protected content, KFX, password
archives, multipart archives and formats outside the listed set. Mark such
files with a specific reason. No DRM removal, online conversion or external
reader handoff substitutes for supported in-app reading.

## Architecture and proposed delivery sequence

Keep the existing four modules. Pure extension matching, source identity,
scan reconciliation and format capabilities belong in core:model. Folder
enumeration, persisted grants, source records and scan state belong in
core:data. Source-based opening and format adapters belong in reader.
Folder setup, results/progress and reconnection UI belong in app.

Schema versioning must account for the separately proposed annotation schema:
allocate the next version against the actual branch state and test both upgrade
paths. Do not assume an independent v2-to-v3 migration can replace notes v3.
Keep the existing owned-file importer working and avoid changing EPUB/PDF
saved-locator semantics while adding external sources.

Proposed implementation slices, for owner review rather than a new roadmap:

1. Feasibility checks and concrete dependency decisions for external-source
   opening and the six additional formats. Resolve blockers before promising
   the full implementation plan.
2. Folder access, recursive scan, extension filtering, source persistence and
   EPUB/PDF direct reading, including reconnection and migration.
3. TXT/FB2 and CBZ readers; CBR and MOBI/AZW3 adapters using approved choices.
4. Whole-flow validation across the eight formats, including large libraries,
   storage use and device performance, then independent branch review.

Intermediate slices are progress toward the complete request, not a redefinition
of completion. New dependencies and the implementation plan need the owner's
approval under AGENTS.md; selecting this feature does not itself approve them.

## Acceptance and verification

- Synthetic trees with root and nested ebooks, uppercase/mixed-case extensions,
  same names at different paths, unsupported suffixes and empty directories.
- Enable/disable recursion and each extension; All/Clear; persisted selections.
- Scan twice and overlap saved roots without adding duplicate source records.
- Read from every target format in-app, navigate, restart and resume correctly.
  Check Vietnamese text for text formats and numeric page ordering for comics.
- Revoke access, rename/move/delete/replace a source, interrupt scanning, reject
  a subfolder and reconnect. Preserve existing reading data without false
  missing-file reports or resurrection of soft-deleted books.
- Confirm no source writes and no permanent full-library copies. Measure first
  scan and subsequent scan on a representative synthetic tree of 2,500+ files;
  verify responsive Stop and bounded heap/cache on the Neo 3 GT emulator.
- Validate sequential bounded work and cleanup for huge images, large archives,
  malformed XML, oversized files, unavailable space and provider failures.
- Run migration/unit/integration tests, repository CI, device tests and debug
  build. Preserve the PDF engine, locator compatibility and baseline lint count.
- New app strings in English and Vietnamese; keyboard/TalkBack access to folder,
  filter, scan, stop and reconnect controls. Keep private titles/screenshots out
  of git and record real-device findings in the manual checklist.

## Sources

- [Android document and directory access](https://developer.android.com/training/data-storage/shared/documents-files): tree grants, persisted access and restricted locations.
- [Readium Kotlin 3.4.0 format matrix](https://github.com/readium/kotlin-toolkit/blob/3.4.0/README.md): existing toolkit coverage and partial CBZ support.
- [libmobi](https://github.com/bfabiszewski/libmobi): candidate MOBI/KF8 parser, not an approved dependency.
- Repository: LibraryScreen.kt, LibraryViewModel.kt, ImportSourceFactory.kt,
  BookImporter.kt, Book.kt, ReaderEngine.kt and ReaderViewModel.kt.
