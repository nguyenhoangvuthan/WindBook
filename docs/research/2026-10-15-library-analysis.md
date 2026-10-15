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

- The `language` field is wrong or missing for 117 EPUBs: 103 tagged `en` contain Vietnamese text, and 14 have no tag. WindBook must not rely on it to pick Vietnamese typography.
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

## Consequences for the design

1. **Vietnamese EPUB is now the largest single group (368 books).** Most are calibre conversions with a 1.2 line height, and almost none has a PDF twin. How they read with publisher styles decides whether Vietnamese-friendly spacing belongs in the PDF display work or stays in M2.
2. Reflowing PDFs still helps few books. English prose often has an EPUB twin, technical books break when reflowed, and scans have no text. It stays deferred.
3. Auto-cropped margins, fit to width and continuous vertical scroll help the ~550 typeset and calibre PDFs (87 of them Vietnamese).
4. The 49 scans (median 26 MB, up to 485 MB and 2 141 pages, some with pages over 1 500 pt) need screen-sized rendering with a bounded cache. The current navigator is too slow and too memory-hungry.
5. Comic PDFs and slides want one page at a time, fitted whole.
6. Large books (120 typeset PDFs over 500 pages) make an outline (TOC) and page jump important (M3).
7. Importing 17 GB of books by copying doubles storage. Folder import that reads files in place, prefers the EPUB twin, skips non-book files and ignores the comics archives is worth pulling forward from M6.
