# Architecture Overview

This document describes how the Heroes plugin is structured and the core invariants every contributor should understand before modifying game, hero, or ability code. Coding rules live in [docs/DEVELOPMENT.md](DEVELOPMENT.md).

---

## Core Systems

### Entry Point (`Heroes.java`)
`Heroes` boots configs, maps, stats, hero archetypes, registries, managers (each registering its own domain listeners via `initialize()`), cross-cutting listeners (`listener/`), `HeroesCommand` registrations, optional LuckPerms context (`game/map/TemplateWorldContext`), teammate glow (`TeamGlow`), auto-spectate, NPCs, practice mode, and self-registered `BoardDisplay` lobby leaderboards. On shutdown (`onDisable`), it tears down GUIs, games, heroes, minions, practice, queues, listeners, NPCs, and displays before flushing `PlayerStatsManager` and cancelling remaining tasks last.

### Hero System (`hero/`)
Heroes use a component-based composition around `Hero`:
- **`Hero`** — owns the player/bot `Controller`, ability slots (`primary`, `secondary`, `smash`, `passives`), `HeroEventBus`, and mechanics (`Attributes`, `DoubleJump`, `Jab`, `Energy`, `CooldownManager`), alongside `AbilityListener` (`hero/`) and `MovementListener`/`JabListener` (`hero/mechanics/`).
- **`HeroBuilder`** — builds `Hero` instances from `HeroConfig` (`heroes/<id>.yml` merged over `heroes/default.yml`) and validates every hero on boot (`validateAll()`).
- **`HeroConfig`** — loads/reloads hero YAML archetypes and owns `/balance` edits via `YmlSurgeon`.
- **`HeroManager`** — active `UUID -> Hero` registry, hero selection, unlock checks, and Bug charm state.
- **`MinionHero` / `MinionManager`** (`hero/minion/`) — NPC-backed summoned units that reuse the `Hero` model with their own AI (`control/ai/MinionBotAI`) and owner-attributed kill/damage credit.

### Ability System (`ability/`)
- **`Ability` interface & `BaseAbility`** — defines execution, cooldown/energy/ammo gating, windup, and lifecycle hooks (`perform`, `onTick`, `onHit`, `onLand`, `onDoubleJump`, `onDeath`, `onOwnerLost`).
- **Templates** (`ability/template/`):
  - `BaseAbility` — root abstract class (config/skin overlay, cooldown, energy, ammo, windup)
  - `RangedAbility` — shared burst, dropoff, and optional `Aoe` base for `Hitscan` and `Projectile`
  - `Projectile` — tick-stepped projectile instances (`isIndependent() = true`) with swept hitboxes and display entities
  - `Hitscan` — instant raycast cylinder hits with trails
  - `Cone` — frustum cone hitbox with optional linger duration
  - `Dash` — velocity movement with collision tracking
  - `Shockwave` — expanding ground-impact wave
  - `Tether` — minion leash to owner (paused while owner is respawning/eliminated)
  - `Aoe` — reusable explosion helper
- **Kits** (`ability/kit/<hero>/`) — hero-specific subclasses registered by lowercase key in `AbilityRegistry.registerAll()`.

### Controller Abstraction (`control/`)
Everything a hero does to its body goes through `Controller` (`PlayerController` for humans, `BotController` for Citizens NPCs) so abilities, mechanics, and physics behave uniformly across players, bots, and minions. See [docs/BOTS.md](BOTS.md) for the AI and strategy layers.

### Game Loop (`game/`)
- **`Game`** — match state machine (`WAITING -> STARTING -> IN_PROGRESS -> ENDED`), rosters (`Team`, `GamePlayer`), 1-tick `gameTick()` loop (bounds enforcement, respawn countdowns, hero & minion ticks, smash crystals), death/respawn flow, stats/XP/Elo awards, and `MatchLog` output.
- **`GameManager`** — creates/joins/spectates matches, tracks `entityGames`, and handles map swaps (`changeMap`).
- **`game/rules/`** — `GameModeConfig`, `GameModePreset`, `WinCondition`, `OnKillAction`, `BossSetup`.
- **`game/series/`** — `MatchSeries` and `SeriesPlayerState` for multi-round modes (Series, Nuzlocke, Ironman).
- **`game/map/`** — `GameMap` record, `GameMapManager` (template catalog, per-match world cloning into `active_worlds/`, async cleanup), `MapPool`, `StageSampler`.
- **`game/queue/`** — `QueueManager`, `AutoQueue`, `AfkTracker`.
- **`PracticeManager` / `PracticeArena`** — lobby practice boxes with target dummies that take knockback without a match.

