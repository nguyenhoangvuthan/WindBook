# WindBook M1 – Core Reading Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A signed WindBook APK that imports EPUB/PDF files, shows them in a library, opens them in Readium, and always reopens at the exact saved position (including after process death).

**Architecture:** Four Gradle modules. `:core:model` (pure Kotlin) holds domain types and pure logic. `:core:data` holds Room 3, files and importer. `:reader` wraps Readium behind `BookOpener`/`ReaderSession` and owns `ReaderHostFragment`, which embeds Readium navigator fragments. `:app` holds Compose UI, Navigation 3, Hilt and ViewModels. Libraries are DI-free; `:app` wires everything with Hilt `@Provides`.

**Tech Stack:** Kotlin 2.4.20 (AGP 9.4.1 built-in Kotlin), Gradle 9.6.0, JDK 21, Compose BOM 2026.09.00, Material 3 1.4.0, Navigation 3 1.2.0, Hilt 2.60.1, Room 3.0.3 + sqlite-bundled 2.7.1, DataStore 1.2.1, Readium 3.4.0 (+ PDFium adapter), Coil 3.6.3, JUnit 4 + Robolectric 4.17 + Turbine.

**Spec:** `docs/superpowers/specs/2026-10-13-windbook-design.md` (milestone M1: F1.1–F1.13)

## Global Constraints

- applicationId and namespace root: `dev.kent.windbook`
- minSdk 31, targetSdk 36, compileSdk 37
- AGP 9.4.1, Gradle 9.6.0, JDK 21 to run Gradle, bytecode target Java 11, core library desugaring ON in `:app` and `:reader`
- Do NOT apply `org.jetbrains.kotlin.android` (AGP 9 built-in Kotlin). Pure JVM module uses `org.jetbrains.kotlin.jvm` 2.4.20
- Room 3 (`androidx.room3`, KSP only); production driver `BundledSQLiteDriver`; tests use `AndroidSQLiteDriver` under Robolectric
- Readium 3.4.0; repository `https://jitpack.io` restricted to group `com.github.marain87` (PdfiumAndroid)
- No `MANAGE_EXTERNAL_STORAGE`/`READ_EXTERNAL_STORAGE`; files only via SAF; no `INTERNET` permission in M1
- Max import size 500 MB; EPUB zip guard: ≤ 10 000 entries, ≤ 2 GB declared uncompressed, no absolute or `..` entry names
- Reading position stored as Readium Locator JSON + progression; saved on every `currentLocator` emission
- User-data rows carry `updated_at` and `deleted_at`; deleting a book is a soft delete + file removal
- Only Apache/BSD/MIT/OFL dependencies
- Robolectric tests run at `sdk=35` (`src/test/resources/robolectric.properties`)

## Review Focus

1. **Import from a cloud provider (Drive/OneDrive) with unknown size and null display name**: import streams without loading into RAM; title falls back to "Untitled" when both metadata and name are missing. Test: `BookImporterTest.import_withoutTitleOrDisplayName_usesUntitled` (Task 5).
2. **EPUB with empty `<dc:title>`**: the library shows the file name, not a blank card. Test: `BookImporterTest.import_blankMetadataTitle_fallsBackToFileName` (Task 5).
3. **Re-importing a book the user deleted earlier** (same SHA-256, tombstoned row): the book comes back with a fresh file instead of failing on the unique index. Test: `BookImporterTest.import_afterDelete_revivesTombstonedBook` (Task 5).
4. **Rapid page turning (holding volume-down)**: the last emitted locator is what ends up stored, with no lost or out-of-order writes. Test: `ReaderViewModelTest.rapidLocatorChanges_persistLastLocator` (Task 11).
5. **Process death while reading**: reopening restores the reader screen and the saved locator. Test: `ReaderViewModelTest.ready_containsSavedLocator` (Task 11) plus the manual `am kill` step in Task 13.

---

## File Structure

```
WindBook/
├── settings.gradle.kts                 modules + repositories (incl. JitPack for PDFium)
├── build.gradle.kts                    plugin versions (apply false)
├── gradle.properties
├── gradle/libs.versions.toml           single source of versions
├── .gitignore
├── .github/workflows/android-ci.yml
├── core/model/                         (JVM)
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/dev/kent/windbook/core/model/
│       │   ├── Book.kt                 Book, BookFormat, ReadingStatus, LibraryItem
│       │   ├── FormatSniffer.kt        magic-byte detection
│       │   ├── SearchText.kt           accent-insensitive normalisation
│       │   ├── LibraryQuery.kt         filter/sort/continue-reading
│       │   └── MetadataExtractor.kt    port implemented by :reader
│       ├── test/kotlin/…               FormatSnifferTest, SearchTextTest, LibraryQueryTest
│       └── testFixtures/kotlin/dev/kent/windbook/testing/TestBooks.kt
├── core/data/                          (Android library)
│   ├── build.gradle.kts
│   ├── schemas/                        Room schema JSON (committed)
│   └── src/main/kotlin/dev/kent/windbook/core/data/
│       ├── db/Entities.kt              BookEntity, ReadingProgressEntity, BookWithProgressRow
│       ├── db/LibraryDao.kt
│       ├── db/WindBookDatabase.kt
│       ├── files/BookFileStore.kt
│       ├── importer/ImportModels.kt    ImportSource, ImportResult, ImportError
│       ├── importer/ZipGuard.kt
│       ├── importer/BookImporter.kt
│       ├── LibraryRepository.kt
│       └── LibraryPreferences.kt
├── reader/                             (Android library)
│   ├── build.gradle.kts
│   └── src/main/
│       ├── res/values/ids.xml
│       └── kotlin/dev/kent/windbook/reader/
│           ├── ReaderApi.kt            BookOpener, ReaderSession
│           ├── ReadiumServices.kt
│           ├── ReadiumMetadataExtractor.kt
│           ├── ReaderEngine.kt         BookOpener impl + ReadiumReaderSession
│           ├── LocatorCodec.kt
│           └── ReaderHostFragment.kt
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/values/themes.xml, strings.xml
        └── kotlin/dev/kent/windbook/
            ├── WindBookApp.kt, MainActivity.kt
            ├── di/AppModule.kt
            ├── input/KeyEventDispatcher.kt
            ├── navigation/Routes.kt, WindBookNavHost.kt
            ├── ui/theme/Theme.kt, ui/icons/WindBookIcons.kt
            ├── library/ImportSourceFactory.kt, LibraryViewModel.kt, LibraryScreen.kt
            └── reader/ReaderViewModel.kt, ReaderScreen.kt
```

---

### Task 1: Gradle scaffold that builds an empty Compose app

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `.gitignore`, `local.properties` (git-ignored)
- Create: `core/model/build.gradle.kts`, `core/data/build.gradle.kts`, `reader/build.gradle.kts`, `app/build.gradle.kts`, `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/themes.xml`, `app/src/main/res/values/strings.xml`
- Create: `app/src/main/kotlin/dev/kent/windbook/WindBookApp.kt`, `app/src/main/kotlin/dev/kent/windbook/MainActivity.kt`
- Create: `core/data/src/main/AndroidManifest.xml`, `reader/src/main/AndroidManifest.xml` (empty `<manifest/>`)
- Test: `app/src/test/kotlin/dev/kent/windbook/SmokeTest.kt`, `app/src/test/resources/robolectric.properties`

**Interfaces:**
- Produces: version catalog aliases used by every later task (`libs.*`), module paths `:core:model`, `:core:data`, `:reader`, `:app`.

- [ ] **Step 1: Commit the existing docs on `main`, then branch**

```bash
cd ~/KentProject/WindBook
git add docs && git commit -m "docs: WindBook blueprint, research, spec and plans"
git switch -c m1-core-reading-loop
```

- [ ] **Step 2: Generate the Gradle wrapper (Gradle 9.6.0)**

```bash
SCRATCH=/tmp/claude-1000/-home-kent-KentProject-WindBook/4f4d02da-89ae-4382-b895-1b8b4f5a6e52/scratchpad
mkdir -p "$SCRATCH/gradle-dist" && cd "$SCRATCH/gradle-dist"
curl -fL -o gradle-9.6.0-bin.zip https://services.gradle.org/distributions/gradle-9.6.0-bin.zip
unzip -q -o gradle-9.6.0-bin.zip
cd ~/KentProject/WindBook
"$SCRATCH/gradle-dist/gradle-9.6.0/bin/gradle" wrapper --gradle-version 9.6.0 --distribution-type bin --no-daemon
```
Expected: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.{jar,properties}` exist. (The `settings.gradle.kts` from Step 3 isn't needed for `wrapper`; if Gradle complains, run Step 3 first and repeat.)

- [ ] **Step 3: Write `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // PdfiumAndroid / AndroidPdfViewer used by readium-adapter-pdfium.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.marain87") }
        }
    }
}

rootProject.name = "WindBook"

include(":app", ":core:model", ":core:data", ":reader")
```

- [ ] **Step 4: Write `gradle/libs.versions.toml`**

```toml
[versions]
agp = "9.4.1"
kotlin = "2.4.20"
ksp = "2.3.12"
hilt = "2.60.1"
androidx-hilt = "1.4.0"
compose-bom = "2026.09.00"
navigation3 = "1.2.0"
lifecycle = "2.11.0"
activity = "1.13.0"
fragment = "1.9.1"
appcompat = "1.8.0"
core-ktx = "1.19.1"
room3 = "3.0.3"
sqlite = "2.7.1"
datastore = "1.2.1"
readium = "3.4.0"
coil = "3.6.3"
coroutines = "1.11.0"
serialization = "1.11.0"
desugar = "2.1.5"
junit = "4.13.2"
robolectric = "4.17"
turbine = "1.2.1"
androidx-test-core = "1.7.0"
androidx-test-runner = "1.7.0"
androidx-test-ext-junit = "1.3.0"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "core-ktx" }
androidx-appcompat = { module = "androidx.appcompat:appcompat", version.ref = "appcompat" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activity" }
androidx-fragment-ktx = { module = "androidx.fragment:fragment-ktx", version.ref = "fragment" }
androidx-fragment-compose = { module = "androidx.fragment:fragment-compose", version.ref = "fragment" }
androidx-fragment-testing = { module = "androidx.fragment:fragment-testing", version.ref = "fragment" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-navigation3 = { module = "androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "lifecycle" }
androidx-navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "navigation3" }
androidx-navigation3-ui = { module = "androidx.navigation3:navigation3-ui", version.ref = "navigation3" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
compose-material3 = { module = "androidx.compose.material3:material3" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
androidx-hilt-viewmodel-compose = { module = "androidx.hilt:hilt-lifecycle-viewmodel-compose", version.ref = "androidx-hilt" }
room3-runtime = { module = "androidx.room3:room3-runtime", version.ref = "room3" }
room3-compiler = { module = "androidx.room3:room3-compiler", version.ref = "room3" }
sqlite-bundled = { module = "androidx.sqlite:sqlite-bundled", version.ref = "sqlite" }
sqlite-framework = { module = "androidx.sqlite:sqlite-framework", version.ref = "sqlite" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
readium-shared = { module = "org.readium.kotlin-toolkit:readium-shared", version.ref = "readium" }
readium-streamer = { module = "org.readium.kotlin-toolkit:readium-streamer", version.ref = "readium" }
readium-navigator = { module = "org.readium.kotlin-toolkit:readium-navigator", version.ref = "readium" }
readium-adapter-pdfium = { module = "org.readium.kotlin-toolkit:readium-adapter-pdfium", version.ref = "readium" }
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
desugar-jdk-libs = { module = "com.android.tools:desugar_jdk_libs", version.ref = "desugar" }
junit = { module = "junit:junit", version.ref = "junit" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidx-test-core" }
androidx-test-runner = { module = "androidx.test:runner", version.ref = "androidx-test-runner" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidx-test-ext-junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
room3 = { id = "androidx.room3", version.ref = "room3" }
```

- [ ] **Step 5: Root `build.gradle.kts`, `gradle.properties`, `.gitignore`, `local.properties`**

`build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room3) apply false
}
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

`.gitignore`:
```gitignore
.gradle/
.idea/
.kotlin/
build/
**/build/
local.properties
captures/
*.iml
*.jks
*.keystore
keystore.properties
.env
.remember/
```

`local.properties` (not committed):
```properties
sdk.dir=/home/kent/Android/Sdk
```

- [ ] **Step 6: Module build files**

`core/model/build.gradle.kts`:
```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-test-fixtures`
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testFixturesImplementation(libs.junit)
}
```

`core/data/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

android {
    namespace = "dev.kent.windbook.core.data"
    compileSdk = 37
    defaultConfig {
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":core:model"))
    api(libs.room3.runtime)
    ksp(libs.room3.compiler)
    implementation(libs.sqlite.bundled)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(testFixtures(project(":core:model")))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.sqlite.framework)
}
```

`reader/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.kent.windbook.reader"
    compileSdk = 37
    defaultConfig {
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    api(project(":core:model"))
    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.navigator)
    implementation(libs.readium.adapter.pdfium)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.kotlinx.coroutines.android)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(testFixtures(project(":core:model")))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(testFixtures(project(":core:model")))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.fragment.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
```

`app/build.gradle.kts`:
```kotlin
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "dev.kent.windbook"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.kent.windbook"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystoreProperties.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystoreProperties.isNotEmpty()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":reader"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.hilt.viewmodel.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(testFixtures(project(":core:model")))
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.sqlite.framework)
}
```

`app/proguard-rules.pro`:
```proguard
# Readium, Room, Hilt and kotlinx.serialization ship consumer rules.
# Keep Navigation 3 route classes (serialized into saved state).
-keep class dev.kent.windbook.navigation.** { *; }
```

`core/data/src/main/AndroidManifest.xml` and `reader/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest />
```

- [ ] **Step 7: Minimal app sources**

`app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:name=".WindBookApp"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.WindBook">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

Create a launcher icon: `app/src/main/res/mipmap-anydpi/ic_launcher.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```
`app/src/main/res/values/colors.xml`:
```xml
<resources>
    <color name="ic_launcher_background">#F8F4EC</color>
</resources>
```
`app/src/main/res/drawable/ic_launcher_foreground.xml` (open book glyph):
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#2B2B2B"
        android:pathData="M30,38c8,-4 16,-4 24,1v34c-8,-5 -16,-5 -24,-1zM78,38c-8,-4 -16,-4 -24,1v34c8,-5 16,-5 24,-1z" />
</vector>
```

`app/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">WindBook</string>
</resources>
```

`app/src/main/res/values/themes.xml`:
```xml
<resources>
    <!-- AppCompat parent: Readium navigator fragments need an AppCompat context. -->
    <style name="Theme.WindBook" parent="Theme.AppCompat.DayNight.NoActionBar">
        <item name="android:windowBackground">@color/ic_launcher_background</item>
    </style>
