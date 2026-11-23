# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **Smash Heroes Minecraft Plugin** for PaperMC - a fast-paced arena PvP minigame where players select unique heroes with distinct abilities. Built with Java 21 and PaperMC 1.21+ API.

## Build Commands

Use PowerShell to invoke the Gradle wrapper:

```bash
# Compile only (fastest way to check for errors)
powershell.exe -Command "Set-Location 'C:\Users\willi\IdeaProjects\Heroes'; .\gradlew.bat compileJava 2>&1"

# Full build
powershell.exe -Command "Set-Location 'C:\Users\willi\IdeaProjects\Heroes'; .\gradlew.bat build 2>&1"

# Clean and rebuild
powershell.exe -Command "Set-Location 'C:\Users\willi\IdeaProjects\Heroes'; .\gradlew.bat clean build 2>&1"
```

Do NOT use `./gradlew` - it will fail with "JAVA_HOME is not set".

The JAR outputs directly to `C:\Users\willi\OneDrive\Desktop\Heroes Server\plugins` for testing.

### Legacy code

Old implementation lives in `.legacy/` at the repo root — it is **outside** `src/` and is **not compiled** by Gradle, so it produces no build errors. Do not import from it. `compileJava` should be fully clean (aside from deprecation/unchecked notes).

## Architecture

### Core Systems

**Plugin Entry Point**: `Heroes.java` - Initializes maps, heroes, registers 9 listeners and the commands via reflection-based CommandMap.

**Game Loop**: `game/` package
- `Game` - Individual match instance with teams, state, and lifecycle
- `GameManager` - Creates/finds games, handles matchmaking
- `GameState` enum: WAITING → STARTING → IN_PROGRESS → ENDED
- `GameMode` enum: ONE_V_ONE, TWO_V_TWO, CUSTOM

**Character System**: `hero/` package
- `Hero` - Main character class with event bus for abilities/mechanics
- `HeroBuilder` - Constructs heroes from configuration
- `HeroManager` - Registry of hero instances per player
- `HeroStats` record - Immutable stat definitions (the live stat block)

### Hero Architecture

Heroes use a **component-based design**. Mechanics live in `hero/mechanics/`:
- `Attributes` - Weight, movement speed, type (MELEE/RANGED/HYBRID)
- `DoubleJump` - Jump count and velocity
- `Jab` - Melee attack damage and knockback
- `Energy` - Resource system for abilities

`CooldownManager`, `Cooldown`, and `Ammo` live in the `ability/` package.

**Event Bus Pattern**: Heroes register listeners for game events:
```java
hero.listenOnJabHit(victim -> ...);
hero.listenOnAbilityHit((ability, victim) -> ...);
hero.listenOnTick(h -> ...);
```

### Ability System

`ability/` package

**Interface**: `Ability` defines the contract - `execute()`, `canExecute()`, lifecycle hooks (`onHit`, `onLand`, `tick`)

**Templates** in `ability/template/`:
- `BaseAbility` - Root abstract (config parsing, cooldown/energy/ammo lifecycle)
- `RangedAbility` - Shared base for `Hitscan` / `Projectile` (burst, dropoff, AoE)
- `Projectile` - Moving projectile entities
- `Hitscan` - Raycast instant-hit attacks
- `Cone` - Area-of-effect abilities
- `Dash` - Movement abilities with collision
- `Shockwave` - Ground impact abilities
- `Tether` - Passive minion leash; `Aoe` / `Ammo` - reusable helpers

**Kits** in `ability/kit/{hero}/`:
Each hero has a subfolder with its ability classes extending templates.

### Controller Abstraction

`control/` package separates input handling:
- `Controller` interface - Abstract entity control
- `PlayerController` - Wraps player input/display
- `BotController` - AI with `Strategy` pattern (`control/strategy/`: Aggressive, Defensive, MidRange)

### GUI System

`vfx/ui/gui/` package - Abstract `GUI` base class with `GUIManager` registry.
Key GUIs: `HeroSelectGUI`, `GameMapGUI`, `GameSettingsGUI`, `TeamSettingsGUI`

