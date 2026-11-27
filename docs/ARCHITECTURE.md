# Architecture Overview

This document describes how the Heroes plugin is structured and the core invariants every contributor should understand before modifying game, hero, or ability code. Coding rules live in [docs/DEVELOPMENT.md](DEVELOPMENT.md).

---

## Core Systems

### Entry Point (`Heroes.java`)
`Heroes` boots configs, maps, stats, hero archetypes, registries, managers (each registering its own domain listeners via `initialize()`), cross-cutting listeners (`listener/`), `HeroesCommand` registrations, optional LuckPerms context (`game/map/TemplateWorldContext`), teammate glow (`TeamGlow`), auto-spectate, NPCs, practice mode, and self-registered `BoardDisplay` lobby leaderboards. On shutdown (`onDisable`), it tears down GUIs, games, heroes, minions, practice, queues, listeners, NPCs, and displays before flushing `PlayerStatsManager` and cancelling remaining tasks last.

### Hero System (`hero/`)
Heroes use a component-based composition around `Hero`:
- **`Hero`** — owns the player/bot `Controller`, ability slots (`primary`, `secondary`, `smash`, `passives`), `HeroEventBus`, and mechanics (`Attributes`, `DoubleJump`, `Jab`, `Energy`, `CooldownManager`, `StatusEffects`), alongside `AbilityListener` (`hero/`) and `MovementListener`/`JabListener` (`hero/mechanics/`).
- **`StatusEffects`** (`hero/mechanics/`): generic crowd-control registry owned by each `Hero` (`hero.status()`). Abilities apply an effect through `StatusEffects.applyTo(victim, spec, source, applier, onExpire)`, which also covers non-hero victims (practice dummies, kept in a static map and ticked by `PracticeManager`). A living hero's `tick()` calls `status.tick(hero)` for timers and loop feedback; dormant heroes do not tick it. Restrictions (`blocks(Restriction)`) are the union of all active effects and are read by `Hero` input methods (`usePrimary`, `useSecondary`, `useSmash`, `onSneak`, `onSwap`), `DoubleJump`, `Jab`, and the bot controller/AI. `CooldownManager` prefixes the action bar with the highest-priority opponent-applied effect (`messages.yml` `status.indicator.<id>`). Toggles, feedback, and presets are config: see [docs/HEROES.md](HEROES.md) §4.
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
- **`PracticeManager` / `PracticeArena`** — lobby practice boxes (`game-config.yml` `practice-arenas`) with target dummies that take knockback without a match. Entering practice forces the smash ready, so ultimates are testable; summoned minions tick with their owner and are teleported to the player if they leave the box. Containment is opt-in per hit path: `practiceArenaOf(hero)` (unwraps minions to their owner), `isWithinPracticeBounds(hero, loc)`, `clampPracticeRange(hero, start, dir, range)` and `enemiesFor(entity)` (dummies in the caller's arena). The shared templates (`Projectile`, `Cone`, `Dash`, `Aoe`, `Planted`, `Tether`) already apply them; player movement abilities are deliberately not contained. See [docs/DEVELOPMENT.md](DEVELOPMENT.md) §6 for the kit rule.
- **`MatchLog` / `TeamBalancer`**: `MatchLog.write` appends monthly JSON-lines match logs; at enable it also seeds an in-memory cache of recent Custom games (window `team-balance.session-window-hours`), which `MatchLog.sessionScores` turns into wins-minus-losses for `TeamBalancer.apply` to auto-balance custom-game teams (ties fall back to Elo). `MatchLog.recentGamesForPlayer` reads the files for one player and must run off the main thread.
- **`PlayerVisibility` / `TabList`**: per-viewer hiding and tab scoping. Lobby viewers keep real in-game players shown (different world, so nothing renders) so they stay in the tab list with a mode glyph (`messages.yml` `tablist.glyph.*`); bots are listed only for viewers in the same game.
- **Chat & preferences**: `listener/ChatFormatListener` renders `[level] name: message` (`messages.yml` `chat.level-prefix`) and highlights `@name` mentions with a sound for mentioned players who kept chat pings on; that toggle is a persisted `PlayerStats` flag edited in `PreferencesGUI` (`/settings`).

### Physics, Progression, & Presentation (`physics/`, `progression/`, `vfx/`, `npc/`)
- **`physics/`** — `Hitbox` (cylinder, cone, sphere, swept box), `Interactions` (single entry point for all damage and Smash knockback), `Stun`, `Homing`, `WallDetection`.
- **`progression/`** — `PlayerStats`, `PlayerStatsManager` (in-memory cache + `boardIndex` snapshot for lag-free leaderboards/lookups), global `HeroLevel`, per-mode `HeroProgress`, `EloCalculator`.
- **`vfx/`** — `VisualEffect`, `SoundEffect`, `ParticleBuilder`, `AbstractDisplayBuilder` (`ItemDisplayBuilder`, `BlockDisplayBuilder`, `TextDisplayBuilder`), `CosmeticManager`, `TeamGlow`, `vfx/ui/gui/` (`GUI`/`GUIManager`), `vfx/ui/prompt/` (`PromptManager`), `vfx/ui/visual/` (scoreboards, leaderboards, tab list).
- **`npc/`** — `NpcService` abstraction with `CitizensNpcService` and `NoOpNpcService` fallback. `vfx/ui/visual/HeroNpcSkins` sends per-viewer equipment packets so each lobby player sees the hero NPCs in their own selected skin.
- **`LobbyMobility`** (`hero/mechanics/`): gives lobby players the default hero speed and one mid-air double jump, reading `heroes/default.yml` (`stats.speed`, `mobility.*`); reset when joining a game, entering practice, and on logout.

### Discord (`discord/`, `util/`, `vfx/ui/visual/`)
- **Outbound webhooks:** `util/DiscordWebhook` (`post`, `postForId`, `edit`) sends off the main thread, and `contentPayload` always sets `allowed_mentions.parse` to empty. `DiscordStatus` keeps one live status message edited in place (message id persisted in the plugin folder) and posts hero-mastery announcements; `ErrorReporter` posts caught game-loop exceptions. Keys live under `discord:` in `game-config.yml`.
- **Slash-command bot:** `discord/DiscordBot` connects JDA asynchronously when `discord.bot.token` is set and registers one `BotCommand` per slash command (`StatsSlashCommand`, `LeaderboardSlashCommand`, `GamesSlashCommand`, `OnlineSlashCommand`) on `discord.bot.guild-id`. JDA is not shaded: `HeroesPluginLoader` (declared as `loader:` in `paper-plugin.yml`) resolves it from Maven Central through Paper's library loader, and `build.gradle.kts` keeps it `compileOnly`.
- **Shared formatting:** `progression/StatsFormat` is used by both `StatsCommand`/`LeaderboardCommand` and the Discord commands so numbers and labels match.

---

## Core Invariants (Things That Will Trip You Up)

1. **The 0 HP buffer is intentional.**
   Players survive at displayed 0 HP (Bukkit health `0.5`, `Interactions.ZERO_HP_BUFFER`). Depleting health clamps to the buffer; kills come from knockback ring-outs or taking another damaging hit while already at the buffer. Never replace this with a standard death-at-0-HP check.

2. **Hero lifecycle has three distinct transitions:**
   - **Death / respawn:** `Hero.triggerDeath()` fires `onDeath` on abilities and mobility; the event bus and independent projectiles stay alive.
   - **Elimination:** `Hero.eliminate()` marks the hero dormant and fires `triggerDeath()`. Dormant heroes only tick `isIndependent()` abilities (and `DoubleJump.tickDormant`) so in-flight projectiles and hazards can still score posthumous kills.
   - **Full teardown:** `Hero.cleanup()` runs only on logout, hero swap, or game end. It fires `onDeath` and `onOwnerLost` on every ability and mobility component, then clears the event bus.

   Status effects on a hero are cleared by `triggerDeath()` (so also elimination), by the respawn flow (`Respawns`), and by `cleanup()`, all through `hero.status().clear(hero)` without end feedback. Effects an ability applied to *other* entities are not cleared by the victim's side: the applying ability removes them with `StatusEffects.removeAllFrom(this)` in `onDeath` and `onOwnerLost`.

3. **`GamePlayer` entries are never removed.**
   Leavers get `leftGame = true` so end-of-game stats, XP, and Elo calculations remain intact.

4. **Vanilla knockback is cancelled during `Interactions` damage.**
   `KnockbackListener` cancels Paper's `EntityKnockbackEvent` while `Interactions.isProcessingDamage(victim)` is true, because the cosmetic `victim.damage(0.4)` hurt flash would otherwise overwrite our computed Smash velocity.
   Before writing the velocity, `Interactions` calls `StatusEffects.onKnockbackTaken(victim)`, which ends `break_on_knockback` effects; if the victim is still held (`hold`), the knockback is skipped but damage still applies.

5. **Primary fire uses a plain `STICK` held-use window (not consumables).**
   Vanilla client packets arrive roughly every 4 ticks while right-click is held. `AbilityListener` fires once on a fresh click, engages a lazy 1-tick sampler when consecutive packets arrive within `210ms`, and stops the task when nobody is holding fire.

6. **Ultimate charging rules.**
   `Hero.registerDefaultPassives()` reduces smash cooldown on jab hits and/or ability hits according to `HeroType` (`MELEE`, `RANGED`, `HYBRID`) using `templates.yml`. `AbilityType.SMASH` hits never self-charge the ultimate.

7. **Live `plugins/Heroes/` is the runtime source of truth.**
   `saveResource(path, false)` never overwrites existing files on disk. `src/main/resources/` ships fresh-install defaults only (exception: `bot-names.yml` is read directly from the jar). New YAML keys must be added to `src/main/resources/` and mirrored to any live server folder.

8. **Never hot-swap the plugin jar on a running server.**
   Always stop the server before replacing the jar.
