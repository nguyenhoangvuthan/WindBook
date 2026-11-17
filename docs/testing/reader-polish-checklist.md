# Reader polish checklist — 2026-11-17

Spec `docs/superpowers/specs/2026-11-17-reader-polish-design.md`, plan
`docs/superpowers/plans/2026-11-17-reader-polish.md`, branch `reader-polish`.
Device: emulator `Medium_Phone_API_37.0` (1080×2400, 420 dpi) with one synthetic
Vietnamese EPUB (own text, not an owner book). Screenshots stay in the git-ignored
`.superpowers/checks/ui-compare/after/`. Not checked on the physical phone.

| # | Item | Result |
|---|---|---|
| 1 | Reader slider thumb keeps a side gutter (≥16 dp from the edge) | Pass (EN, VI, VI at 130 % font) |
| 2 | Footer shows chapter title left, percentage right; long title ellipsizes | Pass (unit tests for blank/long title; device shows "Chương 1: Khởi hành" + 0 %) |
| 3 | PDF footer keeps `Page n / N` | Unit test only; not re-run on a device PDF |
| 4 | `Aa` button in the top bar for EPUB/TXT, none for PDF, not duplicated in ⋮ | Pass on device (EPUB); PDF absence by unit test |
| 5 | Aa sheet: segmented scope with check mark, one hint line, numbers beside sliders, colour swatches | Pass (EN, VI, VI at 130 %) |
| 6 | Placeholder cover is a warm tint | Pass (clay tint on the shelf and the continue card) |
| 7 | "Book folders" text aligned with the "Library" title | Pass (EN and VI) |
| 8 | Size slider 80–200 % in 10 % steps with the number shown | Unit test; the slider was not dragged on device |
| 9 | Line-height reset button only when a value is set | Unit test; not exercised on device |

Observed but out of scope: at 130 % font the "Finished/Đã đọc xong" filter chip is cut at
the right edge (the row scrolls horizontally).

Known flake: `ReaderViewModelPdfTest.legacyReflowBookReopensVerticallyAtTheSavedPageEvenForSlides`
failed once in a full run with `UncaughtExceptionsBeforeTest` (an exception leaked from an
earlier test) and passed alone and on the rerun; not caused by this branch.
