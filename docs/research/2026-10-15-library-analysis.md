# Owner's library: what WindBook must display well

Date: 2026-10-15 (re-measured after the library was reorganised) · Source: `Library/00_Catalog/metadata.json` on the owner's external drive (2 376 records, every `current_path` present), plus `pdfinfo` for the 83 PDFs the catalog has no PDF metadata for and the EPUB stylesheets read in place. This note keeps aggregate numbers only, not titles.

Language is measured from each record's `text_sample`: the share of letters that carry Vietnamese diacritics (ă â đ ê ô ơ ư or a tone mark). The split is clean: 294 EPUBs score 0, the Vietnamese ones score 0.20–0.33, and only 6 fall in between (counted as mixed).

## Composition

19.6 GB in 2 376 files. Books (PDF, EPUB, MOBI, AZW3) are 17.3 GB, about 1 100 distinct titles outside the comics folder.

| Group | Files | Notes |
|---|---|---|
| EPUB | 672 (3.3 GB) | **368 Vietnamese (55 %), 294 English (44 %)**, 6 mixed, 4 without text. Median 1.2 MB, 90th percentile 11.7 MB, max 102 MB. |
| PDF | 703 (14.0 GB) | Five kinds, see below. |
| MOBI / AZW3 | 3 / 1 | Not supported. |
| Comics folder | 1 075 | 854 PNG + 85 JPG (image folders), 27 ZIP, 22 RAR, 87 PDF. **Archives and image folders are storage only. The owner does not want them scanned into the app.** |
| Not books | 9 | `.xmind`, `.ppt`, `.xlsx`, `.sql`, `.docx`, `.webp`. |

### EPUB

- When measured, the catalog's `language` field was wrong or missing for 117 EPUBs: 103 tagged `en` contained Vietnamese text, and 14 had no tag. The owner is correcting the catalog labels. The `dc:language` inside the EPUB files stays as published, so WindBook must not rely on it to pick Vietnamese typography.
- **100 EPUBs (15 %, 71 of them Vietnamese) reference a file that is not in the book**, most often a stylesheet. On the emulator, opening such a book crashed WindBook (see the PDF display spec, §3.8).
- 324 of the 368 Vietnamese EPUBs were produced by calibre and 38 by Sigil. 105 embed their own fonts (English: 11 of 294).
- Body line height set by the publisher stylesheet (most common value in the book):

