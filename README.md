# Heroes

A Smash Heroes–style arena PvP minigame for PaperMC. Players pick a hero with its own primary, secondary, ultimate and passive abilities, then knock each other off the map in fast matches.

Not affiliated with Hypixel or Mojang.

## Status

Open beta, version `4.0.1b`. Expect rough edges; bugs and balance feedback go in [Issues](../../issues).

## Features

- **20 heroes**, each defined by a YML file in `src/main/resources/heroes/` plus ability classes built on shared templates (projectile, hitscan, cone, dash, shockwave).
- **Queues and custom games**: 1v1, 2v2, and custom lobbies with per-team and per-player settings.
- **Game mode presets**: Boss, Team Deathmatch, Evolution, Series, Nuzlocke and Ironman.
- **Bots** with per-hero AI tuning, usable in custom games and `/practice`.
- **Progression**: per-hero levels, Elo, stats and leaderboards.
- **Parties**, spectating, map-skip votes and an admin map editor.

## Requirements

| | |
|---|---|
| Server | Paper 1.21.11 |
| Java | 21 |
| Optional plugins | [Citizens](https://citizensnpcs.co/) (lobby NPCs, bots, practice dummies), [LuckPerms](https://luckperms.net/) |

Maps are not bundled. Each map is a YML in the plugin's `maps/` folder that points at a template world on the server; see `src/main/resources/maps/example-map.yml`.

## Building

```bash
./gradlew build        # Linux / macOS
gradlew.bat build      # Windows
```

Gradle downloads JDK 21 if it is missing. The plugin jar lands in `build/libs/`.

To drop the jar straight into a test server instead, create a `local.properties` in the repo root (it is gitignored):

```properties
deployDir=C:\\path\\to\\server\\plugins
```

Restart the server after every build. Replacing the jar under a running server causes `NoClassDefFoundError`.

## Commands

| Command | What it does |
|---|---|
| `/join`, `/queue`, `/play` | Queue for a mode or join a preset |
| `/hero` | Pick a hero |
| `/party`, `/pc` | Party management and party chat |
| `/spectate`, `/autospectate` | Watch games |
| `/practice` | Solo practice arena |
| `/stats`, `/leaderboard` | Stats and rankings |
| `/lobby`, `/stuck` | Return to the lobby, or unstick yourself |
| `/skipmap` | Vote to skip the current map |

Admin commands: `/mapadmin`, `/mappool`, `/balance`, `/forceready`, `/forceend`, `/unlock`, `/token`, `/restart`.

## Repository layout

```
src/main/java/me/stolyy/heroes/
├── hero/         Hero core, stats, mechanics, minions
├── ability/      Ability templates and per-hero kits (ability/kit/<hero>/)
├── control/      Player and bot controllers, bot AI and strategies
├── game/         Match lifecycle, queues, maps, mode rules, settings
├── physics/      Hitboxes, damage and knockback, stun, homing
├── progression/  Stats, Elo, hero levels
├── vfx/          Cosmetics, sound and particle effects, GUIs, scoreboards
├── npc/          Citizens integration behind a service interface
├── command/      Admin and player commands
└── listener/     Bukkit event handlers
src/main/resources/   Default configs: hero YMLs, game modes, messages, bots
resourcepack/         Item model selector JSONs
tools/                Helper scripts for config and match-log analysis
docs/                 Development rules and deployment notes
```

Coding rules are in [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).
