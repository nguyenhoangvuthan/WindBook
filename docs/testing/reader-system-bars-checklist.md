# Reader system-bar verification

Owner scope: EPUB/PDF/TXT are sufficient for the current build; FB2/CBZ/CBR/MOBI/AZW3 are paused. Fullscreen reading must keep the status bar visible. Only the navigation bar hides; pages and controls respect actual system-bar/camera insets. Battery percentage follows the Android setting.

## Automated checks

`ReaderViewportTest` measures child bounds with changing top, side and bottom insets and verifies that nested controls do not apply the same inset twice. Required CI: 346 JVM tests (51 model, 111 data, 45 reader, 139 app), zero failures/errors. Lint: zero errors, ten unchanged baseline warnings. Debug APK builds. Independent scoped review approved with no findings.

## Android 16 emulator

AVD Neo3GT_API_36, 1080×2400, 420 dpi; synthetic books only. Before the fix, opening a PDF produced `statusBars visible=false`. After the fix, actual `dumpsys window` sources show status visible and navigation hidden while reading. Screenshots were inspected separately to confirm rendered content and icon contrast, not just window flags.

| Case | Observed result |
|---|---|
| PDF portrait, overlay shown/hidden | Status bar remains visible; content/controls begin below it |
| PDF landscape, dark theme, simulated tall cutout | Visible light status icons; reader stays outside left camera bound |
| EPUB portrait, dark theme, overlay hidden | Rendered chapter remains below status bar; clock, battery percentage and notifications visible |
| TXT portrait, dark theme | Vietnamese text and supplementary Unicode render below visible status bar |
| TXT landscape, light theme, simulated cutout | Visible dark status icons; left camera bound reserved; paragraphs render within remaining width |
| Source unavailable/reconnect state | Status stays visible; actions remain usable |
| Return to library | System bars visible again |

The emulator was returned to portrait/light mode with simulated cutout disabled. Local evidence is under `.superpowers/checks/statusbar/` (ignored): report, window dumps, screenshots, CI/build logs and review. Early screenshots taken during navigation are not final rendered evidence; use `pdf-ready.png`, `pdf-dark-landscape-cutout.png`, `epub-portrait-final.png`, `txt-ready-final.png` and `txt-landscape-light-final.png`.

## Owner phone follow-up

On the Neo 3 GT, open EPUB/PDF/TXT, hide the reading menu, rotate and return to the library. Confirm the clock/battery/notification icons remain visible and content clears the real camera cutout. Physical-phone results are pending; emulator validation does not replace them. Android 12 status-bar behavior was not rerun for this narrow fix; earlier TXT reading/restore gates on Android 12 and 16 remain recorded separately.
