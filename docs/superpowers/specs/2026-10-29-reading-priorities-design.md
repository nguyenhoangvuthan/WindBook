# Cover speed and daily reading priorities

Date: 2026-10-29
Status: proposed; owner requested the work and confirmed that the delay is cover
appearance during library scans/updates. Written design and task-plan approval
are pending under AGENTS.md section 6. No implementation is claimed.
Baseline: WindBook 0.1.5, current main.

## 1. Intent and delivery order

Deliver four independently verified increments in this order:

1. Faster first useful cover and faster initial cover preparation.
2. EPUB/TXT typography and core reading navigation.
3. Offline backup/restore and portable note export.
4. EPUB text highlights/notes using the existing Notes workflow.

The owner approved this direction after the October29 market comparison. This
design specifies the user-visible choices and persistence contracts that were
not determined by that request. It is a focused subset of M2/M3/M7/M4, not
completion of those whole milestones. Every increment has its own implementation
branch, review and APK; local integration follows owner direction. Push requires
separate authorization. Do not wait for all four increments to deliver a useful APK.

Global constraints: offline/no INTERNET, no account, no new Gradle dependencies,
English/Vietnamese strings together, read-only source books, stable existing
locators and durable reading data, bounded memory and temporary storage. Preserve
current PDF Fit page, proportional shelf covers, status bar and cutout behavior.
TTS, AI, cloud sync, OCR, scanned-page notes and paused formats stay outside scope.

## 2. Evidence and uncertainty about cover delay

The current coordinator discovers the entire tree before starting enrichment.
FolderCoverLoader then stages, verifies, extracts and commits one book at a time.
ReadiumMetadataExtractor opens a publication for EPUB/PDF metadata; PDF cover
rendering additionally opens the framework PDF renderer. These are concrete
execution paths, not measured proof of each stage's runtime contribution.

The previous 25-book comparison removed a repeated 80,837,091-byte read, but
cover-pass medians were 3515ms before and 3634ms after. It did not demonstrate a
wall-time gain. Source verification and cover quality must not be weakened to
produce a favorable number. Missing older ignored reports in this checkout are
not permission to recreate owner-library data or claim repeated evidence.

### 2.1 Proposed execution changes

- Profile discovery, staging/hash, EPUB guard/parsing/cover decode, PDF metadata,
  PDF render, compression and database publication separately. Logs contain
  synthetic IDs and timings, not private titles or document paths.
- Start one cover worker after the first committed discovery batch instead of
  waiting for the entire tree. Feed source IDs through a bounded queue. Discovery
  may continue, but only one cover snapshot/extractor is active at a time.
  The queue contains identifiers, never full books or decoded bitmaps.
- Query each queued candidate against the active run before work. Keep existing
  source stamps, immutable-snapshot SHA, commit guards and cleanup. A final pass
  after discovery catches candidates invalidated by concurrent discovery updates.
- Do not use the current random-source-ID keyset cursor as a live-discovery
  frontier: later inserts may sort before that cursor. Keep the final keyset pass
  only after discovery has stopped. Backpressure must not lose source IDs.
- Add a guarded EPUB fast path for ordinary EPUB2/3 title/author and declared
  raster covers. It reads the same verified snapshot, preserves archive guards,
  bounds XML/entry reads and image decoding, and never loads chapters unnecessarily.
  Use Readium on unsupported/ambiguous structures rather than replacing real
  metadata with filenames or treating unsupported structures as missing covers.
  Unsafe archives remain rejected rather than passed to a fallback.
- Keep PDF metadata extraction and framework cover rendering serial and unchanged
  unless measurements justify a separate proposal. No additional PDF engine,
  native thread pool or second cover worker is included.
- COMPLETE still means discovery and all scheduled cover attempts finished.
  Publish each accepted cover immediately. Stop/disconnect invalidates the run
  before joining both discovery and cover work. Late native results cannot commit.
- Show completed/attempted cover progress and the remaining queue after discovery
  when useful. Never show a fabricated total while the tree is still being scanned.
  Books without an embedded cover retain their existing fallback.

The EPUB fast path is an explicit architectural change relative to October27's
one-extractor design. Approval of this document would approve only this bounded,
fallback-backed fast path, not arbitrary new parsers or parallel native rendering.
Cover output retains proportional Fit, maximum600px and current WebP quality80.
The Add-books metadata path should share the proven extractor without changing
its EPUB/PDF-only format support in this speed increment.

### 2.2 Performance acceptance

