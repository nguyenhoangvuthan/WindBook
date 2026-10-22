# Folder reader feasibility and dependency proposal

Original investigation: 2026-10-21, based on repository and pinned upstream
source/API inspection. That investigation made no application or dependency
changes. The 2026-10-22 source-session follow-up below records implementation
and synthetic-device evidence; native reader gates remain pending.

## Findings affecting the plan

- `BookOpener.open(File, BookFormat)` and `ReaderViewModel.open()` assume an
  owned local file. Replace this boundary with a source resolver and an owned
  reader lease. `content://` is not a filesystem path.
- `LibraryRepository.delete()` deletes local book files and `LibraryDao.softDelete()`
  deletes progress. Folder disconnect/hide must use separate operations that
  retain progress and never call filesystem deletion on an external source.
- `books.sha256` and `books.file_path` are currently nonnull, with a unique hash
  index. Scanning should not hash or copy every ebook; migration must permit
  unhashed external books and persist document sources separately.
- The cached Readium **3.4.0** AAR exposes `ContentResource(Uri, ContentResolver)`,
  `ContentResourceFactory`, `AssetRetriever.retrieve(Resource, FormatHints)`,
  `AssetRetriever.retrieve(AbsoluteUrl, FormatHints)` and `Uri.toAbsoluteUrl()`.
  It also includes `ImageNavigatorFragment`. This establishes usable API seams,
  not acceptable provider performance; URI EPUB opening must get a device test.
- PDF can retain the existing engine with descriptor ownership added at open.
  Its current synchronous `close()` and cancellation windows need coverage as
  the source lifetime changes. No Pdfium rendering or PDF Reflow is involved.

## Source-session follow-up (2026-10-22)

Task 5 implements bounded source leases without a permanent book copy. Readium
3.4.0's pinned `ContentResource.stream(position)` closes and reopens the resolver
URI when seeking backwards. Synthetic EPUB reading opened the provider twice on
both API 31 and API 36. Thus raw URI acquisition, fingerprinting and later Readium
reads cannot promise the same revision. `dup()` also shares a file offset, and a
provider-owned seekable file can still change after acquisition.

External sources therefore use one bounded snapshot under the active session
lease, acquired through one read-only provider descriptor. Both PDF and EPUB read
that snapshot; independent streams/descriptors have independent offsets. The
same acquired lease must survive Task 6 fingerprinting, revision verification,
metadata and reader opening. Imported owned sources remain direct.
`SourceAccess.initialize()` provides an idempotent IO orphan sweep for Task 7
app-startup wiring, including launches that never open a book; first acquisition
also initializes the workspace lazily. The original
500 MiB source, 1 GiB scratch, 64 MiB reserve and 64 KiB buffer defaults are
implemented; no fallback retries a corrupt or denied source.

The existing importer EPUB archive guard is shared through `core:model` and
runs before Readium on acquired EPUBs too: 10,000 entries, 2 GiB declared expanded
bytes, unsafe paths, container presence and corrupt-ZIP checks. This preserves
the previous import boundary without adding a module dependency. Metadata work
must consume the verified lease and keep this guard ahead of external EPUB parsing.

Synthetic API 31/36 tests cover content, PDF rendering metadata, blocked pipes,
cancellation, independent offsets, provider replacement, quotas, orphan cleanup,
main-thread lifecycle and archive validation. Both platforms pass 10 cases;
the synthetic external EPUB snapshot is 1,453 bytes and returns to zero tracked
cache bytes on close. Final measured EPUB open/read times:

| Emulator API | Session snapshot | Direct URI API seam | Provider opens (snapshot / URI) |
|---|---:|---:|---:|
| 31 | 46 ms | 17 ms | 1 / 2 |
| 36 | 57 ms | 19 ms | 1 / 2 |

These tiny-fixture results do not establish large-library scan performance or
physical-phone performance.

## Dependency proposal (approved 2026-10-22; adapter gates pending)

| Purpose | Proposed pin | Why this candidate | Remaining execution proof |
|---|---|---|---|
| CBR | `com.github.junrar:junrar:8.1.2`, upstream commit `b9b61cedd7c0b7fcbd2ab6b4cc6413e2bb9e481d` | This release includes RAR5 handling; Java bytecode target 8; avoids a second native engine | API 31/36 RAR4/RAR5 fixtures, solid archive page seeking, actual memory/APK delta and Android API compatibility |
| Junrar logging | Transitive `org.slf4j:slf4j-api:2.0.17` | Declared by pinned build; do not add a logging binding | Verify resolved graph and no new duplicate-class/lint issue |
| MOBI/AZW3 | libmobi **v0.12**, commit `85dcfe803fc2a21020ddcf15c3eb66b93d388add` | C parser exposes legacy MOBI and KF8; Android listed upstream | JNI cross-build for existing APK ABIs, parser limits/cancellation, Vietnamese content, images, links and deterministic resource output |

