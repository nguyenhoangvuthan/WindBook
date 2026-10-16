# WindBook – PDF comfort: light import, remembered zoom, reflow

Date: 2026-10-15 · Status: draft for review
Inputs: `docs/superpowers/specs/2026-10-15-windbook-pdf-display-design.md` (shipped, `43f8f98`), `docs/research/2026-10-15-library-analysis.md`

## 1. Purpose

The PDF display work made PDFs fit the phone, but two problems remain:

- **Importing a large scan is still heavy.** Readium's PDF parser renders a cover at the first page's full device-pixel size. For the owner's 24 MB textbook that is about 15 500 × 25 000 px: roughly 36 s and 1.5 GB per import. Six books in the library have pages over 1 500 pt.
- **Text stays small.** Cropping margins enlarged body text only 1.20–1.26×, because the books' text blocks already fill 75–80 % of the page width.

The owner chose three answers:

1. Make import light.
2. Remember the zoom per book.
3. Add an image-based reflow that works for scans and PDFs with a text layer alike.

**Success criteria**

1. Importing a PDF with 2654×4329 pt pages takes ≤ 1.5 s on the emulator. The import adds < 100 MB PSS. Title and author are still read, and the cover is still made.
2. A book zoomed once reopens at the same zoom and horizontal position. A book never zoomed still opens at 1× fit-width, with no sideways panning.
3. In Reflow, at the default size, body text of the calibre Vietnamese PDF is at least 1.8× its height in the uncropped full-page view. No sideways panning. Vietnamese diacritics stay attached to their letters.
4. Reflow analysis and layout of one scanned page take ≤ 300 ms on the emulator. Switching to Reflow shows the first reflowed page in ≤ 1.5 s.
5. Reading position is kept when switching between Vertical, Page by page and Reflow.

Constraints carried over: no new Gradle dependency; minSdk 31; every user-visible string in `values/` and `values-vi/`; PdfiumAndroid never renders.

## 2. Part 1 – Light PDF import

- `ReadiumServices` wraps its `PdfiumDocumentFactory` in a factory whose documents delegate everything to Pdfium except `cover()`, which returns `null`. Readium still reads title, author and page count. It no longer renders a cover.
- `ReadiumMetadataExtractor` makes the PDF cover itself. It opens the file with `PdfDocumentHandle`, renders page 1 at 600 px height (the stored cover size) and closes it. The cover bytes are WebP as today. EPUB import is unchanged.
- Any failure in the cover step leaves `coverWebp = null`. The book still imports.

## 3. Part 2 – Remembered zoom (vertical mode)

- `BookDisplayPrefs` gains `zoom: Float?` (1–4) and `zoomX: Float?`. `zoomX` is the horizontal scroll as a fraction 0–1 of the scrollable width. Both are stored in the existing `book_preferences` JSON as `pdfZoom` and `pdfZoomX`. No migration is needed: the codec already keeps unknown keys. Null means 1× at the left edge.
- `PdfReaderView` takes `initialZoom` and `initialZoomX` and reports `onZoomChanged(zoom, zoomX)` 500 ms after a pinch, double-tap or horizontal drag settles. The ViewModel saves it.
- Volume / page-down keys while zoomed follow reading order:
  - Pan right by one viewport width minus 10 % overlap until the right edge.
  - The next press returns to the left edge and scrolls down 90 % of the viewport height.
  - Page-up mirrors this.
  - At 1× the keys scroll as today.
- Page-by-page mode is unchanged: zoom per page, reset on turn. Reflow has its own text size (§4).

## 4. Part 3 – Image reflow

### 4.1 Page analysis (`PageAnalyzer`, `:core:model`, pure Kotlin)

Input is the luminance of the page's crop box rendered about 1200 px wide.

1. **Binarise.** Otsu threshold from the page histogram (scans have grey paper).
2. **Columns.** A vertical white gutter ≥ 3 % of the width that spans ≥ 80 % of the height splits the page. At most 3 columns, read left to right.
3. **Lines per column.** Horizontal projection.
   - A run taller than 1.6× the median line height is split at its lightest row when that row has ≤ 15 % of the run's peak ink. This separates lines that touch at tight 1.2 leading.
   - A run thinner than 0.5× the median line height that lies within 0.35× of that height from a neighbouring line is merged into that line. The line below wins when both are in reach, because marks above letters are far more common in Vietnamese than the dot below. This keeps detached diacritics (Ể, ỗ, ặ) with their letters.
4. **Figures.** A line is a figure line when any of these holds:
   - it is taller than 2.5× the median;
   - it is taller than 25 % of the page (a page that is one big picture);
   - its ink density is above 45 %;
   - it is a leftover thin run (a rule).

   Consecutive figure lines form one figure block (tables and formulas included).
5. **Words.** Vertical projection inside a line. A gap wider than 0.35× the column's median x-height separates words. (A space is about 0.25 em, so 0.3× the full line height, about 1.3 em, would glue words together.) A line's x-height runs from its first to its last row holding ≥ 40 % of its peak row ink; its baseline is that last row.
6. **Paragraphs.** A line starts a paragraph when any of these holds:
   - the gap above it exceeds 1.5× the median line gap;
   - it is indented more than 4 % of the column width;
   - the previous line ended before 80 % of the column width.

