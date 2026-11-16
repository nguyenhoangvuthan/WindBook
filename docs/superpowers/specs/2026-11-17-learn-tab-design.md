# Learn tab — English study from the owner's own books

Date: 2026-11-17
Status: **draft, awaiting owner approval.** Nothing in this document is implemented.
Baseline: 0.1.11 (Updraft rename, 3D icon, PDF crop default), database v10.

## 1. Intent

The owner wants the app to work like an IELTS study app whose reading material is the
books already in the library instead of articles and essays. Vocabulary and grammar must
stay attached to the sentences they came from, and be organised by level.

What exists today (0.1.9): select text → AI card (translation, grammar, vocabulary, idioms)
→ accepted answers saved beside the highlight → chosen words saved to a flat vocabulary
list reachable from Notes. Lookup is on demand only. There is no level, no review, no
exercise and no progress view.

## 2. Goals, non-goals, anti-goals

Goals
- A fifth tab, **Learn**, that gives a reason to open the app for ten minutes without a book open.
- Every study item shows the book sentence it came from and can jump back to it.
- Daily review works **offline and without an API key**.
- Words carry a level so the owner can aim study at a target.

Non-goals
- Listening, speaking and essay marking. Books cannot supply them.
- Official CEFR or IELTS band labels. Levels are frequency bands plus an academic flag (section 5).
- Whole-library analysis, cross-book recommendations, cloud sync, accounts.
- OCR for scanned PDFs; TXT learning; paused formats.

Anti-goals (would make it a failure even if it works)
- A band-score promise or any claim that the app replaces real test practice.
- Background network use. The network is still used only when the owner taps an AI action.
- Study data that can be lost: backup/restore (already approved, October 29 plan) must
  cover the new tables before the owner relies on them.

## 3. Approaches considered

A. **Offline core first, AI as an optional layer (chosen).** Review scheduling, cloze from
   the source sentence, word levels and in-book word suggestions all run on device from
   bundled word lists. AI only explains, tags grammar and writes practice questions.
   Trade-off: levels are frequency based rather than official; grammar still needs AI.
B. AI-first. Gemini assigns levels, picks words and generates everything. Less data to
   bundle, but each session costs tokens, needs a connection, gives unstable levels and
   contradicts the offline principle. Rejected.
C. Import a third-party IELTS deck. Fast, but the words are no longer tied to the owner's
   books, which is the whole point. Rejected.

The owner asked for a plan for the staged proposal, which is approach A.

## 4. Phases

Each phase is shippable on its own and is delivered in this order.

| Phase | What the owner gets | Needs network | Schema |
|---|---|---|---|
| 0 | Backup/restore and note export (existing approved plan) extended to learning tables | no | none |
| 1 | Learn tab with daily review of saved words; cloze built from the book sentence | no | v10→11 additive |
| 2 | Level on every word; filter and counts by level; target level in Settings | no | none |
| 3 | "Words worth learning in this chapter" while reading; add with one tap; mark as known | no | v11→12 |
| 4 | Grammar notebook: grammar points tagged from a fixed list, with book examples | AI | decided after trial |
| 5 | IELTS-style reading practice generated from the passage just read | AI | decided after trial |

Phases 4 and 5 are **not** approved by this document. Each needs a desktop trial like the
0.1.9 one (quality of tags and questions, token cost) and its own short spec.

## 5. Design decisions

**Tab.** `TopLevelTab.LEARN` between Notes and Settings. Sections: Today (due count and a
Start button), Words (search, filter by level and book), later Grammar and Practice. The
existing vocabulary dialog in Notes is replaced by a link to Learn → Words.

**Review (phase 1).** One row per word in a new `vocabulary_reviews` table: due date,
interval, ease, repetitions, lapses, last review. Scheduling is a simplified SM-2 with three
answers (Again, Good, Easy), implemented as a pure function in `:core:model` so it is fully
JVM-tested. New words are due immediately; at most 20 new words enter per day (builder's
call, a setting later if needed).

**Card.** Front: the source sentence with the term blanked, plus book title. Back: the term,
the saved meaning, the full sentence and "Open in book". If the term cannot be located in the
saved quote (inflected form, multi-word phrase), the card falls back to term → meaning. Blank
building is a pure function with tests for case, punctuation and repeated occurrences.

**Levels (phase 2).** Bundled plain-text lists, no Gradle dependency:
New General Service List (bands by rank: 1–1000, 1001–2000, 2001–2800) and New Academic
Word List (academic flag). Both are published under CC BY-SA 4.0; the builder must re-check
the licence text and bundle the attribution before adding the files, and stop if it differs.
Words outside the lists are "beyond list". Inflections are resolved with the lists' own
headword/inflection tables, not a new library. Level is computed when read, so no migration.
The UI says "frequency band" and "academic", never CEFR or IELTS band.

**Suggestions (phase 3).** For the open EPUB chapter (text PDF pages if the existing text
extraction can supply page text cheaply; otherwise EPUB only, stated in the UI), tokenise,
resolve to headwords, drop words already saved or marked known, keep the owner's target band
and academic words, and list them with their sentence. One tap saves a word with its sentence
and location and no meaning; the meaning can be filled by the existing AI card or typed.
This needs words that are not tied to an AI answer, so v11→12 makes `vocabulary.answer_id`
optional and adds `book_id`, `quote` and `locator` columns plus a `known_words` table.

**Languages.** All new strings in English and Vietnamese. Only English books produce
suggestions; a Vietnamese book shows an explanatory empty state.

## 6. Constraints

AGENTS.md rules apply: no new Gradle dependency, strict TDD, additive migrations with
preserved rows proven by tests, no titles in git, device verification for anything visual.
Database work follows the existing explicit-migration pattern (`LearningMigration.kt`).

## 7. Left to the builder

Exact SM-2 constants, card layout details, how the daily new-word cap is stored, tokeniser
rules for hyphens and apostrophes, and whether Words is a list or a grid.

## 8. Open questions for the owner

1. Current level and target band. It only sets the default target band in phase 2; it does
   not block phase 1.
2. Should phase 0 (backup) ship before phase 1, or together with it? Recommendation: before.
