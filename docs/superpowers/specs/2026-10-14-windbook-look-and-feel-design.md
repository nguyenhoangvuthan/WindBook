# WindBook – Look & Feel (visual redesign)

Date: 2026-10-14 · Status: draft for review
Inputs: `docs/WindBook_Technical_Blueprint.md` §5 (four reference images), `docs/superpowers/specs/2026-10-13-windbook-design.md`
Mockups: `.superpowers/brainstorm/*/content/screens-v1.html` (local only, git-ignored)

## 1. Purpose

After M1, the owner tested the app and found it does not look like the four reference images in blueprint §5. The cause: M1 had no visual design step. It shipped stock Material 3 with wallpaper-based dynamic colour.

This spec defines one visual language for all of WindBook, taken from the four images. It also restyles the screens that exist today and adds the bottom navigation shell. Screens that depend on later milestones (reading settings sheet, TOC, highlights) are designed here and built in those milestones, to this spec.

**Success criteria**
1. With the app open next to reference image 01, the Library reads as the same style: cream paper, bronze-gold accent, serif headings, rounded pills, shadowed covers, bottom tab bar.
2. Light and dark (system setting) both use the warm palette. Dynamic colour is gone.
3. Every text/background pair in both palettes is ≥ 4.5:1. Icon-only and graphical elements are ≥ 3:1.
4. No behaviour from M1 regresses. All existing tests pass, updated only where the UI structure changed.

## 2. Decisions taken in brainstorming

| Question | Decision |
|---|---|
| Order of work | Design everything now. Restyle the existing screens now. The settings sheet, TOC and notes screens are built in M2/M3/M4 to this spec |
| Colour direction | **A · Cream paper & bronze gold** (from image 01). Layouts per screen follow the matching image |
| Bottom navigation | 4 tabs: Library / Reading / Notes / Settings. "Discover" (store) and "Me" are dropped |
| Dark mode | Follow the system. Warm dark palette, not black/grey |
| Notes & Settings tabs now | Minimal real content. Settings: language, storage, about. Notes: designed empty state until M4 |
| Implementation approach | Custom Material 3 `ColorScheme` + `Typography` + `Shapes` + bundled Literata. No custom component library |

Dropped from the reference images on purpose: the Discover tab, the "Sketches" and "Summary" tabs (ink drawing; AI summary is excluded by the main spec), and the pen/shape/eraser tools in image 04. Genre chips such as "Sci‑fi / Business" in image 01 need user collections. They arrive with M6 Collections.

## 3. Design tokens

### 3.1 Colour

All colours live in `ui/theme/Color.kt` as two `ColorScheme`s. `WindBookTheme` picks one with `isSystemInDarkTheme()`.

| Role (M3 slot) | Light | Dark | Used for |
|---|---|---|---|
| `background`, `surface` | `#F7EFDF` | `#17130F` | Screen background (paper) |
| `surfaceContainerLow` | `#FCF7EC` | `#221C16` | Search pill, pills, cards, reader bars, sheets |
| `surfaceContainer` | `#F2E8D3` | `#2A221A` | Bottom navigation bar |
| `onSurface`, `onBackground` | `#3B2A1A` | `#EFE4CF` | Body text, titles |
| `onSurfaceVariant` | `#7A6650` | `#A89880` | Secondary text, counts, placeholders, unselected tabs |
| `outlineVariant` | `#E3D3B4` | `#3A3026` | Pill borders, dividers, progress track |
| `outline` | `#A38B6B` | `#6E5E4A` | Focus/stronger borders |
| `primary` / `onPrimary` | `#8C6430` / `#FFFFFF` | `#D2A464` / `#17130F` | Filled buttons with text, selected-tab label and icon, text accents |
| `primaryContainer` / `onPrimaryContainer` | `#EBCF9C` / `#3B2A1A` | `#5A4426` / `#EFE4CF` | Selected chip, selected tab indicator |
| `tertiary` / `onTertiary` | `#B8894A` / `#FFFFFF` | `#D2A464` / `#17130F` | FAB (icon only), progress bar fill, "current" markers |
| `error` / `onError` | `#B3261E` / `#FFFFFF` | `#F2B8B5` / `#601410` | Errors (M3 defaults) |

Measured contrast (WCAG): light ink on paper 12.0, muted on paper 4.78, muted on cards 5.11, `primary` with white text 5.28, ink on selected chip 9.10, FAB white icon on gold 3.13 (icon only, needs 3:1). Dark ink on paper 14.7, muted on paper 6.57, `onPrimary` on `primary` 8.13, ink on selected chip 7.28.

Rule: `tertiary` gold `#B8894A` is never a text colour on light surfaces (2.74:1). Text accents use `primary`.

Highlight colours (M4, defined now in `Color.kt` so M4 does not invent them): yellow `#F3D27A`, red `#E58C7A`, green `#8CC9A0`, blue `#82B4E0`, purple `#B49AD8`. Highlighted text keeps the reading theme's text colour.

### 3.2 Typography

