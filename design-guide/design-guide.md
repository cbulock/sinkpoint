# Game family design guide

## Design direction

Aim for a calm, approachable puzzle game: soft rounded geometry, lightly tinted containers, strong readable headings, and a playfield that stays visually steady. Keep most screens quiet; reserve illustration and celebration for the launcher, small difficulty glyphs, results, and sharing.

The measurements and colors below come from Minescape's current source. Guidance for new games is a recommendation unless explicitly described as existing behavior.

## Color and themes

Minescape offers Device, Light, and Dark. Device follows system brightness. On API 31+, all three choices use Android's dynamic color scheme for their chosen brightness; the app has no fixed-brand-palette toggle. On API 26–30, the following fallback scheme applies.

Use semantic roles in components instead of literal violet, mint, or coral values. Wallpaper colors may change the hues while preserving the family structure.

| Explicit fallback role | Light | Dark |
| --- | --- | --- |
| primary | `#6554D9` | `#C1B5FF` |
| onPrimary | `#FFFFFF` | `#2C205F` |
| primaryContainer | `#EAE6FF` | `#433477` |
| secondary | `#176952` | `#B8E9D7` |
| secondaryContainer | `#B8E9D7` | `#264C40` |
| onSecondaryContainer | `#123E31` | `#B8E9D7` |
| tertiary | `#AB4235` | `#FF7969` |
| tertiaryContainer | `#FFDAD4` | `#60372F` |
| background / surface | `#F7F8FC` | `#181824` |
| onBackground / onSurface | `#282640` | `#F0EEFF` |
| surfaceContainer | `#FFFFFF` | `#232331` |
| surfaceVariant | `#E9EAF3` | `#333345` |
| onSurfaceVariant | `#616075` | `#C2BED5` |
| outlineVariant | `#DCDCE9` | `#464359` |

Light `onPrimaryContainer` is explicitly `#282640`. Roles not explicitly supplied in `Theme.kt`, including error roles and some on-container roles, come from Material 3 defaults. Copy the complete theme code to reproduce this behavior; this table is not a complete `ColorScheme`.

Use primary for main actions, resume/daily highlights, and personal bests; secondary for positive outcomes and the easiest difficulty; tertiary for a setback or harder difficulty; error containers for actual mistakes. Pair containers with their corresponding `on…Container` foregrounds in new components. Some existing home cards explicitly use `onSurface`, so review contrast if reproducing them under dynamic palettes.

The fixed artwork palette is violet `#6554D9`, mint `#B8E9D7`, coral `#FF7969`, ink `#282640`, and lavender `#DBD5F5` / `#EAE6FF`. Launcher art, confetti, and exported share cards can retain this palette independently of wallpaper colors.

Minesweeper numbers use fixed distinct colors even with dynamic theming. These are game semantics, not general UI accents:

| Number | Light | Dark |
| --- | --- | --- |
| 1 | `#6048C5` | `#B3A7FF` |
| 2 | `#167158` | `#82DDB4` |
| 3 | `#B5483C` | `#FFAA9B` |
| 4 | `#286EA6` | `#8DCBFF` |
| 5 | `#8F6012` | `#FFD67B` |
| 6 | `#1D7581` | `#88DBE4` |
| 7 | `#9C3E80` | `#F7AFE0` |
| 8 | `#282640` | `#DFD6EF` |

Define any new game's semantic colors separately, test both themes, and provide a numeral, symbol, or label in addition to color.

## Typography

The Compose theme uses Material 3's default typography and shapes; it does not install a custom font or custom type scale. Most rounding is supplied on individual components.

| Purpose | Existing style |
| --- | --- |
| Home title | `headlineMedium`, Bold |
| Full-screen settings/statistics title | `headlineSmall`, Bold |
| Result headline | `headlineSmall`, ExtraBold |
| Card/difficulty title | `titleMedium`, SemiBold |
| Section heading | `titleMedium`, Bold |
| Large metric | `titleLarge`, Bold or ExtraBold |
| Supporting explanation | `bodyMedium` |
| Metadata or counting details | `bodySmall`, usually onSurfaceVariant |
| Controls / badges | `labelMedium` or `labelLarge` |

Use `sp` through these text styles; do not shrink labels to fit or cap the user's font scale. Canvas-rendered board numerals are sized relative to tiles and use Android bold sans-serif, so they require separate zoom and accessibility handling.

## Layout measurements

| Pattern | Existing measurement |
| --- | --- |
| Home content maximum width | 560 dp, centered |
| Settings/statistics/daily maximum width | 640 dp, centered |
| Result maximum width | 440 dp |
| Home horizontal padding | 20 dp |
| Typical card content padding | 16 or 20 dp |
| Typical section gaps | 16 or 24 dp |
| Related content gaps | 4, 8, or 12 dp |
| Explicit minimum for many actions | 48 dp |
| Home difficulty row | Minimum 80 dp high |
| Main cards | 20 dp corner radius |
| Grouped presets / overview card | 24 dp corner radius |
| Result outer surface | 28 dp corner radius |
| Result stat tile | 18 dp corner radius |
| Compact actions | Often 14 dp corner radius |
| Board viewport | 12 dp corner radius, 8 dp padding |
| Home difficulty glyph | 44 dp, 14 dp corner radius |

