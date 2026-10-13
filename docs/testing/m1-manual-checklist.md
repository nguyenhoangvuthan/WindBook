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
| 16 | Neo 3 GT: `adb shell getevent -l`, press shoulder triggers | Record key codes for M3 F3.11 | n/a | |
| 17 | Cold start `am start -W` | TotalTime < 2000 ms | ✅ 691–921 ms | |