Junrar's recent source supersedes old issue reports that it cannot read RAR5.
Use `Archive(File)` and bounded extraction to app-controlled streams; do not
use a general filesystem extraction helper. One archive belongs to one worker.
Pinned source documents that `Archive` is not thread-safe. The runtime dependency
in its Gradle file is slf4j-api; Bouncy Castle and Jackson entries seen in release
notes are test/tool dependencies, not reasons to add them to WindBook.

libmobi's selected native surface is `mobi_init`, `mobi_load_file`,
`mobi_init_rawml`, `mobi_parse_rawml`, `mobi_free_rawml` and `mobi_free`.
Build a shared JNI library with bundled miniz and the internal writer; do not
add libxml2 or a system-installed converter. Preserve upstream notices and
COPYING in the approved vendor directory. Upstream identifies its license as
LGPL-3.0-or-later; verify the distribution packaging before a release. The local SDK has CMake 4.1.2. The owner approved NDK 29.0.14206865
on 2026-10-22 and it is installed for the native plan. No
claim of measured APK size or successful Android integration is made here.

libarchive v3.8.10 was also inspected: its header exposes both RAR and RAR5
readers. It is not the selected proposal because Junrar now offers both without
another native toolchain. Do not silently substitute it if Junrar fails a gate.

TXT, FB2 and CBZ can be planned using Kotlin/JDK/Android and the installed
Readium toolkit. This is an engineering approach, not proof those adapters
already exist. Build deterministic temporary EPUBs for normalized text formats;
use an image-page session for comics. Reuse existing rendering/progress surfaces
where their semantics fit, but keep the original source format in the library.

## Proposed bounded preparation policy

- Source limit: 500 MiB, measured while streaming when size is unknown.
- One active preparation job; 64 KiB copy/hash buffers; 1 GiB total session
  scratch budget, with at least 64 MiB free-space reserve. Fail with a specific
  space/size message, preserving the source and library row.
- At most 10,000 archive entries and 2 GiB declared or observed expanded bytes;
  session scratch limit still takes precedence. Do not allocate from metadata.
- Text normalization: stream to disk; split at paragraph boundaries into at
  most 64 Ki UTF-16 code units per XHTML resource. Reject a text document whose
  normalized text exceeds 64 MiB; expose an explicit unsupported-size error.
- Comic bitmap cache: 48 MiB; cap a decoded page at 16 MiB and sample oversized
  images. Page resources use stable entry names, not extraction order.
- Per-book session artifacts disappear after session release. Startup cleanup
  removes only orphaned session directories. Covers may persist, capped to
  600 px on the longest edge; source books never become a permanent cache.

These are planning defaults for review, not measured performance results. The
native parser gate must prove bounds are enforceable before its adapter ships;
a failed memory/cancellation gate blocks that adapter, not source preservation.

## Sources

- [Android document access](https://developer.android.com/training/data-storage/shared/documents-files): persisted tree permissions and restricted roots.
- [Readium 3.4.0](https://github.com/readium/kotlin-toolkit/tree/3.4.0): compare the pinned source with the locally cached AAR.
- [Junrar 8.1.2 release](https://github.com/junrar/junrar/releases/tag/v8.1.2), [pinned build](https://github.com/junrar/junrar/blob/v8.1.2/build.gradle), [Archive API](https://github.com/junrar/junrar/blob/v8.1.2/src/main/java/com/github/junrar/Archive.java).
- [libmobi 0.12 release](https://github.com/bfabiszewski/libmobi/releases/tag/v0.12), [public API](https://github.com/bfabiszewski/libmobi/blob/v0.12/src/mobi.h), [README](https://github.com/bfabiszewski/libmobi/blob/v0.12/README.md).
- [libarchive RAR APIs](https://github.com/libarchive/libarchive/blob/v3.8.10/libarchive/archive.h).

- [Android NDK r29](https://github.com/android/ndk/releases/tag/r29): proposed native toolchain pin.
