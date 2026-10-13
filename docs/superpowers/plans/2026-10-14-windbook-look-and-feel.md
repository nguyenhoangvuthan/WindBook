# WindBook Look & Feel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** WindBook looks like the four reference images in blueprint §5. It gets a cream-paper and bronze-gold palette (light + warm dark), Literata serif headings, a restyled Library and reader chrome, and a 4-tab shell (Library / Reading / Notes / Settings) with real minimal content.

**Architecture:** All visual decisions live in `:app` `ui/theme` (colour schemes, typography, shapes) and `ui/components` (cover, progress line, pill, empty state, screen title). Screens only consume those. Navigation stays Navigation 3. A pure `selectTab` back-stack function and a `TopLevelTab` enum drive a `NavigationBar` that shows only on top-level keys. The `:reader` module gets one new input, `darkTheme`, which picks the initial Readium EPUB theme (SEPIA/DARK).

**Tech Stack:** Kotlin 2.4.20, Compose BOM 2026.09.00 (Material 3), Navigation 3 1.2.0, Hilt 2.60.1, AppCompat 1.8.0 per-app locales, Readium 3.4.0 (`EpubPreferences`, `Theme`), Coil 3.6.3. Tests: JUnit 4, Robolectric 4.17 (`sdk=35`), Compose UI test, Turbine.

**Spec:** `docs/superpowers/specs/2026-10-14-windbook-look-and-feel-design.md`

## Global Constraints