Output is `PageLayout(blocks)` in reading order, in page fractions. A block is either `TextLine(top, bottom, words, startsParagraph)` or `Figure(box)`. A page with no text line yields one `Figure` covering the crop box.

### 4.2 Layout (`ReflowLayouter`, `:core:model`, pure Kotlin)

Inputs: a `PageLayout`, a target width in px and a target line height in px.

- Scale = target line height ÷ the page's median source line height.
- Words are placed left to right into rows. A word keeps its vertical offset from its source line's top, so baselines stay aligned.
- Spacing:
  - word gap = 0.35× the target line height;
  - line spacing 1.3;
  - a paragraph start adds 0.5× the line height above it and an indent of 1.5× the line height.
- A word wider than the target width is scaled down to fit.
- A figure is scaled to fit the width, never above the text scale, and gets its own row.

Output is `ReflowPage(height, pieces)`. Each piece is a source rectangle in page fractions plus a destination rectangle in px.

### 4.3 Display

- `PdfLayout` gains `REFLOW`. The View menu shows three radios: Vertical scroll / Page by page / Reflow ("Dàn lại chữ"). While in Reflow it also shows **A−** and **A+**.
- **Text size.** Seven levels of on-screen line height: 18, 21, 24, 28, 32, 38, 44 dp. The default is 24 dp. The level is stored per book as `BookDisplayPrefs.reflowSize` (`pdfReflowSize`). Pinch in Reflow steps the level.
- **View.** A `LazyColumn` with one item per PDF page. Each item draws its `ReflowPage` on a `Canvas`, copying pieces from a source bitmap of the page's crop box. Pages are separated by a 16 dp gap. The background is the paper colour, as in vertical mode. Volume keys scroll 90 %.
- **Source bitmap.** The crop box is rendered at the scale the layout needs, capped at 2× the viewport width. Above the cap the pieces are drawn scaled up (slightly soft), so memory stays bounded. Bitmaps live in the existing `PdfPages` LRU (≤ 48 MB). The next page is prefetched.
- **Lazy analysis.** A page is analysed when it is about to show, on the PDF thread. Its `PageLayout` is cached per page (small). Until it is ready, the page shows as in vertical mode, then swaps to the reflowed version.
- **Position.** Same page reporting as vertical mode, including the last-page rule. Locators keep the M1 format, so switching modes keeps the page.

### 4.4 Data

`BookDisplayPrefs(pdfLayout, cropMargins, zoom, zoomX, reflowSize)`. JSON keys: `pdfLayout` (now including `"REFLOW"`), `cropMargins`, `pdfZoom`, `pdfZoomX`, `pdfReflowSize`. An unknown layout name decodes as null (the default).

## 5. Components

| Unit | Module | Responsibility |
|---|---|---|
| `NoCoverPdfFactory` | `:reader` | Wraps Readium's Pdfium factory, `cover()` = null |
| `ReadiumMetadataExtractor` | `:reader` | PDF cover from `PdfDocumentHandle` at 600 px |
| `BookDisplayPrefs` + codec | `:core:model`, `:core:data` | New fields and keys |
| `PageAnalyzer`, `PageLayout` | `:core:model` | §4.1 |
| `ReflowLayouter`, `ReflowPage` | `:core:model` | §4.2 |
| `PdfPageSource.pageLayout(page)`, `renderCropBox(page, widthPx)` | `:reader` | Analysis on the PDF thread, cached; source bitmaps through the LRU |
| `PdfReaderView` | `:app` | Initial / reported zoom, reading-order keys, `REFLOW` view |
| `ReaderViewModel`, `ReaderOverlay` | `:app` | Save zoom and size; third radio, A− / A+ |

## 6. Testing

- **JVM `PageAnalyzer`** on synthetic luminance pages:
  - line and word counts;
  - a detached mark above a line is merged into it;
  - two columns;
  - figure block;
  - paragraph by indent and by gap;
  - grey-paper scan (Otsu);
  - blank page;
  - full-bleed image.
- **JVM `ReflowLayouter`:**
  - no piece beyond the width;
  - word order preserved;
  - paragraph indent;
  - over-wide word scaled;
  - a larger size gives more rows;
  - a figure fits the width.
- **JVM codec:** new keys round-trip; unknown keys are kept; `"REFLOW"` decodes.
- **Compose (Robolectric):**
  - `PdfReaderView` restores zoom and reports `onZoomChanged`;
  - reading-order volume keys;
  - Reflow shows and scrolls;
  - the View menu has three radios and A− / A+ in Reflow.
- **ViewModel:** zoom and size survive a new ViewModel.
- **Device:**
  - importing a PDF with 2654×4329 pt pages ≤ 1.5 s with a ≤ 600 px cover and its title;
  - a generated PDF with text drawn through `PdfDocument` is analysed into the expected number of lines.
- **Measured on the emulator:**
  - import time and PSS for the scanned textbook;
  - reflow screenshots of the Vietnamese scan, the calibre PDF and the LaTeX book;
  - per-page analysis time;
  - reflow text height vs the uncropped page (criterion 3);
  - PSS while scrolling Reflow.

## 7. Out of scope

Hyphenation, table reconstruction, OCR, text selection and search in Reflow, right-to-left scripts, reflow in page-by-page mode, zoom memory in page-by-page mode.
