# Android Reader Feature Benchmark (2026)

Date: 2026-10-13 · Companion to `2026-10-13-windbook-validation.md`
Goal: take the best feature from every leading reader, keep only what helps a real reader, and fit it to WindBook's constraints. The constraints are: personal use first, then Play Store; **Nubia Neo 3 GT** (AMOLED 120 Hz, Unisoc T9100, no Gemini Nano); Readium engine.

Apps compared: **Kindle**, **Google Play Books**, **Kobo**, **Moon+ Reader**, **ReadEra**, **Librera**, **KOReader**, **Readest**, **Readwise Reader**, **ElevenReader**, plus language readers (**Beelinguapp**, **LingQ**).

Legend for the WindBook column: **v1** = first personal build · **v1.x** = once v1 is daily-driven · **v2** = Play Store / later · **✗** = deliberately skipped.

---

## 0. Common vs. specific features

### 0.1 Common features (table stakes): what most apps already have

Codes: **KN** Kindle · **PB** Play Books · **KB** Kobo · **M+** Moon+ · **RE** ReadEra · **LB** Librera · **KO** KOReader · **RD** Readest · **RW** Readwise Reader · **11** ElevenReader
✓ = yes · ~ = partial / paid tier / plugin / region-limited · ✗ = no

| Feature | KN | PB | KB | M+ | RE | LB | KO | RD | RW | 11 | WindBook |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Open your own EPUB/PDF | ~ | ✓ | ~ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | **v1** |
| Day / sepia / night themes | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ~ | **v1** |
| Font, size, spacing, margins | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ~ | **v1** |
| Bookmarks | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ~ | **v1** |
| Highlight + note | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ~ | **v1** |
| Search inside book | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ~ | **v1** |
| Dictionary lookup | ✓ | ✓ | ✓ | ✓ | ~ | ✓ | ✓ | ✓ | ✓ | ✗ | **v1** (offline) |
| Translate selection | ✓ | ✓ | ✗ | ✓ | ~ | ✓ | ✓ | ✓ | ✓ | ✗ | **v1** (offline EN↔VI) |
| Read aloud (TTS) | ~ | ✓ | ~ | ✓ | ✓ | ✓ | ~ | ✓ | ✓ | ✓ | v1.x |
| Sync across devices | ✓ | ✓ | ✓ | ~ | ~ | ~ | ✓ | ✓ | ✓ | ✓ | v2 |
| Reading stats | ~ | ✗ | ✓ | ✓ | ~ | ✗ | ✓ | ~ | ✗ | ✗ | v1.x (data in v1) |
| No account needed | ✗ | ✗ | ✗ | ✓ | ✓ | ✓ | ✓ | ✓ | ✗ | ✗ | **v1** |
| Fully offline | ~ | ~ | ~ | ✓ | ✓ | ✓ | ✓ | ✓ | ✗ | ~ | **v1** |
| No ads, free | ✓ | ✓ | ✓ | ✗ | ✓ | ~ | ✓ | ✓ | ✗ | ~ | **v1** |
| AI assistant | ~ | ✗ | ✗ | ✗ | ✗ | ✗ | ~ | ✗ | ✓ | ✓ | v2 |

**Takeaway:** the first 8 rows are table stakes, and WindBook v1 must have them just to be considered. They don't differentiate it.

### 0.2 Specific features: what only each app does

