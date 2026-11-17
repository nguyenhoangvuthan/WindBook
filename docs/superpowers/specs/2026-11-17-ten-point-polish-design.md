# Ten-point polish: format filter, page-turn taps, EPUB clean layout — 2026-11-17

## Intent

The owner scored the app 7.3/10 after the reader-polish round and asked for the work that
gets it to 10: a format filter, a fix for page turns being read as zoom, EPUB font/layout
fixes ("other apps show these books correctly and with much nicer text alignment"), and the
remaining items from the scorecard. Order agreed with the owner: filter and tap fix first.

## Evidence (recon, 2026-11-17)

Owner screenshots from the phone, reproduced from the read-only library `/home/kent/Library`:

- book A (calibre conversion of `pdftohtml 0.36` output):
  the TOC dot leaders are stored in the file as runs of U+FFFD (`EF BF BD`). Updraft does
  not mis-decode anything. Updraft's own fault is that the unbroken run is not wrapped,
  so it overflows the column and overlaps the following lines.
- book B (same converter): every TOC line is its own
  `<p class="calibre1">` with `margin: 1em 0`, italic `<i>`, inside `<a>`. Publisher styles
  are kept today, so lines are far apart, blue and underlined.
- Same book, first page: the cover is 8 image tiles (592/518 px wide) that formed a 2×4
  grid in the PDF; the converter made each tile its own paragraph, so they stack with gaps.
- Code: `typographyPreferences()` never sets `publisherStyles = false`, so Readium ignores
  `lineHeight` — the Aa "Line height" slider currently has no effect on any EPUB.
- Paged PDF uses one `detectTapGestures(onTap = turnAt, onDoubleTap = zoomAt)` for the whole
  page (`PdfReaderView.kt:451`, and the annotation layer in `PdfAnnotationUi.kt:92-108`).
  Two quick taps on an edge are therefore a double tap → zoom, and every single tap waits
  for the double-tap timeout (~300 ms) before turning.

## Goals

1. **Format filter.** Library can be narrowed to EPUB, PDF or TXT, combined with the status
   filter and search.
2. **Taps never zoom by accident.** In paged PDF, a tap in the left/right 25 % turns the page
   immediately; two quick edge taps turn two pages. Double-tap zoom works only in the
   middle 50 %. Same rules on the annotation layer.
3. **EPUB clean layout by default (owner chose option A).** Every EPUB opens with the app's
   layout: justified text, line height 1.5, small paragraph spacing, no paragraph-margin
   blowups, link colour = text colour. A "Keep original formatting" switch in the Aa sheet
   restores publisher styles for this book or for all books (existing scope selector).
   The line-height slider works in clean layout.
4. **No overflowing text.** Unbreakable runs (like the U+FFFD leaders, long URLs) wrap at the
   column edge in both clean and original layout.
5. **Scorecard fixes.** Notes "Vocabulary" button aligned with its title; Notes search field
   uses the same pill shape as Library search; Settings "Reading appearance (Aa)" row aligned
   with the other rows; placeholder cover tints are dimmed in dark theme; empty Library offers
   "Choose files" and "Choose folder" directly (one tap less); a one-time hint on the first
   paged-PDF/EPUB open explains tap zones (edge = turn, middle = menu, middle double tap =
   zoom on PDF) and disappears on the first tap.

## Non-goals

- Restoring the lost dot leaders or rebuilding the tiled cover grid: the information is not
  in the file. Clean layout removes the gaps between image-only paragraphs, which is the
  best a reflowable reader can do; we report the result honestly after checking it.
- Rewriting or copying book files; the source library stays read-only.
- Changing the Reading tab (owner confirmed cover-only shelves in 0.1.8; with one sample book
  it only *looked* empty).
- New Gradle dependencies, DB migrations, paused formats, TTS.

## Anti-goals (failure even if it "works")

- A page turn that sometimes zooms, or feels slower than today.
- Clean layout that breaks exact-text highlights, search hits or saved positions.
- Silently changing book text (offsets would shift anchors).

## Constraints

- Readium 3.4.0 only: `EpubPreferences(publisherStyles, textAlign, lineHeight,
  paragraphSpacing, paragraphIndent, hyphens)`; `RsProperties.linkColor`;
  `PublicationOpener.open(..., onCreatePublication = { container = ... })` for CSS injection.
- Typography JSON keeps unknown keys; a new field needs no migration.
- EN + VI strings, 48 dp targets, 130 % font, no new lint warnings.
- Supersedes AGENTS.md key decision "EPUB keeps publisher styles" (owner decision
  2026-11-17, option A). PDF is unaffected.

## Approaches considered

- **A. Clean layout by default, per-book/global "keep original" switch** — chosen by the owner.
- B. Keep publisher styles by default, add an opt-in clean switch — bad books stay bad until
  the owner toggles each one.
- C. Rewrite book HTML at import — rejected: mutates/copies files, risks breaking books.

For the wrap fix: a `<style>` injected into every HTML resource at publication-open time
(Readium container transform) was chosen over per-page `evaluateJavascript` injection, which
would re-paginate after first paint (visible jump, locator drift).

## Decisions made by the builder (owner can override)

- Format filter: a second chip row under the status chips, multi-select, nothing selected =
  all formats; only formats present in the library get a chip; hidden when the library has
  one format. Not persisted across launches (same as the status filter today).
- Clean-layout values: justify, line height 1.5, paragraph spacing 0.5 rem, indent 0,
  hyphens on (browsers hyphenate only languages with a dictionary; Vietnamese is unaffected).
- Image-only paragraphs get zero vertical margin in clean layout.
- Tap-zone hint shown once per install, stored in `LibraryPreferences`.
- Dark placeholder tints: same hues at reduced lightness, still warm (red ≥ blue).

- Clean layout is EPUB-only (reflowable). Fixed-layout EPUB, PDF and TXT keep their current
  rendering; the switch is hidden for them like the other font controls.
- Stored as a new nullable typography field (`originalLayout`): null inherits the global
  value, global null = clean layout.
- Work happens on branch `ten-point-polish` started from `reader-polish` (not yet merged);
  next build would be 0.1.13.

## Left to the plan

Exact Readium CSS hook, test seams for the gesture split, string wording, and how the hint
overlay is layered over Readium's fragment.

## Acceptance

JVM CI green, lint not worse than 0 errors/14 warnings, emulator checks on the three owner
books (book A TOC wraps; book B TOC tight and justified; cover tiles without gaps), a
Vietnamese EPUB, a paged PDF double-tap/edge-tap check, EN/VI and 130 % font. The owner's
phone is the final judge; no claim of "10/10" before the owner rescores.
