# Development Guidelines

How code in this repository is written, where things go, and what to check before calling a change done. For the system overview and lifecycle invariants, see [docs/ARCHITECTURE.md](ARCHITECTURE.md).

## 1. Errors are loud

- No defensive null guards, no `try/catch` that swallows errors, and no silent fallback behaviour. A bug should surface as an exception with a message that names the thing that went wrong.
- The only `catch` blocks that may continue in runtime gameplay are the per-entity loops in `Game` (hero tick, respawn tick, minion tick, bot init). They exist so one broken hero cannot kill the whole match, and they report through `util/ErrorReporter` (logs at `SEVERE` and posts to Discord if configured).
- Discord I/O (`util/DiscordWebhook`, `discord/DiscordBot` connect) may catch, log a warning, and continue: an unreachable or misconfigured Discord must never stop or lag the game server.
- Startup soft-dependency probes (`vfx/glow/GlowServiceProvider` falling back to `NoOpGlowService` on an unsupported server build, and `npc/NpcServiceProvider` falling back to `NoOpNpcService` when Citizens is absent) are the one permitted degradation path; they log once on boot.
- Validation of user input (commands, GUI prompts) may catch and reply with a message.
- A broken hero or ability YML logs a `SEVERE` error naming the hero, slot, and key; the broken ability is omitted from the hero rather than replaced with a dummy or silent fallback.
- An unknown enum value in config is an error, never a fallback to a default constant.

*(Note: some pre-existing code predates these rules and is being migrated incrementally; all new and edited code must follow them.)*

## 2. Config

- Hero and ability code reads YML through `util/Configs.require*`. Bukkit's one-argument getters return `0`/`null` on a missing key and two-argument getters hide typos; neither should be used in `ability/` or `hero/`.
- Defaults live in data, not Java:
  - `heroes/default.yml` for hero-level defaults (stats, mobility, combat, energy, unlock block).
  - `templates.yml` for template-level defaults shared by abilities. Read them with `Configs.orTemplate(config, key, "section.key")` or `TemplateConfig.require*`.
- Defaults in Java are allowed in three places: player data deserialization from older builds (`PlayerStatsManager`), optional cosmetic overrides (skins), and genuine opt-in flags where absent means off (`getBoolean(key, false)`).
- Config homes across `src/main/resources/`:
  - `game-config.yml` (`util/GameConfigManager`) — match timing, Elo, crystals, queues, practice arenas, glow, Discord (`discord:` webhooks and bot), team-balance window, restart, polls
  - `gamemodes.yml` (`util/GameModeConfigManager`) — custom mode preset defaults and map pools
  - `bots.yml` (`util/BotConfig`) — global bot cadence, disabled heroes, difficulty tiers
  - `progression.yml` (`progression/ProgressionConfig`) — XP curve, tokens, starting roster
  - `templates.yml` (`util/TemplateConfig`) — ability template defaults
  - `lobby.yml` (`npc/NpcConfig`, `vfx/ui/visual/BoardDisplay` with `EloBoardSource` and `LevelBoardSource`, `vfx/ui/visual/MemorialDisplays`) — lobby spawn, NPCs, leaderboards
  - `messages.yml` (`GameConfigManager.getMessage*`) — player-facing strings
  - `bot-names.yml` (`util/BotNameManager`) — read directly from the plugin jar (not extracted to `plugins/Heroes/`)
- `# FILL` comments in YML files mark balance/tuning placeholder values intended to be dialed in via playtests and `/balance` — they are not syntax errors.
- `saveResource(false)` never overwrites existing files in a live `plugins/Heroes/` folder, so `src/main/resources/` is fresh-install defaults only. Server operators must mirror new keys into their live folder, and PRs must list every new/changed key.
- Units: time keys end in `_ms` or `_ticks`, or are seconds when the surrounding template already uses seconds (`cooldown`, `active_duration`, `windup`). Multipliers end in `_mult`. Additive deltas end in `_add` or `_bonus`. Angles end in `_deg`. Distances are blocks with no suffix.