| App | Signature features nobody else matches |
|---|---|
| **Kindle** | **X-Ray** (characters/terms across the book) · **Word Wise** (inline hints over hard words) · **Ask this Book** (spoiler-safe AI) · **Recaps / Story So Far** · Popular Highlights · Whispersync for Voice (ebook ↔ Audible) |
| **Play Books** | **Bubble Zoom** (comic panels) · "flowing text" for scanned books · Family Library |
| **Kobo** | **Reading Life** stats + awards · **TypeGenius** fine font weight/sharpness (its Pocket read-later integration ended when Mozilla shut Pocket down in 2025) |
| **Moon+** | Extreme gesture/tap customisation · several **auto-scroll** styles · **shake to speak** · 3D page curl · auto dual page on foldables · auto-push highlights to Readwise |
| **ReadEra** | **Quotes hub** for all books · auto-finds documents on the device · To read / Have read / Favorites lists · totally free, no ads, no account |
| **Librera** | Widest formats (DjVu, CBR, DOCX, RTF, FB2, MOBI…) · **TTS → MP3/WAV** · music mode (auto-scroll for sheet music) · crops 2-page scans to single pages |
| **KOReader** | **Vocabulary builder with spaced repetition** · **PDF reflow** (re-typesets scanned PDFs) · e-ink optimised · plugin ecosystem (incl. AI) · Calibre wireless |
| **Readest** | **Split-screen parallel reading** · KOReader-sync interop · same app on Android/iOS/desktop/web · downloadable offline voices |
| **Readwise Reader** | **Ghostreader** with your own API key · **daily highlight resurfacing** · one inbox for EPUB + articles + RSS + newsletters · MCP/CLI access |
| **ElevenReader** | **Word-synced AI voices** · **GenFM** (turns a document into a podcast) · celebrity voices · soundscapes |
| *Beelinguapp* | Side-by-side bilingual text + karaoke audio |

### 0.3 How each app positions itself

| | Store / ecosystem | Your own files | Customisation | Learning tools | AI | Price model |
|---|---|---|---|---|---|---|
| Kindle, Play Books, Kobo | ★★★ | ★ | ★★ | ★★ (Kindle) | ★★ (Kindle) | Free app, sell books |
| Moon+, Librera | — | ★★★ | ★★★ | ★ | — | Ads / one-time Pro |
| ReadEra | — | ★★★ | ★★ | ★ | — | Free / Premium |
| KOReader, Readest | — | ★★★ | ★★★ | ★★ | ★ | Free, open source |
| Readwise Reader | — | ★★ | ★★ | ★★★ | ★★★ | Subscription |
| ElevenReader | — | ★★ | ★ | ★ | ★★★ (voice) | Freemium |
| **WindBook** | — | ★★★ | ★★ presets + ★★★ advanced | ★★★ | ★★ (v2, your key) | Free personal → Pro later |

### 0.4 WindBook-specific features (no single app above combines these)

| # | WindBook feature | Why it's different | Closest existing |
|---|---|---|---|
| 1 | **AMOLED eye-comfort pack**: low-flicker dimming + font-weight boost + auto Paper/Dusk theme by time | Readers treat themes as colours only. None tune for AMOLED flicker and thin-font fade at night | Kobo TypeGenius (weight only, Kobo devices) |
| 2 | **One-tap lookup card**: offline dictionary + **offline EN↔VI translation** + pronounce + highlight + save word, in one pop-up | Kindle/Play Books translate needs internet. KOReader works offline but with a clunky UI. Most split these across menus | KOReader |
| 3 | **Words with context → spaced-repetition review** (captured from v1) | Only KOReader/Kindle do this, and neither is a modern phone-native app for your own files | KOReader vocabulary builder |
| 4 | **One-tap Markdown/Obsidian export**, offline, no account | Readwise needs a subscription and the cloud. Others export plain text or HTML | Readwise, KOReader |
| 5 | **Shoulder-trigger page turns** on gaming phones (Neo 3 GT), plus volume keys | No reader supports gaming-phone triggers | Moon+ (volume keys only) |
| 6 | **Footnote pop-up + "back to where I was" + time left in chapter**, together in a modern UI | Each exists somewhere, never all three in a clean phone UI | Kindle (own store only) |
| 7 | **Presets-first UI with Moon+-level "Advanced"** | Power users get control. Everyone else never sees a wall of settings | — |
| 8 | **(v2) Spoiler-safe "Ask this book" on *your own files* with *your own key*** | Kindle's version only works on Kindle-store books. Readwise's AI isn't position-aware | Kindle, Readwise |
| 9 | **Private by design**: no account, no ads, no analytics, nothing leaves the phone unless you tap it | ReadEra is private but has no learning tools. Readwise has learning tools but is cloud-only | ReadEra |

