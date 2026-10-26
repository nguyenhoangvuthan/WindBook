# Daily reading feedback — scoped design

Status: owner requested folder scanning/settings, page-by-page default and time/title sorting on 2026-10-26; owner explicitly confirmed PDF-text selection → note → save/edit in Notes → exact passage revisit. Current execution continues that authorized scope. Existing no-dependency, bilingual, private/offline and no-push rules apply.

## Folder workflow

Treat a folder as a root task: scan every descendant matching selected EPUB/PDF/TXT, with EPUB/PDF primary. No total-file/depth cap or permanent source copy. The owner's `/home/kent/Library` is an example of the root-task semantics; Android can only access folders on its device under an actual tree grant. Do not imply that a Linux host path grants phone access.

Show folder scan settings in Settings and before saved folders/results on the folder screen: EPUB/PDF/TXT only, recursive enabled by default, remembered choices. Keep existing folder choices; Settings supplies defaults for new selections. Typing a device path matching an existing grant can reuse it; otherwise the scan action launches the system tree picker positioned at a recognized device path. After that grant, start the recursive scan automatically. Typed paths cannot bypass permissions. Expose incomplete/error status and retain delivered entries. A single bad provider row should not silently hide remaining valid siblings; do not remove identity/grant checks to make a count appear successful.

Add explicit folder-result sorting by book title (A–Z/Z–A) and time added (newest/oldest), with stable ties and readable labels. Existing library title/date options remain available and must be discoverable. Time means time added to WindBook for this increment, clearly labeled rather than guessing file creation dates.

## PDF default

For no explicit per-book choice, open every PDF page by page, including portrait, mixed and unknown page sizes. Preserve explicit saved Vertical/Paged choices and legacy Reflow decoding. EPUB/TXT already use paginated publication navigation. Keep the recently fixed always-visible status bar and safe insets.

## PDF text notes only

Bring the confirmed text-PDF part of the October21 notes design into implementation now. On API35+ (owner phone API36), long-press a word, adjust handles on one page, use the compact horizontal Highlight color / Note popup, save only after persistence succeeds. Retain the previously confirmed gold/green/blue/pink/purple colors and optional note body; no overlapping second toolbar. Existing highlights reopen by ID for edit/color/delete; blank notes cannot save. Draft survives rotation; cancel does not create an annotation; failed save retains draft and retry.

Notes tab lists persisted PDF text highlights/notes with book/page/quote/body, search, edit/delete and navigation to the selected source. Revisit must work repeatedly for an already-open book, reveal the correct full-page normalized rectangles, temporarily fit/show a cropped-out anchor, and keep normal reading progress/locators intact. Source revision changes must never inherit anchors. Soft deletion retains notes and hides them from active lists; notes count as durable reading data in revision/dedup policy.

No EPUB annotations, scanned-PDF/region notes, OCR, export, file annotation rewriting or new dependency in this increment. API31–34 PDF reading still works and reports text selection unavailable. Empty/no-text selection does not create a guessed region/page note.

Persist versioned full-page coordinates, not bitmap pixels. Guard finite in-range rectangles and dimensions; bound quote/selection state and avoid whole-document text extraction. Existing PDF dedicated worker owns native selection calls; keep one native call plus newest pending drag and discard stale page/session/geometry results. Current database is v6: additive v7 migration, historical exports1–6 unchanged.

## Completion evidence

Meaningful RED/GREEN tests, mandatory full CI/lint and debug APK, API36 synthetic actual selection/save/restart/Notes/revisit gates plus crop/zoom/layout/rotation and scanning/settings/sort UI. API31 reading guard/migration behavior must remain valid. Independently review the final change before handoff; no merge or push. Keep concise checkpoint so interrupted work resumes without historical rediscovery.