</resources>
```

`app/src/main/kotlin/dev/kent/windbook/WindBookApp.kt`:
```kotlin
package dev.kent.windbook

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WindBookApp : Application()
```

`app/src/main/kotlin/dev/kent/windbook/MainActivity.kt` (temporary body, replaced in Task 12):
```kotlin
package dev.kent.windbook

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { Text("WindBook") }
    }
}
```

- [ ] **Step 8: Write the smoke test**

`app/src/test/resources/robolectric.properties`:
```properties
sdk=35
```
Create the same file at `core/data/src/test/resources/robolectric.properties` and `reader/src/test/resources/robolectric.properties`.

`app/src/test/kotlin/dev/kent/windbook/SmokeTest.kt`:
```kotlin
package dev.kent.windbook

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class SmokeTest {
    @Test
    fun appNameIsWindBook() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("WindBook", context.getString(R.string.app_name))
    }
}
```

- [ ] **Step 9: Build and test**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, `SmokeTest` passes, `app/build/outputs/apk/debug/app-debug.apk` exists.
If plugin resolution fails for Hilt or KSP against AGP 9.4.1, check the newest stable versions with `curl -s https://repo1.maven.org/maven2/com/google/dagger/hilt-android/maven-metadata.xml | grep release` (and the same for `com/google/devtools/ksp/symbol-processing-api`), bump `libs.versions.toml`, and note the change in the commit message.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "build: Gradle scaffold for WindBook (AGP 9.4.1, Kotlin 2.4.20, Compose, Hilt)"
```

---

### Task 2: `:core:model` domain types and pure logic

**Files:**
- Create: `core/model/src/main/kotlin/dev/kent/windbook/core/model/Book.kt`
- Create: `core/model/src/main/kotlin/dev/kent/windbook/core/model/FormatSniffer.kt`
- Create: `core/model/src/main/kotlin/dev/kent/windbook/core/model/SearchText.kt`
- Create: `core/model/src/main/kotlin/dev/kent/windbook/core/model/LibraryQuery.kt`
- Create: `core/model/src/main/kotlin/dev/kent/windbook/core/model/MetadataExtractor.kt`
- Create: `core/model/src/testFixtures/kotlin/dev/kent/windbook/testing/TestBooks.kt`
- Test: `core/model/src/test/kotlin/dev/kent/windbook/core/model/FormatSnifferTest.kt`, `SearchTextTest.kt`, `LibraryQueryTest.kt`

**Interfaces:**
- Produces:
  - `enum class BookFormat(val extension: String, val mimeType: String) { EPUB, PDF }`
  - `enum class ReadingStatus { TO_READ, READING, FINISHED }`
  - `data class Book(id: String, title: String, author: String?, format: BookFormat, filePath: String, fileSize: Long, sha256: String, coverPath: String?, status: ReadingStatus, addedAt: Long, lastOpenedAt: Long?)`
  - `data class LibraryItem(book: Book, progression: Double)`
  - `enum class FileSignature { PDF, ZIP, UNKNOWN }`; `object FormatSniffer { const val HEADER_SIZE = 1024; fun signature(header: ByteArray): FileSignature }`
  - `object SearchText { fun normalize(text: String): String }`
  - `enum class LibrarySort { RECENT, TITLE, AUTHOR, ADDED, PROGRESS }`, `enum class StatusFilter { ALL, READING, TO_READ, FINISHED }`, `enum class LibraryLayout { GRID, LIST }`
  - `data class LibraryQuery(text: String = "", sort: LibrarySort = RECENT, filter: StatusFilter = ALL) { fun apply(items: List<LibraryItem>): List<LibraryItem> }`
  - `fun continueReading(items: List<LibraryItem>): LibraryItem?`
  - `data class ExtractedMetadata(title: String?, author: String?, coverWebp: ByteArray?)`; `fun interface MetadataExtractor { suspend fun extract(file: File, format: BookFormat): Result<ExtractedMetadata> }`
  - Test fixture `object TestBooks { fun epub(dir: File, name: String = "book.epub", title: String = "Test Book", author: String = "Ada Writer"): File; fun pdf(dir: File, name: String = "doc.pdf"): File; fun zip(dir: File, name: String, entries: Map<String, ByteArray>): File }`

- [ ] **Step 1: Write the failing tests**

`FormatSnifferTest.kt`:
```kotlin
package dev.kent.windbook.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatSnifferTest {
    @Test fun pdfMagicAtStart() =
        assertEquals(FileSignature.PDF, FormatSniffer.signature("%PDF-1.7\n".toByteArray()))

    @Test fun pdfMagicAfterJunkWithinHeader() =
        assertEquals(FileSignature.PDF, FormatSniffer.signature(ByteArray(300) { 0x20 } + "%PDF-1.4".toByteArray()))

    @Test fun zipMagic() =
        assertEquals(FileSignature.ZIP, FormatSniffer.signature(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00)))

    @Test fun emptyIsUnknown() =
        assertEquals(FileSignature.UNKNOWN, FormatSniffer.signature(ByteArray(0)))

    @Test fun textIsUnknown() =
        assertEquals(FileSignature.UNKNOWN, FormatSniffer.signature("hello world".toByteArray()))
}
```

`SearchTextTest.kt`:
```kotlin
package dev.kent.windbook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {
    @Test fun stripsVietnameseDiacriticsAndCase() =
        assertEquals("tieng viet", SearchText.normalize("Tiếng Việt"))

    @Test fun mapsDStroke() =
        assertTrue(SearchText.normalize("Đắc Nhân Tâm").contains("dac nhan"))

    @Test fun plainAsciiUnchangedExceptCase() =
        assertEquals("clean code", SearchText.normalize("Clean Code"))
}
```

`LibraryQueryTest.kt`:
```kotlin
package dev.kent.windbook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryQueryTest {
    private fun item(
        id: String,
        title: String,
        author: String? = null,
        status: ReadingStatus = ReadingStatus.TO_READ,
        addedAt: Long = 0,
        lastOpenedAt: Long? = null,
        progression: Double = 0.0,
    ) = LibraryItem(
        Book(id, title, author, BookFormat.EPUB, "/b/$id.epub", 1, "sha$id", null, status, addedAt, lastOpenedAt),
        progression,
    )

    private val dune = item("1", "Dune", "Frank Herbert", ReadingStatus.READING, addedAt = 10, lastOpenedAt = 500, progression = 0.4)
    private val viet = item("2", "Dế Mèn Phiêu Lưu Ký", "Tô Hoài", ReadingStatus.FINISHED, addedAt = 30, lastOpenedAt = 100, progression = 1.0)
    private val code = item("3", "clean code", null, ReadingStatus.TO_READ, addedAt = 20)
    private val all = listOf(code, viet, dune)

    @Test fun recentSortsByLastOpenedThenNeverOpenedLast() =
        assertEquals(listOf("1", "2", "3"), LibraryQuery(sort = LibrarySort.RECENT).apply(all).map { it.book.id })

    @Test fun titleSortIsCaseInsensitive() =
        assertEquals(listOf("3", "2", "1"), LibraryQuery(sort = LibrarySort.TITLE).apply(all).map { it.book.id })

    @Test fun authorSortPutsMissingAuthorLast() =
        assertEquals(listOf("1", "2", "3"), LibraryQuery(sort = LibrarySort.AUTHOR).apply(all).map { it.book.id })

    @Test fun addedSortNewestFirst() =
        assertEquals(listOf("2", "3", "1"), LibraryQuery(sort = LibrarySort.ADDED).apply(all).map { it.book.id })

    @Test fun progressSortHighestFirst() =
        assertEquals(listOf("2", "1", "3"), LibraryQuery(sort = LibrarySort.PROGRESS).apply(all).map { it.book.id })

    @Test fun searchIgnoresAccentsAndMatchesAuthor() {
        assertEquals(listOf("2"), LibraryQuery(text = "de men").apply(all).map { it.book.id })
        assertEquals(listOf("2"), LibraryQuery(text = "to hoai").apply(all).map { it.book.id })
    }

    @Test fun statusFilter() =
        assertEquals(listOf("1"), LibraryQuery(filter = StatusFilter.READING).apply(all).map { it.book.id })

    @Test fun continueReadingPicksMostRecentReadingBook() {
        assertEquals("1", continueReading(all)?.book?.id)
        assertNull(continueReading(listOf(code, viet)))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:model:test`
Expected: FAIL, compilation errors (`Unresolved reference: FormatSniffer`, …).

- [ ] **Step 3: Implement**

`Book.kt`:
```kotlin
package dev.kent.windbook.core.model

enum class BookFormat(val extension: String, val mimeType: String) {
    EPUB("epub", "application/epub+zip"),
    PDF("pdf", "application/pdf"),
}

enum class ReadingStatus { TO_READ, READING, FINISHED }

data class Book(
    val id: String,
    val title: String,
    val author: String?,
    val format: BookFormat,
    val filePath: String,
    val fileSize: Long,
    val sha256: String,
    val coverPath: String?,
    val status: ReadingStatus,
    val addedAt: Long,
    val lastOpenedAt: Long?,
)

/** A library entry: the book plus its reading progression in 0.0..1.0. */
data class LibraryItem(val book: Book, val progression: Double)
```

`FormatSniffer.kt`:
```kotlin
package dev.kent.windbook.core.model

enum class FileSignature { PDF, ZIP, UNKNOWN }

/** Detects a file type from its first bytes, ignoring the file name. */
object FormatSniffer {
    const val HEADER_SIZE = 1024

    private val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    fun signature(header: ByteArray): FileSignature = when {
        header.startsWith(ZIP_MAGIC) -> FileSignature.ZIP
        // The PDF spec tolerates bytes before the header, within the first 1024 bytes.
        header.indexOf(PDF_MAGIC) >= 0 -> FileSignature.PDF
        else -> FileSignature.UNKNOWN
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        if (needle.size > size) return -1
        outer@ for (i in 0..size - needle.size) {
            for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}
```

`SearchText.kt`:
```kotlin
package dev.kent.windbook.core.model

import java.text.Normalizer

/** Lower-cases and strips diacritics so "tieng viet" matches "Tiếng Việt". */
object SearchText {
    private val combiningMarks = "\\p{Mn}+".toRegex()

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .replace('đ', 'd')
}
```

`LibraryQuery.kt`:
```kotlin
package dev.kent.windbook.core.model

import java.text.Collator
import java.util.Locale

enum class LibraryLayout { GRID, LIST }

enum class StatusFilter {
    ALL, READING, TO_READ, FINISHED;

    fun matches(status: ReadingStatus): Boolean = when (this) {
        ALL -> true
        READING -> status == ReadingStatus.READING
        TO_READ -> status == ReadingStatus.TO_READ
        FINISHED -> status == ReadingStatus.FINISHED
    }
}

private val titleCollator: Comparator<String> =
    Collator.getInstance(Locale.ROOT).apply { strength = Collator.PRIMARY }
        .let { collator -> Comparator { a, b -> collator.compare(a, b) } }

enum class LibrarySort {
    RECENT, TITLE, AUTHOR, ADDED, PROGRESS;

    val comparator: Comparator<LibraryItem>
        get() = when (this) {
            RECENT -> compareByDescending<LibraryItem> { it.book.lastOpenedAt ?: Long.MIN_VALUE }
                .thenByDescending { it.book.addedAt }
            TITLE -> compareBy(titleCollator) { it.book.title }
            AUTHOR -> compareBy<LibraryItem, String?>(nullsLast(titleCollator)) { it.book.author }
                .thenBy(titleCollator) { it.book.title }
            ADDED -> compareByDescending { it.book.addedAt }
            PROGRESS -> compareByDescending<LibraryItem> { it.progression }
                .thenBy(titleCollator) { it.book.title }
        }
}

data class LibraryQuery(
    val text: String = "",
    val sort: LibrarySort = LibrarySort.RECENT,
    val filter: StatusFilter = StatusFilter.ALL,
) {
    fun apply(items: List<LibraryItem>): List<LibraryItem> {
        val needle = SearchText.normalize(text.trim())
        return items
            .filter { filter.matches(it.book.status) }
            .filter { needle.isEmpty() || it.matches(needle) }
            .sortedWith(sort.comparator)
    }

    private fun LibraryItem.matches(needle: String): Boolean =
        SearchText.normalize(book.title).contains(needle) ||
            book.author?.let { SearchText.normalize(it).contains(needle) } == true
}

/** The book shown in the "Continue reading" card: the most recently opened READING book. */
fun continueReading(items: List<LibraryItem>): LibraryItem? =
    items.filter { it.book.status == ReadingStatus.READING && it.book.lastOpenedAt != null }
        .maxByOrNull { it.book.lastOpenedAt!! }
```

`MetadataExtractor.kt`:
```kotlin
package dev.kent.windbook.core.model

import java.io.File

/** Metadata pulled from a book file. [coverWebp] is an already-encoded WebP image. */
class ExtractedMetadata(
    val title: String?,
    val author: String?,
    val coverWebp: ByteArray?,
)

/** Port implemented by the reader engine; a failure means the file can't be opened. */
fun interface MetadataExtractor {
    suspend fun extract(file: File, format: BookFormat): Result<ExtractedMetadata>
}
```

`core/model/src/testFixtures/kotlin/dev/kent/windbook/testing/TestBooks.kt`:
```kotlin
package dev.kent.windbook.testing

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Generates tiny, valid book files for tests. */
object TestBooks {

    fun epub(
        dir: File,
        name: String = "book.epub",
        title: String = "Test Book",
        author: String = "Ada Writer",
    ): File {
        val file = File(dir, name)
        ZipOutputStream(file.outputStream()).use { zip ->
            // OCF: "mimetype" first and stored uncompressed.
            val mime = "application/epub+zip".toByteArray()
            zip.putNextEntry(
                ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mime.size.toLong()
                    compressedSize = mime.size.toLong()
                    crc = CRC32().apply { update(mime) }.value
                },
            )
            zip.write(mime)
            zip.closeEntry()
            zip.text(
                "META-INF/container.xml",
                """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            )
            zip.text(
                "OEBPS/content.opf",
                """<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="uid">urn:uuid:${name.hashCode()}</dc:identifier><dc:title>$title</dc:title><dc:creator>$author</dc:creator><dc:language>en</dc:language><meta property="dcterms:modified">2026-01-01T00:00:00Z</meta></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/></spine></package>""",
            )
            zip.text(
                "OEBPS/nav.xhtml",
                """<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Nav</title></head><body><nav epub:type="toc"><ol><li><a href="c1.xhtml">Chapter One</a></li></ol></nav></body></html>""",
            )
            zip.text(
                "OEBPS/c1.xhtml",
                """<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>Chapter One</title></head><body><h1>Chapter One</h1><p>Hello WindBook.</p></body></html>""",
            )
        }
        return file
    }

    /** Header-valid PDF; good enough for format sniffing, not for rendering. */
    fun pdf(dir: File, name: String = "doc.pdf"): File =
        File(dir, name).apply { writeText("%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n") }

    fun zip(dir: File, name: String, entries: Map<String, ByteArray>): File {
        val file = File(dir, name)
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (entryName, bytes) ->
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    private fun ZipOutputStream.text(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray())
        closeEntry()
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:model:test`
Expected: PASS (16 tests).

- [ ] **Step 5: Commit**

```bash
git add core/model
git commit -m "feat(model): book domain, format sniffing, accent-insensitive library query"
```

---

### Task 3: Room 3 database with books and reading progress

**Files:**
- Create: `core/data/src/main/kotlin/dev/kent/windbook/core/data/db/Entities.kt`
- Create: `core/data/src/main/kotlin/dev/kent/windbook/core/data/db/LibraryDao.kt`
- Create: `core/data/src/main/kotlin/dev/kent/windbook/core/data/db/WindBookDatabase.kt`
- Test: `core/data/src/test/kotlin/dev/kent/windbook/core/data/db/LibraryDaoTest.kt`

**Interfaces:**
- Consumes: nothing beyond Room.
- Produces:
  - `BookEntity` (columns per spec §5 `books`), `ReadingProgressEntity(bookId, locatorJson, progression, updatedAt)`, `BookWithProgressRow(book: BookEntity, progression: Double)`
  - `interface LibraryDao` with: `observeLibrary(): Flow<List<BookWithProgressRow>>`, `suspend findById(id: String): BookEntity?`, `suspend findBySha(sha256: String): BookEntity?` (includes tombstoned), `suspend insert(book: BookEntity)`, `suspend update(book: BookEntity)`, `suspend updateStatus(id: String, status: String, now: Long)`, `suspend markOpened(id: String, now: Long)`, `suspend softDelete(id: String, now: Long)` (also deletes progress, in one transaction), `suspend purge(id: String)`, `suspend upsertProgress(progress: ReadingProgressEntity)`, `suspend progress(bookId: String): ReadingProgressEntity?`
  - `abstract class WindBookDatabase : RoomDatabase { abstract fun libraryDao(): LibraryDao; companion object { fun create(context: Context): WindBookDatabase; fun inMemory(context: Context, driver: SQLiteDriver): WindBookDatabase } }`

- [ ] **Step 1: Write the failing test**

`LibraryDaoTest.kt`:
```kotlin
package dev.kent.windbook.core.data.db

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryDaoTest {
    private lateinit var db: WindBookDatabase
    private lateinit var dao: LibraryDao

    @Before fun setUp() {
        db = WindBookDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
        dao = db.libraryDao()
    }

    @After fun tearDown() = db.close()

    private fun book(id: String, sha: String = "sha-$id", status: String = "TO_READ") = BookEntity(
        id = id, title = "Book $id", author = null, format = "EPUB", filePath = "/f/$id.epub",
        fileSize = 10, sha256 = sha, coverPath = null, sourceUri = null, status = status,
        addedAt = 1, lastOpenedAt = null, updatedAt = 1, deletedAt = null,
    )

    @Test fun observeLibrary_joinsProgressAndExcludesDeleted() = runTest {
        dao.insert(book("a"))
        dao.insert(book("b"))
        dao.upsertProgress(ReadingProgressEntity("a", """{"href":"c1"}""", 0.25, 5))
        dao.softDelete("b", now = 9)

        val rows = dao.observeLibrary().first()

        assertEquals(listOf("a"), rows.map { it.book.id })
        assertEquals(0.25, rows.single().progression, 1e-9)
    }

    @Test fun unreadBookHasZeroProgression() = runTest {
        dao.insert(book("a"))
        assertEquals(0.0, dao.observeLibrary().first().single().progression, 1e-9)
    }

    @Test fun markOpened_promotesToReadToReadingAndKeepsFinished() = runTest {
        dao.insert(book("a"))
        dao.insert(book("f", status = "FINISHED"))

        dao.markOpened("a", now = 42)
        dao.markOpened("f", now = 43)

        assertEquals("READING", dao.findById("a")!!.status)
        assertEquals(42L, dao.findById("a")!!.lastOpenedAt)
        assertEquals("FINISHED", dao.findById("f")!!.status)
    }

    @Test fun softDelete_hidesBookRemovesProgressButFindByShaStillSeesIt() = runTest {
        dao.insert(book("a", sha = "same"))
        dao.upsertProgress(ReadingProgressEntity("a", "{}", 0.5, 1))

        dao.softDelete("a", now = 7)

        assertNull(dao.findById("a"))
        assertNull(dao.progress("a"))
        assertEquals(7L, dao.findBySha("same")!!.deletedAt)
    }

    @Test fun purge_cascadesProgress() = runTest {
        dao.insert(book("a"))
        dao.upsertProgress(ReadingProgressEntity("a", "{}", 0.5, 1))

        dao.purge("a")

        assertNull(dao.findBySha("sha-a"))
        assertNull(dao.progress("a"))
    }

    @Test fun shaIsUnique() = runTest {
        dao.insert(book("a", sha = "dup"))
        val failure = runCatching { dao.insert(book("b", sha = "dup")) }.exceptionOrNull()
        assertNotNull(failure)
    }

    @Test fun upsertProgress_overwrites() = runTest {
        dao.insert(book("a"))
        dao.upsertProgress(ReadingProgressEntity("a", "{\"v\":1}", 0.1, 1))
        dao.upsertProgress(ReadingProgressEntity("a", "{\"v\":2}", 0.2, 2))
        val p = dao.progress("a")!!
        assertTrue(p.locatorJson.contains("2"))
        assertEquals(0.2, p.progression, 1e-9)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*LibraryDaoTest*'`
Expected: FAIL, compilation errors (unresolved `WindBookDatabase`, `BookEntity`).

- [ ] **Step 3: Implement**

`Entities.kt`:
```kotlin
package dev.kent.windbook.core.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["sha256"], unique = true),
        Index(value = ["last_opened_at"]),
    ],
)
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String?,
    val format: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    val sha256: String,
    @ColumnInfo(name = "cover_path") val coverPath: String?,
    @ColumnInfo(name = "source_uri") val sourceUri: String?,
    val status: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
)

@Entity(
    tableName = "reading_progress",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ReadingProgressEntity(
    @PrimaryKey @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "locator_json") val locatorJson: String,
    val progression: Double,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

data class BookWithProgressRow(
    @Embedded val book: BookEntity,
    val progression: Double,
)
```

`LibraryDao.kt`:
```kotlin
package dev.kent.windbook.core.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    @Query(
        """
        SELECT b.*, COALESCE(p.progression, 0.0) AS progression
        FROM books b LEFT JOIN reading_progress p ON p.book_id = b.id
        WHERE b.deleted_at IS NULL
        """,
    )
    fun observeLibrary(): Flow<List<BookWithProgressRow>>

    @Query("SELECT * FROM books WHERE id = :id AND deleted_at IS NULL")
    suspend fun findById(id: String): BookEntity?

    /** Includes tombstoned rows so a re-import can revive them. */
    @Query("SELECT * FROM books WHERE sha256 = :sha256 LIMIT 1")
    suspend fun findBySha(sha256: String): BookEntity?

    @Insert
    suspend fun insert(book: BookEntity)

    @Update
    suspend fun update(book: BookEntity)

    @Query("UPDATE books SET status = :status, updated_at = :now WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, now: Long)

    @Query(
        """
        UPDATE books SET last_opened_at = :now, updated_at = :now,
            status = CASE WHEN status = 'TO_READ' THEN 'READING' ELSE status END
        WHERE id = :id
        """,
    )
    suspend fun markOpened(id: String, now: Long)

    @Query("UPDATE books SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun tombstone(id: String, now: Long)

    @Query("DELETE FROM reading_progress WHERE book_id = :id")
    suspend fun deleteProgress(id: String)

    @Transaction
    suspend fun softDelete(id: String, now: Long) {
        tombstone(id, now)
        deleteProgress(id)
    }

    /** Hard delete; used by tombstone garbage collection after sync (M7) and by tests. */
    @Query("DELETE FROM books WHERE id = :id")
    suspend fun purge(id: String)

    @Upsert
    suspend fun upsertProgress(progress: ReadingProgressEntity)

    @Query("SELECT * FROM reading_progress WHERE book_id = :bookId")
    suspend fun progress(bookId: String): ReadingProgressEntity?
}
```

`WindBookDatabase.kt`:
```kotlin
package dev.kent.windbook.core.data.db

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [BookEntity::class, ReadingProgressEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class WindBookDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        const val FILE_NAME = "windbook.db"

        fun create(context: Context): WindBookDatabase =
            Room.databaseBuilder<WindBookDatabase>(
                context = context,
                name = context.getDatabasePath(FILE_NAME).absolutePath,
            )
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()

        fun inMemory(context: Context, driver: SQLiteDriver): WindBookDatabase =
            Room.inMemoryDatabaseBuilder<WindBookDatabase>(context)
                .setDriver(driver)
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
    }
}
```
If Room 3's builder signatures differ (the reified builders are carried over from Room 2.7 KMP), open `room3-runtime` sources in Android Studio (`Room.kt`) and adjust only the builder calls. Entities and DAO stay the same.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*LibraryDaoTest*'`
Expected: PASS (7 tests). `core/data/schemas/dev.kent.windbook.core.data.db.WindBookDatabase/1.json` is generated.

- [ ] **Step 5: Commit**

```bash
git add core/data
git commit -m "feat(data): Room 3 schema v1 for books and reading progress"
```

---

### Task 4: Book file storage and EPUB zip guard

**Files:**
- Create: `core/data/src/main/kotlin/dev/kent/windbook/core/data/files/BookFileStore.kt`
- Create: `core/data/src/main/kotlin/dev/kent/windbook/core/data/importer/ImportModels.kt`
- Create: `core/data/src/main/kotlin/dev/kent/windbook/core/data/importer/ZipGuard.kt`
- Test: `core/data/src/test/kotlin/dev/kent/windbook/core/data/files/BookFileStoreTest.kt`, `core/data/src/test/kotlin/dev/kent/windbook/core/data/importer/ZipGuardTest.kt`

**Interfaces:**
- Produces:
  - `class FileTooLargeException(val limitBytes: Long) : IOException`
  - `data class CopiedFile(val file: File, val size: Long, val sha256: String)`
  - `class BookFileStore(root: File) { fun copyToTemp(input: InputStream, maxBytes: Long): CopiedFile; fun readHeader(file: File): ByteArray; fun commit(temp: File, id: String, format: BookFormat): File; fun writeCover(id: String, webp: ByteArray): File; fun delete(path: String?); fun usableBytes(): Long }`
  - `enum class ImportError { UNSUPPORTED_FORMAT, NOT_AN_EPUB, CORRUPT, TOO_LARGE, TOO_LARGE_UNCOMPRESSED, TOO_MANY_ENTRIES, UNSAFE_PATHS, NO_SPACE, READ_FAILED, UNREADABLE }`
  - `interface ImportSource { val displayName: String?; val sourceUri: String?; fun open(): InputStream }`
  - `sealed interface ImportResult { data class Imported(val bookId: String); data class Duplicate(val bookId: String); data class Failed(val error: ImportError) }`
  - `class ZipGuard(maxEntries: Int = 10_000, maxUncompressedBytes: Long = 2 GiB) { fun inspectEpub(file: File): ImportError? }`

- [ ] **Step 1: Write the failing tests**

`BookFileStoreTest.kt`:
```kotlin
package dev.kent.windbook.core.data.files

import dev.kent.windbook.core.model.BookFormat
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BookFileStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun sha(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test fun copyToTemp_streamsAndHashes() {
        val store = BookFileStore(tmp.root)
        val bytes = ByteArray(200_000) { (it % 251).toByte() }

        val copied = store.copyToTemp(bytes.inputStream(), maxBytes = 1_000_000)

        assertEquals(bytes.size.toLong(), copied.size)
        assertEquals(sha(bytes), copied.sha256)
        assertArrayEquals(bytes, copied.file.readBytes())
    }

    @Test fun copyToTemp_overLimit_throwsAndLeavesNoTempFile() {
        val store = BookFileStore(tmp.root)
        try {
            store.copyToTemp(ByteArray(2_000).inputStream(), maxBytes = 1_000)
            fail("expected FileTooLargeException")
        } catch (e: FileTooLargeException) {
            assertEquals(1_000L, e.limitBytes)
        }
        assertTrue(store.tempDir.listFiles().orEmpty().isEmpty())
    }

    @Test fun commit_movesIntoBooksDirWithExtension() {
        val store = BookFileStore(tmp.root)
        val copied = store.copyToTemp("%PDF-1.4".toByteArray().inputStream(), 100)

        val final = store.commit(copied.file, "id1", BookFormat.PDF)

        assertEquals("id1.pdf", final.name)
        assertEquals(store.booksDir, final.parentFile)
        assertFalse(copied.file.exists())
    }

    @Test fun readHeader_returnsAtMost1024Bytes() {
        val store = BookFileStore(tmp.root)
        val f = tmp.newFile("big").apply { writeBytes(ByteArray(5_000) { 1 }) }
        assertEquals(1024, store.readHeader(f).size)
        val small = tmp.newFile("small").apply { writeBytes(ByteArray(10)) }
        assertEquals(10, store.readHeader(small).size)
    }

    @Test fun delete_isNullSafe() {
        val store = BookFileStore(tmp.root)
        store.delete(null)
        val cover = store.writeCover("c", byteArrayOf(1, 2, 3))
        store.delete(cover.absolutePath)
        assertFalse(cover.exists())
    }
}
```

`ZipGuardTest.kt`:
```kotlin
package dev.kent.windbook.core.data.importer

import dev.kent.windbook.testing.TestBooks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ZipGuardTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun validEpubPasses() =
        assertNull(ZipGuard().inspectEpub(TestBooks.epub(tmp.root)))

    @Test fun zipWithoutContainerIsNotAnEpub() {
        val zip = TestBooks.zip(tmp.root, "x.zip", mapOf("readme.txt" to "hi".toByteArray()))
        assertEquals(ImportError.NOT_AN_EPUB, ZipGuard().inspectEpub(zip))
    }

    @Test fun parentTraversalIsRejected() {
        val zip = TestBooks.zip(
            tmp.root, "evil.epub",
            mapOf("META-INF/container.xml" to ByteArray(1), "../../evil.so" to ByteArray(1)),
        )
        assertEquals(ImportError.UNSAFE_PATHS, ZipGuard().inspectEpub(zip))
    }

    @Test fun tooManyEntriesIsRejected() {
        val entries = (0 until 6).associate { "f$it" to ByteArray(1) } + ("META-INF/container.xml" to ByteArray(1))
        val zip = TestBooks.zip(tmp.root, "many.epub", entries)
        assertEquals(ImportError.TOO_MANY_ENTRIES, ZipGuard(maxEntries = 5).inspectEpub(zip))
    }

    @Test fun zipBombIsRejected() {
        val zip = TestBooks.zip(
            tmp.root, "bomb.epub",
            mapOf("META-INF/container.xml" to ByteArray(1), "big" to ByteArray(5_000)),
        )
        assertEquals(ImportError.TOO_LARGE_UNCOMPRESSED, ZipGuard(maxUncompressedBytes = 1_000).inspectEpub(zip))
    }

    @Test fun truncatedZipIsCorrupt() {
        val good = TestBooks.epub(tmp.root)
        val broken = tmp.newFile("broken.epub").apply { writeBytes(good.readBytes().copyOf(60)) }
        assertEquals(ImportError.CORRUPT, ZipGuard().inspectEpub(broken))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*BookFileStoreTest*' --tests '*ZipGuardTest*'`
Expected: FAIL, unresolved `BookFileStore`, `ZipGuard`, `ImportError`.

- [ ] **Step 3: Implement**

`BookFileStore.kt`:
```kotlin
package dev.kent.windbook.core.data.files

import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.FormatSniffer
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class FileTooLargeException(val limitBytes: Long) : IOException("File exceeds $limitBytes bytes")

data class CopiedFile(val file: File, val size: Long, val sha256: String)

/** Owns the app-private copies of books and covers under [root]. */
class BookFileStore(private val root: File) {
    val booksDir = File(root, "books")
    val coversDir = File(root, "covers")
    val tempDir = File(root, "import-tmp")

    /** Streams [input] into a temp file while hashing; never holds the whole book in memory. */
    fun copyToTemp(input: InputStream, maxBytes: Long): CopiedFile {
        tempDir.mkdirs()
        val temp = File.createTempFile("import-", ".part", tempDir)
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        try {
            temp.outputStream().buffered().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) throw FileTooLargeException(maxBytes)
                    digest.update(buffer, 0, read)
                    out.write(buffer, 0, read)
                }
            }
        } catch (t: Throwable) {
            temp.delete()
            throw t
        }
        return CopiedFile(temp, total, digest.digest().joinToString("") { "%02x".format(it) })
    }

    fun readHeader(file: File): ByteArray = file.inputStream().use { input ->
        val buffer = ByteArray(FormatSniffer.HEADER_SIZE)
        var filled = 0
        while (filled < buffer.size) {
            val read = input.read(buffer, filled, buffer.size - filled)
            if (read < 0) break
            filled += read
        }
        buffer.copyOf(filled)
    }

    fun commit(temp: File, id: String, format: BookFormat): File {
        booksDir.mkdirs()
        val destination = File(booksDir, "$id.${format.extension}")
        if (!temp.renameTo(destination)) {
            temp.copyTo(destination, overwrite = true)
            temp.delete()
        }
        return destination
    }

    fun writeCover(id: String, webp: ByteArray): File {
        coversDir.mkdirs()
        return File(coversDir, "$id.webp").apply { writeBytes(webp) }
    }

    fun delete(path: String?) {
        if (path != null) File(path).delete()
    }

    fun usableBytes(): Long = root.usableSpace
}
```

`ImportModels.kt`:
```kotlin
package dev.kent.windbook.core.data.importer

import java.io.InputStream

enum class ImportError {
    UNSUPPORTED_FORMAT,
    NOT_AN_EPUB,
    CORRUPT,
    TOO_LARGE,
    TOO_LARGE_UNCOMPRESSED,
    TOO_MANY_ENTRIES,
    UNSAFE_PATHS,
    NO_SPACE,
    READ_FAILED,
    UNREADABLE,
}

/** Where a book comes from (a SAF Uri in the app, a File in tests). */
interface ImportSource {
    val displayName: String?
    val sourceUri: String?
    fun open(): InputStream
}

sealed interface ImportResult {
    data class Imported(val bookId: String) : ImportResult
    data class Duplicate(val bookId: String) : ImportResult
    data class Failed(val error: ImportError) : ImportResult
}
```

`ZipGuard.kt`:
```kotlin
package dev.kent.windbook.core.data.importer

import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/** Rejects EPUB zips that are malformed, malicious (path traversal) or zip bombs. */
class ZipGuard(
    private val maxEntries: Int = 10_000,
    private val maxUncompressedBytes: Long = 2L * 1024 * 1024 * 1024,
) {
    fun inspectEpub(file: File): ImportError? {
        try {
            ZipFile(file).use { zip ->
                var count = 0
                var total = 0L
                var hasContainer = false
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    count++
                    if (count > maxEntries) return ImportError.TOO_MANY_ENTRIES
                    if (isUnsafe(entry.name)) return ImportError.UNSAFE_PATHS
                    if (entry.size > 0) total += entry.size
                    if (total > maxUncompressedBytes) return ImportError.TOO_LARGE_UNCOMPRESSED
                    if (entry.name == "META-INF/container.xml") hasContainer = true
                }
                return if (hasContainer) null else ImportError.NOT_AN_EPUB
            }
        } catch (e: IOException) {
            // ZipException is an IOException; Android 14+ also throws it for unsafe names.
            return if (e.message?.contains("path", ignoreCase = true) == true) ImportError.UNSAFE_PATHS else ImportError.CORRUPT
        }
    }

    private fun isUnsafe(name: String): Boolean {
        val normalized = name.replace('\\', '/')
        return normalized.startsWith("/") || normalized.split('/').any { it == ".." }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*BookFileStoreTest*' --tests '*ZipGuardTest*'`
Expected: PASS (11 tests).

- [ ] **Step 5: Commit**

```bash
git add core/data
git commit -m "feat(data): streaming book file store with SHA-256 and EPUB zip guard"
```

---

### Task 5: Book importer

**Files:**
- Create: `core/data/src/main/kotlin/dev/kent/windbook/core/data/importer/BookImporter.kt`
- Test: `core/data/src/test/kotlin/dev/kent/windbook/core/data/importer/BookImporterTest.kt`

**Interfaces:**
- Consumes: `LibraryDao` (Task 3), `BookFileStore`, `ZipGuard`, `ImportSource`, `ImportResult`, `ImportError` (Task 4), `MetadataExtractor`, `FormatSniffer` (Task 2).
- Produces: `class BookImporter(dao: LibraryDao, files: BookFileStore, metadata: MetadataExtractor, zipGuard: ZipGuard = ZipGuard(), clock: () -> Long = System::currentTimeMillis, newId: () -> String = { UUID.randomUUID().toString() }, maxBytes: Long = 500 MiB) { suspend fun import(source: ImportSource): ImportResult }`

- [ ] **Step 1: Write the failing test**

`BookImporterTest.kt`:
```kotlin
package dev.kent.windbook.core.data.importer

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.data.db.WindBookDatabase
import dev.kent.windbook.core.data.files.BookFileStore
import dev.kent.windbook.core.model.ExtractedMetadata
import dev.kent.windbook.core.model.MetadataExtractor
import dev.kent.windbook.testing.TestBooks
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookImporterTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: WindBookDatabase
    private lateinit var store: BookFileStore
    private var ids = 0
    private var metadata = MetadataExtractor { _, _ -> Result.success(ExtractedMetadata("Meta Title", "Meta Author", byteArrayOf(9, 9))) }

    @Before fun setUp() {
        db = WindBookDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
        store = BookFileStore(tmp.newFolder("app"))
    }

    @After fun tearDown() = db.close()

    private fun importer(maxBytes: Long = 500L * 1024 * 1024) = BookImporter(
        dao = db.libraryDao(), files = store, metadata = { f, fmt -> metadata.extract(f, fmt) },
        clock = { 1_000L }, newId = { "id${++ids}" }, maxBytes = maxBytes,
    )

    private fun source(file: File, name: String? = file.name) = object : ImportSource {
        override val displayName = name
        override val sourceUri = "content://test/${file.name}"
        override fun open(): InputStream = file.inputStream()
    }

    @Test fun import_epub_storesBookCopyAndCover() = runTest {
        val result = importer().import(source(TestBooks.epub(tmp.root)))

        assertEquals(ImportResult.Imported("id1"), result)
        val book = db.libraryDao().findById("id1")!!
        assertEquals("Meta Title", book.title)
        assertEquals("Meta Author", book.author)
        assertEquals("EPUB", book.format)
        assertEquals("TO_READ", book.status)
        assertTrue(File(book.filePath).exists())
        assertTrue(File(book.coverPath!!).exists())
        assertTrue(store.tempDir.listFiles().orEmpty().isEmpty())
    }

    @Test fun import_pdf_isDetectedByContentNotName() = runTest {
        val pdf = TestBooks.pdf(tmp.root, "weird-name.bin")
        assertEquals(ImportResult.Imported("id1"), importer().import(source(pdf)))
        assertEquals("PDF", db.libraryDao().findById("id1")!!.format)
    }

    @Test fun import_sameFileTwice_isDuplicate() = runTest {
        val file = TestBooks.epub(tmp.root)
        importer().import(source(file))
        assertEquals(ImportResult.Duplicate("id1"), importer().import(source(file)))
        assertEquals(1, store.booksDir.listFiles()!!.size)
    }

    @Test fun import_afterDelete_revivesTombstonedBook() = runTest {
        val file = TestBooks.epub(tmp.root)
        importer().import(source(file))
        val old = db.libraryDao().findById("id1")!!
        db.libraryDao().softDelete("id1", now = 5)
        File(old.filePath).delete()

        val result = importer().import(source(file))

        assertEquals(ImportResult.Imported("id1"), result)
        val revived = db.libraryDao().findById("id1")!!
        assertNull(revived.deletedAt)
        assertTrue(File(revived.filePath).exists())
    }

    @Test fun import_blankMetadataTitle_fallsBackToFileName() = runTest {
        metadata = MetadataExtractor { _, _ -> Result.success(ExtractedMetadata("  ", null, null)) }
        importer().import(source(TestBooks.epub(tmp.root, name = "My Novel.epub")))
        val book = db.libraryDao().findById("id1")!!
        assertEquals("My Novel", book.title)
        assertNull(book.coverPath)
    }

    @Test fun import_withoutTitleOrDisplayName_usesUntitled() = runTest {
        metadata = MetadataExtractor { _, _ -> Result.success(ExtractedMetadata(null, null, null)) }
        importer().import(source(TestBooks.epub(tmp.root), name = null))
        assertEquals("Untitled", db.libraryDao().findById("id1")!!.title)
    }

    @Test fun import_textFile_isUnsupported() = runTest {
        val txt = tmp.newFile("notes.epub").apply { writeText("just text") }
        assertEquals(ImportResult.Failed(ImportError.UNSUPPORTED_FORMAT), importer().import(source(txt)))
        assertTrue(store.booksDir.listFiles().orEmpty().isEmpty())
    }

    @Test fun import_zipThatIsNotEpub_isRejected() = runTest {
        val zip = TestBooks.zip(tmp.root, "photos.epub", mapOf("a.jpg" to ByteArray(3)))
        assertEquals(ImportResult.Failed(ImportError.NOT_AN_EPUB), importer().import(source(zip)))
    }

    @Test fun import_tooLarge_isRejectedWithoutCopy() = runTest {
        val result = importer(maxBytes = 100).import(source(TestBooks.epub(tmp.root)))
        assertEquals(ImportResult.Failed(ImportError.TOO_LARGE), result)
        assertTrue(store.tempDir.listFiles().orEmpty().isEmpty())
    }

    @Test fun import_metadataFailure_isUnreadableAndCleansUp() = runTest {
        metadata = MetadataExtractor { _, _ -> Result.failure(IllegalStateException("bad")) }
        assertEquals(ImportResult.Failed(ImportError.UNREADABLE), importer().import(source(TestBooks.epub(tmp.root))))
        assertTrue(store.booksDir.listFiles().orEmpty().isEmpty())
        assertNull(db.libraryDao().findBySha("anything"))
    }

    @Test fun import_unreadableSource_isReadFailed() = runTest {
        val broken = object : ImportSource {
            override val displayName = "x.epub"
            override val sourceUri = null
            override fun open(): InputStream = throw java.io.FileNotFoundException("gone")
        }
        assertEquals(ImportResult.Failed(ImportError.READ_FAILED), importer().import(broken))
        assertNotNull(store)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*BookImporterTest*'`
Expected: FAIL, unresolved `BookImporter`.

- [ ] **Step 3: Implement**

`BookImporter.kt`:
```kotlin
package dev.kent.windbook.core.data.importer

import dev.kent.windbook.core.data.db.BookEntity
import dev.kent.windbook.core.data.db.LibraryDao
import dev.kent.windbook.core.data.files.BookFileStore
import dev.kent.windbook.core.data.files.FileTooLargeException
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.FileSignature
import dev.kent.windbook.core.model.FormatSniffer
import dev.kent.windbook.core.model.MetadataExtractor
import dev.kent.windbook.core.model.ReadingStatus
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BookImporter(
    private val dao: LibraryDao,
    private val files: BookFileStore,
    private val metadata: MetadataExtractor,
    private val zipGuard: ZipGuard = ZipGuard(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val maxBytes: Long = 500L * 1024 * 1024,
) {
    suspend fun import(source: ImportSource): ImportResult = withContext(Dispatchers.IO) {
        val copied = try {
            source.open().use { files.copyToTemp(it, maxBytes) }
        } catch (e: FileTooLargeException) {
            return@withContext ImportResult.Failed(ImportError.TOO_LARGE)
        } catch (e: IOException) {
            val error = if (files.usableBytes() < LOW_SPACE_BYTES) ImportError.NO_SPACE else ImportError.READ_FAILED
            return@withContext ImportResult.Failed(error)
        }

        try {
            val format = when (FormatSniffer.signature(files.readHeader(copied.file))) {
                FileSignature.PDF -> BookFormat.PDF
                FileSignature.ZIP -> {
                    zipGuard.inspectEpub(copied.file)?.let { return@withContext ImportResult.Failed(it) }
                    BookFormat.EPUB
                }
                FileSignature.UNKNOWN -> return@withContext ImportResult.Failed(ImportError.UNSUPPORTED_FORMAT)
            }

            val existing = dao.findBySha(copied.sha256)
            if (existing != null && existing.deletedAt == null) {
                return@withContext ImportResult.Duplicate(existing.id)
            }

            val id = existing?.id ?: newId()
            val bookFile = files.commit(copied.file, id, format)
            val meta = metadata.extract(bookFile, format).getOrElse {
                bookFile.delete()
                return@withContext ImportResult.Failed(ImportError.UNREADABLE)
            }
            val coverPath = meta.coverWebp?.let { files.writeCover(id, it).absolutePath }
            val now = clock()
            val entity = BookEntity(
                id = id,
                title = meta.title?.takeIf { it.isNotBlank() }?.trim()
                    ?: source.displayName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                    ?: "Untitled",
                author = meta.author?.takeIf { it.isNotBlank() }?.trim(),
                format = format.name,
                filePath = bookFile.absolutePath,
                fileSize = copied.size,
                sha256 = copied.sha256,
                coverPath = coverPath,
                sourceUri = source.sourceUri,
                status = ReadingStatus.TO_READ.name,
                addedAt = now,
                lastOpenedAt = null,
                updatedAt = now,
                deletedAt = null,
            )
            if (existing != null) dao.update(entity) else insertOrDuplicate(entity, bookFile, coverPath)
        } finally {
            copied.file.delete() // no-op once committed (renamed away)
        }
    }

    /** Two concurrent imports of the same file race on the unique sha256 index. */
    private suspend fun insertOrDuplicate(entity: BookEntity, bookFile: File, coverPath: String?): ImportResult =
        try {
            dao.insert(entity)
            ImportResult.Imported(entity.id)
        } catch (e: Exception) {
            val winner = dao.findBySha(entity.sha256) ?: throw e
            bookFile.delete()
            files.delete(coverPath)
            ImportResult.Duplicate(winner.id)
        }

    private suspend fun LibraryDao.update(entity: BookEntity): ImportResult {
        update(entity)
        return ImportResult.Imported(entity.id)
    }

    private companion object {
        const val LOW_SPACE_BYTES = 50L * 1024 * 1024
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*BookImporterTest*'`
Expected: PASS (11 tests).

- [ ] **Step 5: Commit**

```bash
git add core/data
git commit -m "feat(data): book importer with sniffing, dedupe, tombstone revival and metadata fallback"
```

---

### Task 6: Library repository and library preferences

**Files:**
- Create: `core/data/src/main/kotlin/dev/kent/windbook/core/data/LibraryRepository.kt`
- Create: `core/data/src/main/kotlin/dev/kent/windbook/core/data/LibraryPreferences.kt`
- Test: `core/data/src/test/kotlin/dev/kent/windbook/core/data/LibraryRepositoryTest.kt`, `core/data/src/test/kotlin/dev/kent/windbook/core/data/LibraryPreferencesTest.kt`

**Interfaces:**
- Consumes: `LibraryDao`, `BookFileStore`, model types.
- Produces:
  - `class LibraryRepository(dao: LibraryDao, files: BookFileStore, clock: () -> Long = System::currentTimeMillis)` with `fun observeLibrary(): Flow<List<LibraryItem>>`, `suspend fun book(id: String): Book?`, `suspend fun setStatus(id: String, status: ReadingStatus)`, `suspend fun markOpened(id: String)`, `suspend fun delete(id: String)`, `suspend fun savedLocator(id: String): String?`, `suspend fun saveProgress(id: String, locatorJson: String, progression: Double)`
  - `class LibraryPreferences(dataStore: DataStore<Preferences>)` with `val sort: Flow<LibrarySort>`, `val layout: Flow<LibraryLayout>`, `suspend fun setSort(sort: LibrarySort)`, `suspend fun setLayout(layout: LibraryLayout)`
  - `val Context.libraryDataStore: DataStore<Preferences>` (file `library`)

- [ ] **Step 1: Write the failing tests**

`LibraryRepositoryTest.kt`:
```kotlin
package dev.kent.windbook.core.data

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.data.db.BookEntity
import dev.kent.windbook.core.data.db.WindBookDatabase
import dev.kent.windbook.core.data.files.BookFileStore
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.ReadingStatus
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: WindBookDatabase
    private lateinit var repo: LibraryRepository
    private lateinit var bookFile: File
    private lateinit var coverFile: File

    @Before fun setUp() = runTest {
        db = WindBookDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
        val store = BookFileStore(tmp.newFolder("app"))
        repo = LibraryRepository(db.libraryDao(), store, clock = { 77L })
        bookFile = tmp.newFile("a.epub")
        coverFile = tmp.newFile("a.webp")
        db.libraryDao().insert(
            BookEntity("a", "Dune", "Frank Herbert", "EPUB", bookFile.absolutePath, 1, "sha", coverFile.absolutePath,
                null, "TO_READ", 1, null, 1, null),
        )
    }

    @After fun tearDown() = db.close()

    @Test fun observeLibrary_mapsToDomain() = runTest {
        val item = repo.observeLibrary().first().single()
        assertEquals("Dune", item.book.title)
        assertEquals(BookFormat.EPUB, item.book.format)
        assertEquals(ReadingStatus.TO_READ, item.book.status)
        assertEquals(0.0, item.progression, 1e-9)
    }

    @Test fun saveProgress_thenSavedLocatorAndProgression() = runTest {
        repo.saveProgress("a", """{"href":"c1"}""", 0.37)
        assertEquals("""{"href":"c1"}""", repo.savedLocator("a"))
        assertEquals(0.37, repo.observeLibrary().first().single().progression, 1e-9)
    }

    @Test fun markOpened_setsReadingAndTimestamp() = runTest {
        repo.markOpened("a")
        val book = repo.book("a")!!
        assertEquals(ReadingStatus.READING, book.status)
        assertEquals(77L, book.lastOpenedAt)
    }

    @Test fun setStatus_finished() = runTest {
        repo.setStatus("a", ReadingStatus.FINISHED)
        assertEquals(ReadingStatus.FINISHED, repo.book("a")!!.status)
    }

    @Test fun delete_removesFilesAndHidesBook() = runTest {
        repo.saveProgress("a", "{}", 0.5)
        repo.delete("a")
        assertNull(repo.book("a"))
        assertFalse(bookFile.exists())
        assertFalse(coverFile.exists())
        assertEquals(emptyList<Any>(), repo.observeLibrary().first())
        assertNull(repo.savedLocator("a"))
    }
}
```

`LibraryPreferencesTest.kt`:
```kotlin
package dev.kent.windbook.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.kent.windbook.core.model.LibraryLayout
import dev.kent.windbook.core.model.LibrarySort
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LibraryPreferencesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun defaultsThenPersistedValues() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { java.io.File(tmp.root, "library.preferences_pb") }
        val prefs = LibraryPreferences(store)

        assertEquals(LibrarySort.RECENT, prefs.sort.first())
        assertEquals(LibraryLayout.GRID, prefs.layout.first())

        prefs.setSort(LibrarySort.TITLE)
        prefs.setLayout(LibraryLayout.LIST)

        assertEquals(LibrarySort.TITLE, prefs.sort.first())
        assertEquals(LibraryLayout.LIST, prefs.layout.first())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*LibraryRepositoryTest*' --tests '*LibraryPreferencesTest*'`
Expected: FAIL, unresolved `LibraryRepository`, `LibraryPreferences`.

- [ ] **Step 3: Implement**

`LibraryRepository.kt`:
```kotlin
package dev.kent.windbook.core.data

import dev.kent.windbook.core.data.db.BookEntity
import dev.kent.windbook.core.data.db.LibraryDao
import dev.kent.windbook.core.data.db.ReadingProgressEntity
import dev.kent.windbook.core.data.files.BookFileStore
import dev.kent.windbook.core.model.Book
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.LibraryItem
import dev.kent.windbook.core.model.ReadingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LibraryRepository(
    private val dao: LibraryDao,
    private val files: BookFileStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeLibrary(): Flow<List<LibraryItem>> =
        dao.observeLibrary().map { rows -> rows.map { LibraryItem(it.book.toDomain(), it.progression) } }

    suspend fun book(id: String): Book? = dao.findById(id)?.toDomain()

    suspend fun setStatus(id: String, status: ReadingStatus) = dao.updateStatus(id, status.name, clock())

    suspend fun markOpened(id: String) = dao.markOpened(id, clock())

    suspend fun delete(id: String) {
        val book = dao.findById(id) ?: return
        dao.softDelete(id, clock())
        files.delete(book.filePath)
        files.delete(book.coverPath)
    }

    suspend fun savedLocator(id: String): String? = dao.progress(id)?.locatorJson

    suspend fun saveProgress(id: String, locatorJson: String, progression: Double) =
        dao.upsertProgress(ReadingProgressEntity(id, locatorJson, progression.coerceIn(0.0, 1.0), clock()))
}

internal fun BookEntity.toDomain() = Book(
    id = id,
    title = title,
    author = author,
    format = BookFormat.valueOf(format),
    filePath = filePath,
    fileSize = fileSize,
    sha256 = sha256,
    coverPath = coverPath,
    status = ReadingStatus.valueOf(status),
    addedAt = addedAt,
    lastOpenedAt = lastOpenedAt,
)
```

`LibraryPreferences.kt`:
```kotlin
package dev.kent.windbook.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.kent.windbook.core.model.LibraryLayout
import dev.kent.windbook.core.model.LibrarySort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.libraryDataStore: DataStore<Preferences> by preferencesDataStore(name = "library")

class LibraryPreferences(private val dataStore: DataStore<Preferences>) {

    val sort: Flow<LibrarySort> = dataStore.data.map { prefs ->
        prefs[SORT]?.let { runCatching { LibrarySort.valueOf(it) }.getOrNull() } ?: LibrarySort.RECENT
    }

    val layout: Flow<LibraryLayout> = dataStore.data.map { prefs ->
        prefs[LAYOUT]?.let { runCatching { LibraryLayout.valueOf(it) }.getOrNull() } ?: LibraryLayout.GRID
    }

    suspend fun setSort(sort: LibrarySort) {
        dataStore.edit { it[SORT] = sort.name }
    }

    suspend fun setLayout(layout: LibraryLayout) {
        dataStore.edit { it[LAYOUT] = layout.name }
    }

    private companion object {
        val SORT = stringPreferencesKey("sort")
        val LAYOUT = stringPreferencesKey("layout")
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: PASS (all `:core:data` tests, 30 total).

- [ ] **Step 5: Commit**

```bash
git add core/data
git commit -m "feat(data): library repository and persisted library preferences"
```

---

### Task 7: Readium engine: open publications, extract metadata, decode locators

**Files:**
- Create: `reader/src/main/kotlin/dev/kent/windbook/reader/ReaderApi.kt`
- Create: `reader/src/main/kotlin/dev/kent/windbook/reader/ReadiumServices.kt`
- Create: `reader/src/main/kotlin/dev/kent/windbook/reader/ReadiumMetadataExtractor.kt`
- Create: `reader/src/main/kotlin/dev/kent/windbook/reader/ReaderEngine.kt`
- Create: `reader/src/main/kotlin/dev/kent/windbook/reader/LocatorCodec.kt`
- Test: `reader/src/test/kotlin/dev/kent/windbook/reader/ReadiumEngineTest.kt`

**Interfaces:**
- Consumes: `BookFormat`, `MetadataExtractor`, `ExtractedMetadata` (Task 2).
- Produces:
  - `interface ReaderSession : AutoCloseable { val format: BookFormat }`
  - `fun interface BookOpener { suspend fun open(file: File, format: BookFormat): Result<ReaderSession> }`
  - `class ReadiumServices(context: Context, pdfFactory: PdfDocumentFactory<*>? = PdfiumDocumentFactory(context))` with `suspend fun openPublication(file: File): Result<Publication>`
  - `class ReadiumMetadataExtractor(services: ReadiumServices) : MetadataExtractor`
  - `class ReaderEngine(services: ReadiumServices) : BookOpener`
  - `internal class ReadiumReaderSession(format: BookFormat, publication: Publication) : ReaderSession`
  - `object LocatorCodec { fun progression(locatorJson: String): Double? }`; internal `String.toLocatorOrNull(): Locator?`, `Locator.toJsonString(): String`

- [ ] **Step 1: Write the failing test**

`ReadiumEngineTest.kt`:
```kotlin
package dev.kent.windbook.reader

import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.testing.TestBooks
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReadiumEngineTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var services: ReadiumServices

    @Before fun setUp() {
        // PDFium needs native code that Robolectric can't load; EPUB doesn't need it.
        services = ReadiumServices(ApplicationProvider.getApplicationContext(), pdfFactory = null)
    }

    @Test fun extractsTitleAndAuthorFromEpub() = runTest {
        val epub = TestBooks.epub(tmp.root, title = "Dế Mèn Phiêu Lưu Ký", author = "Tô Hoài")

        val meta = ReadiumMetadataExtractor(services).extract(epub, BookFormat.EPUB).getOrThrow()

        assertEquals("Dế Mèn Phiêu Lưu Ký", meta.title)
        assertEquals("Tô Hoài", meta.author)
        assertNull(meta.coverWebp)
    }

    @Test fun extractFailsForGarbage() = runTest {
        val junk = tmp.newFile("junk.epub").apply { writeText("not a zip") }
        assertTrue(ReadiumMetadataExtractor(services).extract(junk, BookFormat.EPUB).isFailure)
    }

    @Test fun engineOpensEpubSession() = runTest {
        val session = ReaderEngine(services).open(TestBooks.epub(tmp.root), BookFormat.EPUB).getOrThrow()
        assertEquals(BookFormat.EPUB, session.format)
        session.close()
    }

    @Test fun engineFailsForMissingFile() = runTest {
        assertTrue(ReaderEngine(services).open(tmp.root.resolve("missing.epub"), BookFormat.EPUB).isFailure)
    }

    @Test fun locatorCodecReadsTotalProgression() {
        val json = """{"href":"OEBPS/c1.xhtml","type":"application/xhtml+xml","locations":{"totalProgression":0.37}}"""
        assertEquals(0.37, LocatorCodec.progression(json)!!, 1e-9)
        assertNull(LocatorCodec.progression("{broken"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :reader:testDebugUnitTest`
Expected: FAIL, unresolved `ReadiumServices`, `ReaderEngine`.

- [ ] **Step 3: Implement**

`ReaderApi.kt`:
```kotlin
package dev.kent.windbook.reader

import dev.kent.windbook.core.model.BookFormat
import java.io.File

/** An opened book, ready to be shown by [ReaderHostFragment]. Close it when the reader goes away. */
interface ReaderSession : AutoCloseable {
    val format: BookFormat
}

fun interface BookOpener {
    suspend fun open(file: File, format: BookFormat): Result<ReaderSession>
}

class ReaderException(message: String) : Exception(message)
```

`ReadiumServices.kt`:
```kotlin
package dev.kent.windbook.reader

import android.content.Context
import java.io.File
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.pdf.PdfDocumentFactory
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/** Shared Readium objects (one per app). */
class ReadiumServices(
    context: Context,
    pdfFactory: PdfDocumentFactory<*>? = PdfiumDocumentFactory(context),
) {
    private val httpClient = DefaultHttpClient()

    private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)

    private val publicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context,
            assetRetriever = assetRetriever,
            httpClient = httpClient,
            pdfFactory = pdfFactory,
        ),
    )

    suspend fun openPublication(file: File): Result<Publication> {
        val asset = assetRetriever.retrieve(file.toUrl())
            .getOrElse { return Result.failure(ReaderException(it.message)) }
        val publication = publicationOpener.open(asset, allowUserInteraction = false)
            .getOrElse {
                asset.close()
                return Result.failure(ReaderException(it.message))
            }
        return Result.success(publication)
    }
}
```

`ReadiumMetadataExtractor.kt`:
```kotlin
package dev.kent.windbook.reader

import android.graphics.Bitmap
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.ExtractedMetadata
import dev.kent.windbook.core.model.MetadataExtractor
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.services.cover

class ReadiumMetadataExtractor(private val services: ReadiumServices) : MetadataExtractor {

    override suspend fun extract(file: File, format: BookFormat): Result<ExtractedMetadata> =
        withContext(Dispatchers.IO) {
            services.openPublication(file).mapCatching { publication ->
                try {
                    ExtractedMetadata(
                        title = publication.metadata.title,
                        author = publication.metadata.authors.firstOrNull()?.name,
                        coverWebp = publication.cover()?.toWebp(),
                    )
                } finally {
                    publication.close()
                }
            }
        }

    private fun Bitmap.toWebp(maxHeight: Int = 600): ByteArray {
        val scaled = if (height > maxHeight) {
            val width = (width * maxHeight.toFloat() / height).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(this, width, maxHeight, true)
        } else {
            this
        }
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, out)
            out.toByteArray()
        }
    }
}
```

`ReaderEngine.kt`:
```kotlin
package dev.kent.windbook.reader

import dev.kent.windbook.core.model.BookFormat
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication

class ReaderEngine(private val services: ReadiumServices) : BookOpener {

    override suspend fun open(file: File, format: BookFormat): Result<ReaderSession> =
        withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext Result.failure(ReaderException("Missing file: $file"))
            services.openPublication(file).map { ReadiumReaderSession(format, it) }
        }
}

internal class ReadiumReaderSession(
    override val format: BookFormat,
    val publication: Publication,
) : ReaderSession {
    override fun close() = publication.close()
}
```

`LocatorCodec.kt`:
```kotlin
package dev.kent.windbook.reader

import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

/** Locator JSON is opaque outside this module; these helpers are the only way in or out. */
object LocatorCodec {
    fun progression(locatorJson: String): Double? =
        locatorJson.toLocatorOrNull()?.locations?.totalProgression
}

internal fun String.toLocatorOrNull(): Locator? =
    runCatching { Locator.fromJSON(JSONObject(this)) }.getOrNull()

internal fun Locator.toJsonString(): String = toJSON().toString()
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :reader:testDebugUnitTest`
Expected: PASS (5 tests). If `publication.close()` or `asset.close()` is a suspend function in 3.4.0, wrap the call in the surrounding coroutine (both call sites are already inside `suspend` code or `withContext`), and in `ReadiumReaderSession.close()` use `runBlocking(Dispatchers.IO) { publication.close() }`.

- [ ] **Step 5: Commit**

```bash
git add reader
git commit -m "feat(reader): Readium-backed BookOpener, metadata extractor and locator codec"
```

---

### Task 8: `ReaderHostFragment` embedding Readium navigators

**Files:**
- Create: `reader/src/main/res/values/ids.xml`
- Create: `reader/src/main/kotlin/dev/kent/windbook/reader/ReaderHostFragment.kt`
- Test: `reader/src/androidTest/kotlin/dev/kent/windbook/reader/ReaderHostFragmentTest.kt`

**Interfaces:**
- Consumes: `ReaderSession`, `ReadiumReaderSession`, `toLocatorOrNull`, `toJsonString` (Task 7).
- Produces:
  - `class ReaderHostFragment : Fragment` with `companion object { fun arguments(format: BookFormat): Bundle }`, `fun bind(session: ReaderSession, initialLocatorJson: String?, callbacks: Callbacks)`, `fun turnPage(forward: Boolean): Boolean`
  - `interface ReaderHostFragment.Callbacks { fun onLocatorChanged(locatorJson: String, progression: Double, chapterTitle: String?); fun onCenterTap() }`

- [ ] **Step 1: Write the failing instrumented test**

`ReaderHostFragmentTest.kt`:
```kotlin
package dev.kent.windbook.reader

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.testing.TestBooks
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderHostFragmentTest {

    @Test
    fun bindingAnEpubShowsNavigatorAndReportsLocator() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val epub = TestBooks.epub(context.cacheDir, name = "host-test.epub")
        val session = runBlocking {
            ReaderEngine(ReadiumServices(context)).open(epub, BookFormat.EPUB).getOrThrow()
        }
        val locatorSeen = CountDownLatch(1)
        var reported = -1.0
        var reportedJson = ""

        val scenario = launchFragmentInContainer<ReaderHostFragment>(
            fragmentArgs = ReaderHostFragment.arguments(BookFormat.EPUB),
            themeResId = androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar,
        )
        scenario.onFragment { fragment ->
            fragment.bind(session, initialLocatorJson = null, callbacks = object : ReaderHostFragment.Callbacks {
                override fun onLocatorChanged(locatorJson: String, progression: Double, chapterTitle: String?) {
                    reported = progression
                    reportedJson = locatorJson
                    locatorSeen.countDown()
                }
                override fun onCenterTap() = Unit
            })
        }

        assertTrue("navigator never reported a locator", locatorSeen.await(15, TimeUnit.SECONDS))
        assertTrue(reportedJson.contains("href"))
        assertTrue(reported in 0.0..1.0)
        scenario.close()
        session.close()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Start Waydroid and connect adb (once per session):
```bash
waydroid session start &   # or open the Waydroid app
sleep 15
WAYDROID_IP=$(waydroid status | awk '/IP address/ {print $3}')
adb connect "$WAYDROID_IP:5555" && adb devices
adb shell getprop ro.build.version.sdk   # must print 31 or higher
```
Run: `./gradlew :reader:connectedDebugAndroidTest`
Expected: FAIL, compilation error `Unresolved reference: ReaderHostFragment`.

- [ ] **Step 3: Implement**

`reader/src/main/res/values/ids.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <item name="windbook_navigator_container" type="id" />
</resources>
```

`ReaderHostFragment.kt`:
```kotlin
package dev.kent.windbook.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.kent.windbook.core.model.BookFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.pdf.PdfNavigatorFactory
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * Hosts a Readium navigator fragment. Readium navigators can't be restored without their
 * publication, so a dummy factory is installed in onCreate and [bind] always replaces the
 * navigator with a fresh one positioned at the given locator (rotation, process death).
 */
@OptIn(ExperimentalReadiumApi::class)
class ReaderHostFragment : Fragment() {

    interface Callbacks {
        fun onLocatorChanged(locatorJson: String, progression: Double, chapterTitle: String?)
        fun onCenterTap()
    }

    private var boundSession: ReadiumReaderSession? = null
    private var pendingBind: (() -> Unit)? = null
    private var callbacks: Callbacks? = null
    private var locatorJob: Job? = null

    private val navigator: Navigator?
        get() = childFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? Navigator

    override fun onCreate(savedInstanceState: Bundle?) {
        val format = BookFormat.valueOf(requireArguments().getString(ARG_FORMAT, BookFormat.EPUB.name))
        childFragmentManager.fragmentFactory = when (format) {
            BookFormat.EPUB -> EpubNavigatorFragment.createDummyFactory()
            BookFormat.PDF -> PdfNavigatorFragment.createDummyFactory(pdfEngineProvider = PdfiumEngineProvider())
        }
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentContainerView(requireContext()).apply { id = R.id.windbook_navigator_container }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pendingBind?.invoke()
        pendingBind = null
    }

    fun bind(session: ReaderSession, initialLocatorJson: String?, callbacks: Callbacks) {
        this.callbacks = callbacks
        val readium = session as ReadiumReaderSession
        if (readium === boundSession) return
        if (view == null) {
            pendingBind = { bind(session, initialLocatorJson, callbacks) }
            return
        }
        boundSession = readium
        val initialLocator = initialLocatorJson?.toLocatorOrNull()

        childFragmentManager.fragmentFactory = when (readium.format) {
            BookFormat.EPUB -> EpubNavigatorFactory(readium.publication)
                .createFragmentFactory(initialLocator = initialLocator)
            BookFormat.PDF -> PdfNavigatorFactory(readium.publication, PdfiumEngineProvider())
                .createFragmentFactory(initialLocator = initialLocator)
        }
        val navigatorClass = when (readium.format) {
            BookFormat.EPUB -> EpubNavigatorFragment::class.java
            BookFormat.PDF -> PdfNavigatorFragment::class.java
        }
        childFragmentManager.commitNow {
            childFragmentManager.findFragmentByTag(NAVIGATOR_TAG)?.let { remove(it) }
            add(R.id.windbook_navigator_container, navigatorClass, Bundle(), NAVIGATOR_TAG)
        }
        attachListeners()
    }

    /** Used for volume keys / page keys. */
    fun turnPage(forward: Boolean): Boolean {
        val overflowable = navigator as? OverflowableNavigator ?: return false
        return if (forward) overflowable.goForward() else overflowable.goBackward()
    }

    private fun attachListeners() {
        val current = navigator ?: return
        (current as? OverflowableNavigator)?.apply {
            // Edge taps and arrow keys turn pages.
            addInputListener(DirectionalNavigationAdapter(this))
            addInputListener(object : InputListener {
                override fun onTap(event: TapEvent): Boolean {
                    callbacks?.onCenterTap()
                    return true
                }
            })
        }
        locatorJob?.cancel()
        locatorJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                current.currentLocator.collect { locator ->
                    callbacks?.onLocatorChanged(
                        locator.toJsonString(),
                        locator.locations.totalProgression ?: 0.0,
                        locator.title,
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        locatorJob?.cancel()
        locatorJob = null
        boundSession = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_FORMAT = "format"
        private const val NAVIGATOR_TAG = "readium-navigator"

        fun arguments(format: BookFormat): Bundle = bundleOf(ARG_FORMAT to format.name)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :reader:connectedDebugAndroidTest`
Expected: PASS (1 test) on the Waydroid device.

- [ ] **Step 5: Commit**

```bash
git add reader
git commit -m "feat(reader): ReaderHostFragment hosting Readium EPUB/PDF navigators"
```

---

### Task 9: App wiring: Hilt graph, theme, icons, key dispatcher

**Files:**
- Create: `app/src/main/kotlin/dev/kent/windbook/di/AppModule.kt`
- Create: `app/src/main/kotlin/dev/kent/windbook/input/KeyEventDispatcher.kt`
- Create: `app/src/main/kotlin/dev/kent/windbook/ui/theme/Theme.kt`
- Create: `app/src/main/kotlin/dev/kent/windbook/ui/icons/WindBookIcons.kt`
- Create: `app/src/main/kotlin/dev/kent/windbook/library/ImportSourceFactory.kt`
- Test: `app/src/test/kotlin/dev/kent/windbook/input/KeyEventDispatcherTest.kt`

**Interfaces:**
- Consumes: `WindBookDatabase`, `BookFileStore`, `BookImporter`, `LibraryRepository`, `LibraryPreferences`, `libraryDataStore` (Tasks 3–6), `ReadiumServices`, `ReadiumMetadataExtractor`, `ReaderEngine`, `BookOpener` (Task 7).
- Produces:
  - Hilt bindings: `LibraryRepository`, `BookImporter`, `LibraryPreferences`, `BookOpener`, `ImportSourceFactory`, `KeyEventDispatcher` (all `@Singleton`)
  - `class KeyEventDispatcher { fun interface PageTurner { fun turn(forward: Boolean): Boolean }; fun register(turner: PageTurner); fun unregister(turner: PageTurner); fun dispatch(event: KeyEvent): Boolean }`
  - `val LocalKeyEventDispatcher: ProvidableCompositionLocal<KeyEventDispatcher>`
  - `@Composable fun WindBookTheme(content: @Composable () -> Unit)`
  - `object WindBookIcons { val Add; val Search; val Close; val Sort; val Grid; val List; val Back: ImageVector }`
  - `fun interface ImportSourceFactory { fun create(uri: Uri): ImportSource }`; `class ContentResolverImportSourceFactory(resolver: ContentResolver) : ImportSourceFactory`

- [ ] **Step 1: Write the failing test**

`KeyEventDispatcherTest.kt`:
```kotlin
package dev.kent.windbook.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyEventDispatcherTest {
    private val turns = mutableListOf<Boolean>()
    private val turner = KeyEventDispatcher.PageTurner { forward -> turns += forward; true }

    @Test fun volumeKeysTurnPagesOnlyWhenRegistered() {
        val dispatcher = KeyEventDispatcher()
        assertFalse(dispatcher.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN)))

        dispatcher.register(turner)
        assertTrue(dispatcher.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN)))
        assertTrue(dispatcher.dispatch(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN)))
        assertTrue(dispatcher.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)))

        assertEquals(listOf(true, false), turns) // UP events consumed but don't turn
    }

    @Test fun otherKeysPassThrough() {
        val dispatcher = KeyEventDispatcher().apply { register(turner) }
        assertFalse(dispatcher.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)))
    }

    @Test fun unregisterOnlyRemovesSameTurner() {
        val dispatcher = KeyEventDispatcher().apply { register(turner) }
        dispatcher.unregister(KeyEventDispatcher.PageTurner { true })
        assertTrue(dispatcher.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN)))
        dispatcher.unregister(turner)
        assertFalse(dispatcher.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN)))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*KeyEventDispatcherTest*'`
Expected: FAIL, unresolved `KeyEventDispatcher`.

- [ ] **Step 3: Implement**

`KeyEventDispatcher.kt`:
```kotlin
package dev.kent.windbook.input

import android.view.KeyEvent
import androidx.compose.runtime.staticCompositionLocalOf

/** Routes hardware page-turn keys (volume, page up/down) to the visible reader. */
class KeyEventDispatcher {

    fun interface PageTurner {
        fun turn(forward: Boolean): Boolean
    }

    @Volatile private var turner: PageTurner? = null

    fun register(turner: PageTurner) {
        this.turner = turner
    }

    fun unregister(turner: PageTurner) {
        if (this.turner === turner) this.turner = null
    }

    /** Returns true when the event was consumed. Both DOWN and UP are consumed so the volume panel never appears. */
    fun dispatch(event: KeyEvent): Boolean {
        val current = turner ?: return false
        val forward = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> true
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_PAGE_UP -> false
            else -> return false
        }
        if (event.action == KeyEvent.ACTION_DOWN) current.turn(forward)
        return true
    }
}

val LocalKeyEventDispatcher = staticCompositionLocalOf<KeyEventDispatcher> {
    error("KeyEventDispatcher not provided")
}
```

`ImportSourceFactory.kt`:
```kotlin
package dev.kent.windbook.library

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import dev.kent.windbook.core.data.importer.ImportSource
import java.io.FileNotFoundException
import java.io.InputStream

fun interface ImportSourceFactory {
    fun create(uri: Uri): ImportSource
}

class ContentResolverImportSourceFactory(private val resolver: ContentResolver) : ImportSourceFactory {
    override fun create(uri: Uri): ImportSource = object : ImportSource {
        override val displayName: String? by lazy { queryDisplayName(uri) }
        override val sourceUri: String = uri.toString()
        override fun open(): InputStream =
            resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
    }

    private fun queryDisplayName(uri: Uri): String? =
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
}
```

`AppModule.kt`:
```kotlin
package dev.kent.windbook.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.kent.windbook.core.data.LibraryPreferences
import dev.kent.windbook.core.data.LibraryRepository
import dev.kent.windbook.core.data.db.WindBookDatabase
import dev.kent.windbook.core.data.files.BookFileStore
import dev.kent.windbook.core.data.importer.BookImporter
import dev.kent.windbook.core.data.libraryDataStore
import dev.kent.windbook.input.KeyEventDispatcher
import dev.kent.windbook.library.ContentResolverImportSourceFactory
import dev.kent.windbook.library.ImportSourceFactory
import dev.kent.windbook.reader.BookOpener
import dev.kent.windbook.reader.ReaderEngine
import dev.kent.windbook.reader.ReadiumMetadataExtractor
import dev.kent.windbook.reader.ReadiumServices
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): WindBookDatabase = WindBookDatabase.create(context)

    @Provides @Singleton
    fun fileStore(@ApplicationContext context: Context): BookFileStore = BookFileStore(context.filesDir)

    @Provides @Singleton
    fun readium(@ApplicationContext context: Context): ReadiumServices = ReadiumServices(context)

    @Provides @Singleton
    fun bookOpener(readium: ReadiumServices): BookOpener = ReaderEngine(readium)

    @Provides @Singleton
    fun importer(db: WindBookDatabase, files: BookFileStore, readium: ReadiumServices): BookImporter =
        BookImporter(db.libraryDao(), files, ReadiumMetadataExtractor(readium))

    @Provides @Singleton
    fun libraryRepository(db: WindBookDatabase, files: BookFileStore): LibraryRepository =
        LibraryRepository(db.libraryDao(), files)

    @Provides @Singleton
    fun libraryPreferences(@ApplicationContext context: Context): LibraryPreferences =
        LibraryPreferences(context.libraryDataStore)

    @Provides @Singleton
    fun importSources(@ApplicationContext context: Context): ImportSourceFactory =
        ContentResolverImportSourceFactory(context.contentResolver)

    @Provides @Singleton
    fun keyEventDispatcher(): KeyEventDispatcher = KeyEventDispatcher()
}
```

`Theme.kt`:
```kotlin
package dev.kent.windbook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** App chrome uses Material You dynamic colour (minSdk 31). The reading surface has its own themes (M2). */
@Composable
fun WindBookTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colors, content = content)
}
```

`WindBookIcons.kt` (Material icon paths, Apache-2.0):
```kotlin
package dev.kent.windbook.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

object WindBookIcons {
    val Add = icon("Add", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
    val Search = icon(
        "Search",
        "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z",
    )
    val Close = icon("Close", "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z")
    val Sort = icon("Sort", "M3,18h6v-2H3v2zM3,6v2h18V6H3zM3,13h12v-2H3v2z")
    val Grid = icon(
        "Grid",
        "M4,8h4V4H4v4zM10,20h4v-4h-4v4zM4,20h4v-4H4v4zM4,14h4v-4H4v4zM10,14h4v-4h-4v4zM16,4v4h4V4h-4zM10,8h4V4h-4v4zM16,14h4v-4h-4v4zM16,20h4v-4h-4v4z",
    )
    val List = icon("List", "M3,13h2v-2H3v2zM3,17h2v-2H3v2zM3,9h2V7H3v2zM7,13h14v-2H7v2zM7,17h14v-2H7v2zM7,7v2h14V7H7z")
    val Back = icon("Back", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z", autoMirror = true)

    private fun icon(name: String, path: String, autoMirror: Boolean = false): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = autoMirror,
        ).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black)).build()
}
```

- [ ] **Step 4: Run test to verify it passes and the graph compiles**

Run: `./gradlew :app:testDebugUnitTest --tests '*KeyEventDispatcherTest*' :app:assembleDebug`
Expected: PASS (3 tests), `BUILD SUCCESSFUL` (Hilt graph generated).

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "feat(app): Hilt graph, dynamic-colour theme, icons and page-turn key dispatcher"
```

---

### Task 10: Library ViewModel

**Files:**
- Create: `app/src/main/kotlin/dev/kent/windbook/library/LibraryViewModel.kt`
- Test: `app/src/test/kotlin/dev/kent/windbook/library/LibraryViewModelTest.kt`, `app/src/test/kotlin/dev/kent/windbook/testing/MainDispatcherRule.kt`

**Interfaces:**
- Consumes: `LibraryRepository`, `LibraryPreferences`, `BookImporter`, `ImportResult`, `ImportError`, `ImportSourceFactory`, `LibraryQuery`, `continueReading`.
- Produces:
  - `data class LibraryUiState(items: List<LibraryItem> = emptyList(), continueReading: LibraryItem? = null, query: String = "", filter: StatusFilter = ALL, sort: LibrarySort = RECENT, layout: LibraryLayout = GRID, isImporting: Boolean = false, isLibraryEmpty: Boolean = true)`
  - `sealed interface LibraryMessage { data class Imported(val count: Int); data class AlreadyInLibrary(val bookId: String); data class ImportFailed(val name: String?, val error: ImportError) }`
  - `class LibraryViewModel` with `val state: StateFlow<LibraryUiState>`, `val messages: Flow<LibraryMessage>`, `fun onQueryChange(text: String)`, `fun onFilterChange(filter: StatusFilter)`, `fun onSortChange(sort: LibrarySort)`, `fun onLayoutChange(layout: LibraryLayout)`, `fun import(uris: List<Uri>)`, `fun setStatus(bookId: String, status: ReadingStatus)`, `fun delete(bookId: String)`

- [ ] **Step 1: Write the failing test**

`MainDispatcherRule.kt`:
```kotlin
package dev.kent.windbook.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
```

`LibraryViewModelTest.kt`:
```kotlin
package dev.kent.windbook.library

import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.kent.windbook.core.data.LibraryPreferences
import dev.kent.windbook.core.data.LibraryRepository
import dev.kent.windbook.core.data.db.WindBookDatabase
import dev.kent.windbook.core.data.files.BookFileStore
import dev.kent.windbook.core.data.importer.BookImporter
import dev.kent.windbook.core.data.importer.ImportError
import dev.kent.windbook.core.data.importer.ImportSource
import dev.kent.windbook.core.model.ExtractedMetadata
import dev.kent.windbook.core.model.LibrarySort
import dev.kent.windbook.core.model.ReadingStatus
import dev.kent.windbook.core.model.StatusFilter
import dev.kent.windbook.testing.MainDispatcherRule
import dev.kent.windbook.testing.TestBooks
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: WindBookDatabase
    private lateinit var repo: LibraryRepository
    private lateinit var importer: BookImporter
    private val files = mutableMapOf<String, File>()

    @Before fun setUp() {
        db = WindBookDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
        val store = BookFileStore(tmp.newFolder("app"))
        repo = LibraryRepository(db.libraryDao(), store)
        // No metadata title, so the importer falls back to the display name ("Dune.epub" -> "Dune").
        importer = BookImporter(db.libraryDao(), store, metadata = { _, _ ->
            Result.success(ExtractedMetadata(null, "Author", null))
        })
    }

    @After fun tearDown() = db.close()

    private fun viewModel(scopeForPrefs: kotlinx.coroutines.CoroutineScope): LibraryViewModel {
        val prefs = LibraryPreferences(PreferenceDataStoreFactory.create(scope = scopeForPrefs) { File(tmp.root, "lib.preferences_pb") })
        val sources = ImportSourceFactory { uri ->
            val file = files.getValue(uri.toString())
            object : ImportSource {
                override val displayName = file.name
                override val sourceUri = uri.toString()
                override fun open() = file.inputStream()
            }
        }
        return LibraryViewModel(repo, prefs, importer, sources)
    }

    private fun uriFor(file: File): Uri = Uri.parse("content://test/${file.name}").also { files[it.toString()] = file }

    @Test fun importThenSearchAndFilter() = runTest {
        val vm = viewModel(backgroundScope)
        val dune = uriFor(TestBooks.epub(tmp.root, "Dune.epub"))
        val emma = uriFor(TestBooks.epub(tmp.root, "Emma.epub", title = "Emma"))

        vm.messages.test {
            vm.import(listOf(dune, emma))
            assertEquals(LibraryMessage.Imported(2), awaitItem())
        }

        vm.state.test {
            var state = awaitItem()
            while (state.items.size < 2) state = awaitItem()
            assertFalse(state.isLibraryEmpty)
            assertEquals(listOf("Dune", "Emma"), state.items.map { it.book.title }.sorted())

            vm.onQueryChange("emm")
            while (state.items.size != 1) state = awaitItem()
            assertEquals("Emma", state.items.single().book.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun duplicateImportReportsAlreadyInLibrary() = runTest {
        val vm = viewModel(backgroundScope)
        val uri = uriFor(TestBooks.epub(tmp.root, "Dune.epub"))
        vm.messages.test {
            vm.import(listOf(uri))
            awaitItem()
            vm.import(listOf(uri))
            assertEquals(LibraryMessage.AlreadyInLibrary::class, awaitItem()::class)
        }
    }

    @Test fun badFileReportsFailureWithName() = runTest {
        val vm = viewModel(backgroundScope)
        val uri = uriFor(tmp.newFile("notes.txt").apply { writeText("hi") })
        vm.messages.test {
            vm.import(listOf(uri))
            assertEquals(LibraryMessage.ImportFailed("notes.txt", ImportError.UNSUPPORTED_FORMAT), awaitItem())
        }
    }

    @Test fun statusChangeAndDelete() = runTest {
        val vm = viewModel(backgroundScope)
        vm.messages.test {
            vm.import(listOf(uriFor(TestBooks.epub(tmp.root, "Dune.epub"))))
            awaitItem()
        }
        val id = repo.observeLibrary().first().single().book.id

        vm.setStatus(id, ReadingStatus.FINISHED)
        vm.onFilterChange(StatusFilter.FINISHED)
        vm.onSortChange(LibrarySort.TITLE)
        vm.state.test {
            var state = awaitItem()
            while (state.items.isEmpty() || state.sort != LibrarySort.TITLE) state = awaitItem()
            assertEquals(ReadingStatus.FINISHED, state.items.single().book.status)
            cancelAndIgnoreRemainingEvents()
        }

        vm.delete(id)
        // delete() runs on viewModelScope and hops to Room's IO dispatcher; wait for the table to reflect it.
        repo.observeLibrary().first { it.isEmpty() }
        assertNull(repo.book(id))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*LibraryViewModelTest*'`
Expected: FAIL, unresolved `LibraryViewModel`, `LibraryMessage`.

- [ ] **Step 3: Implement**

`LibraryViewModel.kt`:
```kotlin
package dev.kent.windbook.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kent.windbook.core.data.LibraryPreferences
import dev.kent.windbook.core.data.LibraryRepository
import dev.kent.windbook.core.data.importer.BookImporter
import dev.kent.windbook.core.data.importer.ImportError
import dev.kent.windbook.core.data.importer.ImportResult
import dev.kent.windbook.core.model.LibraryItem
import dev.kent.windbook.core.model.LibraryLayout
import dev.kent.windbook.core.model.LibraryQuery
import dev.kent.windbook.core.model.LibrarySort
import dev.kent.windbook.core.model.ReadingStatus
import dev.kent.windbook.core.model.StatusFilter
import dev.kent.windbook.core.model.continueReading
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val items: List<LibraryItem> = emptyList(),
    val continueReading: LibraryItem? = null,
    val query: String = "",
    val filter: StatusFilter = StatusFilter.ALL,
    val sort: LibrarySort = LibrarySort.RECENT,
    val layout: LibraryLayout = LibraryLayout.GRID,
    val isImporting: Boolean = false,
    val isLibraryEmpty: Boolean = true,
)

sealed interface LibraryMessage {
    data class Imported(val count: Int) : LibraryMessage
    data class AlreadyInLibrary(val bookId: String) : LibraryMessage
    data class ImportFailed(val name: String?, val error: ImportError) : LibraryMessage
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val preferences: LibraryPreferences,
    private val importer: BookImporter,
    private val sources: ImportSourceFactory,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(StatusFilter.ALL)
    private val importing = MutableStateFlow(false)
    private val _messages = Channel<LibraryMessage>(Channel.BUFFERED)

    val messages: Flow<LibraryMessage> = _messages.receiveAsFlow()

    val state: StateFlow<LibraryUiState> = combine(
        repository.observeLibrary(),
        query,
        filter,
        preferences.sort,
        preferences.layout,
    ) { all, text, statusFilter, sort, layout ->
        LibraryUiState(
            items = LibraryQuery(text, sort, statusFilter).apply(all),
            continueReading = continueReading(all),
            query = text,
            filter = statusFilter,
            sort = sort,
            layout = layout,
            isLibraryEmpty = all.isEmpty(),
        )
    }.combine(importing) { state, busy -> state.copy(isImporting = busy) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onQueryChange(text: String) = query.update { text }

    fun onFilterChange(value: StatusFilter) = filter.update { value }

    fun onSortChange(sort: LibrarySort) {
        viewModelScope.launch { preferences.setSort(sort) }
    }

    fun onLayoutChange(layout: LibraryLayout) {
        viewModelScope.launch { preferences.setLayout(layout) }
    }

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            importing.value = true
            var imported = 0
            try {
                for (uri in uris) {
                    val source = sources.create(uri)
                    when (val result = importer.import(source)) {
                        is ImportResult.Imported -> imported++
                        is ImportResult.Duplicate -> _messages.send(LibraryMessage.AlreadyInLibrary(result.bookId))
                        is ImportResult.Failed -> _messages.send(LibraryMessage.ImportFailed(source.displayName, result.error))
                    }
                }
                if (imported > 0) _messages.send(LibraryMessage.Imported(imported))
            } finally {
                importing.value = false
            }
        }
    }

    fun setStatus(bookId: String, status: ReadingStatus) {
        viewModelScope.launch { repository.setStatus(bookId, status) }
    }

    fun delete(bookId: String) {
        viewModelScope.launch { repository.delete(bookId) }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*LibraryViewModelTest*'`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "feat(app): library view model with import, search, filter, sort and status"
```

---

### Task 11: Reader ViewModel

**Files:**
- Create: `app/src/main/kotlin/dev/kent/windbook/reader/ReaderViewModel.kt`
- Test: `app/src/test/kotlin/dev/kent/windbook/reader/ReaderViewModelTest.kt`

**Interfaces:**
- Consumes: `LibraryRepository` (Task 6), `BookOpener`, `ReaderSession` (Task 7).
- Produces:
  - `enum class ReaderError { NOT_FOUND, FILE_MISSING, CANNOT_OPEN }`
  - `sealed interface ReaderUiState { data object Loading; data class Ready(val session: ReaderSession, val title: String, val initialLocatorJson: String?); data class Error(val error: ReaderError) }`
  - `class ReaderViewModel(bookId: String, library: LibraryRepository, opener: BookOpener)` (Hilt assisted) with `val state: StateFlow<ReaderUiState>`, `val progression: StateFlow<Double>`, `val chapterTitle: StateFlow<String?>`, `val overlayVisible: StateFlow<Boolean>`, `val canMarkFinished: StateFlow<Boolean>`, `val latestLocatorJson: String?` (the position to bind a re-created reader to, e.g. after rotation), `fun onLocatorChanged(locatorJson: String, progression: Double, chapterTitle: String?)`, `fun toggleOverlay()`, `fun markFinished()`, `fun removeBook(onRemoved: () -> Unit)`, `@AssistedFactory interface Factory { fun create(bookId: String): ReaderViewModel }`

- [ ] **Step 1: Write the failing test**

`ReaderViewModelTest.kt`:
```kotlin
package dev.kent.windbook.reader

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.data.LibraryRepository
import dev.kent.windbook.core.data.db.BookEntity
import dev.kent.windbook.core.data.db.WindBookDatabase
import dev.kent.windbook.core.data.files.BookFileStore
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.ReadingStatus
import dev.kent.windbook.testing.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
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
class ReaderViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: WindBookDatabase
    private lateinit var library: LibraryRepository
    private lateinit var bookFile: File

    private class FakeSession(override val format: BookFormat) : ReaderSession {
        var closed = false
        override fun close() { closed = true }
    }

    private val opener = BookOpener { _, format -> Result.success(FakeSession(format)) }

    @Before fun setUp() = runTest {
        db = WindBookDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
        library = LibraryRepository(db.libraryDao(), BookFileStore(tmp.newFolder("app")))
        bookFile = tmp.newFile("a.epub")
        db.libraryDao().insert(
            BookEntity("a", "Dune", null, "EPUB", bookFile.absolutePath, 1, "sha", null, null, "TO_READ", 1, null, 1, null),
        )
    }

    @After fun tearDown() = db.close()

    private suspend fun ReaderViewModel.awaitSettled(): ReaderUiState =
        state.first { it !is ReaderUiState.Loading }

    @Test fun ready_containsSavedLocator() = runTest {
        library.saveProgress("a", """{"href":"c3"}""", 0.6)

        val state = ReaderViewModel("a", library, opener).awaitSettled() as ReaderUiState.Ready

        assertEquals("Dune", state.title)
        assertEquals("""{"href":"c3"}""", state.initialLocatorJson)
    }

    @Test fun opening_marksBookReading() = runTest {
        ReaderViewModel("a", library, opener).awaitSettled()
        assertEquals(ReadingStatus.READING, library.book("a")!!.status)
    }

    @Test fun unknownBook_isNotFound() = runTest {
        assertEquals(ReaderUiState.Error(ReaderError.NOT_FOUND), ReaderViewModel("zzz", library, opener).awaitSettled())
    }

    @Test fun missingFile_isFileMissing() = runTest {
        bookFile.delete()
        assertEquals(ReaderUiState.Error(ReaderError.FILE_MISSING), ReaderViewModel("a", library, opener).awaitSettled())
    }

    @Test fun openerFailure_isCannotOpenAndBookStaysToRead() = runTest {
        val failing = BookOpener { _, _ -> Result.failure(IllegalStateException("broken")) }
        assertEquals(ReaderUiState.Error(ReaderError.CANNOT_OPEN), ReaderViewModel("a", library, failing).awaitSettled())
        assertEquals(ReadingStatus.TO_READ, library.book("a")!!.status)
    }

    @Test fun latestLocator_startsFromSavedAndFollowsChanges() = runTest {
        library.saveProgress("a", """{"href":"c3"}""", 0.6)
        val vm = ReaderViewModel("a", library, opener)
        vm.awaitSettled()
        assertEquals("""{"href":"c3"}""", vm.latestLocatorJson)

        vm.onLocatorChanged("""{"href":"c4"}""", 0.7, "Chapter Four")

        assertEquals("""{"href":"c4"}""", vm.latestLocatorJson)
        assertEquals("Chapter Four", vm.chapterTitle.value)
    }

    @Test fun nearTheEnd_offersMarkFinished() = runTest {
        val vm = ReaderViewModel("a", library, opener)
        vm.awaitSettled()
        assertEquals(false, vm.canMarkFinished.value)

        vm.onLocatorChanged("""{"href":"end"}""", 0.995, null)
        assertEquals(true, vm.canMarkFinished.value)

        vm.markFinished()
        assertEquals(false, vm.canMarkFinished.value)
        var status = library.book("a")!!.status
        val deadline = System.currentTimeMillis() + 5_000
        while (status != ReadingStatus.FINISHED && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            status = library.book("a")!!.status
        }
        assertEquals(ReadingStatus.FINISHED, status)
    }

    @Test fun removeBook_deletesThenCallsBack() = runTest {
        bookFile.delete()
        val vm = ReaderViewModel("a", library, opener)
        vm.awaitSettled()
        val removed = kotlinx.coroutines.CompletableDeferred<Unit>()

        vm.removeBook { removed.complete(Unit) }
        removed.await()

        assertEquals(null, library.book("a"))
    }

    @Test fun rapidLocatorChanges_persistLastLocator() = runTest {
        val vm = ReaderViewModel("a", library, opener)
        vm.awaitSettled()

        repeat(100) { i -> vm.onLocatorChanged("""{"href":"c","i":$i}""", i / 100.0, null) }
        advanceUntilIdle()
        // Room writes run on Dispatchers.IO; poll until the conflated writer catches up.
        var saved = library.savedLocator("a")
        val deadline = System.currentTimeMillis() + 5_000
        while (saved?.contains("\"i\":99") != true && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            saved = library.savedLocator("a")
        }

        assertTrue(saved!!.contains("\"i\":99"))
        assertEquals(0.99, vm.progression.value, 1e-9)
    }

    @Test fun toggleOverlay_flips() = runTest {
        val vm = ReaderViewModel("a", library, opener)
        val before = vm.overlayVisible.value
        vm.toggleOverlay()
        assertEquals(!before, vm.overlayVisible.value)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReaderViewModelTest*'`
Expected: FAIL, unresolved `ReaderViewModel`, `ReaderUiState`.

- [ ] **Step 3: Implement**

`ReaderViewModel.kt`:
```kotlin
package dev.kent.windbook.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kent.windbook.core.data.LibraryRepository
import dev.kent.windbook.core.model.ReadingStatus
import java.io.File
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReaderError { NOT_FOUND, FILE_MISSING, CANNOT_OPEN }

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Ready(
        val session: ReaderSession,
        val title: String,
        val initialLocatorJson: String?,
    ) : ReaderUiState
    data class Error(val error: ReaderError) : ReaderUiState
}

@HiltViewModel(assistedFactory = ReaderViewModel.Factory::class)
class ReaderViewModel @AssistedInject constructor(
    @Assisted private val bookId: String,
    private val library: LibraryRepository,
    private val opener: BookOpener,
) : ViewModel() {

    private val _state = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private val _progression = MutableStateFlow(0.0)
    val progression: StateFlow<Double> = _progression.asStateFlow()

    private val _chapterTitle = MutableStateFlow<String?>(null)
    val chapterTitle: StateFlow<String?> = _chapterTitle.asStateFlow()

    private val _overlayVisible = MutableStateFlow(false)
    val overlayVisible: StateFlow<Boolean> = _overlayVisible.asStateFlow()

    private val finished = MutableStateFlow(false)

    /** True near the end of a book that isn't marked finished yet (spec F1.6). */
    val canMarkFinished: StateFlow<Boolean> = combine(_progression, finished) { progression, isFinished ->
        progression >= FINISH_THRESHOLD && !isFinished
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Where a re-created reader (rotation, process death) must reopen; updated on every page change. */
    @Volatile
    var latestLocatorJson: String? = null
        private set

    /** Keeps only the newest pending locator; the writer always persists the latest one. */
    private val locatorUpdates = MutableSharedFlow<Pair<String, Double>>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private var session: ReaderSession? = null

    init {
        viewModelScope.launch { open() }
        viewModelScope.launch {
            locatorUpdates.collect { (json, progression) ->
                library.saveProgress(bookId, json, progression)
            }
        }
    }

    private suspend fun open() {
        val book = library.book(bookId) ?: run {
            _state.value = ReaderUiState.Error(ReaderError.NOT_FOUND)
            return
        }
        val file = File(book.filePath)
        if (!file.exists()) {
            _state.value = ReaderUiState.Error(ReaderError.FILE_MISSING)
            return
        }
        val savedLocator = library.savedLocator(bookId)
        finished.value = book.status == ReadingStatus.FINISHED
        opener.open(file, book.format)
            .onSuccess { opened ->
                session = opened
                latestLocatorJson = savedLocator
                library.markOpened(bookId)
                _state.value = ReaderUiState.Ready(opened, book.title, savedLocator)
            }
            .onFailure { _state.value = ReaderUiState.Error(ReaderError.CANNOT_OPEN) }
    }

    fun onLocatorChanged(locatorJson: String, progression: Double, chapterTitle: String?) {
        latestLocatorJson = locatorJson
        _progression.value = progression
        _chapterTitle.value = chapterTitle
        locatorUpdates.tryEmit(locatorJson to progression)
    }

    fun toggleOverlay() = _overlayVisible.update { !it }

    fun markFinished() {
        finished.value = true
        viewModelScope.launch { library.setStatus(bookId, ReadingStatus.FINISHED) }
    }

    /** Used from the "file missing" error screen. */
    fun removeBook(onRemoved: () -> Unit) {
        viewModelScope.launch {
            library.delete(bookId)
            onRemoved()
        }
    }

    override fun onCleared() {
        session?.close()
        session = null
    }

    @AssistedFactory
    interface Factory {
        fun create(bookId: String): ReaderViewModel
    }

    private companion object {
        const val FINISH_THRESHOLD = 0.99
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReaderViewModelTest*'`
Expected: PASS (10 tests).

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "feat(app): reader view model with immediate conflated progress saving"
```

---

### Task 12: Screens and Navigation 3 shell

**Files:**
- Create: `app/src/main/kotlin/dev/kent/windbook/library/LibraryScreen.kt`
- Create: `app/src/main/kotlin/dev/kent/windbook/reader/ReaderScreen.kt`
- Create: `app/src/main/kotlin/dev/kent/windbook/navigation/Routes.kt`
- Create: `app/src/main/kotlin/dev/kent/windbook/navigation/WindBookNavHost.kt`
- Modify: `app/src/main/kotlin/dev/kent/windbook/MainActivity.kt` (replace the body)
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/dev/kent/windbook/library/LibraryScreenTest.kt`, `app/src/test/kotlin/dev/kent/windbook/reader/ReaderOverlayTest.kt`

**Interfaces:**
- Consumes: `LibraryViewModel`, `LibraryUiState`, `LibraryMessage` (Task 10), `ReaderViewModel`, `ReaderUiState` (Task 11), `ReaderHostFragment` (Task 8), `KeyEventDispatcher`, `LocalKeyEventDispatcher`, `WindBookTheme`, `WindBookIcons` (Task 9).
- Produces:
  - `@Composable fun LibraryRoute(onOpenBook: (String) -> Unit)` and stateless `@Composable fun LibraryScreen(state: LibraryUiState, actions: LibraryActions, snackbarHostState: SnackbarHostState = remember { SnackbarHostState() })`
  - `data class LibraryActions(onOpenBook: (String) -> Unit, onImportClick: () -> Unit, onQueryChange: (String) -> Unit, onFilterChange: (StatusFilter) -> Unit, onSortChange: (LibrarySort) -> Unit, onLayoutChange: (LibraryLayout) -> Unit, onSetStatus: (String, ReadingStatus) -> Unit, onDelete: (String) -> Unit)`
  - `@Composable fun ReaderRoute(bookId: String, onBack: () -> Unit)`, `@Composable fun ReaderOverlay(visible: Boolean, title: String, chapterTitle: String?, progression: Double, canMarkFinished: Boolean, onMarkFinished: () -> Unit, onBack: () -> Unit)`
  - `@Serializable data object LibraryKey : NavKey`, `@Serializable data class ReaderKey(val bookId: String) : NavKey`

- [ ] **Step 1: Write the failing UI tests**

`LibraryScreenTest.kt`:
```kotlin
package dev.kent.windbook.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import dev.kent.windbook.core.model.Book
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.LibraryItem
import dev.kent.windbook.core.model.LibraryLayout
import dev.kent.windbook.core.model.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryScreenTest {
    @get:Rule val compose = createComposeRule()

    private val opened = mutableListOf<String>()
    private val statuses = mutableListOf<Pair<String, ReadingStatus>>()
    private var importClicks = 0

    private val actions = LibraryActions(
        onOpenBook = { opened += it },
        onImportClick = { importClicks++ },
        onQueryChange = {}, onFilterChange = {}, onSortChange = {}, onLayoutChange = {},
        onSetStatus = { id, status -> statuses += id to status },
        onDelete = {},
    )

    private val dune = LibraryItem(
        Book("1", "Dune", "Frank Herbert", BookFormat.EPUB, "/f", 1, "s", null, ReadingStatus.READING, 1, 5),
        0.42,
    )

    @Test fun emptyLibraryShowsAddBooks() {
        compose.setContent { LibraryScreen(LibraryUiState(isLibraryEmpty = true), actions) }
        compose.onNodeWithText("Add books").assertIsDisplayed().performClick()
        assertEquals(1, importClicks)
    }

    @Test fun continueReadingCardOpensBook() {
        compose.setContent {
            LibraryScreen(LibraryUiState(items = listOf(dune), continueReading = dune, isLibraryEmpty = false), actions)
        }
        compose.onNodeWithText("Continue reading").assertIsDisplayed()
        // "42%" appears in the card and in the grid cell below it.
        compose.onAllNodesWithText("42%").onFirst().assertIsDisplayed()
        compose.onNodeWithText("Continue reading").performClick()
        assertEquals(listOf("1"), opened)
    }

    @Test fun longPressOffersStatusChange() {
        compose.setContent {
            LibraryScreen(LibraryUiState(items = listOf(dune), layout = LibraryLayout.LIST, isLibraryEmpty = false), actions)
        }
        compose.onNodeWithText("Dune").performTouchInput { longClick() }
        compose.onNodeWithText("Mark as finished").performClick()
        assertEquals(listOf("1" to ReadingStatus.FINISHED), statuses)
    }
}
```

`ReaderOverlayTest.kt`:
```kotlin
package dev.kent.windbook.reader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReaderOverlayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun visibleOverlayShowsTitleChapterProgressAndBack() {
        var backs = 0
        compose.setContent {
            ReaderOverlay(
                visible = true, title = "Dune", chapterTitle = "Book One", progression = 0.37,
                canMarkFinished = false, onMarkFinished = {}, onBack = { backs++ },
            )
        }
        compose.onNodeWithText("Dune").assertIsDisplayed()
        compose.onNodeWithText("Book One · 37%").assertIsDisplayed()
        compose.onNodeWithText("Mark as finished").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }

    @Test fun nearEndOffersMarkFinished() {
        var marked = 0
        compose.setContent {
            ReaderOverlay(
                visible = true, title = "Dune", chapterTitle = null, progression = 0.995,
                canMarkFinished = true, onMarkFinished = { marked++ }, onBack = {},
            )
        }
        compose.onNodeWithText("Mark as finished").performClick()
        assertEquals(1, marked)
    }

    @Test fun hiddenOverlayShowsNothing() {
        compose.setContent {
            ReaderOverlay(
                visible = false, title = "Dune", chapterTitle = null, progression = 0.37,
                canMarkFinished = false, onMarkFinished = {}, onBack = {},
            )
        }
        compose.onNodeWithText("Dune").assertDoesNotExist()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*LibraryScreenTest*' --tests '*ReaderOverlayTest*'`
Expected: FAIL, unresolved `LibraryScreen`, `LibraryActions`, `ReaderOverlay`.

- [ ] **Step 3: Implement**

`strings.xml`:
```xml
<resources>
    <string name="app_name">WindBook</string>
    <string name="add_books">Add books</string>
    <string name="empty_library">Your library is empty. Add EPUB or PDF files to start reading.</string>
    <string name="continue_reading">Continue reading</string>
    <string name="search_books">Search title or author</string>
    <string name="back">Back</string>
    <string name="importing">Importing…</string>
    <string name="mark_to_read">Mark as to read</string>
    <string name="mark_reading">Mark as reading</string>
    <string name="mark_finished">Mark as finished</string>
    <string name="delete_book">Delete…</string>
    <string name="delete_title">Delete \"%1$s\"?</string>
    <string name="delete_body">The book file, its reading position and cover are removed from WindBook. The original file on your phone is untouched.</string>
    <string name="delete">Delete</string>
    <string name="cancel">Cancel</string>
    <string name="imported_count">%1$d book(s) added</string>
    <string name="already_in_library">Already in your library</string>
    <string name="open">Open</string>
    <string name="import_failed">Couldn\'t add %1$s: %2$s</string>
    <string name="reader_error_not_found">This book is no longer in your library.</string>
    <string name="reader_error_file_missing">The book file is missing. Remove it and add it again.</string>
    <string name="remove_from_library">Remove from library</string>
    <string name="reader_error_cannot_open">This file can\'t be opened. It may be damaged or protected.</string>
</resources>
```

`LibraryScreen.kt`:
```kotlin
package dev.kent.windbook.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.kent.windbook.R
import dev.kent.windbook.core.model.LibraryItem
import dev.kent.windbook.core.model.LibraryLayout
import dev.kent.windbook.core.model.LibrarySort
import dev.kent.windbook.core.model.ReadingStatus
import dev.kent.windbook.core.model.StatusFilter
import dev.kent.windbook.ui.icons.WindBookIcons
import java.io.File
import kotlin.math.roundToInt

data class LibraryActions(
    val onOpenBook: (String) -> Unit,
    val onImportClick: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onFilterChange: (StatusFilter) -> Unit,
    val onSortChange: (LibrarySort) -> Unit,
    val onLayoutChange: (LibraryLayout) -> Unit,
    val onSetStatus: (String, ReadingStatus) -> Unit,
    val onDelete: (String) -> Unit,
)

private val IMPORT_MIME_TYPES = arrayOf("application/epub+zip", "application/pdf", "application/octet-stream")

@Composable
fun LibraryRoute(onOpenBook: (String) -> Unit, viewModel: LibraryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.import(uris)
    }
    val importedTemplate = stringResource(R.string.imported_count)
    val alreadyText = stringResource(R.string.already_in_library)
    val openText = stringResource(R.string.open)
    val failedTemplate = stringResource(R.string.import_failed)

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            when (message) {
                is LibraryMessage.Imported -> snackbar.showSnackbar(importedTemplate.format(message.count))
                is LibraryMessage.AlreadyInLibrary -> {
                    val result = snackbar.showSnackbar(alreadyText, actionLabel = openText)
                    if (result == SnackbarResult.ActionPerformed) onOpenBook(message.bookId)
                }
                is LibraryMessage.ImportFailed -> snackbar.showSnackbar(
                    failedTemplate.format(message.name ?: "file", message.error.userText()),
                )
            }
        }
    }

    LibraryScreen(
        state = state,
        snackbarHostState = snackbar,
        actions = LibraryActions(
            onOpenBook = onOpenBook,
            onImportClick = { picker.launch(IMPORT_MIME_TYPES) },
            onQueryChange = viewModel::onQueryChange,
            onFilterChange = viewModel::onFilterChange,
            onSortChange = viewModel::onSortChange,
            onLayoutChange = viewModel::onLayoutChange,
            onSetStatus = viewModel::setStatus,
            onDelete = viewModel::delete,
        ),
    )
}

private fun dev.kent.windbook.core.data.importer.ImportError.userText(): String = when (this) {
    dev.kent.windbook.core.data.importer.ImportError.UNSUPPORTED_FORMAT -> "only EPUB and PDF are supported"
    dev.kent.windbook.core.data.importer.ImportError.NOT_AN_EPUB -> "this ZIP isn't an EPUB"
    dev.kent.windbook.core.data.importer.ImportError.CORRUPT -> "the file is damaged; try downloading it again"
    dev.kent.windbook.core.data.importer.ImportError.TOO_LARGE -> "files over 500 MB aren't supported"
    dev.kent.windbook.core.data.importer.ImportError.TOO_LARGE_UNCOMPRESSED,
    dev.kent.windbook.core.data.importer.ImportError.TOO_MANY_ENTRIES,
    dev.kent.windbook.core.data.importer.ImportError.UNSAFE_PATHS -> "the EPUB looks unsafe and was blocked"
    dev.kent.windbook.core.data.importer.ImportError.NO_SPACE -> "your phone is out of storage"
    dev.kent.windbook.core.data.importer.ImportError.READ_FAILED -> "the file couldn't be read"
    dev.kent.windbook.core.data.importer.ImportError.UNREADABLE -> "the book couldn't be opened"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    actions: LibraryActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var searching by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = actions.onQueryChange,
                            placeholder = { Text(stringResource(R.string.search_books)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(stringResource(R.string.app_name))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (searching) actions.onQueryChange("")
                        searching = !searching
                    }) {
                        Icon(if (searching) WindBookIcons.Close else WindBookIcons.Search, contentDescription = "Search")
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(WindBookIcons.Sort, contentDescription = "Sort") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            LibrarySort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text((if (sort == state.sort) "✓ " else "   ") + sort.label()) },
                                    onClick = { actions.onSortChange(sort); sortMenu = false },
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        actions.onLayoutChange(if (state.layout == LibraryLayout.GRID) LibraryLayout.LIST else LibraryLayout.GRID)
                    }) {
                        Icon(
                            if (state.layout == LibraryLayout.GRID) WindBookIcons.List else WindBookIcons.Grid,
                            contentDescription = "Change layout",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.isLibraryEmpty) {
                ExtendedFloatingActionButton(
                    onClick = actions.onImportClick,
                    icon = { Icon(WindBookIcons.Add, contentDescription = null) },
                    text = { Text(stringResource(if (state.isImporting) R.string.importing else R.string.add_books)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLibraryEmpty) {
            EmptyLibrary(onImportClick = actions.onImportClick, modifier = Modifier.padding(padding))
        } else {
            LibraryContent(state, actions, Modifier.padding(padding))
        }
    }
}

@Composable
private fun EmptyLibrary(onImportClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.empty_library), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onImportClick) { Text(stringResource(R.string.add_books)) }
    }
}

@Composable
private fun LibraryContent(state: LibraryUiState, actions: LibraryActions, modifier: Modifier = Modifier) {
    val columns = if (state.layout == LibraryLayout.GRID) GridCells.Adaptive(minSize = 104.dp) else GridCells.Fixed(1)
    LazyVerticalGrid(
        columns = columns,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.continueReading?.takeIf { state.query.isBlank() }?.let { current ->
            item(span = { GridItemSpan(maxLineSpan) }) { ContinueReadingCard(current, actions.onOpenBook) }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { FilterRow(state.filter, actions.onFilterChange) }
        items(state.items, key = { it.book.id }) { item ->
            BookCell(item, state.layout, actions)
        }
    }
}

@Composable
private fun ContinueReadingCard(item: LibraryItem, onOpen: (String) -> Unit) {
    Card(onClick = { onOpen(item.book.id) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(item, Modifier.width(56.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.continue_reading), style = MaterialTheme.typography.labelLarge)
                Text(item.book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { item.progression.toFloat() }, modifier = Modifier.fillMaxWidth())
                Text(percent(item.progression), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun FilterRow(selected: StatusFilter, onSelect: (StatusFilter) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusFilter.entries.forEach { filter ->
            FilterChip(selected = filter == selected, onClick = { onSelect(filter) }, label = { Text(filter.label()) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCell(item: LibraryItem, layout: LibraryLayout, actions: LibraryActions) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Box {
        val clickable = Modifier.combinedClickable(
            onClick = { actions.onOpenBook(item.book.id) },
            onLongClick = { menu = true },
        )
        if (layout == LibraryLayout.GRID) {
            Column(clickable) {
                Cover(item, Modifier.fillMaxWidth())
                Text(item.book.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.progression > 0) Text(percent(item.progression), style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Row(clickable.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Cover(item, Modifier.width(48.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    item.book.author?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                }
                Text(percent(item.progression), style = MaterialTheme.typography.labelMedium)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            listOf(
                ReadingStatus.TO_READ to R.string.mark_to_read,
                ReadingStatus.READING to R.string.mark_reading,
                ReadingStatus.FINISHED to R.string.mark_finished,
            ).filter { it.first != item.book.status }.forEach { (status, label) ->
                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                    actions.onSetStatus(item.book.id, status)
                    menu = false
                })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.delete_book)) }, onClick = {
                menu = false
                confirmDelete = true
            })
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title, item.book.title)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = { actions.onDelete(item.book.id); confirmDelete = false }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Cover(item: LibraryItem, modifier: Modifier) {
    val shape = RoundedCornerShape(6.dp)
    val coverModifier = modifier.aspectRatio(2f / 3f).clip(shape)
    if (item.book.coverPath != null) {
        AsyncImage(
            model = File(item.book.coverPath!!),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = coverModifier,
        )
    } else {
        Box(coverModifier.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Text(
                item.book.title.take(1).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

private fun percent(progression: Double) = "${(progression * 100).roundToInt()}%"

private fun LibrarySort.label() = when (this) {
    LibrarySort.RECENT -> "Recent"
    LibrarySort.TITLE -> "Title"
    LibrarySort.AUTHOR -> "Author"
    LibrarySort.ADDED -> "Date added"
    LibrarySort.PROGRESS -> "Progress"
}

private fun StatusFilter.label() = when (this) {
    StatusFilter.ALL -> "All"
    StatusFilter.READING -> "Reading"
    StatusFilter.TO_READ -> "To read"
    StatusFilter.FINISHED -> "Finished"
}
```

`ReaderScreen.kt`:
```kotlin
package dev.kent.windbook.reader

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.compose.AndroidFragment
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kent.windbook.R
import dev.kent.windbook.input.KeyEventDispatcher
import dev.kent.windbook.input.LocalKeyEventDispatcher
import dev.kent.windbook.ui.icons.WindBookIcons
import kotlin.math.roundToInt

@Composable
fun ReaderRoute(bookId: String, onBack: () -> Unit) {
    val viewModel = hiltViewModel<ReaderViewModel, ReaderViewModel.Factory>(
        key = bookId,
        creationCallback = { factory -> factory.create(bookId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val overlay by viewModel.overlayVisible.collectAsStateWithLifecycle()
    val progression by viewModel.progression.collectAsStateWithLifecycle()
    val chapterTitle by viewModel.chapterTitle.collectAsStateWithLifecycle()
    val canMarkFinished by viewModel.canMarkFinished.collectAsStateWithLifecycle()

    ImmersiveReadingWindow()

    when (val current = state) {
        ReaderUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is ReaderUiState.Error -> ReaderErrorScreen(
            error = current.error,
            onBack = onBack,
            onRemove = { viewModel.removeBook(onRemoved = onBack) },
        )
        is ReaderUiState.Ready -> Box(Modifier.fillMaxSize()) {
            ReaderSurface(current, viewModel)
            ReaderOverlay(
                visible = overlay,
                title = current.title,
                chapterTitle = chapterTitle,
                progression = progression,
                canMarkFinished = canMarkFinished,
                onMarkFinished = viewModel::markFinished,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun ReaderSurface(state: ReaderUiState.Ready, viewModel: ReaderViewModel) {
    val dispatcher = LocalKeyEventDispatcher.current
    val hostRef = remember { mutableStateOf<ReaderHostFragment?>(null) }
    val callbacks = remember(viewModel) {
        object : ReaderHostFragment.Callbacks {
            override fun onLocatorChanged(locatorJson: String, progression: Double, chapterTitle: String?) =
                viewModel.onLocatorChanged(locatorJson, progression, chapterTitle)
            override fun onCenterTap() = viewModel.toggleOverlay()
        }
    }

    AndroidFragment<ReaderHostFragment>(
        modifier = Modifier.fillMaxSize(),
        arguments = ReaderHostFragment.arguments(state.session.format),
    ) { fragment ->
        hostRef.value = fragment
        // A re-created fragment (rotation) must open where the reader is now, not where the book was opened.
        fragment.bind(state.session, viewModel.latestLocatorJson ?: state.initialLocatorJson, callbacks)
    }

    DisposableEffect(dispatcher) {
        val turner = KeyEventDispatcher.PageTurner { forward -> hostRef.value?.turnPage(forward) ?: false }
        dispatcher.register(turner)
        onDispose { dispatcher.unregister(turner) }
    }
}

/** Hides system bars (swipe to peek) and keeps the screen on while the reader is shown. */
@Composable
private fun ImmersiveReadingWindow() {
    val activity = LocalActivity.current ?: return
    val view = LocalView.current
    DisposableEffect(activity) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }
}

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
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(WindBookIcons.Back, contentDescription = stringResource(R.string.back)) }
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                LinearProgressIndicator(progress = { progression.toFloat() }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                val percent = "${(progression * 100).roundToInt()}%"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = chapterTitle?.let { "$it · $percent" } ?: percent,
                        style = MaterialTheme.typography.labelLarge,
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

@Composable
private fun ReaderErrorScreen(error: ReaderError, onBack: () -> Unit, onRemove: () -> Unit) {
    val message = when (error) {
        ReaderError.NOT_FOUND -> R.string.reader_error_not_found
        ReaderError.FILE_MISSING -> R.string.reader_error_file_missing
        ReaderError.CANNOT_OPEN -> R.string.reader_error_cannot_open
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) { Text(stringResource(R.string.back)) }
        if (error == ReaderError.FILE_MISSING) {
            TextButton(onClick = onRemove) { Text(stringResource(R.string.remove_from_library)) }
        }
    }
}
```

`Routes.kt`:
```kotlin
package dev.kent.windbook.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object LibraryKey : NavKey

@Serializable
data class ReaderKey(val bookId: String) : NavKey
```

`WindBookNavHost.kt`:
```kotlin
package dev.kent.windbook.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.kent.windbook.library.LibraryRoute
import dev.kent.windbook.reader.ReaderRoute

@Composable
fun WindBookNavHost() {
    val backStack = rememberNavBackStack(LibraryKey)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<LibraryKey> {
                LibraryRoute(onOpenBook = { id -> backStack.add(ReaderKey(id)) })
            }
            entry<ReaderKey> { key ->
                ReaderRoute(bookId = key.bookId, onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}
```

`MainActivity.kt` (replace):
```kotlin
package dev.kent.windbook

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import dagger.hilt.android.AndroidEntryPoint
import dev.kent.windbook.input.KeyEventDispatcher
import dev.kent.windbook.input.LocalKeyEventDispatcher
import dev.kent.windbook.navigation.WindBookNavHost
import dev.kent.windbook.ui.theme.WindBookTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var keyEventDispatcher: KeyEventDispatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            WindBookTheme {
                CompositionLocalProvider(LocalKeyEventDispatcher provides keyEventDispatcher) {
                    WindBookNavHost()
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        keyEventDispatcher.dispatch(event) || super.dispatchKeyEvent(event)
}
```

If an import path for a Navigation 3 decorator doesn't resolve (`rememberSaveableStateHolderNavEntryDecorator` has lived in both `navigation3.runtime` and `navigation3.ui`), use Android Studio's auto-import and keep everything else unchanged.

- [ ] **Step 3b: English + Vietnamese UI (spec: UI languages)**

Move every hard-coded user-visible string in `LibraryScreen.kt`/`ReaderScreen.kt` (sort labels, filter labels, import error texts, content descriptions "Search"/"Sort"/"Change layout") into `values/strings.xml`. Create `app/src/main/res/values-vi/strings.xml` with a Vietnamese translation of every string. Enable the per-app language picker: in `app/build.gradle.kts` add `androidResources { generateLocaleConfig = true }`, and create `app/src/main/res/resources.properties` with `unqualifiedResLocale=en-US`. Add a test to `LibraryScreenTest`: `@Config(qualifiers = "vi") emptyLibraryShowsVietnameseAddBooks` asserting the Vietnamese "Add books" label ("Thêm sách").

- [ ] **Step 4: Run tests and build**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS (all app tests: Smoke 1, KeyEventDispatcher 3, LibraryViewModel 4, ReaderViewModel 10, LibraryScreen 3, ReaderOverlay 3), `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "feat(app): library and reader screens on Navigation 3 with immersive reading and volume-key paging"
```

---

### Task 13: Release signing, CI, and end-to-end verification on Waydroid and the Neo 3 GT

**Files:**
- Create: `.github/workflows/android-ci.yml`
- Create: `keystore.properties` (git-ignored, local only)
- Create: `docs/testing/m1-manual-checklist.md`

**Interfaces:**
- Consumes: everything above.
- Produces: signed `app-release.apk` v0.1.0, CI pipeline, recorded manual test results.

- [ ] **Step 1: Write the CI workflow**

`.github/workflows/android-ci.yml`:
```yaml
name: Android CI

on:
  push:
    branches: [main]
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    permissions:
      contents: read
    steps:
      - uses: actions/checkout@v5
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '21'
      - uses: gradle/actions/setup-gradle@v5
      - name: Lint and unit tests
        run: ./gradlew :core:model:test :core:data:testDebugUnitTest :reader:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug --stacktrace
      - name: Build debug APK
        run: ./gradlew :app:assembleDebug
      - uses: actions/upload-artifact@v5
        with:
          name: windbook-debug
          path: app/build/outputs/apk/debug/*.apk
```

- [ ] **Step 2: Run the full local check (what CI runs)**

Run: `./gradlew :core:model:test :core:data:testDebugUnitTest :reader:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, 0 lint errors. Fix any lint *errors* before continuing (warnings may stay).

- [ ] **Step 3: Create the release keystore (once; back it up outside the repo)**

```bash
mkdir -p ~/keys
keytool -genkeypair -v -keystore ~/keys/windbook-release.jks -alias windbook \
  -keyalg RSA -keysize 3072 -validity 10000
cat > keystore.properties <<'EOF'
storeFile=/home/kent/keys/windbook-release.jks
storePassword=CHANGE_ME
keyAlias=windbook
keyPassword=CHANGE_ME
EOF
```
Replace `CHANGE_ME` with the passwords you chose. `keystore.properties` and `*.jks` are git-ignored. Losing this key means the app can never be updated in place, so keep two backups.

- [ ] **Step 4: Build and verify the signed release APK**

```bash
./gradlew :app:assembleRelease
~/Android/Sdk/build-tools/36.0.0/apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```
Expected: `Verified using v2 scheme (APK Signature Scheme v2): true` (v3 too), no errors.

- [ ] **Step 5: Waydroid end-to-end run**

```bash
WAYDROID_IP=$(waydroid status | awk '/IP address/ {print $3}')
adb connect "$WAYDROID_IP:5555"
adb install -r app/build/outputs/apk/release/app-release.apk
adb push /path/to/a-real-book.epub /sdcard/Download/
adb push /path/to/a-real-document.pdf /sdcard/Download/
adb shell monkey -p dev.kent.windbook 1
adb logcat -s AndroidRuntime:E &
```
Walk through `docs/testing/m1-manual-checklist.md` (Step 6) on Waydroid, then repeat on the Neo 3 GT over USB (`adb install -r …`).

- [ ] **Step 6: Write and execute the manual checklist**

`docs/testing/m1-manual-checklist.md`:
```markdown
# M1 manual checklist (run on Waydroid and on the Nubia Neo 3 GT)

Device: ________  Android: ____  Build: v0.1.0 (release)  Date: ____

| # | Step | Expected | Waydroid | Neo 3 GT |
|---|------|----------|----------|----------|
| 1 | Fresh install, open app | Empty library with "Add books" | | |
| 2 | Add an EPUB and a PDF together | Snackbar "2 book(s) added"; both appear with covers or letter tiles | | |
| 3 | Add the same EPUB again | "Already in your library" + Open action works | | |
| 4 | Add a .txt renamed to .epub | "Couldn't add …: only EPUB and PDF are supported" | | |
| 5 | Open EPUB, turn 10 pages via edge taps | Pages turn; centre tap toggles top/bottom overlay with % | | |
| 6 | Volume-down / volume-up | Next / previous page, no volume panel | | |
| 7 | Rotate the phone | Same page after rotation | | |
| 8 | Press Home, then `adb shell am kill dev.kent.windbook`, reopen from recents | Reader reopens at the same page | | |
| 9 | Swipe app away from recents, reopen, open book | Same page | | |
| 10 | Back to library | "Continue reading" shows this book with correct % | | |
| 11 | Open the PDF, scroll/swipe pages, back, reopen | Same page | | |
| 12 | Search "a word from a title without accents" (e.g. "tieng") | Matching Vietnamese title shown | | |
| 13 | Long-press → Mark as finished; filter "Finished" | Book listed under Finished | | |
| 14 | Long-press → Delete… → Delete | Book disappears; re-adding the file works and starts at page 1 | | |
| 15 | Leave reader idle 5 min | Screen stays on | | |
| 16 | Neo 3 GT only: `adb shell getevent -l`, press both shoulder triggers | Record the key codes printed (input for M3 F3.11): ______ | n/a | |
| 17 | Neo 3 GT only: cold start time (`adb shell am start -W dev.kent.windbook/.MainActivity`) | TotalTime < 2000 ms: ____ ms | n/a | |
```
Fill in every cell. Any failure becomes a bug fix (with a regression test) before Step 7.

- [ ] **Step 7: Commit, tag, and merge**

```bash
git add .github docs/testing
git commit -m "ci: GitHub Actions pipeline; docs: M1 manual checklist results"
git tag -a v0.1.0 -m "WindBook M1: core reading loop"
git switch main
git merge --no-ff m1-core-reading-loop -m "Merge M1 core reading loop"
```
