# Heroes

A fast-paced arena PvP minigame plugin for **PaperMC**, inspired by Hypixel's *Smash Heroes*. Pick from a roster of unique heroes with custom abilities, knock opponents off the stage, and play ranked queues, custom modes, or practice matches with bots.

> **Disclaimer:** This is an independent fan-made open-source project and is not affiliated with or endorsed by Hypixel.

---

## Features

- **18 playable heroes** (+ 2 admin-only heroes in development) — each with primary, secondary, ultimate (smash), and passive abilities driven by YAML configs and reusable Java templates.
- **Smash-style combat** — damage increases knockback taken, and players survive at 0 HP until knocked out of the arena or hit again in the 0 HP buffer.
- **Multiple game modes:**
  - Ranked **1v1** and **2v2** queues with Elo ratings, leaderboards, AFK guards, and `/autoqueue`.
  - **Custom games** with parties, bots, per-player settings, and mode presets (**Classic**, **Boss**, **TDM**, **Evolution**, **Series**, **Nuzlocke**, **Ironman**).
  - **`/practice`** arena with knockback target dummies for testing kits and balance changes.
- **Bot AI** — Citizens-backed bots with recovery, evasion, spacing strategies, configurable movement toolkits, and 5 difficulty tiers.
- **Progression & cosmetics** — global per-hero XP/levels, Smash Level, unlock tokens, master skins, and custom model data support.
- **Live balance tuning** — `/balance <hero> <path> <value>` edits hero YMLs with comments preserved and hot-reloads archetypes.

---

## Requirements

- **Server:** PaperMC **1.21.11** (API 1.21.10+)
- **Java:** **Java 21** (toolchain enforced by Gradle)
- **Optional plugins:**
  - **Citizens** — required for lobby NPCs, game bots, minions, and `/practice` target dummies. Without it, the plugin runs cleanly with NPC features disabled (`NoOpNpcService`).
  - **LuckPerms** — registers the `heroes:template-world=true` context calculator so map-builder permissions can be scoped to template worlds.
  - **PacketEvents** — enables Citizens' `RotationTrait` packet hook so bots smoothly face targets while kiting.

---

## Quick Start

