# Selection notes for EPUB and PDF

Date: 2026-10-21
Status: proposed design for owner review; not approved for implementation.

Independent builder review: no important unanswered planning questions.
The Android 12–14 PDF region fallback remains an explicit proposed limit for
owner approval. Gesture and text-extraction feasibility checks remain in planning.

Local interactive mockup (throwaway, not app code):
`.superpowers/brainstorm/27897-1792599917/content/selection-notes-v5.html`.
It demonstrates document modes, the shared compact selection menu, color-only
highlights, composing/saving a note, searching the list,
and returning to its source. Text selection and rectangle dragging are illustrated,
not implemented. Mock data lasts only for the browser tab session.

## 1. Intent and confirmed requirements

The owner needs notes while reading, attached to a selected passage rather
than only to a book or page. Both EPUB and PDF are required. For scanned PDFs,
the owner explicitly chose selecting an image region and attaching a note.
OCR is not requested. Reflow has been removed and must not return.
The owner specified the same compact horizontal popup above selections in EPUB,
text PDFs and scanned PDFs, with Highlight color and Note actions. All three
support colored highlights without a note; scans highlight the selected region.

The first usable release should let the owner select, write, save, find and
revisit a note without leaving WindBook. This pulls a focused part of M4 and
PDF annotations from M8 forward; it does not bring all of either milestone
into scope. Margins/typography and folder import remain separate discussions.

The choices below are proposed implementation and interaction defaults, not
claims of owner approval. Approving this design accepts those defaults.

## 2. Scope

Include:

- EPUB text selection with standalone colored highlights and optional notes.
- Text selection highlights and optional notes for PDFs with an extractable
  text layer on Android 15+ (API 35+), including the owner's Android 16 phone.
- Rectangular region highlights and optional notes for scanned PDFs, and a
  region fallback for any PDF whose text cannot be selected or whose device
  lacks the text-selection API.
- Create, edit and delete a note; revisit its source; list notes for a book or
  across the library; search quote and note text.
- Five shared highlight colors (proposed palette: gold, green, blue, pink, purple),
  with gold as the initial default and a distinct mark when a note is attached.
  PDF text marks follow the selected text rectangles; scan marks use a translucent
  fill and visible outline over the selected region, preserving image legibility.
- Explicit Markdown export of a book's notes through the system save picker.

Defer underline-only annotations, tags, handwriting,
OCR, synchronization, embedding annotations into source PDFs, importing external
PDF annotations, quote cards and a full backup/restore system. Export is included
as a proposed portability measure; it is not a restorable database backup.

## 3. Reading interaction

### Shared selection menu

For EPUB and PDF text, long-press a word and adjust the selection handles.
For scanned pages, draw and adjust a region first. Once selection settles,
show a compact anchored popup directly above the selected passage. Its primary
menu contains exactly two text actions: Highlight color and Note. At the owner's
request, keep the popup around one-third of v2's area and increase label size
from 14 to 18 in the mockup. All three formats place
both actions on one horizontal row, sized to their text with minimal padding
(approximately 150 px wide). Make the text itself clickable, without padded
button boxes. Color swatches appear on a compact horizontal row below. Preserve keyboard
focus and TalkBack action labels. This replaces the previous 240 dp menu and
48 dp padded-button proposal; final app dimensions follow the approved visual. Center it
on the visible selection and respect safe insets. If there is insufficient room
above, place it below. Do not cover the selected text or its handles. Reposition
as the selection changes; dismiss on selection clear or navigation.

Highlight color expands a short row of five labeled color swatches inside the
same popup. Choosing a color saves the highlight immediately without opening
the editor or requiring note text, then dismisses the popup. Failure keeps the
selection and reports that saving failed; do not display a persisted mark until
the write succeeds. Swatches expose their color name and selected state to
TalkBack. Existing Copy behavior may remain in the system/secondary selection
menu, but must not produce a second overlapping toolbar.

Note opens an editor with the selected passage above a multiline input. Save
creates the note with a gold highlight if the passage is new. Cancel does not
create a new annotation. On an existing highlight, Note attaches text to that
same record and preserves its color, anchor and ID. Cancel leaves the existing
highlight untouched. A note-bearing highlight has a visible note indicator.

Tap an existing highlight to reopen its contextual actions: change color,
add/view its note, or delete the annotation. If multiple saved annotations
overlap, first present a compact list to choose one. Updating through an
existing annotation acts on its ID. Creating a new overlapping selection may
create a separate annotation; it must never overwrite a different record.

### EPUB anchors