**Positioning in one line:** *"ReadEra's simplicity + KOReader's learning tools + Kindle's polish, offline, on your own files, tuned for AMOLED eyes."*

---

## 1. Who is best at what (one line each)

| App | Signature strength | Biggest weakness |
|---|---|---|
| Kindle | AI features (Ask this Book, Recaps, Story So Far), X-Ray, Word Wise, Vocabulary Builder | Walled garden. The AI features are US/iOS first, Android "expected end of 2026" |
| Google Play Books | Clean UI, Bubble Zoom for comics, tap-to-translate | Your own files are second-class |
| Kobo | Reading Life stats + awards, TypeGenius font control | Social/store focus, weak a11y |
| Moon+ Reader | Deepest customisation, auto-scroll, dual page, Readwise push (still updated: v10.7, Jul 2026) | Dense, dated UI with a steep learning curve |
| ReadEra | Zero friction, no ads/accounts, quotes hub, collections + To-read/Finished lists | Shallow power features |
| Librera | Format breadth (MOBI, DjVu, FB2, CBR, DOCX…), TTS → MP3 | Busy UI |
| KOReader | Vocabulary builder with spaced repetition, Wikipedia/dictionary lookup, progress sync, stats, plugins (incl. AI) | Hostile on phones, e-ink-first UI |
| Readest | Modern cross-platform, parallel (split) reading, KOReader sync, offline TTS voices (2026) | Web-tech, not native; AGPL |
| Readwise Reader | Highlight resurfacing, Ghostreader AI (define/translate/Q&A, bring-your-own API key), exports, MCP | Subscription, cloud-only |
| ElevenReader | Best-in-class AI voices with word-synced highlighting, 0.25–3× speed | Cloud + subscription |
| Beelinguapp / LingQ | Side-by-side bilingual text, vocab glossary + SRS | Not general-purpose ebook readers |

---

## 2. Full feature catalogue