### Map System

`game/map/` package
- `GameMap` record - Map data (locations, bounds, spawns)
- `GameMapManager` - Loads maps from config, manages world instances

## Key Patterns

**Ultimate Charging**: Melee heroes gain charge from jabs, ranged from ability hits, hybrid from both. Logic in `Hero.registerDefaultPassives()`.

**Command Registration**: Uses reflection to access Bukkit's internal CommandMap for dynamic command registration without plugin.yml entries.

**Lobby Location**: Fixed at (0.5, 0, 500.5) in world "world". `Heroes.teleportToLobby()` handles player reset.

## Package Structure

```
me.stolyy.heroes/
├── Heroes.java          # Plugin entry point
├── hero/                # Hero core: Hero, HeroBuilder, HeroManager, HeroConfig, HeroEventBus, HeroStats, HeroType
│   ├── mechanics/       # Attributes, Energy, Jab, DoubleJump
│   └── minion/          # MinionHero, MinionManager, MinionBotAI
├── ability/             # Ability, AbilityType, AbilityRegistry, Cooldown, CooldownManager, Ammo
│   ├── template/        # BaseAbility, RangedAbility, Hitscan, Projectile, Cone, Dash, Shockwave, Tether, Aoe
│   └── kit/{hero}/      # Per-hero ability implementations
├── control/             # Controller, PlayerController, BotController
│   ├── ai/              # BotAI, BaseBotAI
│   └── strategy/        # Strategy + Aggressive/Defensive/MidRange/Switch
├── game/                # Game, GameManager, Team, TeamColor, GameMode, GameState, GameEffects, SmashCrystal, GameBot
│   ├── map/             # GameMap, GameMapManager
│   ├── queue/           # QueueEntry, QueueManager
│   └── settings/        # GameSettings, PlayerSettings
├── physics/             # Hitbox, Interactions, Stun, Homing, WallDetection, VectorUtil
├── progression/         # PlayerStats(+Manager), ModeStats, HeroProgress, AbilityStats, EloCalculator
├── vfx/                 # Player-facing presentation
│   ├── cosmetic/        # HeroSkin, CosmeticManager
│   ├── effect/          # SoundEffect, VisualEffect, *Builder
│   └── ui/              # gui/ (menus) + visual/ (scoreboards, titles)
├── npc/                 # NPC service abstraction + types
├── command/             # admin/ and general/ command handlers
├── listener/            # Bukkit event handlers
├── party/               # Party system
└── util/                # GameConfigManager, BotNameManager
```

Old code lives in `.legacy/` at the repo root (outside `src/`, not compiled).

## How work is tracked

Work items are GitHub Issues on `Stolyyy/Heroes`. Milestones are patches (`4.0.1b`, `4.0.2b`); hotfix builds carry the `hotfix` label. Board columns: Inbox, Ready, In progress, Needs playtest, Done.

- "triage": read the Inbox, ask what is unclear, label (type / priority / area) and size each issue, comment a one-paragraph plan.
- "start <issue>": implement it, comment with the files changed and every new YML key, move it to Needs playtest. The user closes it after playing.
- Patch notes are generated from the closed issues in the milestone.
- Do not append tasks to markdown files; file an issue. `planning/` holds only the current patch plans and the release plan.

Coding rules live in `docs/DEVELOPMENT.md` and apply to every change.

## Working Preferences

**Error handling**: Do not add defensive error handling, null guards, or try/catch blocks unless explicitly asked. Prefer letting errors surface and be reported so they can be diagnosed and fixed at the source. Silent failure and fallback logic hide bugs and cause hard-to-trace issues.

**Git**: Git is set up but not actively used — this is a solo project and the repo has not been updated in over a year. Do not suggest commits, branches, PRs, or any git workflow steps unless explicitly asked.

## Current State

The plugin uses the current domain-oriented package layout (see Package Structure). Old code lives in `.legacy/` at the repo root, outside `src/` and not compiled. New hero implementations follow the current architecture.

See `planning/ROADMAP.md` for the path-to-completion roadmap and current priorities.
