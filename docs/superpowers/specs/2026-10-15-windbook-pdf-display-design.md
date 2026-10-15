# WindBook – PDF display that fits the phone (and EPUB edge spacing)

Date: 2026-10-15 · Status: approved (Vietnamese EPUB check and missing-resource fix added after the library was re-measured)
Inputs: `docs/research/2026-10-15-library-analysis.md`, `docs/superpowers/specs/2026-10-13-windbook-design.md`, `docs/superpowers/specs/2026-10-14-windbook-look-and-feel-design.md`

## 1. Purpose

The owner reads on a 6.8" phone. The library (re-measured 2026-10-15) holds 703 PDFs and 672 EPUBs. The PDFs are 460 typeset books, 91 calibre conversions, 49 scans, 16 slide decks and 87 comics. Of the EPUBs, 368 are Vietnamese and 294 English. Today the Readium PDFium navigator shows each PDF as a whole page. The text is 5–7 dp, the margins are wide, double-tap zoom forces horizontal panning, and a 24 MB scan takes 20 s and 1.9 GB of memory to open. Separately, an EPUB that references a file missing from the book crashes the app, and about 100 EPUBs (15 %) do that.

This spec replaces PDF display with WindBook's own renderer and adds three small EPUB/library fixes: EPUB edge spacing, no crash on missing EPUB resources, and landscape covers.

**Success criteria**
1. Opening a portrait PDF shows its content at the full screen width with white margins cropped. On the measured LaTeX book, body text is at least 30 % larger than before.
2. Reading never needs horizontal panning at the default zoom.
3. The 24 MB scanned textbook shows its first page in ≤ 1.5 s and stays under 400 MB PSS after scrolling 30 pages (emulator `Neo3GT_API_36` and the phone).
4. Comic PDFs and slides can be read one whole page at a time, and the book remembers that choice.
5. Saved reading positions of PDFs opened before this change still restore to the same page.
6. An EPUB that references a file missing from the book, or a web address, opens and reads normally. The missing file is simply not shown, and the app never crashes.

## 2. Decisions taken in brainstorming

| Question | Decision |
|---|---|
| Scope | A + B together: smart defaults **and** WindBook's own PDF renderer with margin cropping. PDF reflow (C) is deferred. |
| Renderer | **`android.graphics.pdf.PdfRenderer`** (framework, PDFium inside). It renders any page region through a transform `Matrix` and **closes each page after rendering**. No new dependency. Chosen over PdfiumAndroid 1.9.8 after a measured spike (§2.1). |
| Per-book choices | Stored in the database table `book_preferences` that the main spec planned for M2. It is pulled forward, with Room schema v1 → v2. |
| Comic archives and image folders | Out of scope. Storage only, never scanned into the app. |
| EPUB | Keep publisher styles, including line height. Only add top/bottom spacing and cutout safety. Re-checked against the Vietnamese EPUBs (§2.2). |
| Network | WindBook stays offline (no INTERNET permission). Readium gets an HTTP client that always fails softly (§3.8). |

### 2.1 Renderer spike (emulator `Neo3GT_API_36`, 2026-10-15)

Each page was rendered at 1080 px wide, 40 pages per book.

