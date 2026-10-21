# Daily reading feedback and next-scope discussion

Date: 2026-10-20
Status: discussion brief; not an approved design or implementation plan.

## Owner feedback

The owner identified missing notes, inadequate margin/layout handling, and
missing folder loading with access to all files in the folder.
The page-turning comment is provisionally interpreted as positive; confirm
before treating page-turn performance as a reported defect.

The owner subsequently confirmed that notes should attach to selected text in
both EPUB and PDF. For scans, the owner chose selecting an image region and
attaching a note; OCR is not requested. The feedback does not yet identify
whether the margin problem occurs in EPUB, PDF, or both, or define the meaning
of reading all files in a folder.

The owner explicitly requested removal of Reflow. That change is handled
separately in `../superpowers/specs/2026-10-20-remove-pdf-reflow-design.md`.

## Evidence from the current implementation

- `NotesScreen.kt` contains an empty state only. There is no usable notes flow.
- `LibraryScreen.kt` uses `OpenMultipleDocuments`, not a tree picker.
- `LibraryViewModel.import` processes selected files sequentially, continues
  after individual failures, and emits per-file duplicate/error messages.
  Folder-sized batches need an aggregate result instead of a long snackbar queue.
- `BookImporter` already checks file signatures, deduplicates by SHA-256, and
  copies accepted books into app storage. Folder import can reuse this path.
- `ReaderTheme.kt` supplies only the initial EPUB theme. `ReaderHostFragment`
  initializes preferences when binding a session; a live preference update
  path will be needed for interactive typography controls.
- `DisplayPreferencesRepository` stores per-book JSON and preserves unknown
  keys. Existing PDF preferences must survive any EPUB extensions.
- `ReaderViewModel.updatePrefs` derives writes from the last observed
  preferences. Rapid edits can overwrite one another; settle this behavior
  before reusing it for typography controls.

These are source observations, not new emulator or phone test results.

## Proposed order for discussion

### 1. Reading layout

Start with the owner's margin problem. For EPUB, consider adjustable side
margins, font size and line height, with an explicit publisher-style policy,
live updates and per-book persistence. Verify that changes preserve the
reading location and render Vietnamese diacritics correctly.

For fixed-layout PDF, cropping and zoom are separate controls; an EPUB
margin setting cannot be assumed to solve a PDF layout problem.

### 2. Folder import

Proposed first increment: select a folder using the system picker, discover
supported EPUB/PDF files, import sequentially, show progress, permit stopping,
and summarize imported, duplicate, skipped and failed files.

Decide whether nested folders are included and whether the owner expects a
one-time import, a persistent folder browser, automatic rescanning, or an
ordered reading queue that advances to the next file. These are distinct
behaviors. Copying files into app storage also has a storage cost that the
eventual design should make clear.

### 3. Notes

The confirmed interaction is to select a passage and attach a note to it.
EPUB selection annotations and PDF page notes have different requirements;
scanned PDFs do not provide selectable text through the current bitmap UI.

The smallest useful flow should support creating, editing, listing and
reopening notes at their source location. Consider Markdown export alongside
the first notes release so the owner's writing is portable. Export is a
proposal, not a confirmed requirement.

## Scope decision still required

This proposal draws work forward from M4 (notes) and M6 (folders), alongside
part of M2 (layout). It does not approve the complete M2 feature pack or
silently reorder the accepted roadmap.

Next: write the selection-notes design, locate the margin problem, and
define folder behavior; then write a focused design for owner review under
`AGENTS.md` section 6.