Selection is limited to the current EPUB resource/chapter. Changing fonts,
size, theme or orientation must not invalidate the saved anchor. A future
unresolvable anchor leaves the quote and note readable in Notes and reports
that the passage cannot be located; it must not silently jump to the wrong text.

### PDF with text

Long-press a word to select it, then drag either handle across lines on the
same page. Show the shared horizontal Highlight color / Note popup above it
(or below when needed). Color saves a standalone text highlight; Note opens the
same editor and can attach to the existing record. PDF selection and saved marks use page
coordinates, independently of bitmap resolution, crop, zoom and scroll offsets.
The mark must track the same content in both Vertical and Paged modes.

If the selection API is unavailable, or no text is returned at the press point,
show a contextual Select area action. Area selection is also available from
the reader overlay, so a scan does not require guessing where to long-press.
PDF text selection is limited to one page per note in this increment.

### PDF region / scanned page

Select area enters a distinct mode on the current page. Drag to draw one
rectangle, then adjust its corner handles. Show the same horizontal Highlight
color / Note popup above the region, falling below when space is insufficient.
Choosing a color immediately saves the region highlight without requiring text.
Note opens the editor with a preview of the selected region; saving attaches
the note to that same record, preserving color and bounds. A translucent fill
and visible outline mark the region; a distinct indicator identifies an attached
note. Tapping the region reopens its actions, including changing the color.

In selection mode, page-turn taps, page-turn keys and one-finger scrolling
must not move the page while a region/handle is being dragged. The owner can
zoom before entering selection. Cancel exits selection without creating a
note. Selection cannot cross page boundaries; tiny/empty rectangles cannot
be saved. Clamp bounds to the full page, even when margins are cropped.

Region previews are generated on demand at bounded size from the book. The
source file and page anchor remain authoritative; no full-resolution scan
copy is stored for every note. If the file is unavailable, show the saved
note and page reference with an unavailable-preview state.

### Editing and saving

The editor has explicit Save and Cancel actions. Require nonblank text when
saving a note; standalone highlights of all three anchor types have no body
and bypass the editor.
Disable duplicate Save/color submissions while their write is pending.
Only show success after the database write succeeds. A failed save retains
the draft and provides Retry. The draft survives rotation; deliberate Back or
Cancel with changes asks whether to discard. Saved notes survive process death
and app restart. Do not promise recovery of an unsaved draft after force-stop.

Deleting a note asks for confirmation, then removes its visual mark and list
entry. Editing a note changes its body without modifying the quote or anchor.
Selecting a different passage creates another note.

## 4. Notes navigation and export

The existing Notes tab contains notes and standalone highlights from all three
formats, newest edited first. A highlight-only row shows its quote or region
preview, color and a Highlight label rather than an empty note body. Each row shows the book, chapter or page, selected quote or region preview, and note
body. Search matches note text and available quote text. A book filter narrows
the list. The reader overlay opens this list scoped to its book, ordered by
reading position. Return restores the reader state.

Tapping a note opens its book at the anchor and reveals that note. Navigation
from Notes uses the note's anchor rather than the book's last-read locator;
normal continued reading then follows the existing progress-saving policy.
For a PDF mark outside the currently cropped box, temporarily show the full
page while revealing it, without persisting a crop preference change. Start
from a fit-page/fit-width view that makes the mark visible rather than applying
a saved zoom that could place the mark offscreen.

Soft-deleting a book hides its notes from the active list but retains their
records. Reimporting the same book revives them with its existing book ID.
Permanent book purge cascades to notes. Deleting a note must never delete its
book or affect reading progress.

Export is an explicit action for the current book. Save a UTF-8 Markdown file
with book metadata, chapter/page references, quoted text where available and
the note bodies. Standalone highlights include their quote when available and
color name without an empty note section. Region annotations include color,
page number and normalized bounds;
embedded region images are deferred. Escape document content so quotes and
notes remain readable Markdown. Cancellation leaves existing data unchanged.

## 5. Data and architecture

Keep the four-module dependency direction and existing dependencies.

- `core:model`: annotation type, stable ID, book ID, anchor, optional quote,
  annotation kind (highlight or note), shared color token, nullable note body and
  timestamps. Highlight kind accepts EPUB text, PDF text or PDF region anchors
  and no body; note kind requires a nonblank body. Pure validation and page-coordinate transforms.
- `core:data`: Room annotations table and repository. Increment the schema
  from v2 to v3; preserve books, progress and JSON display preferences. Add
  foreign key/index for book ID. Treat insert/update/delete as atomic operations.