| Book | PdfiumAndroid 1.9.8 (Readium's engine) | Framework `PdfRenderer` |
|---|---|---|
| Scanned textbook, 24 MB | First page 213 ms; **PSS grows ~11 MB per page, 566 MB after 40 pages**. No public `closePage`, so pages stay open until the document closes. | First page 223 ms; **PSS flat at ~63 MB** |
| LaTeX book | 75 MB after 40 pages | 61 MB after 40 pages |
| Owner-restricted PDF (RC4, copy disallowed, no open password) | — | Opens and renders (189 ms) |

PdfiumAndroid would exhaust memory on a 300+ page scan, so the framework renderer is used. PdfiumAndroid stays only as Readium's internal dependency. The M3 outline can read bookmarks through it without rendering.

### 2.2 Vietnamese EPUB check (emulator `Neo3GT_API_36`, 2026-10-15)

Of the 368 Vietnamese EPUBs, 257 (70 %) set a body line height below 1.3, almost all calibre's 1.2, and 105 embed their own font. Four real books were opened with publisher styles, one per group:

| Publisher line height | Result |
|---|---|
| 1.2, no embedded font | Comfortable. Stacked diacritics (ầ, ễ, ỹ) clear the line above. |
| 1.2, embedded font | Denser but readable. No mark touches a descender. **The original file crashed the app** (§3.8). |
| 1.5 | Airy. Justified lines show wide word gaps (publisher choice). |
| Not set | Readable. The first line sits at the very top edge (fixed by §3.6). |

Line height is therefore a reading preference, not a rendering fault. It stays with the M2 typography controls (main spec F2.4: line height 1.2–2.0, publisher-styles toggle). This work only adds the edge spacing in §3.6.

## 3. Behaviour

### 3.1 PDF view modes

| Mode | Default for | Layout | Gestures |
|---|---|---|---|
| **Vertical scroll** | Books whose first pages are portrait (width < height) | Pages stacked top to bottom, each fitted to the screen width, 8 dp gap, paper background. Margins cropped (§3.2). | Vertical fling. Pinch zoom 1×–4× anchored at the fingers, with panning while zoomed. Double-tap toggles 1× ↔ 2.5× at the tap point. A single tap toggles the overlay. Volume/page keys scroll by 90 % of the viewport. |
| **Page by page** | Books whose first pages are landscape (slides) | One page at a time, fitted whole (contain), dark surround `#141210`. Margins not cropped by default. | Swipe or edge tap (outer 25 %) turns the page. The middle 50 % toggles the overlay. Double-tap zooms 2.5× at the tap point. Volume/page keys turn the page. |

- **The default is computed from the median aspect of pages 1–5.** Comic PDFs are portrait too, so they open in vertical scroll. The owner switches them once (§3.4) and the choice is remembered.
- **Readium's PDF navigator is no longer used for display.** Readium still opens the publication: metadata, cover and `positions()` for locators.

### 3.2 Margin cropping

The crop box is computed per book, then adjusted per page:

1. Sample up to 12 pages per parity (odd/even), spread evenly, skipping the first 4 and the last 2 pages. Books with ≤ 8 pages sample every page.
2. Render each sample at 300 px width, convert to luminance, and count "ink" pixels (luminance < 200/255).
3. A row/column holds content if it has ≥ max(2, 0.3 % of the line length) ink pixels. Isolated scan specks therefore do not count. The page content box is the first/last content row and column, padded by 1.5 % of the page size on each side.
4. Book box per parity = 10th percentile of the left/top edges and 90th percentile of the right/bottom edges of the sample boxes.
5. Safety rules:
   - Never crop more than 25 % per side.
   - A page whose own content box (computed when it is first rendered at analysis size) reaches outside the book box is shown with the union of both. Content is never cut.
   - A near-blank sample (content < 40 % of either dimension) is ignored.
6. Cropping is on by default in vertical scroll and off in page-by-page mode. It can be switched off per book (§3.4).

Analysis runs off the main thread after the first page is shown. Until it finishes, pages render uncropped. The view then re-lays out once, keeping the current page and its relative offset.

### 3.3 Rendering and memory

- All `PdfRenderer` calls run on one dedicated single-thread dispatcher: it allows one open page at a time and is not thread-safe. The document is opened from a `ParcelFileDescriptor` and closed with the session. Every render opens the page, renders it and closes it again.
- **Base bitmaps:** each page is rendered at the viewport width in pixels, with a `Matrix` (scale + translate) that maps its crop box onto the bitmap. `PdfRenderer` requires `ARGB_8888`, and bitmaps are filled white before rendering because PDF pages are transparent.
- **Zoomed detail:** after a zoom gesture settles (150 ms), only the visible part of each visible page is re-rendered at zoomed resolution (a viewport-sized tile) and drawn over the scaled base bitmap.
- **Cache:** an LRU of bitmaps bounded at min(48 MB, maxMemory / 6). Pages within ±2 of the viewport are prefetched. Renders for pages scrolled out of range are cancelled.
- A page that fails to render shows a grey box with "This page can't be shown". Its neighbours are unaffected.
- A document that fails to open, including one with an open password, uses the existing `CANNOT_OPEN` error screen.

### 3.4 Reader overlay: view menu (PDF only)

The top bar gains a **View** icon button, shown only for PDFs. It opens a menu with three items:
- Vertical scroll (radio)
- Page by page (radio)
- Crop margins (checkbox)

A change applies immediately, keeps the current page, and is saved to `book_preferences` for that book.

### 3.5 Positions and progress

- Page index `i` (0-based) maps to `publication.positions()[i]`, encoded with the existing locator JSON. Saved data keeps the same format as M1.
- Restoring reads `locations.position` (1-based) from the saved locator. A missing or invalid value opens page 0.
- Progress uses the existing `readingProgression(position = i + 1, positionCount = pageCount)`. "Mark finished" therefore works on the last page.
- Chapter title: none for PDFs yet. The outline arrives in M3.

### 3.6 EPUB edge spacing

The EPUB navigator container gets top and bottom padding of max(16 dp, display-cutout inset) on each side. Text never touches the screen edge or sits under a punch-hole camera. Publisher styles, font and line height stay as they are (M2 adds user controls).

### 3.7 Library: landscape covers

A cover image whose width exceeds its height is drawn whole (`ContentScale.Fit`) on the generated paper tint, instead of being cropped to 2:3.

### 3.8 EPUB: missing and remote resources

Readium opens an EPUB as the book's ZIP plus an HTTP container for absolute `http(s)` URLs. When a chapter links a file that is not in the ZIP (100 EPUBs in the library do, usually a stylesheet), the EPUB navigator asks for it by absolute URL. That request reaches Readium's `DefaultHttpClient`. WindBook has no INTERNET permission, so the DNS lookup throws `SecurityException`. Readium does not catch that, and the app crashes ("WindBook keeps stopping").

WindBook therefore passes Readium an `OfflineHttpClient` (in `:reader`, both to the `AssetRetriever` and to the publication parser). Every request fails at once with `HttpError.IO`, without touching the network. The navigator then treats the file as missing, as a browser would. WindBook keeps not requesting INTERNET: the library is local, and a reader that fetches remote resources from books would leak what the owner reads.

## 4. Data

| Table | Columns | Notes |
|---|---|---|
| `book_preferences` | `book_id` TEXT PK → `books.id` ON DELETE CASCADE, `json` TEXT NOT NULL, `updated_at` INTEGER NOT NULL | Same shape as main spec §4. Room DB version 1 → 2 with an explicit `Migration(1, 2)` (CREATE TABLE only, no data changes). |

- JSON is `{"pdfLayout":"VERTICAL"|"PAGED"|null,"cropMargins":true|false|null}`. A null field means "use the default" (§3.1, §3.2).
- Unknown keys are preserved on write, so M2 can add fields without another migration.
- Model: `BookDisplayPrefs(pdfLayout: PdfLayout? = null, cropMargins: Boolean? = null)` in `:core:model`.
- Repository: `observeDisplayPrefs(bookId): Flow<BookDisplayPrefs>` and `setDisplayPrefs(bookId, prefs)`.

## 5. Components

| Unit | Module | Responsibility |
|---|---|---|
| `PdfLayout`, `BookDisplayPrefs`, `defaultPdfLayout(pageAspects)` | `:core:model` | Pure types and the default rule |
| `MarginDetector` | `:core:model` | Pure Kotlin. Input: luminance `ByteArray`, width, height. Output: content box. Also `bookCropBox(samples)` (percentiles, clamps). |
| `BookPreferencesDao`, entity, `Migration(1, 2)`, repository methods | `:core:data` | Persistence |
| `OfflineHttpClient` | `:reader` | Readium `HttpClient` that fails every request softly. `ReadiumServices` uses it instead of `DefaultHttpClient`. |
| `PdfDocumentHandle` | `:reader` | Opens a framework `PdfRenderer` on the single-thread dispatcher. Exposes page count, page sizes (points) and `render(page, crop, widthPx, region?)` returning a Bitmap. |
| `PdfReaderSession : ReaderSession` | `:reader` | Holds the Readium publication and `PdfDocumentHandle`. Exposes `pageCount`, `locatorJsonFor(page)`, `pageOf(locatorJson)`. `ReaderEngine` creates it for PDFs. |
| `PdfPageCache` | `:reader` | Bitmap LRU, prefetch and cancellation |
| `PdfReaderView` | `:app` (`reader/pdf/`) | Compose. `LazyColumn` (vertical) or `HorizontalPager` (paged). Zoom/pan state, tap and key handling, reports page changes. |
| `ReaderScreen` / `ReaderViewModel` | `:app` | EPUB → existing `ReaderHostFragment`. PDF → `PdfReaderView`. The ViewModel loads and saves `BookDisplayPrefs`, and the overlay shows the View menu. |

`ReaderHostFragment` keeps hosting EPUB only. Its PDF branch is removed.

## 6. Testing

- **Unit (JVM):**
  - `MarginDetector` on synthetic luminance arrays: a text block with margins, specks only, scan noise around a block, a full-bleed image, a near-blank page, odd/even books with mirrored margins, and the 25 % clamp.
  - `defaultPdfLayout` with portrait, landscape and mixed pages.
  - `BookDisplayPrefs` JSON round trip, preserving unknown keys.
  - Page ↔ locator mapping, including the M1 locator JSON format and invalid input.
- **Room:** migration test 1 → 2 with `MigrationTestHelper`. Existing rows survive and the cascade delete works.
- **Robolectric Compose:**
  - The View menu shows only for PDF.
  - Choosing "Page by page" calls back.
  - The crop checkbox reflects the state.
- **Instrumented (emulator):**
  - A Readium publication opened by `ReadiumServices` answers a request for a missing or remote resource with a failure, not an exception. Written before the fix and shown failing first.
  - Render a generated PDF page with known margins and check the cropped bitmap's content box.
  - `PdfReaderView` restores a saved page.
- **Manual / measured:** the 7 sample books from the research note, before/after screenshots, and time to first page and PSS for the scanned book (criterion 3). The Vietnamese EPUB with a missing stylesheet opens and reads (criterion 6).

## 7. Out of scope

PDF reflow, PDF night mode (page colour filters), outline/TOC, search, text selection and highlights (M3/M8), RTL page order for manga, comic archives (CBZ/CBR) and image folders, folder import (separate spec).