## 3. Comments

- A comment explains **why**, never **what**. If the code needs a comment to say what it does, rename or split it instead.
- Javadoc on the public API of templates, managers, and the event bus. Private methods get a comment only for an invariant or a workaround, naming the reason.
- No commented-out code. No `TODO` without a GitHub issue number.

## 4. Abstraction

- Logic used twice is pulled out.
- Logic that belongs to a different concept than the class it sits in moves to that concept even when used once (e.g. a spinning display transform belongs on the display builder, not inside one kit).
- Kit code (`ability/kit/**`) contains hero-specific behaviour only. Reusable raycasts, homing loops, display transforms, or particle shapes belong in `ability/template/`, `physics/`, or `vfx/effect/`.
- Template lifecycle is not overridable. `BaseAbility.tick` is final and runs windup and template bookkeeping, then calls `onTick`. Kits override `perform`, `onTick`, `onHit`, and other hooks rather than bypassing lifecycle methods.

## 5. Where things go

| I am adding… | It goes in… |
|---|---|
| an ability for a hero | `ability/kit/<hero>/`, extending a template from `ability/template/` |
| a hero | `heroes/<hero>.yml` (with `bot:`/`ai:`/`movement:`/`strategy:` blocks) + kit folder + `AbilityRegistry.registerAll()` + optional `lobby.yml` NPC + model keys in `docs/MODELS.md` (see [docs/HEROES.md](HEROES.md)) |
| a reusable mechanic (projectile shape, dash variant, aura) | `ability/template/` |
| knockback, damage, hitbox, or stun logic | `physics/` |
| a particle, sound, or display-entity builder | `vfx/effect/` |
| a menu | `vfx/ui/gui/` extending `GUI` |
| a scoreboard, title, tab list, or action bar | `vfx/ui/visual/` |
| a command | `command/admin/` or `command/general/` extending `command/HeroesCommand`, registered once in `Heroes.registerCommands()` (handles player gating, aliases, and op-default permissions) |
| a Bukkit event handler | `listener/` for cross-cutting handlers (registered in `Heroes.registerListeners()`), or next to its owning manager/kit named `*Listener` and registered in its `initialize()` |
| a config key | the owning YML file in §2 + its config manager getter |
| a persisted player stat | `progression/PlayerStats` + both `serializeStats` and `deserializeStats` in `PlayerStatsManager` |
| a match-level record | `game/MatchLog` |

## 6. Lifecycle rules you will trip over

- The 0 HP buffer is intentional. `0.5` health displays as `0` and is alive; the next damaging hit kills.
- A hero is torn down only through `Hero.cleanup()`. Eliminated heroes are dormant: only `isIndependent()` abilities (and `DoubleJump.tickDormant`) tick.
- Composed kits that hold inner template instances must delegate `tick`, `onDeath`, and `onOwnerLost` to them.
- `GamePlayer` entries are never removed. Leavers get `leftGame`.
- Vanilla knockback is cancelled during `Interactions` damage; all knockback is ours.
- Primary fire uses the held-use `STICK` model in `AbilityListener`; there are no consumable items.
- Status effects are applied only through `StatusEffects.applyTo` and cleared by the hero lifecycle (see below); never keep a private stun, root, or lock flag in a kit.
- Every repeating task must stop on its own conditions (entity invalid, dead, offline, empty tracking set). No tick caps to paper over a missing stop condition.
- Never hot-swap the jar on a running server.

### Status effects (kit authors)

