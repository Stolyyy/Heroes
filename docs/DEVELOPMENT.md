# Development guidelines

How code in this repository is written, where things go, and what to check before calling a change done. The architecture overview is in `CLAUDE.md` (agent-facing) and will move to `docs/ARCHITECTURE.md`; this file is the rulebook.

## 1. Errors are loud

- No defensive null guards, no try/catch that swallows, no fallback behaviour. A bug should surface as an exception with a message that names the thing that went wrong.
- The only catch blocks that may continue are the per-entity loops in `Game` (hero tick, respawn tick, minion tick, bot init). They exist so one broken hero cannot kill the whole match, and they report through `util/ErrorReporter`, which logs at SEVERE and can post to Discord with the game context.
- Validation of user input (commands, GUI prompts) may catch and reply with a message. That is the other allowed case.
- A broken hero fails at load with the file and key named. Never return a dummy or a partially built hero.
- An unknown enum value in config is an error, never a fallback to the first constant.

## 2. Config

- Hero and ability code reads YML through `util/Configs.require*`. Bukkit's one-argument getters return 0 or null on a missing key and the two-argument ones hide typos; neither is allowed in `ability/` or `hero/`.
- Defaults live in data, not Java:
  - `heroes/default.yml` for hero-level defaults (stats, mobility, jab, unlock block).
  - `templates.yml` for template-level defaults shared by all abilities (windup, ult charge, dash exit velocity, minion charge). Read them with `Configs.orTemplate(config, key, "section.key")`. Every entry carries its unit in the name or a comment.
- Defaults in Java are allowed in exactly three places: player data written by older builds (`PlayerStats` deserialisation), optional cosmetic overrides (skins), and genuine opt-in flags where absent means off (`getBoolean(key, false)`).
- The live `plugins/Heroes/` folder is the runtime source of truth. `saveResource(false)` never overwrites, so `src/main/resources` is fresh-install defaults only. A new key goes into both.
- Units: time keys end in `_ms` or `_ticks`, or are seconds when the surrounding template already uses seconds (`cooldown`, `active_duration`, `windup`). Multipliers end in `_mult`. Additive deltas end in `_add` or `_bonus`. Angles end in `_deg`. Distances are blocks with no suffix.

## 3. Comments

- A comment explains why, never what. If the code needs a comment to say what it does, rename or split it instead.
- Javadoc on the public API of templates, managers and the event bus. Private methods get a comment only for an invariant or a workaround, and the comment names the reason.
- No commented-out code. No TODO without an issue number.

## 4. Abstraction

- Logic used twice is pulled out.
- Logic that belongs to a different concept than the class it sits in moves to that concept even when used once. The spinning saber model lives with the display builder, not in the saber throw.
- Kit code (`ability/kit/**`) contains hero-specific behaviour only. If a kit file holds a raycast loop, a homing loop, a `Transformation` build or a particle shape, that logic belongs in `ability/template`, `physics` or `vfx/effect`.
- Template lifecycle is not overridable. `BaseAbility.tick` is final and runs windup and template bookkeeping, then calls `onTick`. Kits override `perform`, `onTick`, `onHit` and the other hooks, never the lifecycle methods.

## 5. Where things go

| I am adding… | It goes in… |
|---|---|
| an ability for a hero | `ability/kit/<hero>/`, extending a template from `ability/template/` |
| a hero | `heroes/<hero>.yml` + kit folder + `AbilityRegistry.registerAll()` + model keys in `docs/MODELS.md` + the `bots.yml` block |
| a reusable mechanic (projectile shape, dash variant, aura) | `ability/template/` |
| knockback, damage, hitbox or stun logic | `physics/` |
| a particle, sound or display-entity builder | `vfx/effect/` |
| a menu | `vfx/ui/gui/` extending `GUI` |
| a scoreboard, title, tab list or action bar | `vfx/ui/visual/` |
| a command | `command/admin/` or `command/general/`, registered in `Heroes.registerCommands` |
| a Bukkit event handler | next to the manager that owns the state it touches, named `*Listener`, registered from that manager's `initialize()`; `listener/` holds only cross-cutting handlers (chat, join/quit, damage routing) |
| a config key | `game-config.yml` + a getter in `util/GameConfigManager`, or `templates.yml` for ability-wide defaults |
| a persisted player stat | `progression/PlayerStats` + both `serializeStats` and `deserializeStats` |
| a match-level record | `game/MatchLog` |

## 6. Lifecycle rules you will trip over

- The 0 HP buffer is intentional. 0.5 health displays as 0 and is alive; the next damaging hit kills. Kills come from knockback finishes.
- A hero is torn down only through `Hero.cleanup()`. Eliminated heroes are dormant: only independent abilities tick.
- Composed kits that hold inner template instances must delegate `tick`, `onDeath` and `onOwnerLost` to them.
- `GamePlayer` entries are never removed. Leavers get `leftGame`.
- Vanilla knockback is cancelled during `Interactions` damage; all knockback is ours.
- Primary fire uses the held-use STICK model in `AbilityListener`; there are no consumable items.
- Every repeating visual task must stop on its own conditions: entity invalid, dead, offline, grounded, not moving. No tick caps to paper over a missing condition.
- Never hot-swap the jar on a running server.

## 7. Toolbox

- `/practice` puts you in the practice arena with target dummies that take knockback; the fastest way to feel a change.
- `/balance <hero> <path> <value>` edits a live YML value and reloads; `/balance reload` reloads every hero and `templates.yml`.
- `src/main/resources/example.yml` documents every `visuals:` and `sound:` block shape (particles, shapes, models, colours) and is meant to be copied from.
- `vfx/effect/ParticleBuilder` (points, lines, rings, discs), `VisualEffect` (yml-driven), `SoundEffect`, `TextDisplayBuilder` and `AbstractDisplayBuilder` are the visual primitives. Prefer them over raw `World.spawnParticle`.
- Everything a hero does to its body goes through `control/Controller` (velocity, messages, action bar, titles, sounds, equipment), never the `Player` directly. Bots share the same interface.
- `physics/Hitbox` shapes (box sweep, cone, sphere) are the hit tests; `physics/Interactions.on(attacker, victim)` is the only way to deal damage or knockback and must name the ability.
- `tools/matchlog.py` summarises the match log; `tools/yml_keys.py` lists every config key the code reads with its inferred unit.
- `spark` is installed on the servers. Profile before and after any efficiency change.

## 8. Before you call it done

1. `gradlew compileJava` is clean apart from the known deprecation notes.
2. Every new YML key is in both the resource file and the live folder, and the report names it.
3. Behaviour changes have been played, not just compiled. Use `/practice` or a custom game with bots.
4. No new default in Java outside the three allowed cases.
5. The issue on GitHub has a comment with the files changed and is moved to Needs playtest.
