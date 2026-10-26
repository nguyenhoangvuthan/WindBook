# Settings alignment and real-library verification — 2026-10-27

Owner request: align Settings text, import varied EPUB/PDF from the provided local
library and deliver an updated APK. This is a small layout correction to existing
controls. No reading, storage, dependency, schema or permission change.

## Layout

Root cause: language labels, checkbox labels, section headers and action buttons
used different horizontal padding. Extension labels also used a serif heading style
amid body controls. Text now shares a 32 dp left column in Settings; radio/checkbox
controls trail the label, data rows use consistent body typography and spacing,
and action text retains button semantics with minimum 48 dp targets. Shared folder
controls keep their existing callbacks and wrap with available space.

| Gate | Evidence |
|---|---|
| Actual API36 alignment regression | RED before fix; GREEN final labels at x84px, including first text action and folder entry |
| Vietnamese normal/130% font size | Actual screenshots: controls and long storage/about labels remain readable; page scrolls |
| English language selection | Actual screenshot; switching back to Vietnamese succeeds |
| Settings→folder route and checkbox/chip actions | Actual route opens; real root grant/scan succeeds |
| Mandatory CI/lint/build |385 JVM tests pass; lint 0 errors / 10 baseline warnings;0.1.2 debug APK built |
| Independent scoped review | Approved against frozen three-file source diff; no confirmed finding |

## Real-library test

Read-only inventory found672 EPUB and 703 PDF (about 17.3 GB). The emulator had about
4.4 GB free, so the device test uses24 actual books (12 EPUB / 12 PDF, 26.28 MB), rather than
claiming the entire library was copied. Generic device filenames identify samples;
full private mapping stays outside Git. Categories include Vietnamese/English EPUB 2
and 3, embedded fonts, SVG, image-heavy publications; PDF LaTeX/InDesign/calibre,
landscape, a long book, scanner-produced and no-initial-text candidates. Categories
use existing catalog metadata/EPUB package inspection; no claim that every PDF page
or arbitrary source feature was exhaustively checked.

- Android Add-books picker selects24 files; app reports24 added, no import error.
- Typed device root→grant→scan reports24 checked / 24 found / 0 errors/complete.
- Final APK opens all 24 samples (12 EPUB / 12 PDF); a volume page-turn input is exercised for each, then returning to the filtered folder list succeeds. Spot-checked actual rendered EPUB text and PDF pages; this is an opening/navigation smoke check, not a read-through of every page.
- All 24 host originals and emulator copies retain their initial SHA-256 after all reading checks. The All/Clear Settings actions set all three chips on/off correctly and are restored to All.
- APK: `WindBook-0.1.2-settings-debug.apk`, versionCode3/versionName0.1.2,79,877,252 bytes. SHA-256: `c8d719469eea61afc188b9e6782ee6a4e6dcae7f017acd24fe6b87cbe5c40fd9`. Copy checksum, package version and signature verified. No new dependencies or source-file writes.

Current logs/screenshots/private manifest live under ignored
`.superpowers/checks/settings-library/`. No private titles, source paths or page
images are committed. Prior full-tree/synthetic 2,500-file and PDF-note evidence
remains valid because those runtime paths are unchanged.