- Package root `dev.kent.windbook`; minSdk 31, targetSdk 36, compileSdk 37; bytecode target Java 11.
- Every user-visible string is a resource in **both** `values/strings.xml` (English) and `values-vi/strings.xml` (Vietnamese). The only exceptions are `translatable="false"` strings, which live in `values/` only.
- Dynamic colour is removed. Colours come only from `LightColors` / `DarkColors` in `ui/theme/Color.kt`. Screens never hard-code a colour, except the generated-cover tints and the highlight colours defined in `ui/theme` / `ui/components`.
- Contrast: text on its background ≥ 4.5:1 in both schemes. Icon-only / graphical elements ≥ 3:1.
- Literata is bundled as `app/src/main/res/font/literata.ttf` (Google Fonts variable TTF, SHA-256 `b41138c9373112f32abb589cc22e8674b06ed4048b0c513be922bdd26f274440`). Its OFL licence ships at `app/src/main/assets/licenses/literata-OFL.txt` (SHA-256 `8742963604cd89dc81437811a850018fc03b2bfad686d7422c8235967c87614e`).
- Literata is used for `display*`, `headline*`, `title*`. `body*` and `label*` use `FontFamily.Default`. All sizes in `sp`.
- No button without a working action. TOC (☰), typography (Tt) and overflow (⋮) do not appear in the reader in this plan.
- Only Apache/BSD/MIT/OFL dependencies. This plan adds no new Gradle dependency.
- Robolectric tests run at `sdk=35` (existing `src/test/resources/robolectric.properties`).
- Commits: conventional prefix (`feat(app):`, `test:`, `docs:` …). **No `Co-Authored-By` trailer** (owner's standing rule).
- The full check that CI runs: `./gradlew :core:model:test :core:data:testDebugUnitTest :reader:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug`.

## Review Focus

1. **Searching or filtering the library**: the header still shows the total number of books, not the filtered count. Test: `LibraryViewModelTest.importThenSearchAndFilter` asserts `bookCount == 2` while one item is visible (Task 2).
2. **Vietnamese and long titles in the reader top bar**: the title is uppercased with its diacritics intact ("Đắc nhân tâm" → "ĐẮC NHÂN TÂM") and stays on one line. Test: `ReaderOverlayTest.titleIsUppercasedKeepingVietnameseDiacritics` (Task 6).
3. **Opening a book from the Reading tab, then pressing back**: you land on the Reading tab, not the Library. Test: `BackStackTest.readerOpenedFromReadingTabReturnsToIt` (Task 5).
4. **A READING book that was never opened** (status set by hand from the long-press menu): it still appears in the Reading tab, after the opened ones. Test: `ReadingNowTest.neverOpenedReadingBookSortsLast` (Task 3).
5. **Toggling system dark mode while a book is open**: the activity is recreated and the re-bound EPUB navigator renders with the DARK theme. Test: `ReaderHostFragmentTest.darkModeBindsEpubWithDarkTheme` (Task 6).

---

## File Structure

```
app/src/main/
├── AndroidManifest.xml                               + AppLocalesMetadataHolderService (Task 4)
├── assets/licenses/literata-OFL.txt                  new (Task 1)
├── res/font/literata.ttf                             new (Task 1)
├── res/values/colors.xml, values-night/colors.xml    window background per mode (Task 1)
├── res/values/themes.xml                             window background → @color/window_background (Task 1)
├── res/values/strings.xml, values-vi/strings.xml     new strings (Tasks 2–6)
└── kotlin/dev/kent/windbook/
    ├── ui/theme/Color.kt        LightColors, DarkColors, HighlightColors (Task 1)
    ├── ui/theme/Type.kt         Literata FontFamily, WindBookTypography (Task 1)
    ├── ui/theme/Shape.kt        WindBookShapes, PillShape, CoverShape (Task 1)
    ├── ui/theme/Theme.kt        WindBookTheme without dynamic colour (Task 1)
    ├── ui/icons/WindBookIcons.kt  + MoreHoriz, Check (Task 2); Shelf, OpenBook, Note, Settings (Task 5)
    ├── ui/components/BookCover.kt      BookCover, generatedCoverTint (Task 2)
    ├── ui/components/ProgressLine.kt   ProgressLine, formatPercent (Task 2)
    ├── ui/components/Pill.kt           Pill (Task 2)
    ├── ui/components/EmptyState.kt     EmptyState (Task 2)
    ├── ui/components/ScreenTitle.kt    ScreenTitle (Task 3)
    ├── library/LibraryScreen.kt        Route + Scaffold + content (Task 2, rewritten)
    ├── library/LibraryViewModel.kt     + bookCount (Task 2)
    ├── library/components/LibraryHeader.kt  header, search pill, sort pill, filter row, label helpers (Task 2)
    ├── library/components/BookCell.kt       ContinueReadingCard, BookCell, menu, delete dialog (Task 2)
    ├── reading/ReadingViewModel.kt     ReadingUiState, readingNow, ReadingViewModel (Task 3)
    ├── reading/ReadingScreen.kt        ReadingRoute, ReadingScreen (Task 3)
    ├── notes/NotesScreen.kt            NotesScreen (Task 4)
    ├── settings/AppLanguage.kt         AppLanguage, AppLocaleController, AppCompatLocaleController (Task 4)
    ├── settings/SettingsViewModel.kt   AppInfo, SettingsUiState, SettingsViewModel (Task 4)
    ├── settings/SettingsScreen.kt      SettingsRoute, SettingsScreen (Task 4)
    ├── di/AppModule.kt                 + AppLocaleController, AppInfo (Task 4)
    ├── navigation/Routes.kt            + ReadingKey, NotesKey, SettingsKey (Task 5)
    ├── navigation/TopLevelTab.kt       TopLevelTab, topLevelTab() (Task 5)
    ├── navigation/BackStack.kt         + selectTab (Task 5)
    ├── navigation/WindBookTabBar.kt    NavigationBar (Task 5)
    ├── navigation/WindBookNavHost.kt   Scaffold + conditional tab bar + 5 entries (Task 5)
    └── reader/ReaderScreen.kt          restyled overlay, passes darkTheme (Task 6)
reader/src/main/kotlin/dev/kent/windbook/reader/
    ├── ReaderTheme.kt                  initialEpubPreferences(darkTheme) (Task 6)
    └── ReaderHostFragment.kt           bind(..., darkTheme), epubTheme (Task 6)
```

---

### Task 1: Design tokens (colour, type, shape, font)

**Files:**
- Create: `app/src/main/kotlin/dev/kent/windbook/ui/theme/Color.kt`
- Create: `app/src/main/kotlin/dev/kent/windbook/ui/theme/Type.kt`
- Create: `app/src/main/kotlin/dev/kent/windbook/ui/theme/Shape.kt`
- Modify: `app/src/main/kotlin/dev/kent/windbook/ui/theme/Theme.kt` (whole file)
- Create: `app/src/main/res/font/literata.ttf`, `app/src/main/assets/licenses/literata-OFL.txt`
- Modify: `app/src/main/res/values/colors.xml`, `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/values-night/colors.xml`
- Modify: `docs/superpowers/specs/2026-10-14-windbook-look-and-feel-design.md` §3.1 (two light values)
- Test: `app/src/test/kotlin/dev/kent/windbook/ui/theme/ColorContrastTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `internal val LightColors: ColorScheme`, `internal val DarkColors: ColorScheme`
  - `object HighlightColors { val Yellow; val Red; val Green; val Blue; val Purple; val all: List<Color> }`
  - `val Literata: FontFamily`, `val WindBookTypography: Typography`
  - `val WindBookShapes: Shapes`, `val PillShape: Shape`, `val CoverShape: Shape`
  - `@Composable fun WindBookTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)`

- [ ] **Step 1: Write the failing contrast test**

`app/src/test/kotlin/dev/kent/windbook/ui/theme/ColorContrastTest.kt`:

```kotlin
package dev.kent.windbook.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorContrastTest {

    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun ColorScheme.textPairs(): List<Triple<String, Color, Color>> = listOf(
        Triple("onSurface/surface", onSurface, surface),
        Triple("onSurface/surfaceContainerLow", onSurface, surfaceContainerLow),
        Triple("onSurface/surfaceContainer", onSurface, surfaceContainer),
        Triple("onSurface/surfaceContainerHigh", onSurface, surfaceContainerHigh),
        Triple("onSurfaceVariant/surface", onSurfaceVariant, surface),
        Triple("onSurfaceVariant/surfaceContainerLow", onSurfaceVariant, surfaceContainerLow),
        Triple("onSurfaceVariant/surfaceContainer", onSurfaceVariant, surfaceContainer),
        Triple("onSurfaceVariant/surfaceContainerHigh", onSurfaceVariant, surfaceContainerHigh),
        Triple("primary/surface", primary, surface),
        Triple("primary/surfaceContainerLow", primary, surfaceContainerLow),
        Triple("primary/surfaceContainer", primary, surfaceContainer),
        Triple("onPrimary/primary", onPrimary, primary),
        Triple("onPrimaryContainer/primaryContainer", onPrimaryContainer, primaryContainer),
        Triple("onSecondaryContainer/secondaryContainer", onSecondaryContainer, secondaryContainer),
    )

    private fun assertScheme(name: String, scheme: ColorScheme) {
        scheme.textPairs().forEach { (pair, fg, bg) ->
            val r = ratio(fg, bg)
            assertTrue("$name $pair is ${"%.2f".format(r)}:1, needs 4.5:1", r >= 4.5)
        }
        val fab = ratio(scheme.onTertiary, scheme.tertiary)
        assertTrue("$name FAB icon is ${"%.2f".format(fab)}:1, needs 3:1", fab >= 3.0)
    }

    @Test fun lightSchemeMeetsContrast() = assertScheme("light", LightColors)

    @Test fun darkSchemeMeetsContrast() = assertScheme("dark", DarkColors)

    @Test fun highlightColoursKeepDarkInkReadable() {
        val ink = LightColors.onSurface
        HighlightColors.all.forEach { assertTrue(ratio(ink, it) >= 4.5) }
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.kent.windbook.ui.theme.ColorContrastTest'`
Expected: compilation FAILS with `Unresolved reference: LightColors`.

- [ ] **Step 3: Add the colour schemes**

`app/src/main/kotlin/dev/kent/windbook/ui/theme/Color.kt`:

```kotlin
package dev.kent.windbook.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Cream paper and bronze gold (reference image 01). Values are pinned by ColorContrastTest. */
internal val LightColors = lightColorScheme(
    primary = Color(0xFF7E5A26),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEBCF9C),
    onPrimaryContainer = Color(0xFF3B2A1A),
    inversePrimary = Color(0xFFD2A464),
    secondary = Color(0xFF7E5A26),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEBCF9C),
    onSecondaryContainer = Color(0xFF3B2A1A),
    tertiary = Color(0xFFB8894A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEBCF9C),
    onTertiaryContainer = Color(0xFF3B2A1A),
    background = Color(0xFFF7EFDF),
    onBackground = Color(0xFF3B2A1A),
    surface = Color(0xFFF7EFDF),
    onSurface = Color(0xFF3B2A1A),
    surfaceVariant = Color(0xFFF2E8D3),
    onSurfaceVariant = Color(0xFF725E48),
    surfaceTint = Color(0xFFB8894A),
    inverseSurface = Color(0xFF3B2A1A),
    inverseOnSurface = Color(0xFFF7EFDF),
    outline = Color(0xFFA38B6B),
    outlineVariant = Color(0xFFE3D3B4),
    surfaceBright = Color(0xFFFCF7EC),
    surfaceDim = Color(0xFFE8DCC2),
    surfaceContainerLowest = Color(0xFFFFFBF3),
    surfaceContainerLow = Color(0xFFFCF7EC),
    surfaceContainer = Color(0xFFF2E8D3),
    surfaceContainerHigh = Color(0xFFEDE2CB),
    surfaceContainerHighest = Color(0xFFE8DCC2),
)

/** Warm dark: brown-charcoal paper, cream ink, bronze accent. */
internal val DarkColors = darkColorScheme(
    primary = Color(0xFFD2A464),
    onPrimary = Color(0xFF17130F),
    primaryContainer = Color(0xFF5A4426),
    onPrimaryContainer = Color(0xFFEFE4CF),
    inversePrimary = Color(0xFF7E5A26),
    secondary = Color(0xFFD2A464),
    onSecondary = Color(0xFF17130F),
    secondaryContainer = Color(0xFF5A4426),
    onSecondaryContainer = Color(0xFFEFE4CF),
    tertiary = Color(0xFFD2A464),
    onTertiary = Color(0xFF17130F),
    tertiaryContainer = Color(0xFF5A4426),
    onTertiaryContainer = Color(0xFFEFE4CF),
    background = Color(0xFF17130F),
    onBackground = Color(0xFFEFE4CF),
    surface = Color(0xFF17130F),
    onSurface = Color(0xFFEFE4CF),
    surfaceVariant = Color(0xFF2A221A),
    onSurfaceVariant = Color(0xFFA89880),
    surfaceTint = Color(0xFFD2A464),
    inverseSurface = Color(0xFFEFE4CF),
    inverseOnSurface = Color(0xFF17130F),
    outline = Color(0xFF6E5E4A),
    outlineVariant = Color(0xFF3A3026),
    surfaceBright = Color(0xFF342A20),
    surfaceDim = Color(0xFF17130F),
    surfaceContainerLowest = Color(0xFF120F0C),
    surfaceContainerLow = Color(0xFF221C16),
    surfaceContainer = Color(0xFF2A221A),
    surfaceContainerHigh = Color(0xFF342A20),
    surfaceContainerHighest = Color(0xFF3E3227),
)

/** Highlight colours for M4, defined now so the palette stays in one place. */
object HighlightColors {
    val Yellow = Color(0xFFF3D27A)
    val Red = Color(0xFFE58C7A)
    val Green = Color(0xFF8CC9A0)
    val Blue = Color(0xFF82B4E0)
    val Purple = Color(0xFFB49AD8)
    val all = listOf(Yellow, Red, Green, Blue, Purple)
}
```

- [ ] **Step 4: Run the contrast test**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.kent.windbook.ui.theme.ColorContrastTest'`
Expected: 3 tests PASS. If one fails, the message names the pair. Darken the foreground until it passes, and copy the new value into the spec table (Step 9).

- [ ] **Step 5: Add the font and licence**

```bash
mkdir -p app/src/main/res/font app/src/main/assets/licenses
curl -sL -o app/src/main/res/font/literata.ttf 'https://github.com/google/fonts/raw/main/ofl/literata/Literata%5Bopsz%2Cwght%5D.ttf'
curl -sL -o app/src/main/assets/licenses/literata-OFL.txt 'https://github.com/google/fonts/raw/main/ofl/literata/OFL.txt'
sha256sum app/src/main/res/font/literata.ttf app/src/main/assets/licenses/literata-OFL.txt
```

Expected hashes: `b41138c9…f274440` for the TTF and `8742963…7614e` for the OFL. If upstream changed, run `fc-query --format='%{charset}\n' app/src/main/res/font/literata.ttf`. Confirm the output contains `1a0-1a1`, `1af-1b0` and a range covering `1ea0-1ef9` (Vietnamese), then record the new hash in this plan.

- [ ] **Step 6: Add typography and shapes**

`app/src/main/kotlin/dev/kent/windbook/ui/theme/Type.kt`:

```kotlin
package dev.kent.windbook.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.kent.windbook.R

/** Literata variable font (SIL OFL 1.1). One file, three named weights. */
val Literata = FontFamily(
    Font(R.font.literata, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.literata, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.literata, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private fun TextStyle.serif(weight: FontWeight) = copy(fontFamily = Literata, fontWeight = weight)

private val Base = Typography()

/** Serif for display/headline/title (screen and book titles); system sans for body and labels. */
val WindBookTypography = Typography(
    displayLarge = Base.displayLarge.serif(FontWeight.Bold),
    displayMedium = Base.displayMedium.serif(FontWeight.Bold),
    displaySmall = Base.displaySmall.serif(FontWeight.Bold),
    headlineLarge = Base.headlineLarge.serif(FontWeight.Bold),
    headlineMedium = Base.headlineMedium.serif(FontWeight.Bold),
    headlineSmall = Base.headlineSmall.serif(FontWeight.Bold),
    titleLarge = Base.titleLarge.serif(FontWeight.SemiBold),
    titleMedium = Base.titleMedium.serif(FontWeight.SemiBold),
    titleSmall = Base.titleSmall.serif(FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge,
    bodyMedium = Base.bodyMedium,
    bodySmall = Base.bodySmall,
    labelLarge = Base.labelLarge,
    labelMedium = Base.labelMedium,
    labelSmall = Base.labelSmall,
)
```

If the compiler reports that `FontVariation` needs an opt-in, annotate `Literata` with `@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)`.

`app/src/main/kotlin/dev/kent/windbook/ui/theme/Shape.kt`:

```kotlin
package dev.kent.windbook.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

val WindBookShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Search field, sort button, filter chips, option pills. */
val PillShape: Shape = RoundedCornerShape(percent = 50)

/** Book cover: tighter corners on the spine (start) side. */
val CoverShape: Shape = RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 6.dp, bottomEnd = 6.dp)
```

- [ ] **Step 7: Rewrite the theme**

`app/src/main/kotlin/dev/kent/windbook/ui/theme/Theme.kt` (whole file):

```kotlin
package dev.kent.windbook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** App chrome palette (cream & bronze, warm dark). The reading page has its own themes (M2). */
@Composable
fun WindBookTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = WindBookTypography,
        shapes = WindBookShapes,
        content = content,
    )
}
```

- [ ] **Step 8: Window background per mode (no white/cream flash at launch)**

`app/src/main/res/values/colors.xml`:

```xml
<resources>
    <color name="ic_launcher_background">#F8F4EC</color>
    <color name="window_background">#F7EFDF</color>
</resources>
```

`app/src/main/res/values-night/colors.xml`:

```xml
<resources>
    <color name="window_background">#17130F</color>
</resources>
```

In `app/src/main/res/values/themes.xml`, change the item to:

```xml
        <item name="android:windowBackground">@color/window_background</item>
```

- [ ] **Step 9: Update the spec palette**

In `docs/superpowers/specs/2026-10-14-windbook-look-and-feel-design.md` §3.1:
- `onSurfaceVariant` light `#7A6650` → `#725E48`
- `primary` light `#8C6430` → `#7E5A26`

Replace the "Measured contrast" paragraph with:

```
Measured contrast (WCAG, pinned by `ColorContrastTest`): every text colour (`onSurface`, `onSurfaceVariant`, `primary`) is ≥ 4.5:1 on `surface`, `surfaceContainerLow`, `surfaceContainer` and `surfaceContainerHigh` in both schemes. The light `onSurfaceVariant` and `primary` were darkened from the mockup to reach this on the tab bar. White on light `primary` is 6.22. The FAB's white icon on gold is 3.13 (icon only, needs 3:1).
```

- [ ] **Step 10: Run the app test suite and lint**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug`
Expected: all tests PASS (existing 25+ and the 3 new ones), lint reports no new errors.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/kotlin/dev/kent/windbook/ui/theme app/src/main/res/font app/src/main/assets/licenses \
  app/src/main/res/values/colors.xml app/src/main/res/values-night/colors.xml app/src/main/res/values/themes.xml \
  app/src/test/kotlin/dev/kent/windbook/ui/theme docs/superpowers/specs/2026-10-14-windbook-look-and-feel-design.md
git commit -m "feat(app): cream & bronze colour schemes, Literata typography and shapes; drop dynamic colour"
```

---

### Task 2: Library restyle and shared components

**Files:**
- Create: `app/src/main/kotlin/dev/kent/windbook/ui/components/BookCover.kt`
- Create: `app/src/main/kotlin/dev/kent/windbook/ui/components/ProgressLine.kt`
- Create: `app/src/main/kotlin/dev/kent/windbook/ui/components/Pill.kt`
- Create: `app/src/main/kotlin/dev/kent/windbook/ui/components/EmptyState.kt`
- Create: `app/src/main/kotlin/dev/kent/windbook/library/components/LibraryHeader.kt`
- Create: `app/src/main/kotlin/dev/kent/windbook/library/components/BookCell.kt`
- Modify: `app/src/main/kotlin/dev/kent/windbook/library/LibraryScreen.kt` (rewrite, keeps `LibraryRoute`, `LibraryActions`, `LibraryScreen` signatures)
- Modify: `app/src/main/kotlin/dev/kent/windbook/library/LibraryViewModel.kt` (`bookCount`)
- Modify: `app/src/main/kotlin/dev/kent/windbook/ui/icons/WindBookIcons.kt` (+ `MoreHoriz`, `Check`)
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-vi/strings.xml`
- Test: `app/src/test/kotlin/dev/kent/windbook/library/LibraryScreenTest.kt`, `app/src/test/kotlin/dev/kent/windbook/library/LibraryViewModelTest.kt`, `app/src/test/kotlin/dev/kent/windbook/ui/components/BookCoverTest.kt`

**Interfaces:**
- Consumes (Task 1): `PillShape`, `CoverShape`, `MaterialTheme.colorScheme.*` roles, `MaterialTheme.typography.*`.
- Produces:
  - `@Composable fun BookCover(bookId: String, title: String, coverPath: String?, modifier: Modifier = Modifier)`
  - `internal fun generatedCoverTint(bookId: String): Color`
  - `@Composable fun ProgressLine(progression: Double, modifier: Modifier = Modifier)`
  - `fun formatPercent(progression: Double): String` → `"42%"`
  - `@Composable fun Pill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, leadingIcon: ImageVector? = null)`
  - `@Composable fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: () -> Unit = {})`
  - `LibraryUiState.bookCount: Int` (all books, unfiltered)
  - `const val LIBRARY_SEARCH_TAG = "library-search"`
  - `WindBookIcons.MoreHoriz`, `WindBookIcons.Check`

- [ ] **Step 1: Add strings**

In `app/src/main/res/values/strings.xml`:
- **Delete** `cd_close_search` and `cd_sort`. Both are replaced below.
- Add before `</resources>`:

```xml
    <string name="tab_library">Library</string>
    <plurals name="book_count">
        <item quantity="one">%d book</item>
        <item quantity="other">%d books</item>
    </plurals>
    <string name="sort">Sort</string>
    <string name="cd_clear_search">Clear search</string>
    <string name="cd_selected">Selected</string>
    <string name="cd_more_options">More options for %1$s</string>
    <string name="finished_caption">Finished</string>
```

In `app/src/main/res/values-vi/strings.xml`, delete the same two keys and add:

```xml
    <string name="tab_library">Kệ sách</string>
    <plurals name="book_count">
        <item quantity="other">%d cuốn</item>
    </plurals>
    <string name="sort">Sắp xếp</string>
    <string name="cd_clear_search">Xoá tìm kiếm</string>
    <string name="cd_selected">Đang chọn</string>
    <string name="cd_more_options">Tùy chọn khác cho %1$s</string>
    <string name="finished_caption">Đã đọc xong</string>
