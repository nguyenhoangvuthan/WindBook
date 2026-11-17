# Reader polish from the UI comparison — 2026-11-17

Source: owner asked for a UI/UX comparison against two reference mockups (an audio-book
app's home and reader screens) and approved the five fixes below on 2026-11-17 ("tôi thông
qua spec luôn"). Evidence: emulator `Medium_Phone_API_37.0` screenshots in the git-ignored
`.superpowers/checks/ui-compare/`. Findings are visual observations from one emulator
and a synthetic Vietnamese EPUB; no claim about the owner's phone or library.

Explicit non-goals (owner rules and the comparison itself): no banners, no hot-books
shelf, no tab-duplicating shortcut tiles, no floating audio/TTS control, no character art
behind text, no floating tool column over the page, no new dependency, no DB change.

## Changes

1. **Reader progress footer.** The position slider keeps a 16 dp gutter on both sides so
   the thumb never sits at the screen edge (system back-gesture zone). The footer row shows
   the current chapter title (when known) on the left and the percentage on the right as
   two separate texts. PDF keeps its `Page n / N` button. Supersedes the 0.1.8 "percent
   only" caption for chapters; the slider itself stays thin (2 dp track, 12 dp thumb).
2. **Aa one tap away.** For reflowable books (EPUB/TXT) the top bar shows an `Aa` text
   button (accessible name = "Reading appearance (Aa)") next to Contents. The entry leaves
   the ⋮ menu. PDF has no Aa (View stays in ⋮).
3. **Reading appearance sheet.**
   - Scope is a two-segment selector (This book | All books) with an obvious selected
     state, and one short plain-language hint line that depends on the scope. The
     "overrides"/"global defaults" wording and the separate inherit hint are removed.
   - Text size shows its number at all times ("Text size · 120%"; "Publisher default" when
     unset) and moves in 10 % steps from 80 to 200 %.
   - Line height shows its number; resetting to the publisher value is a clearly named
     button that appears only when a value is set.
   - Reading colors chips carry a colour swatch (paper, sepia, dark; system = outline
     only) so people pick by look, not by name.
4. **Warm placeholder covers.** All generated cover tints stay in the warm family (red
   channel ≥ blue channel); the cool grey-blue and mint tints are replaced.
5. **Library header alignment.** The "Book folders" button text starts at the same left
   edge (16 dp) as the "Library" title.

## Compatibility and copy rules

- Every new or changed string exists in `values/strings.xml` and `values-vi/strings.xml`.
- Remove strings that become unused (lint must not gain warnings).
- 48 dp minimum touch targets, TalkBack names preserved, works at 130 % font size.
- Per-book/global typography data model unchanged; only presentation changes.

## Acceptance

JVM CI green with no new lint warnings; emulator screenshots of the reader footer, top
bar, Aa sheet (EN and VI) and Library header show the changes; nothing pushed, no APK
exported until the owner asks (next build would be 0.1.12).
