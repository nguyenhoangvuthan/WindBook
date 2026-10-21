# M1 manual checklist

Build: v0.1.0 release (R8 on, debug-signed until the release keystore exists).
Emulator column: AVD `Medium_Phone_API_37.0` (Android 17 / API 37, x86_64), run 2026-10-13 via adb + uiautomator.
Waydroid could not be used: its Android gets no IP (firewalld blocks DHCP on `waydroid0`; fix needs `sudo firewall-cmd --zone=trusted --add-interface=waydroid0 --permanent && sudo firewall-cmd --reload`).

| # | Step | Expected | Emulator API 37 | Neo 3 GT |
|---|------|----------|-----------------|----------|
| 1 | Fresh install, open app | Empty library with "Add books" | ✅ | |
| 2 | Add an EPUB and a PDF together | Both appear with covers or letter tiles | ✅ EPUB cover + PDF first-page cover | |
| 3 | Add the same EPUB again | "Already in your library" + Open action | ✅ | |
| 4 | Add a .txt renamed to .epub | "Couldn't add …: only EPUB and PDF are supported" | ➖ covered by unit tests only | |
| 5 | Open EPUB, centre tap | Overlay with title, chapter · % | ✅ "Chương 2 · 8%" | |
| 6 | Volume-down / volume-up | Next / previous page, no volume panel | ✅ (after dismissing Android's one-time "Viewing full screen" notice, which holds key focus) | |
| 7 | Rotate the phone | Same position after rotation | ✅ after fix `fix(reader)`; crashed before | |
| 8 | Home, `adb shell am kill dev.kent.windbook`, reopen | Reader reopens at same position | ✅ "Chương 2 · 8%" | |
| 9 | Swipe app away from recents, reopen, open book | Same position | ➖ not run | |
| 10 | Back to library | "Continue reading" shows the book with % | ✅ | |
| 11 | Open the PDF | Renders, overlay shows % | ✅ (1-page sample) | |
| 12 | Search without accents ("chuyen gio") | Vietnamese title found, others hidden | ✅ | |
| 13 | Long-press → Mark as finished; filter "Finished" | Book listed under Finished | ➖ Compose UI test only | |
| 14 | Long-press → Delete… → Delete, re-add | Disappears; re-add starts at 0% | ➖ unit tests only | |
| 15 | App language Vietnamese (`cmd locale set-app-locales … vi`) | All UI in Vietnamese | ✅ | |
| 16 | Cold start `am start -W` | TotalTime < 2000 ms | ✅ 691–921 ms | |

## Look & feel (2026-10-14 redesign)

| # | Step | Expected | Emulator | Neo 3 GT |
|---|---|---|---|---|
| L1 | Open the app in light mode | Cream paper background, "Library │ N books" in serif, gold + button | Pass 2026-10-14 | |
| L2 | Type in the search pill, clear with ✕ | List filters as you type; header count does not change | | |
| L3 | Tap ⋯ on a cover | Status/delete menu, same as long-press | | |
| L4 | Switch tabs Library → Reading → Notes → Settings, press Back | Each tab shows its screen; Back returns to Library | Pass 2026-10-14 | |
| L5 | Reading tab with no book in progress | "No book in progress" and a "Go to library" button that switches tab | | |
| L6 | Settings → Tiếng Việt | App switches to Vietnamese immediately; survives an app restart | Pass 2026-10-14 | |
| L7 | Settings → Storage | Book count and total size match the library | Pass 2026-10-14 | |
| L8 | Open an EPUB in light mode | Page is sepia; centre tap shows bars with the title in capitals | Pass 2026-10-14 | |
| L9 | Turn on system dark mode | Warm dark chrome; EPUB page turns dark | Pass 2026-10-14 | |
| L10 | Open a book from the Reading tab, press Back | Returns to the Reading tab | Pass 2026-10-14 | |

## PDF display (2026-10-15)

| # | Step | Expected | Emulator | Neo 3 GT |
|---|---|---|---|---|
| P1 | Open a portrait PDF (LaTeX/InDesign book) | Fills the width, white margins cropped, scrolls vertically, no sideways panning | Pass 2026-10-15 (body text 1.26× the uncropped page; LaTeX book) | |
| P2 | Pinch to 3×, scroll down, wait 0.2 s | Text sharp; no sideways jumps when scrolling down | Pass 2026-10-15 with double-tap 2.5× (adb can't pinch): soft at 0.3 s, sharp tile by 2 s | |
| P3 | Open the scanned textbook, scroll 30 pages | First page ≤ 1.5 s; `dumpsys meminfo` TOTAL PSS < 400 MB | Pass 2026-10-15: first page 1.32 s after the tap (log), ~1.4 s (screen recording); PSS 195 MB open, 267 MB after 51 pages | |
| P4 | Comic PDF → View → Page by page; reopen | Opens page by page next time; edge taps turn pages | Pass 2026-10-15 | |
| P5 | Slides PDF | Opens page by page, whole slide visible | Pass 2026-10-15 | |
| P6 | View → Crop margins off | Whole page with margins; choice remembered | Pass 2026-10-15 | |
| P7 | EPUB | First and last lines clear of the screen edges and camera | Pass 2026-10-15 | |
| P8 | EPUB that links a file missing from the book | Opens and reads; `adb logcat -b crash` empty | Pass 2026-10-15 | |

## PDF comfort (2026-10-16)

Rows C4–C7 below are historical only: Reflow was removed by owner decision on
2026-10-20. C1–C3 remain applicable. Use R1–R3 for the removal build.

| # | Step | Expected | Emulator | Neo 3 GT |
|---|---|---|---|---|
| C1 | Import the scanned textbook (2654×4329 pt pages) | ≤ 1.5 s, PSS +< 100 MB, title and cover shown | Pass 2026-10-16: ~0.75 s after the tap, PSS +15 MB peak, title, author and cover present | |
| C2 | Zoom a portrait PDF, swipe sideways, reopen | Same zoom and position; never-zoomed books open at 1× | Pass 2026-10-16: 2.5× and x = 0.546 restored | |
| C3 | Volume down ×3 while zoomed | Right, right edge, back to the left edge and down | Pass 2026-10-16 | |
| C4 | View → Reflow on the scanned textbook | Words re-wrapped, no sideways panning, tone marks on their letters | Pass 2026-10-16 (body pages; title page after two analyzer fixes) | |
| C5 | Reflow A+ / A− and pinch | Text size steps once per tap or pinch; remembered after reopening | Pass 2026-10-16 (A+ to the largest size, saved per book) | |
| C6 | Reflow → Vertical → Reflow | Same page | Pass 2026-10-16 | |
| C7 | Reflow, 30 pages at the largest size | TOTAL PSS < 400 MB | Pass 2026-10-16: 339 MB settled, 359 MB right after scrolling; one transient 406 MB peak during very fast flings | |


## Reflow removal (2026-10-20/21)

Verified on branch `remove-pdf-reflow`. JVM: 206/206; reader device suite:
17/17 on Neo3GT_API_36. Lint: 0 errors, 9 warnings, identical to a freshly
rerun pre-removal baseline. Debug APK built. Independent code review found
no correctness/regression issues. Screenshots stay in the ignored
`.superpowers/checks/remove-reflow/` directory.

| # | Step | Expected | Verification | Neo 3 GT |
|---|---|---|---|---|
| R1 | Open a PDF and its View menu | Vertical scroll, Page by page and Crop margins; no Reflow or text-size buttons | Emulator UI dump and screenshot inspected 2026-10-20; Compose regression passes | Pending |
| R2 | Open a book with old REFLOW preferences | Vertical mode, saved page/crop/zoom retained, including landscape books | Repository and ViewModel regression tests pass 2026-10-21; phone upgrade check pending | Pending |
| R3 | Switch layouts, reopen, use zoom and volume keys | Existing page layouts and saved state still work | Existing Compose/ViewModel tests and reader device suite pass 2026-10-21; full manual phone pass pending | Pending |