```

- [ ] **Step 2: Write the failing tests**

Replace `app/src/test/kotlin/dev/kent/windbook/library/LibraryScreenTest.kt` with:

```kotlin
package dev.kent.windbook.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import dev.kent.windbook.core.model.Book
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.LibraryItem
import dev.kent.windbook.core.model.LibraryLayout
import dev.kent.windbook.core.model.LibrarySort
import dev.kent.windbook.core.model.ReadingStatus
import dev.kent.windbook.library.components.LIBRARY_SEARCH_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class LibraryScreenTest {
    @get:Rule val compose = createComposeRule()

    private val opened = mutableListOf<String>()
    private val statuses = mutableListOf<Pair<String, ReadingStatus>>()
    private val queries = mutableListOf<String>()
    private val sorts = mutableListOf<LibrarySort>()
    private var importClicks = 0

    private val actions = LibraryActions(
        onOpenBook = { opened += it },
        onImportClick = { importClicks++ },
        onQueryChange = { queries += it },
        onFilterChange = {},
        onSortChange = { sorts += it },
        onLayoutChange = {},
        onSetStatus = { id, status -> statuses += id to status },
        onDelete = {},
    )

    private val dune = LibraryItem(
        Book("1", "Dune", "Frank Herbert", BookFormat.EPUB, "/f", 1, "s", null, ReadingStatus.READING, 1, 5),
        0.42,
    )

    private fun library(vararg items: LibraryItem, layout: LibraryLayout = LibraryLayout.GRID, continueReading: LibraryItem? = null) =
        LibraryUiState(
            items = items.toList(),
            continueReading = continueReading,
            layout = layout,
            bookCount = items.size,
            isLibraryEmpty = items.isEmpty(),
        )

    @Test fun emptyLibraryShowsAddBooks() {
        compose.setContent { LibraryScreen(LibraryUiState(isLibraryEmpty = true), actions) }
        compose.onNodeWithText("Add books").assertIsDisplayed().performClick()
        assertEquals(1, importClicks)
    }

    @Config(qualifiers = "vi")
    @Test fun emptyLibraryShowsVietnameseAddBooks() {
        compose.setContent { LibraryScreen(LibraryUiState(isLibraryEmpty = true), actions) }
        compose.onNodeWithText("Thêm sách").assertIsDisplayed()
    }

    @Test fun headerShowsTotalBookCount() {
        compose.setContent { LibraryScreen(library(dune).copy(bookCount = 12), actions) }
        compose.onNodeWithText("Library").assertIsDisplayed()
        compose.onNodeWithText("12 books").assertIsDisplayed()
    }

    @Config(qualifiers = "vi")
    @Test fun headerCountInVietnamese() {
        compose.setContent { LibraryScreen(library(dune).copy(bookCount = 12), actions) }
        compose.onNodeWithText("12 cuốn").assertIsDisplayed()
    }

    @Test fun searchFieldIsAlwaysVisibleAndReportsTyping() {
        compose.setContent { LibraryScreen(library(dune), actions) }
        compose.onNodeWithTag(LIBRARY_SEARCH_TAG).assertIsDisplayed().performTextInput("dun")
        assertEquals("dun", queries.last())
    }

    @Test fun sortPillOpensMenuAndSelects() {
        compose.setContent { LibraryScreen(library(dune), actions) }
        compose.onNodeWithText("Sort").performClick()
        compose.onNodeWithContentDescription("Selected").assertIsDisplayed() // RECENT is the current sort
        compose.onNodeWithText("Title").performClick()
        assertEquals(listOf(LibrarySort.TITLE), sorts)
    }

    @Test fun continueReadingCardOpensBook() {
        compose.setContent { LibraryScreen(library(dune, continueReading = dune), actions) }
        compose.onNodeWithText("CONTINUE READING").assertIsDisplayed()
        compose.onNodeWithText("42%").assertIsDisplayed()
        compose.onNodeWithText("CONTINUE READING").performClick()
        assertEquals(listOf("1"), opened)
    }

    @Test fun moreButtonOnCoverOpensBookMenu() {
        compose.setContent { LibraryScreen(library(dune), actions) }
        compose.onNodeWithContentDescription("More options for Dune").performClick()
        compose.onNodeWithText("Mark as finished").performClick()
        assertEquals(listOf("1" to ReadingStatus.FINISHED), statuses)
    }

    @Test fun longPressStillOffersStatusChange() {
        compose.setContent { LibraryScreen(library(dune, layout = LibraryLayout.LIST), actions) }
        compose.onNodeWithText("Dune").performTouchInput { longClick() }
        compose.onNodeWithText("Mark as finished").performClick()
        assertEquals(listOf("1" to ReadingStatus.FINISHED), statuses)
    }

    @Test fun finishedBookShowsFinishedCaption() {
        val done = dune.copy(book = dune.book.copy(status = ReadingStatus.FINISHED))
        compose.setContent { LibraryScreen(library(done), actions) }
        compose.onNodeWithText("Finished").assertIsDisplayed()
    }

    @Test fun fabAddsBooks() {
        compose.setContent { LibraryScreen(library(dune), actions) }
        compose.onNodeWithContentDescription("Add books").performClick()
        assertEquals(1, importClicks)
    }

    @Test fun fabWhileImportingSaysImporting() {
        compose.setContent { LibraryScreen(library(dune).copy(isImporting = true), actions) }
        compose.onNodeWithContentDescription("Importing…").assertIsDisplayed()
    }
}
```

Create `app/src/test/kotlin/dev/kent/windbook/ui/components/BookCoverTest.kt`:

```kotlin
package dev.kent.windbook.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookCoverTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tintIsStablePerBook() {
        assertEquals(generatedCoverTint("book-a"), generatedCoverTint("book-a"))
    }

    @Test fun generatedCoverTitleIsDecorativeForAccessibility() {
        // The title under the cover is what TalkBack reads; the cover's own copy must not duplicate it.
        compose.setContent { BookCover("x", "A very long title that goes on and on and on", coverPath = null) }
        compose.onNodeWithText("A very long title", substring = true).assertDoesNotExist()
        compose.onNodeWithText("A very long title", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun percentIsRounded() {
        assertEquals("42%", formatPercent(0.4249))
        assertEquals("100%", formatPercent(0.999))
    }
}
```

In `app/src/test/kotlin/dev/kent/windbook/library/LibraryViewModelTest.kt`, in `importThenSearchAndFilter`, replace

```kotlin
            assertEquals("Emma", state.items.single().book.title)
```

with

```kotlin
            assertEquals("Emma", state.items.single().book.title)
            // The header count is the whole library, not the search result.
            assertEquals(2, state.bookCount)
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.kent.windbook.library.*' --tests 'dev.kent.windbook.ui.components.*'`
Expected: compilation FAILS (`Unresolved reference: bookCount`, `LIBRARY_SEARCH_TAG`, `generatedCoverTint`).

- [ ] **Step 4: Add the icons**

In `app/src/main/kotlin/dev/kent/windbook/ui/icons/WindBookIcons.kt`, add after `Back`:

```kotlin
    val MoreHoriz = icon(
        "MoreHoriz",
        "M6,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM18,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z",
    )
    val Check = icon("Check", "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z")
```

- [ ] **Step 5: Add the shared components**

`app/src/main/kotlin/dev/kent/windbook/ui/components/BookCover.kt`:

```kotlin
package dev.kent.windbook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.kent.windbook.ui.theme.CoverShape
import java.io.File

private val GeneratedCoverTints = listOf(
    Color(0xFFEFE2C4), Color(0xFFD9E8E2), Color(0xFFF3E7D3),
    Color(0xFFE6D5C3), Color(0xFFDCE3EC), Color(0xFFEAD9DF),
)
private val GeneratedCoverInk = Color(0xFF3B2A1A)

/** A warm paper tint for books without a cover image, stable for a given book. */
internal fun generatedCoverTint(bookId: String): Color =
    GeneratedCoverTints[Math.floorMod(bookId.hashCode(), GeneratedCoverTints.size)]

/** 2:3 book cover with a soft shadow. Decorative: callers show the title as text next to it. */
@Composable
fun BookCover(bookId: String, title: String, coverPath: String?, modifier: Modifier = Modifier) {
    val shaped = modifier.aspectRatio(2f / 3f).shadow(4.dp, CoverShape).clip(CoverShape)
    if (coverPath != null) {
        AsyncImage(model = File(coverPath), contentDescription = null, contentScale = ContentScale.Crop, modifier = shaped)
    } else {
        Box(shaped.background(generatedCoverTint(bookId)).padding(8.dp).clearAndSetSemantics {}) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = GeneratedCoverInk,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
```

`app/src/main/kotlin/dev/kent/windbook/ui/components/ProgressLine.kt`:

```kotlin
package dev.kent.windbook.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Thin gold reading-progress bar. */
@Composable
fun ProgressLine(progression: Double, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progression.toFloat().coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(3.dp),
        color = MaterialTheme.colorScheme.tertiary,
        trackColor = MaterialTheme.colorScheme.outlineVariant,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

fun formatPercent(progression: Double): String = "${(progression * 100).roundToInt()}%"
```

`app/src/main/kotlin/dev/kent/windbook/ui/components/Pill.kt`:

```kotlin
package dev.kent.windbook.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.kent.windbook.ui.theme.PillShape

/** Rounded option button (reference images 01 and 02). Selected pills use the gold container. */
@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.minimumInteractiveComponentSize().heightIn(min = 36.dp),
        shape = PillShape,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerLow,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        border = if (selected) null else BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            leadingIcon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
```

`app/src/main/kotlin/dev/kent/windbook/ui/components/EmptyState.kt`:

```kotlin
package dev.kent.windbook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Centered serif title, a muted explanation, and an optional filled action. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}
```

- [ ] **Step 6: Add `bookCount` to the ViewModel**

In `LibraryViewModel.kt`, add the field to `LibraryUiState` after `isLibraryEmpty`:

```kotlin
    val isLibraryEmpty: Boolean = true,
    /** Every book in the library, ignoring search and filter (header count). */
    val bookCount: Int = 0,
)
```

and in the `combine` block set it:

```kotlin
            isLibraryEmpty = all.isEmpty(),
            bookCount = all.size,
        )
```

- [ ] **Step 7: Add the library header components**

`app/src/main/kotlin/dev/kent/windbook/library/components/LibraryHeader.kt`:

```kotlin
package dev.kent.windbook.library.components

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kent.windbook.R
import dev.kent.windbook.core.model.LibraryLayout
import dev.kent.windbook.core.model.LibrarySort
import dev.kent.windbook.core.model.StatusFilter
import dev.kent.windbook.ui.components.Pill
import dev.kent.windbook.ui.icons.WindBookIcons
import dev.kent.windbook.ui.theme.PillShape

const val LIBRARY_SEARCH_TAG = "library-search"

/** "Library │ 12 books", then the search pill and the sort pill (reference image 01). */
@Composable
internal fun LibraryHeader(
    bookCount: Int,
    query: String,
    sort: LibrarySort,
    onQueryChange: (String) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        Row {
            Text(
                stringResource(R.string.tab_library),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                "│",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.alignByBaseline().padding(horizontal = 10.dp),
            )
            Text(
                pluralStringResource(R.plurals.book_count, bookCount, bookCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchPill(query, onQueryChange, Modifier.weight(1f))
            SortPill(sort, onSortChange)
        }
    }
}

@Composable
private fun SearchPill(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.testTag(LIBRARY_SEARCH_TAG),
        placeholder = {
            Text(stringResource(R.string.search_books), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingIcon = { Icon(WindBookIcons.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(WindBookIcons.Close, contentDescription = stringResource(R.string.cd_clear_search))
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = PillShape,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceContainerLow,
            unfocusedContainerColor = colors.surfaceContainerLow,
            unfocusedBorderColor = colors.outlineVariant,
            focusedBorderColor = colors.primary,
        ),
    )
}

@Composable
private fun SortPill(sort: LibrarySort, onSortChange: (LibrarySort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Pill(text = stringResource(R.string.sort), onClick = { open = true }, leadingIcon = WindBookIcons.Sort)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LibrarySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes())) },
                    trailingIcon = if (option == sort) {
                        { Icon(WindBookIcons.Check, contentDescription = stringResource(R.string.cd_selected)) }
                    } else {
                        null
                    },
                    onClick = {
                        onSortChange(option)
                        open = false
                    },
                )
            }
        }
    }
}

/** Status pills plus the grid/list toggle at the end of the row. */
@Composable
internal fun FilterRow(
    selected: StatusFilter,
    layout: LibraryLayout,
    onSelect: (StatusFilter) -> Unit,
    onLayoutChange: (LibraryLayout) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusFilter.entries.forEach { filter ->
                Pill(stringResource(filter.labelRes()), onClick = { onSelect(filter) }, selected = filter == selected)
            }
        }
        IconButton(onClick = { onLayoutChange(if (layout == LibraryLayout.GRID) LibraryLayout.LIST else LibraryLayout.GRID) }) {
            Icon(
                if (layout == LibraryLayout.GRID) WindBookIcons.List else WindBookIcons.Grid,
                contentDescription = stringResource(R.string.cd_change_layout),
            )
        }
    }
}

@StringRes
internal fun LibrarySort.labelRes(): Int = when (this) {
    LibrarySort.RECENT -> R.string.sort_recent
    LibrarySort.TITLE -> R.string.sort_title
    LibrarySort.AUTHOR -> R.string.sort_author
    LibrarySort.ADDED -> R.string.sort_added
    LibrarySort.PROGRESS -> R.string.sort_progress
}

@StringRes
internal fun StatusFilter.labelRes(): Int = when (this) {
    StatusFilter.ALL -> R.string.filter_all
    StatusFilter.READING -> R.string.filter_reading
    StatusFilter.TO_READ -> R.string.filter_to_read
    StatusFilter.FINISHED -> R.string.filter_finished
}
```

- [ ] **Step 8: Add the book cell components**

`app/src/main/kotlin/dev/kent/windbook/library/components/BookCell.kt`:

```kotlin
package dev.kent.windbook.library.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kent.windbook.R
import dev.kent.windbook.core.model.LibraryItem
import dev.kent.windbook.core.model.LibraryLayout
import dev.kent.windbook.core.model.ReadingStatus
import dev.kent.windbook.library.LibraryActions
import dev.kent.windbook.ui.components.BookCover
import dev.kent.windbook.ui.components.ProgressLine
import dev.kent.windbook.ui.components.formatPercent
import dev.kent.windbook.ui.icons.WindBookIcons

@Composable
internal fun ContinueReadingCard(item: LibraryItem, onOpen: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = { onOpen(item.book.id) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(item.book.id, item.book.title, item.book.coverPath, Modifier.width(52.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.continue_reading).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    color = colors.onSurfaceVariant,
                )
                Text(item.book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                item.book.author?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressLine(item.progression, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(formatPercent(item.progression), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookCell(item: LibraryItem, layout: LibraryLayout, actions: LibraryActions) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val book = item.book
    val clickable = Modifier.combinedClickable(
        onClick = { actions.onOpenBook(book.id) },
        onLongClick = { menu = true },
    )
    Box {
        if (layout == LibraryLayout.GRID) {
            Column(clickable) {
                Box {
                    BookCover(book.id, book.title, book.coverPath, Modifier.fillMaxWidth())
                    MoreButton(book.title, onClick = { menu = true }, modifier = Modifier.align(Alignment.TopEnd))
                }
                if (item.progression > 0) {
                    Spacer(Modifier.height(6.dp))
                    ProgressLine(item.progression)
                }
                Spacer(Modifier.height(6.dp))
                Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BookCaption(item)
            }
        } else {
            Row(clickable.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BookCover(book.id, book.title, book.coverPath, Modifier.width(48.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BookCaption(item)
                    if (item.progression > 0) {
                        Spacer(Modifier.height(6.dp))
                        ProgressLine(item.progression)
                    }
                }
                MoreButton(book.title, onClick = { menu = true })
            }
        }
        BookMenu(
            expanded = menu,
            item = item,
            onDismiss = { menu = false },
            onSetStatus = actions.onSetStatus,
            onDeleteRequest = {
                menu = false
                confirmDelete = true
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title, book.title)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    actions.onDelete(book.id)
                    confirmDelete = false
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun BookCaption(item: LibraryItem) {
    val finished = item.book.status == ReadingStatus.FINISHED
    val text = if (finished) stringResource(R.string.finished_caption) else item.book.author ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (finished) {
            Icon(WindBookIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MoreButton(title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Box(
            Modifier.size(28.dp).background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                WindBookIcons.MoreHoriz,
                contentDescription = stringResource(R.string.cd_more_options, title),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun BookMenu(
    expanded: Boolean,
    item: LibraryItem,
    onDismiss: () -> Unit,
    onSetStatus: (String, ReadingStatus) -> Unit,
    onDeleteRequest: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        listOf(
            ReadingStatus.TO_READ to R.string.mark_to_read,
            ReadingStatus.READING to R.string.mark_reading,
            ReadingStatus.FINISHED to R.string.mark_finished,
        ).filter { it.first != item.book.status }.forEach { (status, label) ->
            DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                onSetStatus(item.book.id, status)
                onDismiss()
            })
        }
        DropdownMenuItem(text = { Text(stringResource(R.string.delete_book)) }, onClick = onDeleteRequest)
    }
}
```

- [ ] **Step 9: Rewrite `LibraryScreen.kt`**

Keep the existing `LibraryActions`, `IMPORT_MIME_TYPES`, `LibraryRoute` (unchanged body) and `ImportError.messageRes()`. Replace everything from `@OptIn(ExperimentalMaterial3Api::class)` / `fun LibraryScreen` to the end of the file with:

```kotlin
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    actions: LibraryActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = {
            if (!state.isLibraryEmpty) {
                LibraryHeader(
                    bookCount = state.bookCount,
                    query = state.query,
                    sort = state.sort,
                    onQueryChange = actions.onQueryChange,
                    onSortChange = actions.onSortChange,
                    modifier = Modifier.statusBarsPadding(),
                )
            }
        },
        floatingActionButton = {
            if (!state.isLibraryEmpty) AddBooksFab(importing = state.isImporting, onClick = actions.onImportClick)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLibraryEmpty) {
            EmptyState(
                title = stringResource(R.string.tab_library),
                body = stringResource(R.string.empty_library),
                actionLabel = stringResource(R.string.add_books),
                onAction = actions.onImportClick,
                modifier = Modifier.padding(padding),
            )
        } else {
            LibraryContent(state, actions, Modifier.padding(padding))
        }
    }
}

@Composable
private fun AddBooksFab(importing: Boolean, onClick: () -> Unit) {
    val label = stringResource(if (importing) R.string.importing else R.string.add_books)
    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.tertiary,
        contentColor = MaterialTheme.colorScheme.onTertiary,
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        if (importing) {
            CircularProgressIndicator(Modifier.size(24.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
        } else {
            Icon(WindBookIcons.Add, contentDescription = null)
        }
    }
}

@Composable
private fun LibraryContent(state: LibraryUiState, actions: LibraryActions, modifier: Modifier = Modifier) {
    val columns = if (state.layout == LibraryLayout.GRID) GridCells.Adaptive(minSize = 100.dp) else GridCells.Fixed(1)
    LazyVerticalGrid(
        columns = columns,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            FilterRow(state.filter, state.layout, actions.onFilterChange, actions.onLayoutChange)
        }
        state.continueReading?.takeIf { state.query.isBlank() }?.let { current ->
            item(span = { GridItemSpan(maxLineSpan) }) { ContinueReadingCard(current, actions.onOpenBook) }
        }
        items(state.items, key = { it.book.id }) { item -> BookCell(item, state.layout, actions) }
    }
}
```

Set the imports of `LibraryScreen.kt` to exactly:

```kotlin
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kent.windbook.R
import dev.kent.windbook.core.data.importer.ImportError
import dev.kent.windbook.core.model.LibraryLayout
import dev.kent.windbook.core.model.LibrarySort
import dev.kent.windbook.core.model.ReadingStatus
import dev.kent.windbook.core.model.StatusFilter
import dev.kent.windbook.library.components.BookCell
import dev.kent.windbook.library.components.ContinueReadingCard
import dev.kent.windbook.library.components.FilterRow
import dev.kent.windbook.library.components.LibraryHeader
import dev.kent.windbook.ui.components.EmptyState
import dev.kent.windbook.ui.icons.WindBookIcons
```

- [ ] **Step 10: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.kent.windbook.library.*' --tests 'dev.kent.windbook.ui.components.*'`
Expected: `LibraryScreenTest` (12), `BookCoverTest` (3) and `LibraryViewModelTest` (5) PASS.

- [ ] **Step 11: Full app suite and lint**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug`
Expected: PASS, no lint errors. Lint may warn `UnusedResources` for strings first used in later tasks. That is expected until Task 5.

- [ ] **Step 12: Commit**

```bash
git add app/src/main/kotlin/dev/kent/windbook/ui app/src/main/kotlin/dev/kent/windbook/library \
  app/src/main/res/values/strings.xml app/src/main/res/values-vi/strings.xml \
  app/src/test/kotlin/dev/kent/windbook/library app/src/test/kotlin/dev/kent/windbook/ui/components
git commit -m "feat(app): restyle library (header count, search and sort pills, shadowed covers, gold FAB)"
```

---

### Task 3: Reading tab

**Files:**
- Create: `app/src/main/kotlin/dev/kent/windbook/ui/components/ScreenTitle.kt`
- Create: `app/src/main/kotlin/dev/kent/windbook/reading/ReadingViewModel.kt`
- Create: `app/src/main/kotlin/dev/kent/windbook/reading/ReadingScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-vi/strings.xml`
- Test: `app/src/test/kotlin/dev/kent/windbook/reading/ReadingNowTest.kt`, `app/src/test/kotlin/dev/kent/windbook/reading/ReadingScreenTest.kt`

**Interfaces:**
- Consumes (Task 2): `BookCover`, `ProgressLine`, `formatPercent`, `EmptyState`.
- Produces:
  - `@Composable fun ScreenTitle(text: String, modifier: Modifier = Modifier)`
  - `data class ReadingUiState(val loaded: Boolean = false, val current: LibraryItem? = null, val others: List<LibraryItem> = emptyList())`
  - `internal fun readingNow(all: List<LibraryItem>): ReadingUiState`
  - `@HiltViewModel class ReadingViewModel @Inject constructor(repository: LibraryRepository)` with `val state: StateFlow<ReadingUiState>`
  - `@Composable fun ReadingRoute(onOpenBook: (String) -> Unit, onBrowseLibrary: () -> Unit, viewModel: ReadingViewModel = hiltViewModel())`
  - `@Composable fun ReadingScreen(state: ReadingUiState, onOpenBook: (String) -> Unit, onBrowseLibrary: () -> Unit)`

- [ ] **Step 1: Add strings**

`values/strings.xml`:

```xml
    <string name="tab_reading">Reading</string>
    <string name="reading_empty_title">No book in progress</string>
    <string name="reading_empty_body">Books you open appear here, newest first.</string>
    <string name="go_to_library">Go to library</string>
    <string name="continue_button">Continue</string>
```

`values-vi/strings.xml`:

```xml
    <string name="tab_reading">Đang đọc</string>
    <string name="reading_empty_title">Chưa có sách đang đọc</string>
    <string name="reading_empty_body">Sách bạn mở sẽ hiện ở đây, cuốn mới nhất ở trên cùng.</string>
    <string name="go_to_library">Đến kệ sách</string>
    <string name="continue_button">Đọc tiếp</string>
```

- [ ] **Step 2: Write the failing tests**

`app/src/test/kotlin/dev/kent/windbook/reading/ReadingNowTest.kt`:

```kotlin
package dev.kent.windbook.reading

import dev.kent.windbook.core.model.Book
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.LibraryItem
import dev.kent.windbook.core.model.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingNowTest {
    private fun item(id: String, status: ReadingStatus, opened: Long?, added: Long = 0) = LibraryItem(
        Book(id, "Book $id", null, BookFormat.EPUB, "/f/$id", 1, "sha-$id", null, status, added, opened),
        0.5,
    )

    @Test fun onlyReadingBooksNewestFirst() {
        val state = readingNow(
            listOf(
                item("a", ReadingStatus.READING, opened = 10),
                item("b", ReadingStatus.FINISHED, opened = 99),
                item("c", ReadingStatus.READING, opened = 30),
                item("d", ReadingStatus.TO_READ, opened = 50),
            ),
        )
        assertTrue(state.loaded)
        assertEquals("c", state.current?.book?.id)
        assertEquals(listOf("a"), state.others.map { it.book.id })
    }

    @Test fun neverOpenedReadingBookSortsLast() {
        val state = readingNow(
            listOf(
                item("x", ReadingStatus.READING, opened = null, added = 500),
                item("y", ReadingStatus.READING, opened = 1),
            ),
        )
        assertEquals("y", state.current?.book?.id)
        assertEquals(listOf("x"), state.others.map { it.book.id })
    }

    @Test fun noReadingBooksIsEmptyButLoaded() {
        val state = readingNow(listOf(item("a", ReadingStatus.TO_READ, opened = 1)))
        assertTrue(state.loaded)
        assertNull(state.current)
        assertTrue(state.others.isEmpty())
    }
}
```

`app/src/test/kotlin/dev/kent/windbook/reading/ReadingScreenTest.kt`:

```kotlin
package dev.kent.windbook.reading

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kent.windbook.core.model.Book
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.LibraryItem
import dev.kent.windbook.core.model.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ReadingScreenTest {
    @get:Rule val compose = createComposeRule()

    private val opened = mutableListOf<String>()
    private var browse = 0

    private fun item(id: String, title: String) = LibraryItem(
        Book(id, title, "Author $id", BookFormat.EPUB, "/f/$id", 1, "sha-$id", null, ReadingStatus.READING, 0, 1),
        0.37,
    )

    @Test fun emptyStateLeadsToLibrary() {
        compose.setContent { ReadingScreen(ReadingUiState(loaded = true), { opened += it }, { browse++ }) }
        compose.onNodeWithText("No book in progress").assertIsDisplayed()
        compose.onNodeWithText("Go to library").performClick()
        assertEquals(1, browse)
    }

    @Test fun nothingIsShownBeforeTheLibraryLoads() {
        compose.setContent { ReadingScreen(ReadingUiState(), { opened += it }, { browse++ }) }
        compose.onNodeWithText("No book in progress").assertDoesNotExist()
    }

    @Test fun continueOpensTheCurrentBook() {
        val state = ReadingUiState(loaded = true, current = item("1", "Dune"), others = listOf(item("2", "Emma")))
        compose.setContent { ReadingScreen(state, { opened += it }, { browse++ }) }
        compose.onNodeWithText("Dune").assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()
        assertEquals(listOf("1"), opened)
    }

    @Test fun otherBooksOpenFromTheList() {
        val state = ReadingUiState(loaded = true, current = item("1", "Dune"), others = listOf(item("2", "Emma")))
        compose.setContent { ReadingScreen(state, { opened += it }, { browse++ }) }
        compose.onNodeWithText("Emma").performClick()
        assertEquals(listOf("2"), opened)
    }

    @Config(qualifiers = "vi")
    @Test fun vietnameseTitle() {
        compose.setContent { ReadingScreen(ReadingUiState(loaded = true), { }, { }) }
        compose.onNodeWithText("Đang đọc").assertIsDisplayed()
        compose.onNodeWithText("Chưa có sách đang đọc").assertIsDisplayed()
    }
}
```

- [ ] **Step 3: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.kent.windbook.reading.*'`
Expected: compilation FAILS (`Unresolved reference: readingNow`, `ReadingScreen`).

- [ ] **Step 4: Add `ScreenTitle`**

`app/src/main/kotlin/dev/kent/windbook/ui/components/ScreenTitle.kt`:

```kotlin
package dev.kent.windbook.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Serif screen title used at the top of every tab. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}
```

- [ ] **Step 5: Add the ViewModel**

`app/src/main/kotlin/dev/kent/windbook/reading/ReadingViewModel.kt`:

```kotlin
package dev.kent.windbook.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kent.windbook.core.data.LibraryRepository
import dev.kent.windbook.core.model.LibraryItem
import dev.kent.windbook.core.model.ReadingStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** [loaded] is false until the library has been read once, so the empty state never flashes. */
data class ReadingUiState(
    val loaded: Boolean = false,
    val current: LibraryItem? = null,
    val others: List<LibraryItem> = emptyList(),
)

/** READING books, most recently opened first; books never opened go last, newest added first. */
internal fun readingNow(all: List<LibraryItem>): ReadingUiState {
    val reading = all
        .filter { it.book.status == ReadingStatus.READING }
        .sortedWith(
            compareByDescending<LibraryItem> { it.book.lastOpenedAt ?: Long.MIN_VALUE }
                .thenByDescending { it.book.addedAt },
        )
    return ReadingUiState(loaded = true, current = reading.firstOrNull(), others = reading.drop(1))
}

@HiltViewModel
class ReadingViewModel @Inject constructor(repository: LibraryRepository) : ViewModel() {
    val state: StateFlow<ReadingUiState> = repository.observeLibrary()
        .map(::readingNow)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingUiState())
}
```

- [ ] **Step 6: Add the screen**

`app/src/main/kotlin/dev/kent/windbook/reading/ReadingScreen.kt`:

```kotlin
package dev.kent.windbook.reading

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kent.windbook.R
import dev.kent.windbook.core.model.LibraryItem
import dev.kent.windbook.ui.components.BookCover
import dev.kent.windbook.ui.components.EmptyState
import dev.kent.windbook.ui.components.ProgressLine
import dev.kent.windbook.ui.components.ScreenTitle
import dev.kent.windbook.ui.components.formatPercent

@Composable
fun ReadingRoute(onOpenBook: (String) -> Unit, onBrowseLibrary: () -> Unit, viewModel: ReadingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReadingScreen(state, onOpenBook, onBrowseLibrary)
}

@Composable
fun ReadingScreen(state: ReadingUiState, onOpenBook: (String) -> Unit, onBrowseLibrary: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        ScreenTitle(stringResource(R.string.tab_reading))
        val current = state.current
        when {
            !state.loaded -> Unit
            current == null -> EmptyState(
                title = stringResource(R.string.reading_empty_title),
                body = stringResource(R.string.reading_empty_body),
                actionLabel = stringResource(R.string.go_to_library),
                onAction = onBrowseLibrary,
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = current.book.id) { CurrentBookCard(current, onOpenBook) }
                items(state.others, key = { it.book.id }) { ReadingRow(it, onOpenBook) }
            }
        }
    }
}

@Composable
private fun CurrentBookCard(item: LibraryItem, onOpen: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = { onOpen(item.book.id) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Row(Modifier.padding(16.dp)) {
            BookCover(item.book.id, item.book.title, item.book.coverPath, Modifier.width(96.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(item.book.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                item.book.author?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1)
                }
                Spacer(Modifier.height(12.dp))
                ProgressLine(item.progression)
                Spacer(Modifier.height(4.dp))
                Text(formatPercent(item.progression), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onOpen(item.book.id) }) { Text(stringResource(R.string.continue_button)) }
            }
        }
    }
}

@Composable
private fun ReadingRow(item: LibraryItem, onOpen: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { onOpen(item.book.id) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(item.book.id, item.book.title, item.book.coverPath, Modifier.width(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.book.author?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressLine(item.progression, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(formatPercent(item.progression), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
```

- [ ] **Step 7: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.kent.windbook.reading.*'`
Expected: `ReadingNowTest` (3) and `ReadingScreenTest` (5) PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/dev/kent/windbook/reading app/src/main/kotlin/dev/kent/windbook/ui/components/ScreenTitle.kt \
  app/src/main/res/values/strings.xml app/src/main/res/values-vi/strings.xml app/src/test/kotlin/dev/kent/windbook/reading
git commit -m "feat(app): Reading tab lists books in progress, newest first"
```

---

### Task 4: Notes and Settings tabs

**Files:**
- Create: `app/src/main/kotlin/dev/kent/windbook/notes/NotesScreen.kt`
- Create: `app/src/main/kotlin/dev/kent/windbook/settings/AppLanguage.kt`
- Create: `app/src/main/kotlin/dev/kent/windbook/settings/SettingsViewModel.kt`
- Create: `app/src/main/kotlin/dev/kent/windbook/settings/SettingsScreen.kt`
- Modify: `app/src/main/kotlin/dev/kent/windbook/di/AppModule.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-vi/strings.xml`
- Test: `app/src/test/kotlin/dev/kent/windbook/notes/NotesScreenTest.kt`, `app/src/test/kotlin/dev/kent/windbook/settings/AppLanguageTest.kt`, `app/src/test/kotlin/dev/kent/windbook/settings/SettingsViewModelTest.kt`, `app/src/test/kotlin/dev/kent/windbook/settings/SettingsScreenTest.kt`

**Interfaces:**
- Consumes: `ScreenTitle` (Task 3), `EmptyState` (Task 2), `LibraryRepository.observeLibrary()`, `BookFileStore.usableBytes()`.
- Produces:
  - `@Composable fun NotesScreen()`
  - `enum class AppLanguage(val tag: String, @StringRes val labelRes: Int) { SYSTEM, VIETNAMESE, ENGLISH; companion object { fun fromTags(tags: String): AppLanguage } }`
  - `interface AppLocaleController { fun current(): AppLanguage; fun set(language: AppLanguage) }`, `class AppCompatLocaleController : AppLocaleController`
  - `data class AppInfo(val versionName: String)`
  - `data class SettingsUiState(val language: AppLanguage = AppLanguage.SYSTEM, val bookCount: Int = 0, val totalBytes: Long = 0, val freeBytes: Long = 0, val versionName: String = "")`
  - `@HiltViewModel class SettingsViewModel @Inject constructor(repository: LibraryRepository, files: BookFileStore, locales: AppLocaleController, appInfo: AppInfo)` with `state: StateFlow<SettingsUiState>`, `fun setLanguage(language: AppLanguage)`
  - `@Composable fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel())`, `@Composable fun SettingsScreen(state: SettingsUiState, onLanguageChange: (AppLanguage) -> Unit)`

- [ ] **Step 1: Add strings**

`values/strings.xml`:

```xml
    <string name="tab_notes">Notes</string>
    <string name="notes_empty_title">Your highlights live here</string>
    <string name="notes_empty_body">Highlights and notes you make while reading will appear on this screen.</string>
    <string name="tab_settings">Settings</string>
    <string name="settings_language">Language</string>
    <string name="language_system">Automatic (system)</string>
    <string name="language_vi" translatable="false">Tiếng Việt</string>
    <string name="language_en" translatable="false">English</string>
    <string name="settings_storage">Storage</string>
    <string name="storage_books">Books</string>
    <string name="storage_files">Book files</string>
    <string name="storage_free">Free on device</string>
    <string name="settings_about">About</string>
    <string name="version">Version %1$s</string>
    <string name="licences">Open-source licences</string>
    <string name="licences_body" translatable="false">Literata typeface: SIL Open Font License 1.1\nReadium Kotlin Toolkit: BSD 3-Clause\nAndroidX, Hilt, Coil, Kotlin: Apache License 2.0</string>
    <string name="close">Close</string>