- `reader`: selection/decoration bridge for EPUB; PDF text selection and page
  coordinate mapping through the existing dedicated PDF worker thread.
- `app`: shared editor, Notes list/filter/search, region interaction, overlays,
  navigation to anchors and explicit export.

An EPUB anchor stores the full Readium selection Locator (including text
context), not only progression or a screen coordinate. A PDF anchor stores
zero-based page index and normalized rectangles in the original full-page
coordinate system, plus selected text when available. A text selection can
contain multiple rectangles; an area note contains one. Store explicit anchor
type/version so malformed or future data fails safely. Invalid anchors do not
make the Notes list crash or erase note bodies.

Persist anchors as versioned JSON and queryable note fields separately. Keep
per-book display JSON independent from annotations. New notes must not depend
on the known rapid display-preference write issue. Never store UI pixel
coordinates as the persisted PDF anchor.

Reuse the installed Readium 3.4.0 EPUB navigator, its selection Locator and
Decoration support; verify exact signatures against that pinned version during
planning. Keep framework PdfRenderer for PDF. Android's `getTextContents` and
`selectContent` are available from API 35; guard them at runtime. On API 31–34,
use region highlights and notes for PDFs in this increment. This compatibility choice is
proposed and must be visible in owner review. EPUB selection remains available
across supported Android versions.

All PDF calls share the existing renderer thread; no page stays open while
waiting for user input. Selection requests should be cancellable/coalesced
while dragging, and results must be ignored after the page/session changes.
Do not open PDFs in Readium or add a rendering backend.

Store notes only inside the app, with no network permission or service. No new
Gradle dependency is planned. Every new visible string must have English and
Vietnamese resources. Use the existing cream/brass design and readable marks
in both light and dark themes; label selection and editor controls for TalkBack.

## 6. Acceptance and verification

- Verify the same horizontal popup in EPUB, PDF text and PDF region modes,
  above/below selections near screen edges, with no overlapping
  toolbar, accessible color choices, and preserved selection while tapping it.
- For each of the three anchor types, save a standalone highlight, restart,
  change color, attach a note to the same
  record, and cancel an edit without losing the existing highlight. Verify its
  list and export representation, including failed and duplicate writes.
- Create/edit/delete an EPUB note, restart and reopen the exact passage; repeat
  after changing orientation and later typography settings.
- Create a multiline text note on a PDF with selectable text on API 36; reopen
  it after switching Vertical/Paged, cropping and zooming.
- Attach a region note to a large scan, verify the same rectangle after zoom,
  crop, mode changes and rotation, and open it from Notes.
- Verify API 31–34 gracefully uses PDF region mode without API linkage crashes.
- Verify overlapping notes, empty selections, empty note body, slow/failed save,
  double Save, missing book file and malformed anchor handling.
- Migrate a v2 fixture containing books, progress and display preferences to v3;
  preserve those values and allow new annotations. Verify soft delete/reimport.
- Export text and region notes in Vietnamese; cancel export and verify no data
  loss. Confirm exported region references are understandable without images.
- Test coordinate transforms as pure functions with hand-derived cases; run
  repository/migration and UI integration tests, then verify actual text/region
  placement on emulator. Use synthetic fixtures; keep screenshots out of git.
- Run the full repository CI command, device tests and debug build. Record PSS
  on the large-scan fixture; annotating must not trigger an uncapped full-page
  allocation or retain every visited page bitmap. Independent branch review
  precedes the owner's merge decision.

## 7. Builder decisions and approval boundary

The implementation plan may choose filenames, database serialization details,
request scheduling and test fixture construction without changing user-visible
scope. Verify actual text extraction/selection on representative Vietnamese
PDFs before committing to exact gesture behavior. If the installed engine
cannot support the proposed behavior, return the evidence and revise this
design; do not silently replace text selection with region selection on the
owner's device.

The owner must approve this document before the implementation plan. Margins
and folder import still need their own concrete behavior agreed; they are not
prerequisites for this notes design.

## Sources checked for feasibility

- [Android PdfRenderer.Page](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer.Page): API 35 text extraction/selection; calls run off the UI thread.
- [Readium navigator interfaces](https://github.com/readium/kotlin-toolkit/blob/develop/docs/guides/navigator/navigator.md): selection Locators and decorations; exact 3.4.0 API use is a planning check.
- Existing repository: `ReaderHostFragment.kt`, `PdfDocumentHandle.kt`,
  `PdfReaderView.kt`, `NotesScreen.kt` and `WindBookDatabase`.