| Line height | Vietnamese | English |
|---|---|---|
| < 1.3 (almost all calibre's 1.2) | 257 (70 %) | 268 (91 %) |
| 1.3–1.5 | 24 | 3 |
| ≥ 1.5 | 50 | 5 |
| Not set or `normal` | 37 | 18 |

### PDF

| Kind | Files | Language (measured) | Pages: median / 90th pct / max · > 500 | Size: median / 90th pct / max | Page size |
|---|---|---|---|---|---|
| Typeset book with text (InDesign, LaTeX, Acrobat, AH Formatter, Word…) | 460 | 357 en, 57 vi, 4 mixed, 42 too little text | 339 / 671 / 26 493 · 120 | 8 / 31 / 218 MB | Median width 504 pt. Letter 70, 504×720 47, A4 38, 439×666 35 |
| calibre conversion with text | 91 | 51 en, 30 vi, 10 other/none | 274 / 725 / 1 885 · 20 | 6 / 29 / 120 MB | Letter 68, A4 19 |
| Scan, no extractable text | 49 | not measurable (30 have Vietnamese titles) | 300 / 664 / 2 141 · 9 | 26 / 206 / 485 MB | Mostly A4/Letter. 6 have pages over 1 500 pt (up to 2654×4525). 8 files exceed 100 MB. |
| Landscape (slides) | 16 | 10 en, 2 vi, 1 mixed, 3 none | 134 / 777 / 878 · 3 | 8 / 34 / 80 MB | 720×405, 720×540, 960×540… |
| Comic (Doraemon) | 87 | image only | 182 / 192 / 214 · 0 | 29 / 42 / 52 MB | 48 English at Letter, 39 Vietnamese at 638×1060 |

- 8 PDFs carry permission restrictions (no open password).

### Duplicates

- **139 EPUBs also exist as a PDF of the same title**: 124 English (42 % of English EPUBs), 13 Vietnamese, 2 without text. The PDF twins are 106 typeset books and 31 calibre conversions.
- The catalog already moved 316 duplicate copies to an archive outside `Library/`. Byte-identical files left: 5 groups, 14 files, all images or slides, no books.
- Same title, same format (editions, translations, split volumes): 31 EPUB groups (62 files), 21 PDF groups (43 files).

## Measured on the emulator (Android 16, 1080×2400, 420 dpi), 2026-10-15

| Sample | Before the PDF work |
|---|---|
| Technical EPUB | Comfortable. Publisher styles keep code and lists intact. The first line sits at the very top edge. |
| LaTeX PDF (504×657 pt) | The whole page fits. Body text is about 7 dp. Wide white margins. |
| calibre Vietnamese PDF (Letter) | Body text is about 5 dp, not readable without zoom. |
| Scanned textbook (24 MB) | **20 s to first page, 1.9 GB PSS** (Readium PDFium navigator). Wide scan margins. |
| Slides (landscape) | A small page in the middle of a portrait screen. |
| Double-tap zoom (any PDF) | Zooms about 1.8× but crops both sides, so every line needs a horizontal pan. |

## After the PDF display work (same emulator), 2026-10-15

| Sample | After |
|---|---|
| Technical EPUB | First line clear of the top edge. |
| Vietnamese EPUB that links a missing stylesheet | Opens and reads. Before: the app crashed on open. |
| LaTeX PDF (504×657 pt) | Fills the width with margins cropped. Body text is 1.26× the uncropped page (the book's text block is 75 % of the page width, so cropping cannot gain more without cutting text). |
| calibre Vietnamese PDF (Letter) | Body text 1.20× the uncropped page. Still small; reading it comfortably needs zoom or reflow. |
| Scanned textbook (24 MB, 2654×4329 pt pages) | **First page 1.3 s after the tap, 195 MB PSS; 267 MB after scrolling 51 pages.** Cropped margins appear about 3.5 s after opening. |
| Slides (landscape) | One whole slide at a time on a dark surround; cover shown whole in the library. |
| Comic PDF | Switched once to page by page; reopens that way. |
| Double-tap zoom 2.5× | Scrolls sideways while zoomed; text turns sharp once the zoom settles. |

Why the scan was slow before: Readium's PDF parser renders a cover at the page's full device-pixel size when it opens a PDF (≈ 15 500 × 25 000 px for this scan). WindBook now opens PDFs with the framework `PdfRenderer` only. The importer still extracts PDF metadata through Readium, so importing a large scan still pays that cost.

## Consequences for the design

1. **Vietnamese EPUB is now the largest single group (368 books).** Most are calibre conversions with a 1.2 line height, and almost none has a PDF twin. Four of them were checked on the emulator with publisher styles (1.2 without and with an embedded font, 1.5, unset): stacked diacritics never touch the line above. Line-height controls therefore stay in M2 (F2.4).
2. **An EPUB that references a missing file must not crash the app.** WindBook has no INTERNET permission, yet Readium tries to fetch such files over HTTP.
3. Reflowing PDFs still helps few books. English prose often has an EPUB twin, technical books break when reflowed, and scans have no text. It stays deferred.
4. Auto-cropped margins, fit to width and continuous vertical scroll help the ~550 typeset and calibre PDFs (87 of them Vietnamese).
5. The 49 scans (median 26 MB, up to 485 MB and 2 141 pages, some with pages over 1 500 pt) need screen-sized rendering with a bounded cache. The current navigator is too slow and too memory-hungry.
6. Comic PDFs and slides want one page at a time, fitted whole.
7. Large books (120 typeset PDFs over 500 pages) make an outline (TOC) and page jump important (M3).
8. Importing 17 GB of books by copying doubles storage. Folder import that reads files in place, prefers the EPUB twin, skips non-book files and ignores the comics archives is worth pulling forward from M6.