1. Build the shaded jar (see [Build from Source](#build-from-source)) or grab a release artifact.
2. Drop `Heroes-<version>.jar` into your Paper server's `plugins/` folder (do **not** use the `-plain.jar` — it lacks the shaded `GlowingEntities` library).
3. Start the server once to extract default configs into `plugins/Heroes/`.
4. Configure your lobby & maps:
   - **`plugins/Heroes/lobby.yml`** — set `lobby.spawn` to a valid world/coordinates on your server (defaults to `world` at `0.5, 0.0, 500.5`).
   - **Maps** — map world templates are not bundled in this repository. Create a starter map and template world in-game with `/ma create <name>`, configure spawns/bounds with `/ma set`, save with `/ma save`, and add it to queues with `/mappool <mode> add <name>`.
   - **Resource pack** — optional for local development (items render as fallback vanilla materials), required in production for custom hero models and sounds. Item-model selector definitions live in [resourcepack/](resourcepack/).

---

## Commands & Permissions

### Player Commands

| Command | Aliases | Description |
|---|---|---|
| `/setcharacter [hero]` | `/hero`, `/sethero` | Open hero select GUI or pick a hero by name |
| `/join <mode> [map]` | `/queue`, `/q`, `/play` | Join `1v1`, `2v2`, `custom`, or a preset lobby |
| `/autoqueue <1v1\|2v2\|stop>` | `/aq` | Automatically requeue after ranked games |
| `/party <subcommand>` | `/p` | Create/manage parties and ready checks |
| `/partychat <msg>` | `/pc` | Send a party chat message |
| `/spectate [player]` | `/spec` | Open spectate GUI or spectate a player's match |
| `/autospectate <player\|off>` | `/autospec`, `/aspec`, `/as` | Automatically spectate a player whenever their match starts |
| `/leave` | `/lobby`, `/hub`, `/l`, `/gg` | Return to the lobby |
| `/practice [arena]` | — | Enter/leave the practice range |
| `/stats [player]` | — | View player and hero stats |
| `/leaderboard` | `/lb`, `/elo` | View rankings |
| `/charms` | `/charm`, `/bugcharms` | Open Bug's charm loadout menu |
| `/skipmap` | — | Start or vote in a custom-game map skip poll |
| `/stuck` | `/s` | Unstick yourself inside an arena |
| `/heroesversion` | `/hver` | Show the running Heroes build version |

### Admin Commands (default: OP)

| Command | Aliases | Permission | Description |
|---|---|---|---|
| `/balance <hero\|reload> ...` | `/bal`, `/herobalance` | `heroes.balance` | Inspect/edit hero YML keys live and reload archetypes |
| `/mapadmin <subcommand>` | `/ma`, `/maps`, `/editmap` | `heroes.mapadmin`, `heroes.mapadmin.create`, `heroes.mapadmin.map.<name>` | Create, edit, configure, and save map templates |
| `/mappool <mode> <add\|remove>` | `/mp`, `/map_pool` | `heroes.mappool` | Manage which maps rotate in each mode |
| `/forceready` | `/fr`, `/ready` | `heroes.forceready` | Reset ability cooldowns / charge ultimate |
| `/forceend` | `/fe`, `/endgame` | `heroes.forceend` | Cancel an ongoing match without recording stats/Elo |
| `/token <give\|set\|check>` | `/tokens` | `heroes.token` | Manage hero unlock tokens |
| `/unlock <player> <hero>` | `/unlockhero` | `heroes.unlock` | Unlock a hero for a player |
| `/heroesrestart <now\|drain>` | `/hrestart` | `heroes.restart` | Gracefully drain ranked games and restart the server |

---

## Build from Source

```bash
# macOS / Linux
./gradlew compileJava   # fast compile check
./gradlew build         # builds shaded jar at build/libs/Heroes-<version>.jar

# Windows (PowerShell)
.\gradlew.bat compileJava
.\gradlew.bat build
```

To auto-copy the shaded jar directly into a local test server's `plugins/` folder on `build`, copy [local.properties.example](local.properties.example) to `local.properties` (gitignored) and set `deployDir`, or pass `-PdeployDir=/path/to/server/plugins`.

> **Never hot-swap the jar on a running Paper server.** Stop the server before replacing the jar to avoid `NoClassDefFoundError`.

---

## Project Layout

```text
src/main/java/me/stolyy/heroes/
├── Heroes.java        # Plugin entry point, command/listener/permission wiring
├── hero/              # Hero core (Hero, HeroBuilder, HeroConfig, HeroManager, HeroEventBus, HeroStats)
│   ├── mechanics/     # Attributes, Energy, Jab, DoubleJump
│   └── minion/        # MinionHero, MinionManager
├── ability/           # Ability contract, AbilityRegistry, Cooldown, CooldownManager, Ammo
│   ├── template/      # BaseAbility, RangedAbility, Hitscan, Projectile, Cone, Dash, Shockwave, Tether, Aoe
│   └── kit/<hero>/    # Per-hero ability implementations
├── control/           # Controller abstraction (PlayerController, BotController, BotLoadout)
│   ├── ai/            # HeroBotAI, BotNavigation, BotCombat, BotRecovery, BotEvade, MovementActions, MinionBotAI
│   └── strategy/      # Strategy, ConfigStrategy, Aggressive, Defensive, MidRange, DashSafety, JumpSafety
├── game/              # Match loop (Game, GameManager, Team, GamePlayer, PracticeManager, MatchLog, ...)
│   ├── map/           # GameMap, GameMapManager, MapPool, StageSampler
│   ├── queue/         # QueueManager, AutoQueue, AfkTracker
│   ├── rules/         # GameModeConfig, GameModePreset, WinCondition, OnKillAction, BossSetup
│   ├── series/        # MatchSeries, SeriesPlayerState
│   └── settings/      # GameSettings, PlayerSettings
├── physics/           # Hitbox, Interactions, Stun, Homing, WallDetection, VectorUtil
├── progression/       # PlayerStats, PlayerStatsManager, ModeStats, HeroProgress, HeroLevel, EloCalculator
├── vfx/               # Presentation layer
│   ├── cosmetic/      # CosmeticManager, HeroSkin, MinionBodyDisplay
│   ├── effect/        # VisualEffect, SoundEffect, ParticleBuilder, *DisplayBuilder, ItemBuilder
│   ├── glow/          # TeamGlow, GlowServiceProvider (GlowingEntities wrapper)
│   └── ui/            # gui/ (menus), prompt/ (chat input), visual/ (scoreboards, leaderboards, tab)
├── npc/               # NpcService abstraction, CitizensNpcService, lobby & bot NPC types
├── command/           # admin/ and general/ command handlers
├── listener/          # Bukkit event handlers
├── party/             # Party, PartyManager, PartyReadyCheck
└── util/              # Configs, GameConfigManager, TemplateConfig, YmlSurgeon, ErrorReporter, ...
```

---

## Documentation

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — system overview, lifecycle contracts, and invariants
- [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) — coding rules, config conventions, and pre-PR checklist
- [docs/HEROES.md](docs/HEROES.md) — step-by-step guide to adding a new hero
- [docs/BOTS.md](docs/BOTS.md) — bot AI, strategies, and per-hero bot configuration
- [docs/MODELS.md](docs/MODELS.md) — custom model data key manifest for resource pack work

---

## License & Credits

Licensed under the [MIT License](LICENSE).

- **GlowingEntities** (SkytAsul) — shaded and relocated into `me.stolyy.heroes.libs.skytasul` for per-viewer teammate outlines.
- Integrates optionally with **Citizens**, **LuckPerms**, and **PacketEvents**.
