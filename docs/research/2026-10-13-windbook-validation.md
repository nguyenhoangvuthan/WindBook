# WindBook Blueprint – Validation Report

Date: 2026-10-13 · Reviewed: `docs/WindBook_Technical_Blueprint.md` (the `.pdf` is a pandoc render of the same file)
Target change requested: **Android 12+ (API 31)**, latest *stable* tech, best-of-breed reader features, eye-friendly UI/UX.

Verdict: **the architecture is sound — keep it.** Kotlin + Compose + Readium + Room, offline-first, reader engine behind an interface, Locator-based progress, copy-into-app-storage. What needs fixing is version pinning, Play-policy compliance, a few schema gaps, and the feature/UX layer, which is too generic to compete in 2026.

---

## 1. Fact check of the proposal's claims

| Claim in blueprint | Status | Evidence |
|---|---|---|
| Readium 3.4.0 released Sep 2026 | ✅ Correct (2026-09-11) | Readium CHANGELOG |
| Compose navigators still experimental → use `EpubNavigatorFragment` | ✅ Correct — the Compose "Web Navigators" are still **alpha** | readium.org/kotlin-toolkit/latest |
| Readium needs core library desugaring | ✅ Correct | same |
| Readium supports TTS | ⚠️ Partly — TTS covers reflowable + fixed-layout **EPUB only**; PDF TTS is "want to do" | Readium feature table |
| Min SDK Android 10 (API 29) | ❌ Change to **API 31** per new requirement | — |
| CI on JDK 17, `actions/*@v4` | ⚠️ Works with AGP 9.4 (min JDK 17), but use **JDK 21** and bump actions to current majors (v4 actions target Node 20, which GitHub is retiring) | AGP release notes |
| UI reference images | ⚠️ They are hot-linked `images.openai.com` placeholders, not WindBook designs — replace with real mockups | — |
| No targetSdk / compileSdk stated | ❌ Missing, and it's now a **Play blocker** (see §2) | Play target-SDK policy |

Readium 3.4.0 gotcha the blueprint doesn't mention: the PDFium adapter **changed its default to horizontal pagination** and fixed an off-by-one in page positions. Any saved PDF locators from before 3.4 need `Publication.migrateLegacyPdfiumLocator()`. Since this is greenfield, start on 3.4.0 and the migration doesn't apply.

---

## 2. Recommended stable stack (verified Oct 2026)

| Layer | Pin | Notes |
|---|---|---|
| minSdk | **31** (Android 12) | Unlocks dynamic color, SplashScreen API, stretch overscroll natively |
| targetSdk | **36** | **Required** for new apps/updates on Play since **2026-08-31** |
| compileSdk | **37** | Readium `develop` and androidx.pdf already compile against 37 |
| AGP | **9.4.0** (Sep 2026) | Needs Gradle 9.6.0, JDK 17+, build-tools 36. 9.5 is preview |
| Kotlin | **2.4.x** (2.4.20+) | Compose compiler plugin version = Kotlin version |
| Compose BOM | **2026.09.00** | |
| Material 3 | **1.4.0** stable | 1.5.0 (expressive components graduating) is beta — adopt when stable |
| Navigation | **Navigation 3 1.2.0** | Stable 2026-09-23; blueprint never named a nav library |
| Room | **2.8.5** + KSP | Add FTS4/5 table for search |
| DI | **Hilt** (keep) | Metro is a credible compile-time alternative but younger; not worth the risk for v1 |
| Reader engine | **Readium 3.4.0** | EPUB via `EpubNavigatorFragment`; PDF via `readium-adapter-pdfium` |
| PDF (future) | **androidx.pdf** — watch, don't adopt | Only `1.0.0-beta01` (2026-08-26); annotation/editing APIs still `@ExperimentalPdfApi`. Strong candidate for FR-11 PDF annotation once stable |
| On-device AI (P2) | **ML Kit GenAI (Gemini Nano)** | Private + offline matches the product principles, but device support is limited (recent flagships) — must degrade gracefully |

### Android 16 (target 36) behaviour changes that affect a reader
- **Edge-to-edge is mandatory** (opt-out removed). The reader surface must handle insets and display cutouts.
- **On screens ≥600 dp, apps can no longer lock orientation/aspect ratio.** "Tablet later" is no longer an option: the reader must survive rotation, resize, foldables and split-screen from v1. Locator-based progress already handles reflow correctly. Test it.
- **Predictive back** is on by default. The reader's toolbar / TOC / settings sheets must use proper back handling.

---

## 3. Schema and architecture gaps

