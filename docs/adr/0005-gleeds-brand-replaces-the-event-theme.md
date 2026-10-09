# 0005: The Gleeds brand replaces the event theme

Status: accepted (2026-10-09, PR #45)

## Context

Spec story 50 asked for the event's theme: royal purple and gold with Saudi green accents. The design system's first cut (`docs/design-system.md`) kept that palette and took only type, restraint and contrast rules from the Gleeds brand guidelines (July 2024). The palette now follows the guidelines as well, and the brandmark is included.

## Decision

- **Palette:** the guidelines' own colours only. Obsidian `#3C3C3C` is the page and marble `#F5F5F5` the text. Gleeds yellow `#F7C400` is the one accent, used sparingly: primary action, key data, links and focus. Copper, patina and titanium are the secondaries, with their 50% tints for text. Patina means success and copper means danger.
- **Answer options:** Gleeds yellow and the three secondaries, each with its shape as before. The guidelines' "one secondary colour per context" gives way here: four colours and shapes are the game. Copper takes black text, because marble on copper is 3.8:1 and #11 requires 4.5:1.
- **Brandmark:** its vector paths are copied unaltered from the guidelines PDF into `components/Brandmark.tsx`. It renders in white, top left, at least 100 px wide, with clear space equal to its height. It appears on the join forms, the admin login and both Host screens.

## Considered options

- **Keep purple, gold and green, and add only the brandmark.** Not chosen: the brief is now the Gleeds palette as a whole.
- **Marble (light) pages.** Not chosen: the big screen sits in a lit room and the phones are used at a stand. Obsidian keeps one dark look, and the guidelines allow it as a background.