- **Literata** (SIL OFL 1.1, Google Fonts), the variable TTF, bundled in `app/src/main/res/font/`. It covers Vietnamese. The OFL text ships in `app/src/main/assets/licenses/literata-OFL.txt`.
- Literata serves the `display*`, `headline*` and `title*` styles: screen titles, book titles, card titles. Weights: 700 for screen titles, 600 for titles.
- `body*` and `label*` use the system sans (`FontFamily.Default`).
- All sizes are in `sp`, so the system font scale applies (main spec F2.15).
- Book title in the reader top bar: Literata 600, `labelLarge` size, uppercased in code (`uppercase(locale)`), letter spacing 0.08 em. This avoids depending on an OpenType small-caps feature.
- Section labels in sheets (e.g. "BRIGHTNESS & BACKGROUND"): `labelSmall`, uppercase via string resources, letter spacing 0.12 em, `onSurfaceVariant`.

### 3.3 Shapes and elevation

| Element | Shape |
|---|---|
| Pills: search field, sort button, filter chips, settings options | Fully rounded (`RoundedCornerShape(50)`) |
| Cards (continue reading, settings groups) | 14 dp |
| Bottom sheets | 18 dp top corners |
| Book covers | 3 dp on the spine (start) side, 6 dp on the outer side. Shadow elevation 4 dp |
| FAB | Circle, 56 dp |

Covers without an image get a generated cover: a tinted paper background, chosen deterministically from the book id out of 6 warm tints, with the title in Literata 600.

## 4. Navigation shell

- New top-level keys in `navigation/Routes.kt`: `ReadingKey`, `NotesKey`, `SettingsKey`, next to `LibraryKey`. `ReaderKey` is unchanged.
- **Tab rule:** the back stack holds at most one top-level key above `LibraryKey`. Selecting a tab gives `[LibraryKey]` for Library, or `[LibraryKey, <tab>]` for any other tab. Back from a non-Library tab returns to Library (M3 guidance). Re-selecting the current tab does nothing. This is a pure function `MutableList<NavKey>.selectTab(key)` in `BackStack.kt`, unit-tested like `openReader`.
- The bottom `NavigationBar` shows only when the top entry is a top-level key. On `ReaderKey` it is hidden, and the reader stays immersive as in M1.
- Tabs: Library (shelf icon), Reading (open book), Notes (pencil/note), Settings (gear). Selected state uses `primary` for the icon and label, with a `primaryContainer` indicator.

## 5. Screens

### 5.1 Library (restyle of the existing screen, mockup 01)

Behaviour stays as M1 (F1.4–F1.6, F1.12). Only structure and style change:

1. Header: "Library" in Literata 700, then "│ N books" in `onSurfaceVariant`. N counts all books, not the filtered set.
2. A search pill, always visible, replaces the toggle-search in the top app bar. A **Sort** pill sits at its right and opens the existing sort menu, with the selected option marked by a check icon instead of the "✓ " text prefix.
3. Filter chips (All / Reading / To read / Finished). A grid/list toggle icon sits at the end of the chip row.
4. The Continue reading card (F1.5): a small cover, the label "CONTINUE READING", the title in Literata, the chapter if known, and a gold progress bar.
5. Grid: 3 columns on phones (adaptive `minSize` 100 dp), covers 2:3. A thin gold progress bar under covers with progress > 0. "✓ Finished" caption for finished books, author otherwise. Each cover has a **⋯** button that opens the same menu as long-press (status change, delete). Long-press remains.
6. List layout: the same styling, as a row with a cover thumbnail, title, author, and a progress bar.
7. FAB: a circular gold "+" (replaces the extended "Add books" FAB). Its content description is "Add books". While importing, it shows a progress indicator.
8. Empty library: centered serif message plus a filled "Add books" button.

### 5.2 Reading tab (new)

- A list of books with status READING, newest `lastOpenedAt` first. The newest is a large card (cover, title, author, chapter, progress, "Continue" button). The others are list rows with a progress bar.
- Empty state: "No book in progress" plus a button that switches to the Library tab.
- Data: a new `ReadingViewModel` over `LibraryRepository.observeLibrary()`, filtered and sorted in the ViewModel. No DAO change.

### 5.3 Notes tab (new, placeholder until M4)

Empty state only: an illustration-free serif heading "Your highlights live here" and one line explaining that highlights and notes made while reading appear on this screen. No buttons. M4 replaces the body.

### 5.4 Settings tab (new, minimal)

Grouped cards on paper background:

- **Language:** Automatic (system) / Tiếng Việt / English. Uses `AppCompatDelegate.setApplicationLocales`. Because minSdk is 31, the manifest adds `androidx.appcompat.app.AppLocalesMetadataHolderService` with `autoStoreLocales=true`, so the choice survives restarts on Android 12. The existing generated locale config stays for the Android 13+ system picker.
- **Storage:** number of books, total size of book files (sum of `Book.fileSize`), and free space on the device (`BookFileStore.usableBytes()`). Sizes are formatted with `Formatter.formatShortFileSize`.
- **About:** app name, version (`PackageManager` `versionName`), and "Open-source licences", which lists Literata (OFL) and Readium (BSD-3).
- M2 adds a **Reading** group (themes, fonts, typography defaults) above Language.
- Data: a `SettingsViewModel` exposing `SettingsUiState(language, bookCount, totalBytes, freeBytes, versionName)`.

