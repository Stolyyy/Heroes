# AGENTS.md

Guidance for AI coding agents working in this repository.

## Project Overview

**Heroes** is a Smash Heroes-style arena PvP minigame plugin for **PaperMC 1.21.11** (Java 21, Gradle 8.14.2 + Shadow 8.3.11).

- **Architecture & lifecycle invariants:** [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- **Coding rules (apply to every change):** [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)
- **Adding heroes / bots / models:** [docs/HEROES.md](docs/HEROES.md), [docs/BOTS.md](docs/BOTS.md), [docs/MODELS.md](docs/MODELS.md)

## Build & Verify Commands

Use `compileJava` as the fast verification gate. Only run `build` when producing a deployable jar — if `local.properties` sets `deployDir`, `build` writes straight into the configured server's `plugins/` folder (never overwrite a running server's jar).

```bash
# macOS / Linux
./gradlew compileJava
./gradlew build

# Windows (PowerShell — use gradlew.bat; ./gradlew can fail if JAVA_HOME is unset in bash)
.\gradlew.bat compileJava 2>&1
.\gradlew.bat build 2>&1
```

### Legacy Code

Old pre-rewrite code may sit in `.legacy/` (gitignored, outside `src/`, not compiled by Gradle). Do not import from it. `compileJava` should stay clean aside from known deprecation/unchecked notes.

## Quick Map

- **Entry point:** `me.stolyy.heroes.Heroes` (initializes managers and their domain listeners, registers cross-cutting listeners, commands via CommandMap, op permissions, optional LuckPerms context, lobby displays/NPCs).
- **Core packages:** `hero/` (+ `mechanics/`, `minion/`), `ability/` (+ `template/`, `kit/<hero>/`), `control/` (+ `ai/`, `strategy/`), `game/` (+ `map/`, `queue/`, `rules/`, `series/`, `settings/`), `physics/`, `progression/`, `vfx/` (+ `cosmetic/`, `effect/`, `glow/`, `ui/gui/`, `ui/prompt/`, `ui/visual/`), `npc/`, `discord/` (JDA bot, loaded via `HeroesPluginLoader`), `command/` (`admin/`, `general/`), `listener/`, `party/`, `util/`.
- **Key GUIs:** `HeroSelectGUI`, `HeroInfoGUI`, `SkinSelectGUI`, `CustomGameGUI`, `GameMapGUI`, `GameModeGUI`, `BotSettingsGUI`, `SpectateGUI`, plus `vfx/ui/gui/settings/` (`SettingsGUI` DSL for `GameSettingsGUI`, `PlayerSettingsGUI`, and mode settings GUIs).
- **Helper scripts:** `tools/matchlog.py` (match log summary), `tools/yml_keys.py` (config key & unit audit), `tools/patchnotes.py` (patch-note lines for hero/template YML changes between two git refs).

## Issue Tracking & Workflow

- Work items are GitHub Issues on `Stolyyy/Heroes`. Milestones are patches (current: `4.0.1b`); hotfix builds carry the `hotfix` label.
- Labels: type (`bug`, `feature`, `balance`, `hero`), area (`area:*`), size (`size:S`, `size:M`, `size:L`), status (`needs-spec`, `ready`, `needs-playtest`, `needs-assets`), and owner (`agent:gemini`, `agent:claude`). Status lives in labels; if a project board is in use, keep its column in sync.
- Only pick up issues labelled `ready`. `needs-spec` means a decision is still owed: ask, do not guess.
- One issue per branch (`<type>/<issue#>-<slug>`, cut from `refactor/codebase-review` until that branch merges to `master`) and one PR per issue, using the PR template. CI builds the jar on every PR.
- While the combined playtest branch `playtest/4.0.1b` (PR #74) is open, 4.0.1b issue branches are cut from `origin/playtest/4.0.1b` and their PRs target `playtest/4.0.1b`, not `refactor/codebase-review`. It already contains PRs #52-#70.
- `"triage"`: read unlabelled issues, clarify ambiguities, label (type / area / size / status) each one, comment a one-paragraph plan.
- `"start <issue>"`: implement it, comment with files changed and every new/changed YML key, then swap `ready` for `needs-playtest`.
- If an issue resists two attempts, comment what was tried and what failed, and relabel it `agent:claude`.
- Do not append tasks to markdown files; file a GitHub issue. Private scratch/audit notes live in gitignored `notes/`.

## Working Preferences

- **Loud errors:** Do not add defensive null guards, swallowing `try/catch` blocks, or silent Java config fallbacks unless explicitly requested or listed as an allowed exception in [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).
- **Deliberate game design:** Never remove or "fix" the 0 HP alive buffer (`Interactions.ZERO_HP_BUFFER`).
- **Live configs:** `saveResource(false)` never overwrites existing files in `plugins/Heroes/`. Any new or changed YML key must go into `src/main/resources/` and be listed clearly in your summary so it can be mirrored to live servers.
- **Git:** Commit and push as you work; no need to ask first. Issue work goes on its own branch with a PR (see Issue Tracking & Workflow). Do not merge PRs or push to `master`: Will merges after playtesting.