- Apply restrictions only with `StatusEffects.applyTo(victim, spec, source, applier, onExpire)`, passing the ability (`this`) as `source`. A second apply from the same source refreshes instead of stacking.
- Load the spec from the ability's YML `status:` block with `Spec.fromConfig` (needs `duration:`) or `Spec.fromConfigIndefinite` (ability ends it explicitly). Do not name a preset, id, or toggle in Java; add or change them in `templates.yml` `status.presets` and `messages.yml` `status.indicator.<id>`.
- Apply the status after the ability's own `execute()` damage/knockback, not before: a `break_on_knockback` effect is ended by the next knockback the victim takes, including the ability's own.
- Remove what you applied when the owner dies or is lost: call `StatusEffects.removeAllFrom(this)` in `onDeath` and `onOwnerLost` (`MarauderGrip` is the reference). Statuses on a hero that dies or respawns are cleared by the hero itself.

### Practice arena (kit authors)

- Template hits, ranges, and visuals are already contained to the arena. A kit that builds its own `Hitbox` must add `.filter(victim -> PracticeManager.isWithinPracticeBounds(hero, victim.getLocation()))` (see `ChainSlash`), and a kit that casts its own ray, wave, or visual must bound it with `PracticeManager.clampPracticeRange(hero, start, direction, range)`. Both are no-ops outside practice.
- Do not contain player movement abilities: dashes and teleports may leave the box (practice exits when the player is outside every arena).

### Discord

- Nothing the server sends to Discord may ping anyone: build webhook bodies with `DiscordWebhook.contentPayload`, and set `setAllowedMentions(List.of())` on bot replies (`DiscordBot.replyEmbed` / `replyText` do). Escape player-supplied text with `DiscordWebhook.escapeMarkdown`.
- Read plugin state (stats, games, online players) on the main thread, then hand plain data to the async step; do HTTP and file reads (`MatchLog.recentGamesForPlayer`) off it. `GamesSlashCommand` shows the pattern.
- Format numbers shared with in-game commands through `progression/StatsFormat`, not a second copy.
- JDA is loaded by `HeroesPluginLoader` and is `compileOnly`; do not shade it. Bot secrets (`discord.bot.token`) belong only in the live config, never in `src/main/resources/`.

## 7. Toolbox

- `/practice` puts you in the practice arena with target dummies that take knockback — fastest way to feel a change.
- `/balance <hero> <path> <value>` edits a live YML value and reloads; `/balance templates <path> <value>` does the same for `templates.yml`; `/balance reload` reloads every hero and `templates.yml`. Applied edits are logged by date in `patch-notes.yml`.
- `/forcehero <player> <hero>` selects any hero for a player (bypasses unlocks and `admin-only`); `/settings` opens the personal preferences GUI (`PreferencesGUI`).
- `src/main/resources/example.yml` documents `visuals:` and `sound:` block shapes; `src/main/resources/example-hero.yml` is the full hero YML skeleton.
- `vfx/effect/ParticleBuilder`, `VisualEffect` (yml-driven), `SoundEffect`, `TextDisplayBuilder`, and `AbstractDisplayBuilder` are the visual primitives. Prefer them over raw `World.spawnParticle`.
- Everything a hero does to its body goes through `control/Controller` (velocity, messages, action bar, titles, sounds, equipment), never `Player` directly. Bots share the same interface.
- `physics/Hitbox` shapes (box sweep, cone, sphere, cylinder) are the hit tests; `physics/Interactions.on(attacker, victim)` is the only way to deal damage or knockback and must name the ability.
- `tools/matchlog.py` summarises match logs; `tools/yml_keys.py` lists config keys read by code with inferred units; `tools/patchnotes.py <old-ref> [new-ref]` prints patch-note lines for hero and template YML changes between two git refs (the same format `/balance` appends to `plugins/Heroes/patch-notes.yml` through `util/PatchNotesLog`).

## 8. Before you call it done

1. `./gradlew compileJava` (or `.\gradlew.bat compileJava` on Windows) is clean apart from known deprecation/unchecked notes.
2. Every new or changed YML key is updated in `src/main/resources` (and mirrored to your live server folder if testing locally) and named in the PR/issue comment.
3. Behaviour changes have been playtested (`/practice` or a custom game with bots), not just compiled.
4. No new default in Java outside the three allowed cases in §2.
5. Update the GitHub issue with the files changed and move it to **Needs playtest**.