Treat these as component-specific values rather than forcing every element to one radius. Favor surface color and subtle dividers over heavy shadows. Use safe drawing insets around full-screen content. Let text determine height beyond the minimums.

At font scale greater than 1.3, current screens stack metrics and choices and hide optional emblems. Use wrapping rows for chips, scrolling for short screens, and centered reading widths on tablets. Keep important actions reachable as content grows.

## Screen and component recipes

### Home

Place a resume/view-last-game card first when a game exists. Follow with a clear choice heading, current mode summary and Game options, and grouped one-tap difficulty rows. Rows contain a small game-specific glyph, title, practical detail, and trailing navigation cue, with subtle dividers. Custom options expand below the presets. Use a tinted daily card if the game has daily play, then Learn to play and Statistics actions.

Keep optional setup behind Game options so starting a standard game remains immediate. Persist selections for the next game and explain when a mode affects records.

### Gameplay

Keep the header compact: essential counters, time, and a tonal New game action. Let the playfield take remaining space. Group any mode, hint, undo, zoom, or fit controls near the field. On a finished board, replace live counters with a short outcome summary and View result.

For tile-based games, borrow the soft board language: tinted covered cells, quieter revealed cells, modest depth, clear state symbols, and a visible pending-selection outline. Minescape rounds tiles by 15% of cell size, uses an inset of 5.5% capped at 3 dp, and draws a small lower edge on covered cells. Adapt those ratios to the new game's playfield rather than copying mine symbols.

### Results

Use a rounded dialog with a tinted outcome panel, plain outcome title, short friendly sentence, compact mode badges, and useful metric tiles. Add progress or a personal-best highlight only when meaningful. Stack metrics with large text and scroll the detail area on short displays.

Current Minescape uses outlined Play again and retry actions within the scrolling details, a filled New game action below, View board at the top, and an official daily share action when eligible. Preserve that hierarchy when closely matching it; any different primary action should be a deliberate product decision.

### Settings and statistics

Settings is a full-screen surface with a fixed header and Done action. Group Appearance, game-specific Controls, and Backup and restore into rounded cards. Save changes immediately. Make toggle rows tappable with switch semantics and let chip choices wrap or stack.

Statistics starts with a tinted overview, then difficulty records and recent results. Use filters and explain empty states. Keep practice and official scoring distinctions visible. Minescape shows history ten items at a time; adapt pagination to the new game's data size.

### Artwork and sharing

Give each sibling a distinct, simple game emblem: rounded pieces, restrained bevels and shadows, readable at 48 px. Keep the family palette and violet launcher background; replace the Minesweeper flag/number with the new game's identifying objects. Include adaptive foreground/background resources and a matching monochrome vector for themed launchers. Follow the [icon reference](../launcher-icon.md) for Minescape's existing 20% foreground inset and small-size preview checks.

Share cards should use clear branding, localized date, outcome, and a few useful statistics. Use decorative game imagery without exposing daily solutions. Preview the image before opening Android's chooser. The existing renderer uses a fixed branded palette rather than the device theme.

## Feedback, motion, and words

Minescape celebrates a fresh win with 2.8 seconds of confetti and a loss with a 950 ms explosion, then opens results. These effects follow system animation scale, disappear with animations disabled, leave controls usable, and do not replay on restored completed games. Use similarly restrained, game-appropriate feedback; an explosion is specific to Minesweeper. Avoid shifting the board during feedback.

Offer optional haptics. Current Minescape uses a long-press haptic for marker changes; choose equally purposeful feedback for the new game's actions.

Use short, concrete labels: New game, Play again, View board, Done, Learn to play. Explain consequences near the action: "Practice does not affect records" and "Importing replaces current progress." Keep loss language gentle and factual. Localize user-facing strings in new apps; much of the existing UI uses inline English strings and should not be copied as a localization pattern.

## Accessibility and input

Aim for at least 48 dp targets for ordinary controls. Dense playfields need a deliberate zoom or alternate input strategy. Minescape makes the first touch on cells below 32 dp zoom only, requires a fresh gesture to play, and provides accessibility actions at any zoom. The 32 dp threshold is a board-specific safeguard, not the family-wide target size.

Use the same transform for drawing, hit testing, and accessibility positions. Describe cell coordinates and state, provide named actions, mark headings, and merge label/value semantics where useful. Decorative art should not create extra TalkBack stops.

Cancel a pending action when a gesture becomes scrolling/pinching, crosses targets, or when input mode, layout, viewport, or game identity changes. A hold must not also trigger a tap. Keep inspection available after a game finishes while disabling rule-changing moves. For a different game, define equivalent cancellation rules before writing gesture code.

For new components, check normal text contrast against a 4.5:1 target and large text against 3:1, including several dynamic palettes. These are recommended acceptance criteria, not a claim that every existing Minescape color combination has been audited.
