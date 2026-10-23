# WindBook

A private, offline EPUB/PDF reader for Android 12+ (minSdk 31, targetSdk 36), built with Kotlin, Jetpack Compose and Readium.
Current version: **v0.1.0 (Milestone 1: core reading loop)**. It imports books, shows a library and opens EPUB/PDF. It always reopens at the exact page, even after rotation or the app being killed. UI in English and Vietnamese.

PDF supports vertical scrolling and page-by-page reading, margin cropping and
remembered zoom. Reflow was removed by owner decision on 2026-10-20; books
with that saved mode reopen vertically at their saved page.

Folder browsing uses Android's folder picker to remember a read-only grant. Open
**Book folders**, choose a folder, select extensions and recursive scanning, then
scan manually. Typing a location does not grant access. Stop preserves results
already committed; hiding or disconnecting never deletes source files. EPUB/PDF
open from their original folder, with bounded temporary preparation for the active
book. Discovery supports eight formats; the six additional reader adapters are
still pending. See [folder verification](docs/testing/folder-library-checklist.md).

- Design spec: [docs/superpowers/specs/2026-10-13-windbook-design.md](docs/superpowers/specs/2026-10-13-windbook-design.md)
- Roadmap: [docs/superpowers/plans/2026-10-13-windbook-roadmap.md](docs/superpowers/plans/2026-10-13-windbook-roadmap.md)
- Manual test results: [docs/testing/m1-manual-checklist.md](docs/testing/m1-manual-checklist.md)

---

## 1. Requirements

| Tool | Version | Check |
|---|---|---|
| JDK | 21 | `java -version` |
| Android SDK | platform 37, build-tools 36.0.0 | `ls ~/Android/Sdk/platforms` |
| adb | any recent | `adb version` |

Create `local.properties` in the project root (it is git-ignored):

```properties
sdk.dir=/home/kent/Android/Sdk
```

Gradle 9.6.0 downloads itself through `./gradlew`.

## 2. Build

```bash
./gradlew :app:assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease   # app/build/outputs/apk/release/app-release.apk (smaller, faster)
```

### Release signing key (do this once, before installing on your phone)

Without a key, the release APK is signed with the **debug** key. If you install that on your phone and switch to a real key later, Android refuses the update. Uninstalling then **deletes your library and reading positions**.

```bash
mkdir -p ~/keys
keytool -genkeypair -v -keystore ~/keys/windbook-release.jks -alias windbook \
  -keyalg RSA -keysize 3072 -validity 10000
```

Then create `keystore.properties` in the project root (git-ignored). Use the passwords you just typed:

```properties
storeFile=/home/kent/keys/windbook-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=windbook
keyPassword=YOUR_KEY_PASSWORD
```

Rebuild and check the signature:

```bash
./gradlew :app:assembleRelease
~/Android/Sdk/build-tools/36.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

Back up `~/keys/windbook-release.jks` and its passwords somewhere safe (two copies). Losing the key means the app can never be updated in place.

## 3. Automated tests

```bash
# Unit tests (no device needed): 317 tests at the folder-library checkpoint
./gradlew :core:model:test :core:data:testDebugUnitTest :reader:testDebugUnitTest :app:testDebugUnitTest

# Lint
./gradlew :app:lintDebug

# Device tests (needs an emulator or phone connected, see section 4)
./gradlew :reader:connectedDebugAndroidTest
```

Reports are in `*/build/reports/tests/` (open `index.html` in a browser).

---

## 4. Choose a device for manual testing

Use **one** of the options below. When it's ready, `adb devices` must list exactly one device. If several are listed, prefix commands with `ANDROID_SERIAL=<id>`.

### A. Android emulator (easiest)

```bash
QT_QPA_PLATFORM=xcb setsid nohup ~/Android/Sdk/emulator/emulator -avd Neo3GT_API_36 -no-snapshot-load -cores 4 -gpu swiftshader_indirect > /dev/null 2>&1 &
adb wait-for-device && adb shell getprop sys.boot_completed   # repeat until it prints 1
```

`Neo3GT_API_36` matches the target phone: Android 16 (API 36), 1080×2400, 420 dpi, 4 GB RAM. Create it once:

```bash
~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager "system-images;android-36;google_apis_playstore;x86_64"
echo no | ~/Android/Sdk/cmdline-tools/latest/bin/avdmanager create avd -n Neo3GT_API_36 \
  -k "system-images;android-36;google_apis_playstore;x86_64" -d medium_phone
# then set hw.ramSize=4096 in ~/.android/avd/Neo3GT_API_36.avd/config.ini
```

`-gpu swiftshader_indirect` renders in software. Use it if the emulator closes by itself with `gfxstream ... null ctx` in its log.

### B. Waydroid

Waydroid's Android gets no network address while firewalld blocks it. Fix it once:

```bash
sudo firewall-cmd --zone=trusted --add-interface=waydroid0 --permanent
sudo firewall-cmd --reload
```

Then:

```bash
waydroid session start &
waydroid show-full-ui &
sleep 20
adb connect "$(waydroid status | awk '/IP address/ {print $3}'):5555"
adb shell getprop ro.build.version.sdk   # must be 31 or higher
```

### C. Your phone (Nubia Neo 3 GT) over USB

1. Settings → About phone → tap **Build number** 7 times to unlock Developer options.
2. Settings → System → Developer options → turn on **USB debugging** (and **Install via USB** if MyOS shows it).
3. Plug in the cable, accept the "Allow USB debugging?" prompt on the phone, then run `adb devices`.
4. Recommended for MyOS: Settings → Battery → app battery management → WindBook → **No restrictions**. This stops MyOS from killing the app in the background.

---

## 5. Install the app and copy test books

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb push "/path/to/your book.epub" /sdcard/Download/
adb push "/path/to/your document.pdf" /sdcard/Download/
```