Use fixed-order synthetic and approved real samples, separate fresh and cached
runs, and alternate baseline/candidate at least five times. Record first cover
from scan start, discovery time, cover-stage time, total completion, peak/sampled
PSS, source bytes and cover success count. Separate EPUB and PDF results.

First-cover latency must improve on a deep/wide tree fixture; an early cover must
appear before a deliberately delayed final directory completes. The actual total
cover pass must also show a repeatable gain beyond trial spread on a corpus with
representative EPUBs. Report magnitude and PDF limits honestly. Merely moving
work earlier, changing a spinner or reducing I/O without lower elapsed time does
not satisfy the speed claim. If no elapsed gain is demonstrated, report the gate
as unmet and revise the proposal instead of declaring the speed fix finished.

Cached rescan must not re-extract unchanged verified covers, and must not regress
beyond measurement spread. IDs, source SHA, reading rows, notes and preferences
must remain unchanged for unchanged content. Include unknown-size/time providers,
changed files, no-cover books, Stop, disconnect and reopen/restart.

## 3. Typography and navigation increment

### 3.1 Reading appearance

Add an Aa bottom sheet for reflowable EPUB and normalized TXT. Fixed-layout EPUB
keeps its layout; unavailable controls explain why. PDF keeps its current View
menu and does not offer font controls that cannot affect page images.

Initial controls:

- Font: Publisher, bundled Literata, system serif and system sans. Reuse the
  existing licensed Literata asset through the navigator's supported font API.
  No new fonts or network font resources.
- Text size:80–200%, default inherited/publisher. Line height:1.2–2.0 with an
  explicit publisher/default choice. Margins: narrow/normal/wide.
- Reading colors: follow system, Paper, Sepia, Dark. Preserve the current initial
  sepia/dark behavior for books without newly saved preferences.
- Global defaults, an explicit per-book override and Reset to global. The reader
  sheet labels which scope is being edited; no hidden global changes.
- Changes preview on the real open page. Debounce/coalesce expensive layout work,
  preserve the latest text locator and do not reopen the provider on every change.
  Cancel stale layout results and keep reading progress valid after re-pagination.

Publisher styling remains the default. An explicit reader-style choice may
override the selected property; reset removes that override. Do not force an
entire custom stylesheet on untouched books. User font import, weight tuning,
brightness gestures, dimming overlays, time schedules, spreads and other full-M2
features are deferred.

Persist global defaults in DataStore and per-book fields in the existing JSON
preferences. Preserve PDF fields and unknown keys. Use transactional field updates
for all writers sharing book_preferences; an older whole-object PDF write must
not erase a newer typography update. A global-default reset leaves per-book
overrides intact. Changes to book A cannot leak into book B accidentally.

### 3.2 Navigation

- A contents sheet for EPUB using the publication's existing TOC; nested entries
  and current chapter are visible. TXT may expose generated sections as sections,
  not invented chapter names. Empty TOCs get an honest empty state.
- Bookmark current location; list, rename and remove bookmarks per book. Support
  EPUB/PDF/TXT. Persist locator, quote/label if available, book/revision identity
  and TXT encoding when applicable. Typography changes must not move a bookmark
  to another passage. Bookmark creation is idempotent for the same anchor.
- EPUB/TXT in-book text search with snippets, incremental bounded results,
  cancellation on query/book changes, next-page loading and passage navigation.
  Vietnamese accents/case are tested; do not promise accent-insensitive content
  search unless the installed search service actually supports it correctly.
- PDF gains page-number jump on Android12+. No PDF full-text search or PDF outline
  parser is included in this increment. A PDF bookmark uses the existing locator.
- After chapter/search/bookmark jumps, offer Back to previous location. Keep up
  to10 in-session jump origins. Ordinary page turns do not create history entries.
- A navigation selection moves the existing reader session, never opens a PDF
  through Readium and never creates a second reader for the same book.

Add bookmarks with an additive database migration from v7 to v8. Their presence
counts as durable reading data in source-revision policy, including hide/reconnect
cases. Preserve all v7 rows exactly. Do not claim full M3 completion; footnote
cards, statistics/time-left, auto-scroll and custom key bindings remain future work.

## 4. Offline backup/restore and export increment

### 4.1 Backup

Settings offers Save backup through ACTION_CREATE_DOCUMENT. A versioned
`.windbook` ZIP contains snapshot.json with books/revisions, sources/memberships,
reading progress, TXT encoding states, all preference fields, bookmarks,
annotations and relevant global settings. Include hidden/revision rows needed
by durable references. Preserve raw annotation/preference JSON, not just fields
known to the current UI. Capture database rows consistently; do not copy a live
SQLite file without its WAL.

