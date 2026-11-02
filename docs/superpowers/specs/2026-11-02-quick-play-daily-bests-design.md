# Quick Play, Daily Fleet Hunt, and personal bests

Status: proposed design, pending user approval. No product implementation yet.

## Intent and agreed scope

Add variety to Sinkpoint while retaining its familiar select-cell-and-Fire interaction.
The user selected Quick Play presets, an offline daily fleet hunt, and personal
bests. They explicitly chose one recorded daily attempt, with later replays
treated as practice. All other specific defaults below are builder proposals.

Keep the native Kotlin/Compose app, rounded Material surfaces, accessibility,
automatic saving, and existing solo and nearby modes. No accounts, server,
leaderboard, notifications, currencies, or new combat abilities are needed.

## Approaches

- Presets reuse existing game configurations instead of introducing new combat
  rules. This makes existing variety easier to access; it does not add a new AI.
- Daily scoring could retain the best replay or record the first attempt.
  Best-replay scoring permits relaxed optimization but rewards remembering the
  fleet. The user selected the first attempt, followed by unscored practice.
- A daily hunt has its own saved state and results rather than occupying the solo
  save. This adds persistence work but allows a regular match and a hunt to coexist.
  This is the recommended implementation direction, not a separately confirmed
  user preference.

## Quick Play

Add three named presets to the solo setup chooser, with a concise rules summary:

| Preset | Board | Ship lengths | Turns |
| --- | --- | --- | --- |
| Skirmish | 6 x 6 | 3, 2, 2 | Classic, fire again on hit |
| Classic | 10 x 10 | 5, 4, 3, 3, 2 | Classic, fire again on hit |
| Salvo | 10 x 10 | 5, 4, 3, 3, 2 | Existing salvo rules |

All three allow touching ships. The chooser retains Easy, Normal, and Hard and
provides an explicit start action so selecting a preset cannot replace a save.
Offer Custom using the existing Game options configuration. Start with Classic
selected on first use, then remember the last setup choice and difficulty.
Keep custom configuration separate so choosing a preset does not overwrite it.
Existing unfinished-game replacement confirmation still applies.

Keep Solo and Multiplayer prominent. Put the daily entry immediately below them,
showing today's date and Start, Continue, or the recorded score plus Practice.
Presets initially apply to solo setup; nearby hosting continues to use Game options.

## Daily Fleet Hunt

- Fixed 8 x 8 board, ships of lengths 4, 3, and 2, touching allowed.
- No player fleet, placement step, AI opponent, difficulty, or turn switching.
  Select one target and press Fire. Hits, misses, sinking, and remaining ships
  behave like ordinary play. Finish by sinking the entire fleet; there is no
  shot limit or timer.
- Generate the same fleet for a given ISO calendar date and daily generation
  version on every installation. Use a fixed deterministic algorithm with
  golden fixtures, independent of AI generation changes and device identity.
  New daily versions must be explicit; saved hunts retain their original fleet.
- Use the device's local calendar date. A date denotes the same puzzle across
  time zones, although local midnight occurs at different moments.
- Reserve the scored attempt durably when starting it, before showing the board.
  Back, app restart, and interruptions resume that attempt. Failed writes must
  not expose a new fleet or lose progress.
- First completion records shots and accuracy exactly once. Restarting or
  abandoning a started hunt forfeits that date's score and offers practice;
  confirm this consequence. A completed score cannot be replaced by practice.
- Practice uses the same fleet and is clearly labeled throughout play and results.
  It changes no records or daily statistics. Save practice progress separately
  from the immutable recorded result, within the daily save area.
- If an unfinished hunt crosses midnight, allow finishing it for its original
  date. Starting today's hunt instead requires confirmation that the previous
  unfinished attempt is abandoned. Only one active hunt is retained at a time.
- Recheck the local date on home entry and foreground resume. A running hunt
  never silently changes its date or fleet.
- Offer today's hunt and any active older hunt, not a puzzle archive. Retain a
  monotonic last-reserved scored date to prevent a clock rollback or trimmed
  history from opening a second scored attempt. Earlier dates can only be practice.
  Offline clock tampering, reinstalling, and restoring old backups are not
  anti-cheat targets.

Use the existing board drawing, hit feedback, haptics, zoom, and accessible cell
actions where appropriate. The hunt screen has only the target board, fleet
status, shot count, and Fire controls. Reveal the fleet only after completion or
confirmed abandonment. No hidden fleet enters the active UI state projection.

## Personal bests

For normal solo and nearby matches, persist:

- Fewest shots in a victory achieved by sinking the full opposing fleet.
- Highest accuracy in such a victory, comparing exact hit/shot fractions.
- Current and longest winning streak across completed games in the same category.
  Resignations count as wins/losses for streaks, matching current result behavior,
  but cannot set shot or accuracy records. Unfinished replaced games do not count.

Categories use mode, AI difficulty when applicable, and full gameplay rules:
board size, sorted fleet lengths, touching policy, turn rule, and repeat-hit
policy where relevant. A preset label is presentation, not a separate category;
an equivalent custom setup shares its records. Salvo ignores the inactive
repeat-hit preference when comparing categories. Shots count cells fired upon,
not volleys. Results in other categories neither advance nor break a streak.

Show a concise New personal best message for a strict improvement after a game;
ties retain the original record. The first qualifying result establishes a best.
Statistics shows records with readable rule/difficulty labels. Maintain lifetime
best aggregates independently of the capped recent-match list.

Daily statistics are separate: completed scored hunts, fewest shots, best
accuracy, and latest scored results. All dates share the fixed daily rules but
vary in fleet placement; these are personal records, not difficulty-normalized
rankings. No daily attendance streak or missed-day penalty. Practice and
abandoned attempts cannot set daily bests or increment completion counts.

## Persistence and compatibility

The existing repository stores independent solo and nearby saves, aggregate
statistics, and up to 100 recent results. Old result entries lack exact custom
rules and resignation information, so do not infer new bests from them.
Preserve their existing statistics and history. New records start from games
completed after this feature is installed, including an existing in-progress
game that subsequently finishes. Already recorded finished saves must not be
counted again. Label new records as tracked from this update where useful.

Extend validated persistence with optional/defaulted fields and explicit
migration where necessary. Retain at most 100 recent daily results; keep the
scored-date guard and lifetime aggregates beyond that cap. Store the daily
generation version and daily rules with every active hunt/result.

Update export/import to include daily progress, its scored-date guard, new setup
preferences, and personal bests. Import preserves local nearby progress and
identity as it does today. Avoid duplicate result updates on resume, retry, or
import. Importing a legacy backup without the new fields follows the existing
replacement semantics and initializes those fields empty; show that new progress
and records will also be replaced in the import confirmation. Existing safety
checks, size limits, and unreadable-save preservation still apply.

## Validation and implementation freedom

Required checks cover preset rules and custom-preference preservation;
deterministic daily fixtures; reservation, resume, practice, abandonment and
midnight/clock rollback behavior; once-only recording and failed writes;
category normalization, fractions, ties, resignation eligibility and streaks;
legacy saves and backup round trips. Exercise the actual setup, daily gameplay,
results, and statistics flows through UI/ViewModel tests. Run unit tests, lint,
and debug assembly, and inspect phone/tablet and large-text layouts.

The builder chooses internal model names, composable decomposition, date/clock
injection, and data migration mechanics. Keep hunt-specific turn/state behavior
separate from nearby exchange semantics; reuse the existing placement and shot
resolution logic only where it preserves the daily generation contract.

No publishing or deployment is part of this work.