```

`values-vi/strings.xml`:

```xml
    <string name="tab_notes">Ghi chú</string>
    <string name="notes_empty_title">Highlight của bạn sẽ nằm ở đây</string>
    <string name="notes_empty_body">Những đoạn bạn highlight và ghi chú khi đọc sẽ hiện ở màn hình này.</string>
    <string name="tab_settings">Cài đặt</string>
    <string name="settings_language">Ngôn ngữ</string>
    <string name="language_system">Tự động (theo hệ thống)</string>
    <string name="settings_storage">Bộ nhớ</string>
    <string name="storage_books">Số sách</string>
    <string name="storage_files">Dung lượng sách</string>
    <string name="storage_free">Còn trống trên máy</string>
    <string name="settings_about">Giới thiệu</string>
    <string name="version">Phiên bản %1$s</string>
    <string name="licences">Giấy phép mã nguồn mở</string>
    <string name="close">Đóng</string>
```

- [ ] **Step 2: Write the failing tests**

`app/src/test/kotlin/dev/kent/windbook/notes/NotesScreenTest.kt`:

```kotlin
package dev.kent.windbook.notes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class NotesScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsDesignedEmptyState() {
        compose.setContent { NotesScreen() }
        compose.onNodeWithText("Notes").assertIsDisplayed()
        compose.onNodeWithText("Your highlights live here").assertIsDisplayed()
    }

    @Config(qualifiers = "vi")
    @Test fun vietnamese() {
        compose.setContent { NotesScreen() }
        compose.onNodeWithText("Ghi chú").assertIsDisplayed()
    }
}
```

`app/src/test/kotlin/dev/kent/windbook/settings/AppLanguageTest.kt`:

```kotlin
package dev.kent.windbook.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test fun emptyTagsMeanSystem() = assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags(""))
    @Test fun vietnameseWithRegion() = assertEquals(AppLanguage.VIETNAMESE, AppLanguage.fromTags("vi-VN"))
    @Test fun english() = assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTags("en-US"))
    @Test fun unsupportedFallsBackToSystem() = assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags("fr"))
}
```

`app/src/test/kotlin/dev/kent/windbook/settings/SettingsViewModelTest.kt`:

```kotlin
package dev.kent.windbook.settings

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.kent.windbook.core.data.LibraryRepository
import dev.kent.windbook.core.data.db.WindBookDatabase
import dev.kent.windbook.core.data.files.BookFileStore
import dev.kent.windbook.core.data.importer.BookImporter
import dev.kent.windbook.core.data.importer.ImportSource
import dev.kent.windbook.core.model.ExtractedMetadata
import dev.kent.windbook.testing.MainDispatcherRule
import dev.kent.windbook.testing.TestBooks
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: WindBookDatabase
    private lateinit var store: BookFileStore
    private lateinit var repo: LibraryRepository
    private lateinit var importer: BookImporter

    private val locales = object : AppLocaleController {
        var language = AppLanguage.SYSTEM
        val applied = mutableListOf<AppLanguage>()
        override fun current() = language
        override fun set(language: AppLanguage) {
            this.language = language
            applied += language
        }
    }

    @Before fun setUp() {
        db = WindBookDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
        store = BookFileStore(tmp.newFolder("app"))
        repo = LibraryRepository(db.libraryDao(), store)
        importer = BookImporter(db.libraryDao(), store, metadata = { _, _ ->
            Result.success(ExtractedMetadata(null, "Author", null))
        })
    }

    @After fun tearDown() = db.close()

    private fun source(file: File) = object : ImportSource {
        override val displayName = file.name
        override val sourceUri = file.toURI().toString()
        override fun open() = file.inputStream()
    }

    @Test fun storageCountsBooksAndTheirSizes() = runTest {
        val dune = TestBooks.epub(tmp.root, "Dune.epub")
        val emma = TestBooks.epub(tmp.root, "Emma.epub", title = "Emma")
        importer.import(source(dune))
        importer.import(source(emma))
        val vm = SettingsViewModel(repo, store, locales, AppInfo("9.9.9"))

        vm.state.test {
            var state = awaitItem()
            while (state.bookCount < 2) state = awaitItem()
            assertEquals(dune.length() + emma.length(), state.totalBytes)
            assertTrue(state.freeBytes > 0)
            assertEquals("9.9.9", state.versionName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun choosingALanguageAppliesItAndShowsIt() = runTest {
        val vm = SettingsViewModel(repo, store, locales, AppInfo("1"))
        vm.setLanguage(AppLanguage.VIETNAMESE)
        assertEquals(listOf(AppLanguage.VIETNAMESE), locales.applied)
        vm.state.test {
            var state = awaitItem()
            while (state.language != AppLanguage.VIETNAMESE) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

`app/src/test/kotlin/dev/kent/windbook/settings/SettingsScreenTest.kt`:

```kotlin
package dev.kent.windbook.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val chosen = mutableListOf<AppLanguage>()
    private val state = SettingsUiState(
        language = AppLanguage.SYSTEM, bookCount = 3, totalBytes = 0, freeBytes = 0, versionName = "0.1.0",
    )

    @Test fun currentLanguageIsSelectedAndChoosingCallsBack() {
        compose.setContent { SettingsScreen(state, onLanguageChange = { chosen += it }) }
        compose.onNodeWithText("Automatic (system)").assertIsSelected()
        compose.onNodeWithText("Tiếng Việt").performClick()
        assertEquals(listOf(AppLanguage.VIETNAMESE), chosen)
    }

    @Test fun showsStorageAndVersion() {
        compose.setContent { SettingsScreen(state, onLanguageChange = {}) }
        compose.onNodeWithText("3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Version 0.1.0").performScrollTo().assertIsDisplayed()
    }

    @Test fun licencesDialogNamesLiterata() {
        compose.setContent { SettingsScreen(state, onLanguageChange = {}) }
        compose.onNodeWithText("Open-source licences").performScrollTo().performClick()
        compose.onNodeWithText("Literata", substring = true).assertIsDisplayed()
    }
}
```

`assertIsSelected` works on the row because `Modifier.selectable` (Step 6) merges the radio state into the row node, and the row contains the label text.

- [ ] **Step 3: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.kent.windbook.notes.*' --tests 'dev.kent.windbook.settings.*'`
Expected: compilation FAILS (`Unresolved reference: NotesScreen`, `AppLanguage`, …).

- [ ] **Step 4: Notes screen**

`app/src/main/kotlin/dev/kent/windbook/notes/NotesScreen.kt`:

```kotlin
package dev.kent.windbook.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.kent.windbook.R
import dev.kent.windbook.ui.components.EmptyState
import dev.kent.windbook.ui.components.ScreenTitle

/** Placeholder until M4 adds highlights; the empty state is the designed one, not a "coming soon". */
@Composable
fun NotesScreen() {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        ScreenTitle(stringResource(R.string.tab_notes))
        EmptyState(
            title = stringResource(R.string.notes_empty_title),
            body = stringResource(R.string.notes_empty_body),
        )
    }
}
```

- [ ] **Step 5: Language model and controller**

`app/src/main/kotlin/dev/kent/windbook/settings/AppLanguage.kt`:

```kotlin
package dev.kent.windbook.settings

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dev.kent.windbook.R

enum class AppLanguage(val tag: String, @StringRes val labelRes: Int) {
    SYSTEM("", R.string.language_system),
    VIETNAMESE("vi", R.string.language_vi),
    ENGLISH("en", R.string.language_en);

    companion object {
        /** Maps AppCompat's stored language tags ("vi-VN", "en", "") to a choice; unknown → SYSTEM. */
        fun fromTags(tags: String): AppLanguage {
            val first = tags.substringBefore(',').substringBefore('-').lowercase()
            return entries.firstOrNull { it != SYSTEM && it.tag == first } ?: SYSTEM
        }
    }
}

interface AppLocaleController {
    fun current(): AppLanguage
    fun set(language: AppLanguage)
}

/** Per-app language via AppCompat; on Android 12 it is persisted by AppLocalesMetadataHolderService. */
class AppCompatLocaleController : AppLocaleController {
    override fun current(): AppLanguage = AppLanguage.fromTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())

    override fun set(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(
            if (language == AppLanguage.SYSTEM) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(language.tag),
        )
    }
}
```

- [ ] **Step 6: ViewModel and screen**

`app/src/main/kotlin/dev/kent/windbook/settings/SettingsViewModel.kt`:

```kotlin
package dev.kent.windbook.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kent.windbook.core.data.LibraryRepository
import dev.kent.windbook.core.data.files.BookFileStore
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AppInfo(val versionName: String)

data class SettingsUiState(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val bookCount: Int = 0,
    val totalBytes: Long = 0,
    val freeBytes: Long = 0,
    val versionName: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    repository: LibraryRepository,
    private val files: BookFileStore,
    private val locales: AppLocaleController,
    appInfo: AppInfo,
) : ViewModel() {

    private val language = MutableStateFlow(locales.current())

    val state: StateFlow<SettingsUiState> = combine(repository.observeLibrary(), language) { all, lang ->
        SettingsUiState(
            language = lang,
            bookCount = all.size,
            totalBytes = all.sumOf { it.book.fileSize },
            freeBytes = files.usableBytes(),
            versionName = appInfo.versionName,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(versionName = appInfo.versionName))

    fun setLanguage(choice: AppLanguage) {
        locales.set(choice)
        language.value = choice
    }
}
```

`app/src/main/kotlin/dev/kent/windbook/settings/SettingsScreen.kt`:

```kotlin
package dev.kent.windbook.settings

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kent.windbook.R
import dev.kent.windbook.ui.components.ScreenTitle

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(state, onLanguageChange = viewModel::setLanguage)
}

@Composable
fun SettingsScreen(state: SettingsUiState, onLanguageChange: (AppLanguage) -> Unit) {
    var showLicences by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle(stringResource(R.string.tab_settings))

        SettingsGroup(stringResource(R.string.settings_language)) {
            AppLanguage.entries.forEach { language ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = language == state.language,
                            onClick = { onLanguageChange(language) },
                            role = Role.RadioButton,
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = language == state.language, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(language.labelRes), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        SettingsGroup(stringResource(R.string.settings_storage)) {
            ValueRow(stringResource(R.string.storage_books), state.bookCount.toString())
            ValueRow(stringResource(R.string.storage_files), Formatter.formatShortFileSize(context, state.totalBytes))
            ValueRow(stringResource(R.string.storage_free), Formatter.formatShortFileSize(context, state.freeBytes))
        }

        SettingsGroup(stringResource(R.string.settings_about)) {
            ValueRow(stringResource(R.string.app_name), stringResource(R.string.version, state.versionName))
            Text(
                stringResource(R.string.licences),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLicences = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showLicences) {
        AlertDialog(
            onDismissRequest = { showLicences = false },
            title = { Text(stringResource(R.string.licences)) },
            text = { Text(stringResource(R.string.licences_body)) },
            confirmButton = { TextButton(onClick = { showLicences = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 8.dp),
    )
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) { Column(content = content) }
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

- [ ] **Step 7: DI and manifest**

In `di/AppModule.kt`, add imports `dev.kent.windbook.settings.AppCompatLocaleController`, `dev.kent.windbook.settings.AppInfo`, `dev.kent.windbook.settings.AppLocaleController`, and add these providers after `keyEventDispatcher()`:

```kotlin
    @Provides @Singleton
    fun appLocales(): AppLocaleController = AppCompatLocaleController()

    @Provides @Singleton
    fun appInfo(@ApplicationContext context: Context): AppInfo =
        AppInfo(context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty())
```

In `app/src/main/AndroidManifest.xml`, inside `<application>` after the `</activity>`:

```xml
        <!-- Persists the in-app language choice on Android 12 (13+ stores it in the system). -->
        <service
            android:name="androidx.appcompat.app.AppLocalesMetadataHolderService"
            android:enabled="false"
            android:exported="false">
            <meta-data
                android:name="autoStoreLocales"
                android:value="true" />
        </service>
```

- [ ] **Step 8: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.kent.windbook.notes.*' --tests 'dev.kent.windbook.settings.*'`
Expected: `NotesScreenTest` (2), `AppLanguageTest` (4), `SettingsViewModelTest` (2), `SettingsScreenTest` (3) PASS.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/dev/kent/windbook/notes app/src/main/kotlin/dev/kent/windbook/settings \
  app/src/main/kotlin/dev/kent/windbook/di/AppModule.kt app/src/main/AndroidManifest.xml \
  app/src/main/res/values/strings.xml app/src/main/res/values-vi/strings.xml \
  app/src/test/kotlin/dev/kent/windbook/notes app/src/test/kotlin/dev/kent/windbook/settings
git commit -m "feat(app): Notes empty state and Settings (language, storage, about)"
```

---

### Task 5: Tab navigation shell

**Files:**
- Modify: `app/src/main/kotlin/dev/kent/windbook/navigation/Routes.kt`
- Create: `app/src/main/kotlin/dev/kent/windbook/navigation/TopLevelTab.kt`
- Modify: `app/src/main/kotlin/dev/kent/windbook/navigation/BackStack.kt`
- Create: `app/src/main/kotlin/dev/kent/windbook/navigation/WindBookTabBar.kt`
- Modify: `app/src/main/kotlin/dev/kent/windbook/navigation/WindBookNavHost.kt` (whole file)
- Modify: `app/src/main/kotlin/dev/kent/windbook/ui/icons/WindBookIcons.kt` (+ `Shelf`, `OpenBook`, `Note`, `Settings`)
- Test: `app/src/test/kotlin/dev/kent/windbook/navigation/BackStackTest.kt`, `app/src/test/kotlin/dev/kent/windbook/navigation/WindBookTabBarTest.kt`

**Interfaces:**
- Consumes: `LibraryRoute` (Task 2), `ReadingRoute` (Task 3), `NotesScreen`, `SettingsRoute` (Task 4), `ReaderRoute` (existing), `openReader` (existing).
- Produces:
  - `@Serializable data object ReadingKey : NavKey`, `NotesKey`, `SettingsKey`
  - `enum class TopLevelTab(val key: NavKey, @StringRes val label: Int) { LIBRARY, READING, NOTES, SETTINGS }`
  - `fun NavKey.topLevelTab(): TopLevelTab?`
  - `fun MutableList<NavKey>.selectTab(tab: TopLevelTab)`
  - `@Composable fun WindBookTabBar(selected: TopLevelTab, onSelect: (TopLevelTab) -> Unit)`

- [ ] **Step 1: Write the failing tests**

Append to `app/src/test/kotlin/dev/kent/windbook/navigation/BackStackTest.kt` (inside the class):

```kotlin
    @Test fun selectingATabPutsItAboveLibrary() {
        val stack = mutableListOf<NavKey>(LibraryKey)
        stack.selectTab(TopLevelTab.SETTINGS)
        assertEquals(listOf(LibraryKey, SettingsKey), stack)
    }

    @Test fun switchingTabsKeepsOnlyOneAboveLibrary() {
        val stack = mutableListOf<NavKey>(LibraryKey, ReadingKey)
        stack.selectTab(TopLevelTab.NOTES)
        assertEquals(listOf(LibraryKey, NotesKey), stack)
    }

    @Test fun selectingLibraryReturnsToTheRoot() {
        val stack = mutableListOf<NavKey>(LibraryKey, SettingsKey)
        stack.selectTab(TopLevelTab.LIBRARY)
        assertEquals(listOf(LibraryKey), stack)
    }

    @Test fun reselectingTheCurrentTabDoesNothing() {
        val stack = mutableListOf<NavKey>(LibraryKey, SettingsKey)
        stack.selectTab(TopLevelTab.SETTINGS)
        assertEquals(listOf(LibraryKey, SettingsKey), stack)
    }

    @Test fun readerOpenedFromReadingTabReturnsToIt() {
        val stack = mutableListOf<NavKey>(LibraryKey)
        stack.selectTab(TopLevelTab.READING)
        stack.openReader("a")
        stack.removeAt(stack.lastIndex) // system back from the reader
        assertEquals(ReadingKey, stack.last())
    }

    @Test fun tabBarShowsOnlyOnTopLevelScreens() {
        assertEquals(TopLevelTab.SETTINGS, SettingsKey.topLevelTab())
        assertEquals(TopLevelTab.LIBRARY, LibraryKey.topLevelTab())
        assertEquals(null, ReaderKey("a").topLevelTab())
    }
```

Create `app/src/test/kotlin/dev/kent/windbook/navigation/WindBookTabBarTest.kt`:

```kotlin
package dev.kent.windbook.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class WindBookTabBarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsFourTabsAndReportsSelection() {
        val picked = mutableListOf<TopLevelTab>()
        compose.setContent { WindBookTabBar(selected = TopLevelTab.LIBRARY, onSelect = { picked += it }) }
        compose.onNodeWithText("Library").assertIsSelected()
        compose.onNodeWithText("Reading").assertIsDisplayed()
        compose.onNodeWithText("Notes").assertIsDisplayed()
        compose.onNodeWithText("Settings").performClick()
        assertEquals(listOf(TopLevelTab.SETTINGS), picked)
    }

    @Config(qualifiers = "vi")
    @Test fun vietnameseLabels() {
        compose.setContent { WindBookTabBar(selected = TopLevelTab.LIBRARY, onSelect = {}) }
        compose.onNodeWithText("Kệ sách").assertIsDisplayed()
        compose.onNodeWithText("Cài đặt").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.kent.windbook.navigation.*'`
Expected: compilation FAILS (`Unresolved reference: selectTab`, `TopLevelTab`, `SettingsKey`).

- [ ] **Step 3: Keys, tabs and `selectTab`**

Append to `Routes.kt`:

```kotlin
@Serializable
data object ReadingKey : NavKey

@Serializable
data object NotesKey : NavKey

@Serializable
data object SettingsKey : NavKey
```

`app/src/main/kotlin/dev/kent/windbook/navigation/TopLevelTab.kt`:

```kotlin
package dev.kent.windbook.navigation

import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import dev.kent.windbook.R

/** The four bottom-bar destinations. Order here is the order on screen. */
enum class TopLevelTab(val key: NavKey, @StringRes val label: Int) {
    LIBRARY(LibraryKey, R.string.tab_library),
    READING(ReadingKey, R.string.tab_reading),
    NOTES(NotesKey, R.string.tab_notes),
    SETTINGS(SettingsKey, R.string.tab_settings),
}

/** The tab this key is the root of, or null for screens that hide the tab bar (the reader). */
fun NavKey.topLevelTab(): TopLevelTab? = TopLevelTab.entries.firstOrNull { it.key == this }
```

Append to `BackStack.kt`:

```kotlin
/**
 * Switches tabs. Library is always the root and at most one other tab sits above it, so
 * back from any tab returns to Library (Material guidance) and the stack never grows.
 */
fun MutableList<NavKey>.selectTab(tab: TopLevelTab) {
    if (lastOrNull() == tab.key) return
    while (size > 1) removeAt(lastIndex)
    if (tab != TopLevelTab.LIBRARY) add(tab.key)
}
```

- [ ] **Step 4: Tab icons**

Add to `WindBookIcons` after `Check`:

```kotlin
    val Shelf = icon(
        "Shelf",
        "M4,6H2v14c0,1.1 0.9,2 2,2h14v-2H4V6zM20,2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM19,11H9V9h10v2zM15,15H9v-2h6v2zM19,7H9V5h10v2z",
    )
    val OpenBook = icon(
        "OpenBook",
        "M21,5c-1.11,-0.35 -2.33,-0.5 -3.5,-0.5c-1.95,0 -4.05,0.4 -5.5,1.5c-1.45,-1.1 -3.55,-1.5 -5.5,-1.5S2.45,4.9 1,6v14.65c0,0.25 0.25,0.5 0.5,0.5c0.1,0 0.15,-0.05 0.25,-0.05C3.1,20.45 5.05,20 6.5,20c1.95,0 4.05,0.4 5.5,1.5c1.35,-0.85 3.8,-1.5 5.5,-1.5c1.65,0 3.35,0.3 4.75,1.05c0.1,0.05 0.15,0.05 0.25,0.05c0.25,0 0.5,-0.25 0.5,-0.5V6C22.4,5.55 21.75,5.25 21,5zM21,18.5c-1.1,-0.35 -2.3,-0.5 -3.5,-0.5c-1.7,0 -4.15,0.65 -5.5,1.5V8c1.35,-0.85 3.8,-1.5 5.5,-1.5c1.2,0 2.4,0.15 3.5,0.5V18.5z",
    )
    val Note = icon(
        "Note",
        "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z",
    )
    val Settings = icon(
        "Settings",
        "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z",
    )
```

These are Material icon paths (Apache-2.0): library_books, menu_book (outline variant), edit, settings. Task 7 checks them visually.

- [ ] **Step 5: Tab bar**

`app/src/main/kotlin/dev/kent/windbook/navigation/WindBookTabBar.kt`:

```kotlin
package dev.kent.windbook.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import dev.kent.windbook.ui.icons.WindBookIcons

@Composable
fun WindBookTabBar(selected: TopLevelTab, onSelect: (TopLevelTab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    NavigationBar(containerColor = colors.surfaceContainer) {
        TopLevelTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon(), contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.onPrimaryContainer,
                    selectedTextColor = colors.primary,
                    indicatorColor = colors.primaryContainer,
                    unselectedIconColor = colors.onSurfaceVariant,
                    unselectedTextColor = colors.onSurfaceVariant,
                ),
            )
        }
    }
}

private fun TopLevelTab.icon(): ImageVector = when (this) {
    TopLevelTab.LIBRARY -> WindBookIcons.Shelf
    TopLevelTab.READING -> WindBookIcons.OpenBook
    TopLevelTab.NOTES -> WindBookIcons.Note
    TopLevelTab.SETTINGS -> WindBookIcons.Settings
}
```

- [ ] **Step 6: Nav host**

`app/src/main/kotlin/dev/kent/windbook/navigation/WindBookNavHost.kt` (whole file):

```kotlin
package dev.kent.windbook.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.kent.windbook.library.LibraryRoute
import dev.kent.windbook.notes.NotesScreen
import dev.kent.windbook.reader.ReaderRoute
import dev.kent.windbook.reading.ReadingRoute
import dev.kent.windbook.settings.SettingsRoute

@Composable
fun WindBookNavHost() {
    val backStack = rememberNavBackStack(LibraryKey)
    val currentTab = backStack.lastOrNull()?.topLevelTab()
    Scaffold(
        bottomBar = {
            if (currentTab != null) WindBookTabBar(selected = currentTab, onSelect = { backStack.selectTab(it) })
        },
        // Screens handle their own status-bar inset; the reader must stay edge to edge.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<LibraryKey> {
                    LibraryRoute(onOpenBook = { id -> backStack.openReader(id) })
                }
                entry<ReadingKey> {
                    ReadingRoute(
                        onOpenBook = { id -> backStack.openReader(id) },
                        onBrowseLibrary = { backStack.selectTab(TopLevelTab.LIBRARY) },
                    )
                }
                entry<NotesKey> { NotesScreen() }
                entry<SettingsKey> { SettingsRoute() }
                entry<ReaderKey> { key ->
                    ReaderRoute(bookId = key.bookId, onBack = { backStack.removeLastOrNull() })
                }
            },
        )
    }
}
```

- [ ] **Step 7: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.kent.windbook.navigation.*'`
Expected: `BackStackTest` (8) and `WindBookTabBarTest` (2) PASS.

- [ ] **Step 8: Full app suite, lint, build**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: all PASS. No `UnusedResources` warnings remain for strings added in Tasks 2–4.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/dev/kent/windbook/navigation app/src/main/kotlin/dev/kent/windbook/ui/icons/WindBookIcons.kt \
  app/src/test/kotlin/dev/kent/windbook/navigation
git commit -m "feat(app): four-tab shell (Library, Reading, Notes, Settings); tab bar hidden in the reader"
```

---

### Task 6: Reader chrome and default EPUB theme

**Files:**
- Create: `reader/src/main/kotlin/dev/kent/windbook/reader/ReaderTheme.kt`
- Modify: `reader/src/main/kotlin/dev/kent/windbook/reader/ReaderHostFragment.kt` (`bind`, EPUB factory, `epubTheme`)
- Modify: `app/src/main/kotlin/dev/kent/windbook/reader/ReaderScreen.kt` (`ReaderSurface`, `ReaderOverlay`)
- Test: `reader/src/test/kotlin/dev/kent/windbook/reader/ReaderThemeTest.kt`, `reader/src/androidTest/kotlin/dev/kent/windbook/reader/ReaderHostFragmentTest.kt`, `app/src/test/kotlin/dev/kent/windbook/reader/ReaderOverlayTest.kt`

**Interfaces:**
- Consumes: `ProgressLine`, `formatPercent` (Task 2), `Literata` via `MaterialTheme.typography.titleSmall` (Task 1).
- Produces:
  - `internal fun initialEpubPreferences(darkTheme: Boolean): EpubPreferences`
  - `fun ReaderHostFragment.bind(session: ReaderSession, initialLocatorJson: String?, callbacks: Callbacks, darkTheme: Boolean)`
  - `internal val ReaderHostFragment.epubTheme: Theme?`
  - `ReaderOverlay(...)` keeps its parameters. The rendered title becomes uppercase.

- [ ] **Step 1: Write the failing tests**

`reader/src/test/kotlin/dev/kent/windbook/reader/ReaderThemeTest.kt`:

```kotlin
package dev.kent.windbook.reader

import org.junit.Assert.assertEquals
import org.junit.Test
import org.readium.r2.navigator.preferences.Theme

class ReaderThemeTest {
    @Test fun lightAppOpensEpubInSepia() = assertEquals(Theme.SEPIA, initialEpubPreferences(darkTheme = false).theme)

    @Test fun darkAppOpensEpubInDark() = assertEquals(Theme.DARK, initialEpubPreferences(darkTheme = true).theme)
}
```

In `reader/src/androidTest/kotlin/dev/kent/windbook/reader/ReaderHostFragmentTest.kt`:
- Add `darkTheme = false` to the three existing `fragment.bind(...)` calls: `fragment.bind(session, initialLocatorJson = null, callbacks = object …, darkTheme = false)`, and for the positional calls `fragment.bind(session, null, object …, false)` / `fragment.bind(session, firstJson, object …, false)`.
- Add the imports `org.junit.Assert.assertEquals` and `org.readium.r2.navigator.preferences.Theme`.
- Add this test:

```kotlin
    @Test
    fun darkModeBindsEpubWithDarkTheme() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val epub = TestBooks.epub(context.cacheDir, name = "host-theme.epub")
        val session = runBlocking {
            ReaderEngine(ReadiumServices(context)).open(epub, BookFormat.EPUB).getOrThrow()
        }
        val seen = CountDownLatch(1)
        val scenario = launchFragmentInContainer<ReaderHostFragment>(
            fragmentArgs = ReaderHostFragment.arguments(BookFormat.EPUB),
            themeResId = androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar,
        )
        scenario.onFragment { fragment ->
            fragment.bind(session, null, object : ReaderHostFragment.Callbacks {
                override fun onLocatorChanged(locatorJson: String, progression: Double, chapterTitle: String?) = seen.countDown()
                override fun onCenterTap() = Unit
            }, darkTheme = true)
        }
        assertTrue(seen.await(15, TimeUnit.SECONDS))
        scenario.onFragment { assertEquals(Theme.DARK, it.epubTheme) }

        // Light mode after a config change (system dark mode toggled off): the re-bound navigator is SEPIA.
        scenario.recreate()
        val again = CountDownLatch(1)
        scenario.onFragment { fragment ->
            fragment.bind(session, null, object : ReaderHostFragment.Callbacks {
                override fun onLocatorChanged(locatorJson: String, progression: Double, chapterTitle: String?) = again.countDown()
                override fun onCenterTap() = Unit
            }, darkTheme = false)
        }
        assertTrue(again.await(15, TimeUnit.SECONDS))
        scenario.onFragment { assertEquals(Theme.SEPIA, it.epubTheme) }
        scenario.close()
        session.close()
    }
```

In `app/src/test/kotlin/dev/kent/windbook/reader/ReaderOverlayTest.kt`:
- In `visibleOverlayShowsTitleChapterProgressAndBack`, change `compose.onNodeWithText("Dune").assertIsDisplayed()` to `compose.onNodeWithText("DUNE").assertIsDisplayed()`.
- In `hiddenOverlayShowsNothing`, change `compose.onNodeWithText("Dune").assertDoesNotExist()` to `compose.onNodeWithText("DUNE").assertDoesNotExist()`.
- Add:

```kotlin
    @Test fun titleIsUppercasedKeepingVietnameseDiacritics() {
        compose.setContent {
            ReaderOverlay(
                visible = true, title = "Đắc nhân tâm", chapterTitle = null, progression = 0.1,
                canMarkFinished = false, onMarkFinished = {}, onBack = {},
            )
        }
        compose.onNodeWithText("ĐẮC NHÂN TÂM").assertIsDisplayed()
        compose.onNodeWithText("10%").assertIsDisplayed()
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :reader:testDebugUnitTest --tests 'dev.kent.windbook.reader.ReaderThemeTest' :app:testDebugUnitTest --tests 'dev.kent.windbook.reader.ReaderOverlayTest'`
Expected: `:reader` compilation FAILS (`Unresolved reference: initialEpubPreferences`). `ReaderOverlayTest` FAILS on "DUNE" / "ĐẮC NHÂN TÂM" not found.

- [ ] **Step 3: Initial EPUB preferences**

`reader/src/main/kotlin/dev/kent/windbook/reader/ReaderTheme.kt`:

```kotlin
package dev.kent.windbook.reader

import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Theme

/** Until M2 adds reading themes, the EPUB page follows the app: sepia paper in light mode, dark at night. */
internal fun initialEpubPreferences(darkTheme: Boolean): EpubPreferences =
    EpubPreferences(theme = if (darkTheme) Theme.DARK else Theme.SEPIA)
```

- [ ] **Step 4: Thread `darkTheme` through the host fragment**

In `ReaderHostFragment.kt`:

Change the `bind` signature and its deferred call:

```kotlin
    fun bind(session: ReaderSession, initialLocatorJson: String?, callbacks: Callbacks, darkTheme: Boolean) {
        this.callbacks = callbacks
        val readium = session as ReadiumReaderSession
        if (readium === boundSession) return
        if (view == null) {
            pendingBind = { bind(session, initialLocatorJson, callbacks, darkTheme) }
            return
        }
```

Change the EPUB factory line:

```kotlin
            BookFormat.EPUB -> EpubNavigatorFactory(readium.publication)
                .createFragmentFactory(
                    initialLocator = initialLocator,
                    initialPreferences = initialEpubPreferences(darkTheme),
                )
```

Add below `turnPage`:

```kotlin
    /** The theme the EPUB navigator is rendering with; null for PDF or before bind. */
    internal val epubTheme: Theme?
        get() = (navigator as? EpubNavigatorFragment)?.settings?.value?.theme
```

and add the import `org.readium.r2.navigator.preferences.Theme`.

- [ ] **Step 5: Restyle the overlay and pass the theme**

In `app/src/main/kotlin/dev/kent/windbook/reader/ReaderScreen.kt`:

In `ReaderSurface`, read the mode and pass it to `bind`:

```kotlin
    val darkTheme = isSystemInDarkTheme()
```

(add right after `val dispatcher = LocalKeyEventDispatcher.current`) and change the bind call to:

```kotlin
        fragment.bind(session = state.session, initialLocatorJson = viewModel.latestLocatorJson ?: state.initialLocatorJson, callbacks = callbacks, darkTheme = darkTheme)
```

Replace the whole `ReaderOverlay` function with:

```kotlin
@Composable
fun ReaderOverlay(
    visible: Boolean,
    title: String,
    chapterTitle: String?,
    progression: Double,
    canMarkFinished: Boolean,
    onMarkFinished: () -> Unit,
    onBack: () -> Unit,
) {
    val bar = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.97f)
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(bar)
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(WindBookIcons.Back, contentDescription = stringResource(R.string.back)) }
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.08.em),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                // Balances the back button so the title is truly centred.
                Spacer(Modifier.width(48.dp))
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(bar)
                    .navigationBarsPadding(),
            ) {
                ProgressLine(progression)
                Row(
                    Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val percent = formatPercent(progression)
                    Text(
                        text = chapterTitle?.let { "$it · $percent" } ?: percent,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (canMarkFinished) {
                        TextButton(onClick = onMarkFinished) { Text(stringResource(R.string.mark_finished)) }
                    }
                }
            }
        }
    }
}
```

Imports to add: `androidx.compose.foundation.isSystemInDarkTheme`, `androidx.compose.foundation.layout.width`, `androidx.compose.ui.text.style.TextAlign`, `androidx.compose.ui.unit.em`, `dev.kent.windbook.ui.components.ProgressLine`, `dev.kent.windbook.ui.components.formatPercent`. Imports to remove (now unused): `androidx.compose.foundation.layout.height`, `androidx.compose.material3.LinearProgressIndicator`, `kotlin.math.roundToInt`. Keep `height` only if `ReaderErrorScreen` still uses it; it does (`Spacer(Modifier.height(24.dp))`), so **keep** `height`.

- [ ] **Step 6: Run the JVM tests**

Run: `./gradlew :reader:testDebugUnitTest :app:testDebugUnitTest --tests 'dev.kent.windbook.reader.*'`
Expected: `ReaderThemeTest` (2) and `ReaderOverlayTest` (4) PASS, other reader tests unchanged.

- [ ] **Step 7: Run the instrumented reader tests**

Start the emulator if it isn't running (README §4A; if it dies mid-boot, use `setsid nohup ~/Android/Sdk/emulator/emulator -avd Medium_Phone_API_37.0 -no-snapshot-load -cores 4 &` with `QT_QPA_PLATFORM=xcb`), wait for `adb shell getprop sys.boot_completed` to print `1`, then:

Run: `./gradlew :reader:connectedDebugAndroidTest`
Expected: 3 tests PASS (2 existing + `darkModeBindsEpubWithDarkTheme`).

- [ ] **Step 8: Commit**

```bash
git add reader/src app/src/main/kotlin/dev/kent/windbook/reader/ReaderScreen.kt app/src/test/kotlin/dev/kent/windbook/reader/ReaderOverlayTest.kt
git commit -m "feat(reader): restyled reader bars; EPUB opens in sepia (light) or dark following the app"
```

---

### Task 7: Visual verification, checklist and roadmap

**Files:**
- Modify: `docs/testing/m1-manual-checklist.md` (new "Look & feel" section)
- Modify: `docs/superpowers/plans/2026-10-13-windbook-roadmap.md` (new row)

**Interfaces:**
- Consumes: the whole app.
- Produces: screenshots in the scratchpad (not committed) and a checklist the owner can repeat on the Neo 3 GT.

- [ ] **Step 1: Full CI check**

Run: `./gradlew :core:model:test :core:data:testDebugUnitTest :reader:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Record the total test count from the reports (`app/build/reports/tests/testDebugUnitTest/index.html` etc.) for the summary.

- [ ] **Step 2: Install and load books**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell ls /sdcard/Download/
```

If no EPUB/PDF is listed, push at least two real books: `adb push "/path/to/book.epub" /sdcard/Download/`. Open WindBook, add the books with the + button, and open one so that "Continue reading" and the Reading tab have content.

- [ ] **Step 3: Screenshot light and dark**

For `MODE` in `no` then `yes`:

```bash
adb shell cmd uimode night MODE
# visit each screen in the app, then for each:
adb exec-out screencap -p > "$SCRATCH/lookfeel-MODE-<screen>.png"
```

The screens are Library (grid), Library (list), Reading, Notes, Settings, and the reader with the overlay shown (tap the page centre). `$SCRATCH` is the session scratchpad directory. Restore with `adb shell cmd uimode night no`.

- [ ] **Step 4: Compare with the mockups**

Open each screenshot next to `.superpowers/brainstorm/*/content/screens-v1.html`. For each screen, note pass/fail on:
- paper background, not white or grey
- serif titles (Literata), with Vietnamese diacritics intact when the app is set to Tiếng Việt in Settings
- gold FAB and progress bars
- pills for search, sort and filters
- cover shadows
- all four tab icons clearly recognisable
- the EPUB page is sepia in light mode and dark in dark mode

Fix any failure before continuing. If a fix changes code, run the test of the touched task and amend the related commit in a new commit `fix(app): …`.

- [ ] **Step 5: Checklist section**

Append to `docs/testing/m1-manual-checklist.md`:

```markdown
## Look & feel (2026-10-14 redesign)

| # | Step | Expected | Emulator | Neo 3 GT |
|---|---|---|---|---|
| L1 | Open the app in light mode | Cream paper background, "Library │ N books" in serif, gold + button | | |
| L2 | Type in the search pill, clear with ✕ | List filters as you type; header count does not change | | |
| L3 | Tap ⋯ on a cover | Status/delete menu, same as long-press | | |
| L4 | Switch tabs Library → Reading → Notes → Settings, press Back | Each tab shows its screen; Back returns to Library | | |
| L5 | Reading tab with no book in progress | "No book in progress" and a "Go to library" button that switches tab | | |
| L6 | Settings → Tiếng Việt | App switches to Vietnamese immediately; survives an app restart | | |
| L7 | Settings → Storage | Book count and total size match the library | | |
| L8 | Open an EPUB in light mode | Page is sepia; centre tap shows bars with the title in capitals | | |
| L9 | Turn on system dark mode | Warm dark chrome; EPUB page turns dark | | |
| L10 | Open a book from the Reading tab, press Back | Returns to the Reading tab | | |
```

- [ ] **Step 6: Roadmap row**

In `docs/superpowers/plans/2026-10-13-windbook-roadmap.md`, add after the M1 row:

```markdown
| Look & feel | `2026-10-14-windbook-look-and-feel.md` | M1 | Cream & bronze palette (light + warm dark), Literata, restyled Library/Reader, 4-tab shell, Settings (language, storage, about). Later milestones build their screens to `2026-10-14-windbook-look-and-feel-design.md` §5.6 | Planned |
```

- [ ] **Step 7: Commit**

```bash
git add docs/testing/m1-manual-checklist.md docs/superpowers/plans/2026-10-13-windbook-roadmap.md
git commit -m "docs: look & feel manual checks and roadmap entry"
```