The initial backup is data-only by default; an explicit Include imported books
option adds owned EPUB/PDF files and referenced covers after size/free-space
checks. External folder books are referenced, never silently copied wholesale.
Explain which original files are needed to restore. A data-only backup must not
claim that the user's book files are included. No passwords, grants or private
diagnostic files are collected. Partial output after failure/cancellation is
reported as invalid, never shown as a successful backup.

### 4.2 Restore

Use ACTION_OPEN_DOCUMENT, inspect and validate the full archive in bounded private
scratch before mutation, then show a preview of books, notes and missing sources.
The user confirms the concrete import inside the app. First release uses an
additive restore that retains current library data; there is no silent Replace
library operation.

Exact matching durable records are reused. Conflicting incoming records are
imported under deterministic IDs derived from the archive identity and original
IDs, with all foreign keys remapped together; existing records are not overwritten
merely because their timestamps differ. Preview reports retained conflicts and
possible duplicate library entries. Repeating the identical archive is idempotent
and preserves subsequent local edits. Matching SHA alone cannot collapse distinct
revisions/encoding history; hashes are deliberately nonunique in the current DB.

Persisted Android grants cannot be restored from JSON. Restore external folders
as disconnected unless the current device already has a valid matching grant and
the matching existing record is reused. Absolute file paths from another device
are not assumed usable. Missing owned files become reconnectable records; included
files are verified and staged in app-owned storage before database publication.
Reconnection verifies bytes before showing old bookmarks or notes.

Validate manifest version, entry names, duplicates, decompression limits, book
hashes, anchors and foreign keys. Reject unsupported future snapshot versions
without mutation. Database changes are atomic, scans/readers are quiesced during
restore, and staged files are retired on failure. Cancellation/corrupt archive/
insufficient space cannot delete or corrupt the pre-restore library.

### 4.3 Markdown export

Export one book or all notes through a user-selected save destination. Include
book identity/title/author, quoted text, note body, color, stable location and
export time. PDF locations include page numbers; EPUB locations later include
chapter and locator. Correctly escape Markdown and retain Vietnamese Unicode.
Do not imply that Markdown alone is a restorable backup. This export uses a
shared format-aware model so EPUB notes extend it in the next increment.

No scheduled backup, sync, cloud integration or automatic outbound sharing.

## 5. EPUB notes increment

On supported reflowable EPUB, long-press text, adjust native selection, choose one
of the current five colors and optionally enter a note. Reuse Notes list/search,
edit/delete, draft recovery and exact-source revisit. Tap a highlight to edit.
Selection is limited to the current resource/chapter; changing chapter or layout
clears only ephemeral selection, not saved highlights. Cross-chapter selection,
fixed-layout annotation, TXT notes and scanned-PDF regions are outside this pass.

Anchors contain a versioned EPUB locator plus exact quote and bounded surrounding
context, bound to the pinned session SHA. Highlight and revisit use semantic
locations and Readium decorations, not screen coordinates or saved page numbers.
Re-pagination/rotation must retain the same passage. Invalid or unavailable anchors
remain visible in Notes with an explanation; do not guess a new passage in a
changed file. Cancel callbacks from old sessions and clear selection after saving.

Generalize the annotation model to explicit PDF_TEXT and EPUB_TEXT kinds. Add an
additive v8→v9 migration with existing rows defaulted to PDF_TEXT. Preserve all
legacy PDF payloads and geometry; keep its Android15+ selection restriction.
PDF reading still works on Android12+. Update backup/restore/export to round-trip
both kinds; recognize the preceding snapshot version and preserve its raw fields.
Source books are never rewritten and no external note service is required.

## 6. Verification and review

Use failing tests before each behavior change. Gate migrations on exact row and
foreign-key preservation. Device gates cover Vietnamese/English and large UI font,
actual text layout, saved passage after typography changes, process death,
rotation, search cancellation, backup failure/restore round trip and EPUB/PDF
note coexistence. Use synthetic content for committed fixtures/screenshots.

Run mandatory CI before every code commit, no added lint warnings, then assemble
the increment APK and independent whole-branch review/fix pass. Export APK and
SHA256 only to main-root publish/, untracked. Record measurements and limitations
with AGENTS/current state and the execution ledger. Do not label an unmeasured
performance improvement, unrun device gate or pending independent review as passed.

Approval requested: this written design plus the companion phased task plan,
with one implementer working sequentially and one independent review per increment.
