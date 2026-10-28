# Reader market comparison against WindBook 0.1.5

Research date: 2026-10-29. Scope: Android reading of personally owned EPUB/PDF/TXT
files, particularly a large Vietnamese library on a 4 GB phone.

## Method and limits

This is a desk comparison of current first-party product pages, publisher-owned
store listings and documentation against WindBook's implemented 0.1.5 behavior.
It is not a hands-on competitor benchmark. Advertised capabilities establish
feature availability, not reliability, latency, Vietnamese layout quality, or
ease of use. Platform, document and paid-tier restrictions still apply.
No competitor APKs were installed and no books or private library data were uploaded.

WindBook evidence: [README](../../README.md), [current state](../../AGENTS.md),
[reader overlay](../../app/src/main/kotlin/dev/kent/windbook/reader/ReaderScreen.kt),
[Settings](../../app/src/main/kotlin/dev/kent/windbook/settings/SettingsScreen.kt),
[manifest](../../app/src/main/AndroidManifest.xml),
[roadmap](../superpowers/plans/2026-10-13-windbook-roadmap.md), and
[0.1.5 verification](../testing/cover-import-pdf-fit-checklist.md).
The documented CodeGraph executable was unavailable in this checkout; focused
text reads were used instead.

The October 13 benchmark includes aspirational features and milestone labels.
It must not be treated as an inventory of the shipped app. This comparison uses
only current implemented behavior for the WindBook column.

## Assessment

WindBook is a credible personal reader with a working local-library and PDF-note
loop. It is not yet at feature parity with mature general-purpose readers.
Its clearest present advantage is owner control over a deliberately network-free
workflow and its evolution. Most individual reading features are already common.
Claims of better speed, lower total memory use or superior UX are unproven.

## Competitive map