### A. Library & import
| Feature | Best example | WindBook |
|---|---|---|
| Open from file picker, no account, works offline | ReadEra | **v1** |
| Duplicate detection (SHA-256) | — | **v1** |
| Continue-reading hero card + recent list | Kindle, Play Books | **v1** |
| Grid (covers) / list toggle, sort, search | all | **v1** |
| Reading status: To read / Reading / Finished | ReadEra | **v1** (one column, big UX win) |
| Collections (book in many) | ReadEra | **v1.x** |
| **Watched folder** (auto-import new files in a chosen folder via SAF tree URI) | Moon+, Librera | **v1.x** |
| Edit metadata / cover | Moon+ | v1.x |
| OPDS catalogs | Moon+, KOReader | v1.x |
| Calibre content server / wireless | KOReader | v2 |
| Home-screen "continue reading" widget (Glance) | Kindle | v1.x |
| Formats: EPUB 2/3, PDF, CBZ (Readium native) | — | **v1** EPUB+PDF, CBZ v1.x |
| MOBI / AZW3 / FB2 / DjVu | Librera | **✗** (Readium can't; tell users to convert with Calibre) |

### B. Reading surface & navigation
| Feature | Best example | WindBook |
|---|---|---|
| Paginated **and** continuous scroll | Moon+, Play Books | **v1** |
| Immersive full screen; tap centre = controls | all | **v1** |
| Configurable tap zones (left/right/top/bottom) | KOReader, Moon+ | **v1** (defaults) / v1.x (custom) |
| **Volume keys** page turn | Moon+, Kindle | **v1** |
| **Shoulder-trigger page turn** (device-specific) | — | **v1** if `getevent` shows key events, else document the Game Space mapping |
| Page-turn animation: none / slide (Readium 3.2+) | Apple Books | **v1** (default *none*) |
| Status footer: time left in chapter / book, clock, battery | Kindle, KOReader | **v1** ("time left" needs reading speed from sessions) |
| TOC with current-chapter marker + per-chapter progress | KOReader | **v1** |
| **Footnote pop-up** instead of jumping | ReadEra, KOReader | **v1** |
| "Back to where I was" after following a link | Kindle | **v1** |
| Full-screen image viewer with pinch zoom (Readium 3.4 `TapEvent.targetElement`) | Play Books | v1.x |
| Auto-scroll (adjustable speed) | Moon+ | v1.x |
| Keep screen on while reading; in-app orientation lock (allowed on phones <600 dp) | Moon+ | **v1** |
| Two-page spread in landscape | Moon+ | v1.x |
| Bubble/panel zoom for comics | Play Books | ✗ |

### C. Typography & eye comfort (the core of the product)
| Feature | Best example | WindBook |
|---|---|---|
| Theme presets: Paper, Sepia, Dusk (dark grey), OLED Black | Play Books, Apple Books | **v1** |
| **Auto theme** by time-of-day / system dark mode | Kindle | **v1** |
| Font family (bundled Literata + sans + user fonts) | Kobo TypeGenius, Moon+ | **v1** bundled, v1.x user fonts |
| Size, line height, margins, paragraph spacing, indent | Moon+ | **v1** |
| **Font weight / boldness boost** (thin fonts look faint on AMOLED at low brightness) | Kobo, KOReader | **v1** |
| Justify + hyphenation | KOReader | **v1** |
| Publisher styles on/off | Apple Books, Readium | **v1** |
| In-reader brightness gesture (left edge swipe) | Moon+ | **v1** |
| **Low-flicker dimming**: keep panel brightness up, dim with overlay (AMOLED PWM) | — (WindBook original) | **v1** (toggle, experimental) |
| Warm tint overlay for evening (comfort, *not* eye protection) | Moon+ | v1.x |
| Break nudge (20-20-20) after N minutes, opt-in | — | v1.x |
| Per-book settings override | Moon+ | v1.x |
| E-ink mode (no animation, B/W, full refresh) | KOReader | v2 |
| Bionic Reading (bold first half of words) | — | **✗**: peer-reviewed tests (Snell 2024; Readwise n=2,074) show no speed gain |

### D. Highlights, notes & knowledge
| Feature | Best example | WindBook |
|---|---|---|
| Multi-colour highlight + underline, attach note | Kindle, ReadEra | **v1** |
| Notes list per book, jump to location | all | **v1** |
| **Global "Quotes" hub** across all books, searchable, filter by colour/tag | ReadEra, Readwise | v1.x |
| Tags on highlights | Readwise | v1.x |
| **Export Markdown** (Obsidian-ready, with chapter + location) | KOReader, Readwise | **v1** (single tap, share sheet) |
| Share quote as styled image | Kindle, Apple Books | v1.x |
| Daily highlight resurfacing (SRS review) | Readwise | v2 |
| Push to Readwise / Anki export | Moon+, KOReader | v2 |
| Handwriting/ink notes on PDF (androidx.ink / androidx.pdf) | Xodo | v2 (when androidx.pdf is stable) |

### E. Language: dictionary, translate, learning
| Feature | Best example | WindBook |
|---|---|---|
| Long-press word → **definition pop-up** (offline dictionary file) | Kindle, KOReader | **v1** |
| Long-press → **translate word / sentence offline** (ML Kit Translation: on-device, 50+ languages incl. **Vietnamese**, runs fine on Unisoc) | Play Books, Readest | **v1** |
| Translate whole paragraph / page on demand | Librera | v1.x |
| **Vocabulary builder**: auto-save looked-up words with context sentence → flashcards with spaced repetition | KOReader, Kindle | v1.x (data captured in v1, review UI later) |
| Pronounce word (system TTS) | Kindle | **v1** (cheap) |
| Wikipedia lookup | KOReader | v1.x (online) |
| Parallel bilingual view (original ↔ translated, paragraph-aligned) | Readest, Beelinguapp | v2 |
| Word Wise-style inline hints | Kindle | ✗ (needs licensed word-difficulty data) |

### F. Listening (TTS)
| Feature | Best example | WindBook |
|---|---|---|
| Read aloud with **sentence highlight + auto page follow** (Readium EPUB TTS) | ElevenReader, Readium | **v1.x** (EPUB only; Readium has no PDF TTS) |
| Speed, pitch, voice picker (any installed system engine) | Moon+ | v1.x |
| Background playback + lock-screen controls (Media3 session) | ElevenReader | v1.x |
| Sleep timer (end of chapter / N min) | Moon+ | v1.x |
| Better voices: recommend installing an **offline neural TTS engine** (sherpa-onnx/Piper; Kokoro is too slow on mid-range chips) | Readest (2026) | v1.x (docs/link, no bundling) |
| Export chapter to audio file | Librera | ✗ |
| EPUB 3 media overlays (audiobook + text sync) | Readium | v2 |

### G. Progress, stats, motivation
| Feature | Best example | WindBook |
|---|---|---|
| Reading sessions recorded (time, pages, book) | Kobo, KOReader | **v1** (data only) |
| Time-left estimates (chapter/book) from personal reading speed | Kindle | **v1** |
| Stats screen: daily minutes, streak, calendar heat-map, books finished | Kobo, Moon+ Pro | v1.x |
| Yearly goal | Kindle, Apple Books | v1.x |
| Awards/badges | Kobo | ✗ (gimmick for a personal app) |

### H. AI (your phone has no Gemini Nano → cloud, opt-in, bring-your-own key)
| Feature | Best example | WindBook |
|---|---|---|
| **Explain / define this passage in context** | Readwise Ghostreader | v2 |
| **Ask this book**, spoiler-safe (only text before your current position), answers cite locations | Kindle | v2 |
| Story so far / chapter recap | Kindle Recaps | v2 |
| Highlights → flashcards | Readwise | v2 |
| Bring-your-own API key, nothing uploaded without an explicit action | Readwise (BYO key) | v2 rule |

### I. Backup, sync, privacy
| Feature | Best example | WindBook |
|---|---|---|
| Manual backup/restore (zip: DB + settings, optionally books) | ReadEra, Moon+ | **v1** |
| Scheduled auto-backup to a user-chosen folder (SAF) | Moon+ | v1.x |
| KOReader progress-sync protocol compatibility | Readest | v2 |
| Google Drive / WebDAV sync of library + notes | Moon+, KOReader | v2 |
| No ads, no account, no analytics in personal build | ReadEra | **v1** |

### J. Accessibility
| Feature | WindBook |
|---|---|
| TalkBack labels on library + reader controls, ≥48 dp targets | **v1** |
| Respect system font scale & "Remove animations" | **v1** |
| Atkinson Hyperlegible Next as a bundled option (low-vision legibility) | **v1** |

---

## 3. UI/UX principles (2026), tuned for the Neo 3 GT

1. **The page is the product.** No chrome while reading. One tap gives a minimal bottom bar: progress scrubber, TOC, **Aa**, more.
2. **One-handed on a tall 20:9 phone.** Every reader control lives in the bottom third. Settings open as a bottom sheet with **live preview** of the page behind it.
3. **Presets first, knobs second.** 4 themes × 3 typography presets cover 90% of use. "Advanced" reveals Moon+-level control. This answers Moon+'s main criticism.
4. **Material 3 (stable 1.4) for the shell, not the page.** Dynamic colour themes the library and settings. The reading surface always uses the curated reading palettes.
5. **Evidence-based eye comfort.** Light themes for bright rooms, dark for dim. Contrast ≥7:1 but not pure #000/#FFF. Line height ~1.5. Lines ~45–75 characters by default (faster longer lines exist, but readers prefer moderate lengths). Font-weight boost for AMOLED at night. Low-flicker dimming option. Honest copy: no "blue-light protection" claims.
6. **120 Hz-friendly motion.** Short, interruptible transitions (≤250 ms). Page-turn animation optional. Respect "Remove animations".
7. **Never lose the reader's place.** Save the Locator on every page change (MyOS kills apps aggressively). Restore exactly, including after font changes and rotation.
8. **Gestures don't fight the system.** Keep tap zones away from the back-gesture edges. Brightness swipe on the left edge only inside the safe inset.
9. **Instant lookup.** Long-press opens a single card: definition · translation · pronounce · highlight · note. One tap to save to vocabulary. No app-switching.
10. **Quiet by default.** No onboarding carousel. The first launch opens the import button. No notifications except an opt-in break nudge and the TTS media control.

---

## 4. What WindBook v1 is, in one paragraph

A native, private EPUB/PDF reader that opens instantly, never loses your place, and has the most comfortable reading surface on an AMOLED phone. It offers curated themes, Literata, font-weight boost, low-flicker dimming, footnote pop-ups, volume/shoulder-key paging and time-left estimates. Long-press gives an offline dictionary, offline English↔Vietnamese translation and a highlight, and notes export to Markdown in one tap. No account, no ads, no cloud. Everything else (stats, TTS, vocabulary review, OPDS, AI) builds on data v1 already records.

---

## Sources
- Kindle AI features: https://engadget.com/2202142/kindle-app-features-ios-has-ereader-still-doesnt-have · https://www.slashgear.com/2240743/ios-kindle-app-features-better-than-amazon-e-reader/ · https://www.aboutamazon.com/md/news/books-and-authors/kindle-recaps-feature-ebook-series-refreshers · https://gigazine.net/gsc_news/en/20251216-ask-this-book
- Kindle Word Wise / X-Ray / Vocabulary: https://betanews.com/article/amazon-brings-kindle-features-to-its-android-app/ · https://www.expertreviews.co.uk/technology/tablets-ereaders/amazon-adds-word-wise-and-family-library-to-kindle-family-in-latest
- Google Play Books: https://nerdschalk.com/google-play-books-update-brings-dictionary-notes-translation-and-more/ · https://appgoblin.info/apps/400989007
- Kobo Reading Life: https://www.kobo.com/readinglife
- Moon+ Reader: https://www.rustore.ru/catalog/app/com.flyersoft.moonreader · https://www.apkmirror.com/?p=1410470 · https://www.itechguides.com/best/ebook-reader-software/moon-reader/
- ReadEra: https://gizmodo.com/download/readera-book-reader-pdf-epub · https://www.apkmirror.com/?p=13132007
- Librera: https://f-droid.org/en/packages/com.foobnix.pro.pdf.reader/
- KOReader: https://github.com/koreader/koreader/wiki/ · https://cameracode.coffee/koreader-language-learning · https://www.apkmirror.com/?p=12987240
- Readest: https://readest.com/docs · https://marlvel.ai/apps/readest-ebook-reader/versions/0.11.20
- Readwise Reader: https://readwise.io/reader/update-aug2026 · https://www.stork.ai/es/readwise
- ElevenReader: https://www.stork.ai/en/elevenreader
- Beelinguapp / LingQ: https://beelinguapp.com/ · https://alternativeto.net/software/bireader/
- ML Kit Translation languages: https://developers.google.com/ml-kit/language/translation/translation-language-support
- Offline neural TTS: https://github.com/CodeBySonu95/VoxSherpa-TTS
- Bionic Reading evidence: https://research.vu.nl/en/publications/no-bionic-reading-does-not-work/ · https://blog.readwise.io/bionic-reading-results/
- Line length: https://journals.uc.edu/index.php/vl/article/view/5671 · https://oreilly.com/library/view/100-things-every/9780132658638/ch18.html
- Dark vs light mode fatigue: https://www.ncbi.nlm.nih.gov/pmc/articles/PMC12027292/ · AAO: https://aao.org/newsroom/news-releases/detail/protect-your-eyes-in-home-office
