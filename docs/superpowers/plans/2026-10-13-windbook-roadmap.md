# WindBook Roadmap (plan index)

Spec: `docs/superpowers/specs/2026-10-13-windbook-design.md`

The spec covers eight milestones. Each milestone is shippable on its own (signed APK the owner daily-drives) and gets its **own detailed implementation plan**. That plan is written when the milestone starts, because it builds on the real interfaces the previous milestone produced. Planning all eight in detail now would mean inventing signatures that M1 will change.

| Milestone | Plan file | Depends on | Delivers | Status |
|---|---|---|---|---|
| M1 Core reading loop | `2026-10-13-windbook-m1-core-reading-loop.md` | — | Import → library → read EPUB/PDF → never lose place; CI; signed APK | Done (v0.1.0; phone checklist remains) |
| Look & feel | `2026-10-14-windbook-look-and-feel.md` | M1 | Cream & bronze palette (light + warm dark), Literata, restyled Library/Reader, 4-tab shell, Settings (language, storage, about). Later milestones build their screens to `2026-10-14-windbook-look-and-feel-design.md` §5.6 | Done (branch `look-and-feel`) |
| PDF display | `2026-10-15-windbook-pdf-display.md` | Look & feel | Own PDF renderer (framework PdfRenderer, PDFs no longer opened through Readium), auto-cropped margins, vertical scroll / page by page per book, `book_preferences` (schema 2), EPUB edge spacing, no crash on missing EPUB resources, landscape covers | Done (branch `pdf-display`) |
| PDF comfort | `2026-10-16-windbook-pdf-comfort.md` | PDF display | Light PDF import (no full-size Readium cover), per-book zoom memory with reading-order volume keys. Reflow removed by owner decision on 2026-10-20; legacy choices reopen vertically | Done; Reflow removal on `remove-pdf-reflow` (pending merge) |
| M2 Reading comfort | `…-m2-reading-comfort.md` | M1 reader host, DataStore | Themes, auto theme, fonts, typography, weight boost, brightness, low-flicker dim, warm tint, settings sheet, per-book prefs, orientation, spreads, e-ink mode, break nudge | Plan at M1 done |
| M3 Navigation & flow | `…-m3-navigation.md` | M1, M2 settings sheet | TOC, bookmarks, footnote pop-ups, back-after-link, sessions, status footer + time left, EPUB search, image viewer, auto-scroll, custom tap zones + key bindings | Plan at M2 done |
| M4 Highlights & notes | `…-m4-highlights-notes.md` | M3 sessions/locators | Highlights/underline + notes, per-book list, Quotes hub, tags, Markdown export, quote image | Plan at M3 done |
| M5 Language & learning | `…-m5-language.md` | M4 selection menu | Lookup card, StarDict dictionaries, ML Kit translation, pronounce word, Wikipedia, vocabulary, SM-2 review (words + highlights), parallel bilingual view | Plan at M4 done |
| M6 Library power | `…-m6-library.md` | M1 importer | Collections, watched folders, book details/metadata edit, CBZ, Glance widget, OPDS/Calibre, stats + goals, user fonts | Plan at M5 done (can swap order with M5) |
| M7 Data & sync | `…-m7-data-sync.md` | All data tables | Backup/restore, scheduled backup, WebDAV sync, KOReader progress sync, Readwise push, Anki CSV | Plan at M6 done |
| M8 PDF annotations & Play readiness | `…-m8-pdf-play.md` | androidx.pdf stable (gate) | PDF search/highlights/ink, a11y audit, baseline profiles + benchmarks, AAB + Play listing | Plan at M7 done |

Excluded by decision: read aloud / TTS playback, AI assistant, and the benchmark's ✗ items.

Folder source increment: Tasks 1–7 implemented and independently reviewed; Task 8 integration/device evidence is recorded in the folder checklist; independent whole-branch review and the consolidated fix-wave rereview are complete (Approved at `b4b702c`). EPUB/PDF/TXT source reading is available; TXT is independently reviewed at `98425b3`. On 2026-10-24 the owner paused FB2/CBZ/CBR/MOBI/AZW3 and prioritized keeping the status bar visible during fullscreen reading. Remaining format tasks in `2026-10-21-ebook-format-readers.md` are deferred, not complete.

Daily feedback increment (2026-10-27): recursive EPUB/PDF/TXT folder-task scan, visible scan defaults, title/time-added sorting and paged PDF defaults implemented. PDF text highlights/notes on Android15+ include CRUD/search and exact passage revisit; this is a scoped M4 subset, not completion of broader annotations/export. Mandatory385JVM tests, baseline-only lint, actual API31/36 gates and independent review pass. Local0.1.1 APK awaits owner phone testing/merge decision; EPUB/scan notes and paused readers remain deferred. See `docs/testing/daily-reading-feedback-checklist.md`.

Settings alignment correction (2026-10-27,0.1.2): consistent label column, typography and row spacing; actual Vietnamese/English/large-font verification and varied real EPUB/PDF import/read checks. No broader milestone or paused-reader scope change. See `docs/testing/settings-library-checklist.md`.