### Physics, Progression, & Presentation (`physics/`, `progression/`, `vfx/`, `npc/`)
- **`physics/`** — `Hitbox` (cylinder, cone, sphere, swept box), `Interactions` (single entry point for all damage and Smash knockback), `Stun`, `Homing`, `WallDetection`.
- **`progression/`** — `PlayerStats`, `PlayerStatsManager` (in-memory cache + `boardIndex` snapshot for lag-free leaderboards/lookups), global `HeroLevel`, per-mode `HeroProgress`, `EloCalculator`.
- **`vfx/`** — `VisualEffect`, `SoundEffect`, `ParticleBuilder`, `AbstractDisplayBuilder` (`ItemDisplayBuilder`, `BlockDisplayBuilder`, `TextDisplayBuilder`), `CosmeticManager`, `TeamGlow`, `vfx/ui/gui/` (`GUI`/`GUIManager`), `vfx/ui/prompt/` (`PromptManager`), `vfx/ui/visual/` (scoreboards, leaderboards, tab list).
- **`npc/`** — `NpcService` abstraction with `CitizensNpcService` and `NoOpNpcService` fallback.

---

## Core Invariants (Things That Will Trip You Up)

1. **The 0 HP buffer is intentional.**
   Players survive at displayed 0 HP (Bukkit health `0.5`, `Interactions.ZERO_HP_BUFFER`). Depleting health clamps to the buffer; kills come from knockback ring-outs or taking another damaging hit while already at the buffer. Never replace this with a standard death-at-0-HP check.

2. **Hero lifecycle has three distinct transitions:**
   - **Death / respawn:** `Hero.triggerDeath()` fires `onDeath` on abilities and mobility; the event bus and independent projectiles stay alive.
   - **Elimination:** `Hero.eliminate()` marks the hero dormant and fires `triggerDeath()`. Dormant heroes only tick `isIndependent()` abilities (and `DoubleJump.tickDormant`) so in-flight projectiles and hazards can still score posthumous kills.
   - **Full teardown:** `Hero.cleanup()` runs only on logout, hero swap, or game end. It fires `onDeath` and `onOwnerLost` on every ability and mobility component, then clears the event bus.

3. **`GamePlayer` entries are never removed.**
   Leavers get `leftGame = true` so end-of-game stats, XP, and Elo calculations remain intact.

4. **Vanilla knockback is cancelled during `Interactions` damage.**
   `KnockbackListener` cancels Paper's `EntityKnockbackEvent` while `Interactions.isProcessingDamage(victim)` is true, because the cosmetic `victim.damage(0.4)` hurt flash would otherwise overwrite our computed Smash velocity.

5. **Primary fire uses a plain `STICK` held-use window (not consumables).**
   Vanilla client packets arrive roughly every 4 ticks while right-click is held. `AbilityListener` fires once on a fresh click, engages a lazy 1-tick sampler when consecutive packets arrive within `210ms`, and stops the task when nobody is holding fire.

6. **Ultimate charging rules.**
   `Hero.registerDefaultPassives()` reduces smash cooldown on jab hits and/or ability hits according to `HeroType` (`MELEE`, `RANGED`, `HYBRID`) using `templates.yml`. `AbilityType.SMASH` hits never self-charge the ultimate.

7. **Live `plugins/Heroes/` is the runtime source of truth.**
   `saveResource(path, false)` never overwrites existing files on disk. `src/main/resources/` ships fresh-install defaults only (exception: `bot-names.yml` is read directly from the jar). New YAML keys must be added to `src/main/resources/` and mirrored to any live server folder.

8. **Never hot-swap the plugin jar on a running server.**
   Always stop the server before replacing the jar.