1. **`collections` + `book_collections`** tables are missing, though the screen map has *Collections*.
2. **Sync-readiness now, cheaply:** add `updated_at` + `deleted_at` (soft delete / tombstones) to `bookmarks`, `annotations`, `collections`. Without tombstones, FR-13 cloud sync can't resolve deletes later. UUID PKs are already right.
3. **Full-text search:** use a Room FTS table populated by a WorkManager indexing job (content extracted via Readium's Content API). Don't scan the EPUB on every query.
4. **`annotations.style`** (highlight / underline / strikethrough) besides `color`; **`annotations.chapter_title`** for the notes list and export.
5. **`reading_sessions`** is good. It powers stats/streaks (see §4), so keep it in P0, not just as a table.
6. **`books.file_size` + `books.source_uri`** for the storage-management screen the blueprint itself promises.
7. Add a **reading-preferences-per-book** override (font size, theme, margins) with global defaults in DataStore. Users want PDFs and novels set differently.

---

## 4. Features: what the best readers combine (2026)

> Superseded in detail by **`2026-10-13-reader-feature-benchmark.md`** (full per-app matrix with v1 / v1.x / v2 tiers). The table below is the short version.

Synthesis of Kindle, Apple Books, Moon+ Reader, ReadEra, KOReader, Readest, Readwise Reader:

| Feature | Who does it best | Recommend | Priority |
|---|---|---|---|
| Zero-friction open & resume, no account | ReadEra | Already the core flow ✅ | P0 |
| Typography/theme control | Moon+ Reader | Yes, but **curated presets + "Advanced"**. Moon+'s density is its most-cited weakness | P0 |
| Long-press **dictionary + translate** | Kindle, Readest | Offline dictionary (StarDict/Wiktionary dump) + system translate intent. Essential for your English-tech-reading persona | **P0/P1** (promote) |
| Reading stats, goals, streaks | Kindle, Moon+ Pro | From `reading_sessions`; time-to-finish-chapter estimate | P1 |
| Highlight **export** (Markdown / Obsidian / Readwise CSV) | Readwise, KOReader | Cheap and high-value; core to "Research Mode" | P1 |
| Page-turn animation option | Apple Books | Readium 3.2+ supports animated pagination. Default *off* or subtle | P1 |
| Volume-key page turn, auto-scroll, keep-screen-on | Moon+ | Trivial, loved by power users | P1 |
| OPDS catalogs + Calibre content server | Moon+, KOReader | Biggest library-source unlock for a sideload reader | P1 |
| Spoiler-safe **AI Q&A up to current position** | Kindle *Ask this Book* (Dec 2025) | The 2026 trend. Do it **on-device** (Gemini Nano) with citations to Locators, cloud LLM as opt-in | P2 |
| **"Story so far" / chapter recap** | Kindle Recaps / Story-so-far (2026) | Same AI pipeline; summarise only text before the current Locator | P2 |
| Flashcards from highlights (spaced repetition) | Readwise | Your Research Mode differentiator | P2 |
| KOReader progress sync / cross-device | KOReader, Readest | Interop-first is cheaper than building a backend | P2 |
| Split-screen parallel reading (original + translation) | Readest | Natural tablet feature since you can't lock orientation anyway | P2 |
| E-ink mode | KOReader | Android e-ink tablets (Boox, etc.) run Android 12+. No animations, pure B/W, full refresh | P2 |

**Differentiation thesis:** Kindle has AI but is a walled garden. Moon+ has control but dated, dense UX. KOReader has power but is hostile on phones. Readest is cross-platform web tech, not native. **WindBook's gap = native, beautiful, private, on-device-AI reader for files you own, with research-grade notes export.** That's a coherent pitch. Keep the P2 AI feature honest about device support.

Drop / skip: **Bionic Reading**-style fixation bolding. Independent tests haven't shown reliable speed or comprehension gains. At most, add it as a toggle later.

---

## 5. Eye-comfort UI/UX: evidence-based rules

What the evidence actually says (so the app doesn't make false health claims):
- **Dark vs light:** no clear winner overall. Light (positive polarity) generally reads faster and is better in **bright** surroundings. Dark is more comfortable in **dim** surroundings. **Ambient light and luminance contrast matter more than "mode".** → Auto-switch by time/ambient light, and make it easy to override.
- **Blue light:** the American Academy of Ophthalmology states there's no evidence screen blue light causes eye strain or eye damage. Strain comes from **prolonged near focus and reduced blinking**. → Offer a warm tint as a *comfort/evening* option; don't market it as eye protection.
- **Breaks:** the 20-20-20 habit is the commonly recommended mitigation. → Optional, gentle break nudge tied to reading session time.

Concrete design rules:

| Area | Rule |
|---|---|
| Themes | Ship 4 presets: **Paper** (warm off-white bg ~#F8F4EC, text ~#2B2B2B), **Sepia**, **Dusk** (dark grey ~#1E1E1E, text ~#D8D4CC — *not* pure white on pure black, avoids halation), **OLED Black** (opt-in). Never use #FFFFFF/#000000 for body text pairs by default |
| Contrast | Body text ≥ 7:1 (WCAG AAA), but avoid the 21:1 maximum in dark themes |
| Typeface | Bundle **Literata** (designed for long-form screen reading) + a sans (e.g., Inter / Atkinson Hyperlegible Next for low vision) + allow user fonts. Respect publisher fonts via a toggle |
| Size | Default ~18–20 sp equivalent, respect system font scale |
| Line height | 1.5 default (WCAG 1.4.8), range 1.2–2.0 |
| Line length | **45–75 characters**. On tablets/landscape, cap column width or use two columns. Don't let text run edge-to-edge on wide screens |
| Justification | Justify **only with hyphenation on**, otherwise ragged-right (avoids rivers) |
| Margins | Generous by default; tap zones must not overlap system gesture edges |
| Chrome | Fully immersive while reading; one tap reveals a minimal bottom bar (progress, TOC, Aa). Status info (time, battery, page/chapter remaining) as a tiny, low-contrast footer, toggleable |
| Brightness | In-reader brightness slider (window-level, below system minimum via dim overlay) + swipe-on-edge gesture |
| Motion | Page-turn animation optional; honour system "Remove animations"; e-ink mode = zero animation |
| Dynamic color | Use Material You for library/settings chrome **only**. The reading surface uses the curated reading themes, never wallpaper-derived colours |
| Accessibility | TalkBack on library + reader controls, TTS with sentence highlight (Readium EPUB), large touch targets (≥48 dp) |

---

## 6. Plan adjustments

- **Sprint 1:** add targetSdk 36 / edge-to-edge / predictive-back scaffold and Navigation 3. Treat rotation and resize as P0 acceptance tests.
- **Sprint 2:** reading themes + typography presets belong here, not "polish" — they *are* the product.
- **Sprint 3:** add dictionary lookup and highlight export. FTS indexing via WorkManager.
- **8 weeks is tight** for a solo dev including PDF + TTS + search + backup. Ship the alpha after Sprint 2 as the blueprint suggests, and expect Sprint 3–4 to slip ~2 weeks.
- **Licences:** PdfiumAndroid/AndroidPdfViewer are Apache-2.0/BSD-family; Literata is OFL. If you ever consider Readest code, it's **AGPL**, so don't mix it in.

## Sources
- Readium Kotlin Toolkit releases & changelog — https://github.com/readium/kotlin-toolkit/releases , https://github.com/readium/kotlin-toolkit/blob/main/CHANGELOG.md , https://readium.org/kotlin-toolkit/latest/
- AGP release notes — https://developer.android.com/build/releases/gradle-plugin
- Compose BOM/compiler setup — https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- Material 3 releases — https://developer.android.com/jetpack/androidx/releases/compose-material3
- Navigation 3 releases — https://developer.android.com/jetpack/androidx/releases/navigation3
- Room releases — https://developer.android.com/jetpack/androidx/releases/room
- androidx.pdf releases — https://developer.android.com/jetpack/androidx/releases/pdf
- Play target SDK requirement — https://developer.android.com/google/play/requirements/target-sdk
- ML Kit GenAI — https://developers.google.com/ml-kit/genai
- Kindle Recaps — https://www.aboutamazon.com/md/news/books-and-authors/kindle-recaps-feature-ebook-series-refreshers ; Ask this Book — https://gigazine.net/gsc_news/en/20251216-ask-this-book ; https://engadget.com/2202142/kindle-app-features-ios-has-ereader-still-doesnt-have
- Readest docs — https://readest.com/docs
- Moon+ Reader review — https://www.itechguides.com/best/ebook-reader-software/moon-reader/ ; Android reader roundup — https://unstore.io/discover/best-ebook-reader-apps-android/
- Light vs dark mode fatigue study (2025, PMC) — https://www.ncbi.nlm.nih.gov/pmc/articles/PMC12027292/
- AAO on screens & eye strain — https://aao.org/newsroom/news-releases/detail/protect-your-eyes-in-home-office
