# Faster initial cover preparation and proportional display

Status: approved by the owner on 2026-10-27; implementation plan approved; execution in progress.
Baseline: main `2ae7699`, WindBook 0.1.4.

## Owner intent

On 2026-10-27 the owner reports slow initial folder-import cover preparation,
asks for correctly fitted cover images, and asks for PDF pages to fit the reader
without distorting the book. The owner explicitly confirms the delay occurs only
during the first scan/import, and prefers a whole PDF page with unused space when
necessary. Zoom remains available.

## Evidence and limits

- FolderCoverLoader processes sources serially. SourceAccess first stages an
  external source into bounded local scratch storage; SourceLease.fingerprint()
  then reads that complete snapshot again before metadata/cover extraction.
  This is an identified redundant I/O pass, not yet a measured allocation of
  total runtime. SAF access, parsing and rendering still have real costs.
- Existing device evidence measured 24 real books (12 EPUB, 12 PDF, 56.35 MB)
  at 3,750 ms initially and 299 ms on a cached rescan. These are historical
  observations, not a benchmark of the owner's full library or phone.
- BookCover crops portrait and square covers to a 2:3 frame, and changes wide
  covers from Crop to Fit after the image loads. Portrait covers can lose text.
- PagedPages already calculates a proportional fit. PageBitmap uses FillBounds,
  and paged zoom uses a separate local transform. A persistent native-page
  distortion has not yet been reproduced. Verification must distinguish fit,
  saved crop/zoom and transient mixed-size page geometry.

## Proposed correction

### 1. Initial folder cover preparation

Compute SHA-256 while copying the external source into its immutable snapshot.
Reuse that digest for fingerprint requests on that same live source lease, avoiding
the second full-file read. Owned files retain fingerprinting of their actual bytes;
never substitute document URI, provider metadata, filename or a hash from an older
lease. A failed or cancelled copy publishes neither a snapshot nor a digest.

Keep one folder source snapshot and one extractor active at a time. Preserve all
existing byte/space limits, cancellation, source-revision checks, guarded metadata
publication, optional-cover fallback and cleanup. Keep metadata and cover quality;
do not replace parsing with filename metadata or lower the 600 px cover limit.
Show each cover as soon as its guarded commit succeeds, without waiting for the
remaining covers. COMPLETE still means the cover pass has finished.

Measure the unchanged baseline and the implementation on identical fresh source
sets, including larger files. Record first-cover latency, total cover-pass time,
bytes read for staging/fingerprinting and memory. Claim only measured gains. If
copy/hash is not material, report the remaining bottleneck before expanding to
parallel parsing, new extractors or other architectural changes.

### 2. Shelf covers

Keep the aligned 2:3 shelf frames. Center every real cover inside its frame using
proportional Fit from the first displayed image, including portrait, landscape,
square and unusually narrow covers. Use the existing paper background for unused
space. Preserve the image's entire content and the existing title, menu target,
generated fallback and accessibility behavior. No image stretching or cropping.

### 3. PDF whole-page fit

Keep PAGED as the default. Fit each page, using its actual measured dimensions,
inside the available reader viewport and center it. Render page pixels with a
uniform scale; keep annotation hit targets and highlights aligned to that same
page rectangle. Preserve the system status bar and safe insets.

Provide a localized View-menu action, "Fit page" / "Vừa trang", for an already
opened PDF. It selects PAGED, turns margin cropping off and resets paged zoom/pan
to the whole-page baseline even when PAGED was already selected. It keeps the
current page and saved notes. Apply PAGED and crop=false in one preferences update
and issue an explicit local reset even if those preferences already match. Retain
the separately stored vertical zoom/zoomX for a later return to vertical mode.
Existing explicit display choices are not silently
rewritten until the owner invokes this action. Normal zoom and page turns continue
to work afterward. Vertical mode retains its existing width-based scrolling.

## Verification and delivery

Use regression tests for one-pass digest identity, cancellation/partial-copy
cleanup, limits and existing source lease behavior. Verify actual cover pixels
and PDF corner markers on API36 for portrait, landscape, tall and mixed-size
documents, rotation, fit after zoom/crop, page navigation and text-note alignment.
Reuse existing unaffected import/notes/status-bar evidence.

Run the required CI suite and lint (no new warnings), build a versioned debug APK,
and export it with SHA-256 to `/home/kent/KentProject/WindBook/publish/`. Perform
the repository-required independent review. No new Gradle dependencies, schema
changes, source-library writes, paused formats or automatic push are included.

## Approval and next step

The owner approved this description on 2026-10-27 ("đẫ duyệt"). The owner also approved the task plan ("duyệt kế hoạch"). Execution uses one implementer in this session
with one independent final review, avoiding repeated discovery and per-task agent
overhead. Preserve a checkpoint with exact completed steps and evidence.