| Product and source | Documented strengths | WindBook disadvantage today | WindBook advantage or fit |
|---|---|---|---|
| [ReadEra](https://play.google.com/store/apps/details?id=org.readera&hl=en), [Premium](https://readera.org/premium) | Free offline reading without ads or registration; many formats; font/spacing controls; collections, authors/series; quotes and notes. Premium adds Google Drive sync, a global quotes hub, custom fonts and page thumbnails. | Reading customization, navigation, library organization and annotation breadth are substantially ahead. | A tailored scope and direct owner control. Offline, ad-free use and reading without permanent book copies are shared capabilities, not unique WindBook advantages. |
| [Moon+ Reader](https://www.moondownload.com/), [FAQ](https://www.moondownload.com/faq.html) | Extensive typography, gestures, themes, dictionaries, auto-scroll, statistics and backup/sync. Pro adds ad-free use, additional PDF annotation features and other tools. | Much less control over the reading surface, navigation and reading workflows. | WindBook has fewer controls to configure and no ad-supported tier. Whether its UI is easier requires a task-based test; Pro also removes ads. |
| [Librera](https://librera.mobi/) | Folder auto-scan, format filters, many formats, customizable fonts/CSS, dictionary integration, text search, TTS and settings/session backup. Free edition is ad-supported; Pro is available. | Format breadth, automatic library discovery, search and customization. | WindBook's explicit scan choices, source/revision handling and source-preserving notes fit the owner's narrow workflow. Folder scanning itself is already present in Librera. |
| [KOReader](https://koreader.rocks/), [user guide](https://koreader.rocks/user_guide/) | Broad formats, advanced reader controls, dictionaries, vocabulary tools and local note/highlight export including Markdown. Targets E Ink devices and also supports Android. | Offline learning tools, reader controls, navigation and portable notes are ahead. | WindBook's Compose interface and verification focus on the owner's phone are a different design focus. This does not establish better usability or Android performance. |
| [PocketBook Reader](https://pocketbook.ch/en-ch/app) | Many formats including protected EPUB/PDF, OPDS, dictionaries, TTS/audio, font/margin settings and cross-device cloud reading. | Protected-book compatibility, language/audio tools, customization and ecosystem reach. | WindBook's existing workflow requires no cloud service, account or store integration. This is a workflow distinction, not evidence that PocketBook cannot read local files offline. |
| [Readest](https://readest.com/) | Cross-platform reading, highlights/notes, parallel reading, typography controls, lookup/translation, TTS and sync. Open source. | Desktop/web continuity, study tools, parallel reading and customization. | WindBook concentrates on the Android phone and source-folder use. Native UI technology alone does not prove lower memory use or faster opening. |
| [BookFusion](https://www.bookfusion.com/features), [Android listing](https://play.google.com/store/apps/details?id=com.bookfusion.android.reader&hl=en_US) | EPUB/PDF reading, offline downloads, cross-device sync, rich highlights including PDF areas/images, exports and Calibre/knowledge-tool integrations. | Annotation portability, scanned-page capture, organization and multi-device workflows. | WindBook keeps the existing workflow entirely on-device and leaves source PDFs unchanged. Account requirements for BookFusion local-only use are uncertain in the consulted page; see below. |
| [Google Play Books](https://support.google.com/googleplay/answer/11012086?hl=en) | Personal EPUB/PDF uploads readable across signed-in devices; bookmarks, notes and highlights. Google documents PDF annotation support for uploads after January 20, 2026. | Account-backed cross-device continuity and annotations across EPUB/PDF. | WindBook reads its selected source folders without a book-upload step. Play Books' documented upload workflow is less aligned with an entirely local folder library. |
| [Adobe Acrobat Reader](https://www.adobe.com/acrobat/mobile/acrobat-reader.html), [Reader capabilities](https://www.adobe.com/acrobat/pdf-reader.html) | Free PDF viewing, annotation, forms and signing; additional editing/conversion through subscriptions. Adobe Scan is a separate application with text recognition. | PDF annotation breadth, forms/signatures and document interchange. OCR belongs to the separately documented Scan workflow, not an assumed free Reader feature. | WindBook combines EPUB/PDF/TXT library reading and reading status in one app; Acrobat is a specialist PDF comparator rather than a full replacement for that library workflow. |

BookFusion's consulted features page is internally inconsistent: one FAQ answer
says an account is required, while its free-plan answer says local mobile use is
available without an account. Do not claim mandatory registration for every
BookFusion workflow until verified in the current Android app. Cloud sync does
involve an account. No exact regional prices or market-share claims are used.

## Current strengths, with appropriate qualifications

- **Network-free application design:** the current app deliberately has no INTERNET
  permission. This is a concrete implementation property, stronger evidence than
  simply promising offline reading. It is not a complete device-level privacy audit
  or proof that every competing reader transmits private books.
- **Source-folder workflow:** recursive EPUB/PDF/TXT discovery, explicit format
  choices, covers before first reading, reconnect/revision guards and rescan
  preservation work. Scanning and reading preserve source files. ReadEra and
  Librera already offer overlapping local-library capabilities.
- **Working PDF reading loop:** paged/vertical modes, cropping, remembered view
  preferences, repeatable Fit page, saved progress, and text-note passage revisit
  are implemented. These bring useful parity rather than market leadership.
- **Vietnamese-oriented verification:** Vietnamese UI/search, TXT encoding support
  including Windows-1258, and Vietnamese PDF selection have project evidence.
  No same-corpus comparison establishes better Vietnamese rendering than rivals.
- **Owner control:** priorities, presentation and fixes can be tailored to one
  reader. This is valuable for personal use; it is not yet a defensible public
  product differentiator by itself.

## Most consequential gaps

| Area | Current WindBook state | Practical effect |
|---|---|---|
| Typography and reading comfort | EPUB largely preserves publisher styles; no complete user font/size/spacing/margin control sheet. System dark behavior exists, but broad reading-theme customization is future work. | Poor source typography cannot be corrected conveniently during a long reading session. |
| Navigation and retrieval | Current overlay lacks a complete TOC/bookmark/in-book search workflow. Shelf title/author search and Notes search do exist. | Finding a chapter, marking several places and retrieving an unannotated passage take more effort. Library search must not be confused with content search. |
| Annotation coverage | PDF text notes/highlights on Android 15+; global Notes search/edit/delete and passage revisit. EPUB and scanned-PDF notes remain deferred. | The same study workflow does not span all three reading formats or older supported Android versions. |
| Data portability | No user-facing backup/restore or note export workflow. Source PDF stays unchanged; notes live in WindBook. The manifest disables Android app backup. | Keeping the original book alone does not preserve notes/progress when reinstalling or moving phones. Source preservation and portable annotations are separate requirements. |
| Library organization | Folder filtering, search, sorting and reading status exist; richer collections/tags/series and metadata editing remain future work. | Folder hierarchy alone is limiting for a large mixed library. |
| Import consistency | Folder sources support EPUB/PDF/TXT; Add books supports EPUB/PDF only. | The same TXT file is supported through one entry point but unavailable through another. |
| Format and ecosystem reach | EPUB/PDF/TXT, Android 12+, no cloud sync or protected-book workflow. | Narrower compatibility and fewer cross-device options. Several format expansions are deliberately paused. |
| Reliability and performance evidence | Existing tests cover many persistence and device cases; physical-phone checks and known PDF gesture/save issues remain. | There is insufficient evidence to claim mature-app reliability or a speed advantage. |

The 48 MB PDF bitmap cache budget is not total process RAM. The last cover-pass
comparison removed an extra read of 80,837,091 bytes, but median elapsed time was
3515 ms before versus 3634 ms after. That is an internal old/new comparison,
not a competitor measurement, and it demonstrates no wall-time improvement.
See the [verification checklist](../testing/cover-import-pdf-fit-checklist.md).

## Suggested priorities for an owner decision

1. **Daily reading control:** scope typography controls and core navigation/search.
   They improve every reading session and close gaps against the closest local
   readers, especially ReadEra and Moon+.
2. **Preserve reading work:** scope offline backup/restore and note export before
   accumulating a large annotation collection. Source books already surviving
   does not solve preservation of the app's reading data.
3. **Unify the study workflow:** consider EPUB notes and consistent supported-file
   entry points. Scanned-page annotation would need a separate scoped decision.
4. **Measure and resolve existing friction:** phone tests of PDF panning, rapid
   exits/preference persistence, large-folder scans and cover preparation. Compare
   matched documents on the same hardware before making performance claims.
5. **Organize a large library:** collections/tags/series after basic reading and
   data preservation are satisfactory; order can change with owner feedback.

These are research recommendations, not implementation authorization or an
approved milestone change. TTS/audio and an AI assistant remain excluded by the
current roadmap. Paused reader formats and previously removed features are not
reopened by this comparison. Cross-device sync is a market gap but is not an
automatic priority for the current single-phone, offline product.

## What would substantiate a competitive advantage

A future approved benchmark could compare WindBook, ReadEra, Moon+ and KOReader
on the same phone and lawful test corpus: scan completion and first useful cover,
cold book open, page-turn latency, peak/steady PSS, resume after process death,
Vietnamese text/footnotes, annotation revisit and backup restoration. Record app
versions, settings, trials and paid tiers. Until then, avoid rankings such as
fastest, lightest, best Vietnamese support, or easiest interface.