### 5.5 Reader chrome (restyle of the existing overlay, mockups 02/04)

- Top bar on `surfaceContainerLow`: back arrow and the title (centered, per §3.2). The TOC (☰), typography (Tt) and overflow (⋮) buttons are **not** shown until M3/M2 give them actions. No button may appear without a working action.
- Bottom bar: progress % and chapter title, in the same surface, with a thin gold progress bar across the top edge. "Mark finished" at ≥ 0.99 progress (F1.6) moves here as a text button in `primary`.
- Error screen restyled with the tokens. Copy unchanged.
- **Default EPUB page colour:** the Readium EPUB navigator gets `initialPreferences = EpubPreferences(theme = Theme.SEPIA)` in light mode and `Theme.DARK` in dark mode, so the page matches the chrome (image 02). There is no user control until M2, which replaces this default with the user's choice. PDF pages are unchanged.

### 5.6 Later screens (built in their milestones, to these mockups)

- **M2 Reading settings sheet** (mockup 02): a modal bottom sheet. Sections: Brightness & background (slider; Light / Sepia / Dark / Charcoal pills with colour dots), Font size (slider with small/large "A"), Font (pills; each name rendered in its own face), Margins & line spacing (three pills). The selected pill uses `primaryContainer`.
- **M3 Table of contents** (mockup 03): a full-screen dialog. Close ✕, book title in Literata, tabs Contents / Bookmarks / Notes, then "Chapter n · x%" in `onSurfaceVariant`. Rows: title with nesting indent, page/position on the right. The current chapter gets a 3 dp `tertiary` start bar, a 10 % `tertiary` tint, and its number in `primary` bold.
- **M4 Highlight menu** (mockup 04): a floating dark card (`onSurface` background, `surface` text) above the selection. It has 5 highlight dots (§3.1) plus an underline toggle, then actions Note / Copy / Share / Delete. The Notes tab lists highlights grouped by book.

## 6. Code changes

| File | Change |
|---|---|
| `ui/theme/Color.kt` (new) | Light/dark `ColorScheme`s, highlight colours |
| `ui/theme/Type.kt` (new) | Literata `FontFamily`, `Typography` |
| `ui/theme/Shape.kt` (new) | `Shapes` + cover shape |
| `ui/theme/Theme.kt` | Drop dynamic colour. Wire colours/type/shapes |
| `res/font/literata.ttf`, `assets/licenses/literata-OFL.txt` (new) | Bundled font + licence |
| `navigation/Routes.kt`, `BackStack.kt`, `WindBookNavHost.kt` | Tab keys, `selectTab`, `Scaffold` with conditional `NavigationBar` |
| `library/LibraryScreen.kt` | Restyle per §5.1. Split cover/cell/header composables into `library/components/` so the file stays readable |
| `reading/ReadingScreen.kt`, `ReadingViewModel.kt` (new) | §5.2 |
| `notes/NotesScreen.kt` (new) | §5.3 |
| `settings/SettingsScreen.kt`, `SettingsViewModel.kt` (new) | §5.4 |
| `reader/ReaderScreen.kt` | §5.5 chrome |
| `reader/.../ReaderHostFragment.kt` (`:reader`) | Pass the initial EPUB theme. `ReaderApi` gains a `darkTheme: Boolean` input |
| `AndroidManifest.xml` | `AppLocalesMetadataHolderService` |
| `values/strings.xml`, `values-vi/strings.xml` | All new strings in English and Vietnamese |

## 7. Testing

- **Unit (JVM):** `selectTab` rules (tab switch, re-select, back to Library, reader above a tab). `ReadingViewModel` filter and sort. `SettingsViewModel` storage totals and language state. A **contrast test** that computes WCAG ratios from the `ColorScheme`s and asserts the pairs in §3.1 (≥ 4.5 text, ≥ 3.0 FAB icon).
- **Compose UI (Robolectric):** update `LibraryScreenTest` and `ReaderOverlayTest` to the new structure (search pill, sort pill, ⋯ cover menu, FAB content description, reader bars). New tests: the bottom bar is visible on tabs and hidden in the reader. Reading empty state switches to Library. Settings shows the counts and the language options.
- **Instrumented:** the existing Readium tests still pass. The EPUB theme preference is applied (navigator `settings.theme` is SEPIA in light mode).
- **Manual:** run on the emulator in light and dark, and screenshot Library, Reading, Notes, Settings and the Reader overlay. Compare each with `screens-v1.html`. Add a "Look & feel" section to `docs/testing/m1-manual-checklist.md`.

## 8. Out of scope

The reading settings sheet and user-selectable reading themes (M2). TOC and bookmarks (M3). Highlights, notes and the Notes list (M4). Collections and genre chips (M6). Tablet layouts beyond the adaptive grid. A custom launcher icon redesign.