Use your own books, or public-domain ones from https://www.gutenberg.org or https://standardebooks.org. For test 4 below, make a fake EPUB:

```bash
echo "just text" > /tmp/fake.epub && adb push /tmp/fake.epub /sdcard/Download/
```

To watch for crashes while you test, keep this running in a second terminal:

```bash
adb logcat -s AndroidRuntime:E
```

---

## 6. Manual test steps

Do the steps in order. Write ✅ / ❌ (and a note) in the matching row of [docs/testing/m1-manual-checklist.md](docs/testing/m1-manual-checklist.md), in your device's column.

**1. First launch**
- Uninstall any old copy (`adb uninstall dev.kent.windbook`), install again, open **WindBook**.
- Expect: an empty library with the text "Your library is empty…" and an **Add books** button.

**2. Add two books at once**
- Tap **Add books**. In the file picker, open **Downloads**, long-press the EPUB, then tap the PDF so both are selected. Tap **Select**.
- Expect: "2 book(s) added". Both books appear with a cover (or a coloured tile with the first letter). The EPUB shows the title from inside the book, not the file name.

**3. Duplicate**
- Tap **Add books** again and pick the same EPUB.
- Expect: "Already in your library" with an **Open** button that opens the book.

**4. Wrong file type**
- Tap **Add books** and pick `fake.epub`.
- Expect: "Couldn't add fake.epub: only EPUB and PDF are supported". Nothing is added.

**5. Reading controls**
- Open the EPUB. Tap the **right edge** to go to the next page, the **left edge** to go back. Tap the **centre**.
- Expect: pages turn. The centre tap shows a top bar (back arrow + title) and a bottom bar (progress line + "Chapter · %"). Tap the centre again to hide them.
- The first time, Android may show "Viewing full screen". Tap **Got it**.

**6. Volume keys**
- Press **volume down** a few times, then **volume up**.
- Expect: next page / previous page. The volume slider must NOT appear.

**7. Rotation**
- Note the "Chapter · %" (centre tap), then rotate the phone to landscape and back.
- Emulator: `adb shell settings put system accelerometer_rotation 0 && adb shell settings put system user_rotation 1` (landscape), then `user_rotation 0` (portrait).
- Expect: the same chapter and %. No crash.

**8. App killed in the background (most important test)**
- While reading, note the %, press **Home**, then run:
  ```bash
  adb shell am kill dev.kent.windbook
  adb shell pidof dev.kent.windbook   # must print nothing (process is dead)
  ```
- Open WindBook again from Recents.
- Expect: the reader opens on the same page with the same %.

**9. Swipe away from Recents**
- Open Recents, swipe WindBook away, open it again, then open the same book.
- Expect: the same page.

**10. Continue reading**
- Go back to the library.
- Expect: a **Continue reading** card at the top with this book and the right %.

**11. PDF**
- Open the PDF and swipe through a few pages. Go back, then open it again.
- Expect: it renders, and it reopens on the page you left. On the **last page**, the bottom bar offers **Mark as finished**.

**12. Search without accents**
- Tap the 🔍 icon, type a title without Vietnamese accents (e.g. `tieng viet` for "Tiếng Việt").
- Expect: the book with accents is found, and the others are hidden.

**13. Status**
- Long-press a book → **Mark as finished**. Tap the **Finished** filter chip.
- Expect: the book is listed under Finished.

**14. Delete and re-add**
- Long-press the book → **Delete…** → **Delete**. Then add the same file again.
- Expect: it disappears, comes back after re-adding, and starts at 0%. The original file in Downloads is untouched.

**15. Vietnamese UI**
- Phone: Settings → System → Languages → App languages → WindBook → **Tiếng Việt**.
- Or with adb: `adb shell cmd locale set-app-locales dev.kent.windbook --locales vi` (back to English: `--locales en`).
- Expect: all buttons and menus in Vietnamese ("Thêm sách", "Đọc tiếp", "Tất cả"…).

**16. Startup time**
- Press Home, kill the app (`adb shell am force-stop dev.kent.windbook`), then run:
  ```bash
  adb shell am start -W dev.kent.windbook/.MainActivity | grep TotalTime
  ```
- Expect: TotalTime below 2000 ms. Write the number in the checklist.

---

## 7. Reporting a problem

When a step fails, save the log right after it happens:

```bash
adb logcat -d > ~/windbook-bug-$(date +%H%M).txt
adb exec-out screencap -p > ~/windbook-bug-$(date +%H%M).png
```

Note the step number, what you did and what you saw, and attach both files.
