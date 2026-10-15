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
