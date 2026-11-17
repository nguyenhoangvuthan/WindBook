# Ten-point polish checklist — 2026-11-17

Spec `docs/superpowers/specs/2026-11-17-ten-point-polish-design.md`, plan
`docs/superpowers/plans/2026-11-17-ten-point-polish.md`, branch `ten-point-polish` (from `reader-polish`).
Device: emulator `Medium_Phone_API_37.0` (1080×2400, 420 dpi). Books: the owner's book A, both book B editions (copies pushed to the emulator, source
library untouched) and one small PDF. Screenshots show book pages, so they stay in the git-ignored
`.superpowers/checks/ui-compare/after/`. Not checked on the physical phone.

| # | Item | Result |
|---|---|---|
| 1 | Format chips EPUB/PDF appear only for a mixed library; tapping PDF lists only the PDF | Pass on device (5 books, EPUB+PDF) |
| 2 | Selected format that disappears does not hide the library | Unit test only (ViewModel) |
| 3 | Paged PDF: one edge tap = one page; two/three quick edge taps = two/three pages, no zoom | Pass on device (`input tap` sequences; page 10→12→15) |
| 4 | Paged PDF: double tap in the middle still zooms; single middle tap still toggles the menu | Pass on device |
| 5 | Edge tap no longer waits for the double-tap timeout | Unit test (clock-controlled); not timed on device |
| 6 | EPUB clean layout is the default: justified, line height 1.5, paragraph spacing | Pass on device (book A front matter) |
| 7 | book A table of contents: the U+FFFD leaders wrap inside the column and no longer overlap | Pass on device. The characters are in the file, so they still show as �; only the overflow is fixed |
| 8 | book B cover made of 8 stacked image tiles | **Not fixed.** The tiles were a 2×4 grid in the source PDF; stacked in a column they still look broken. Clean layout only removes the paragraph gaps |
| 9 | Aa sheet: "Keep original formatting" off by default, switching on reflows the open chapter in place and keeps the position | Pass on device (book B first pages) |
| 10 | Line-height slider changes spacing in clean layout, disabled when keeping the original | Unit test; not dragged on device |
| 11 | Notes: Vocabulary button aligned with the title, pill search field | Unit test (alignment); pill shape is visual only and was not re-captured |
| 12 | Settings: Reading appearance row aligned with the other rows | Unit test |
| 13 | Dark theme: dimmed warm placeholder covers | Unit test (luminance and warmth); not re-captured on device |
| 14 | Empty Library shows Choose files / Choose folder directly | Unit tests (EN, VI); not re-captured on device |
| 15 | One-time tap-zone hint on the first reader open | Pass on device (card shown on the first PDF open). Auto-dismiss and persistence by unit tests |

Open items for the owner's phone: the real books (Vietnamese EPUBs, fonts that "looked wrong"), the
other apps' comparison, the 130 % font size on the new format row, TalkBack, and the swatch/pill visuals.

Deviations from the plan: the tap classifier owns no clock (the caller's delay is the double-tap
window); edge-tap page turns pause swiping while they animate, because the pager otherwise swallows
the next touch as "stop scrolling" and a quick second tap was lost; two 0.1.8 tests that described the
old empty-library flow were rewritten for the new one.

Known flake: `ReaderViewModelPdfTest.legacyReflowBookReopensVerticallyAtTheSavedPageEvenForSlides`
failed with `UncaughtExceptionsBeforeTest` in the first full run twice and passed on the rerun each time.
