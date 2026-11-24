# Bot AI & Strategy System

Bots reuse the exact same `Hero`, `Ability`, and `Interactions` pipeline as human players via `control/BotController` (backed by Citizens NPCs).

---

## Architecture

### Control Layer (`control/`)
- **`Controller`** — shared input/body interface for players and bots (`BotController` intentionally no-ops player HUD/notification methods).
- **`BotController`** — wraps a Citizens `NpcHandle`. Because Citizens can temporarily unspawn/recreate NPC entities on chunk loads, `BotController` accessors tolerate a transient null entity; `ensureCombatState()` re-pins navigation speed and combat state.
- **`BotLoadout`** — wires a game bot's strategy (`Strategy.fromConfig`), `HeroBotAI`, difficulty tier delay, and transient charms.

### AI Layer (`control/ai/`)
- **`BotAI` / `HeroBotAI`** — concrete bot AI orchestrating targeting and the per-tick task priority ladder:
  1. **`RECOVER`** (`BotRecovery`) — off-stage or falling toward the void; aims at the nearest sampled stage point (`StageSampler`) and pumps movement tools (including airborne jumps) until back over stage.
  2. **`EVADE`** (`BotEvade`) — melee/hybrid enemy closer than `panic_distance`; retreats along sampled away points while keeping grounded escape tools safe via `DashSafety`.
  3. **`FIGHT`** — target selection (stickiness via `min_target_duration` / `switch_threshold`), delegated to **`BotNavigation`** (crystal chasing, chase/ring/backpedal spacing, Citizens reissue guards, kiting look focus) and **`BotCombat`** (input-delay queue, ability priority `smash > primary > secondary`, ult HP gating, jab resolution), plus strategy jump heuristics.
  4. **`IDLE`** — seeks active smash crystals or stops/follows owner (for minions).
- **`MovementActions`** — priority-ordered list of movement slots (`jump`, `primary`, `secondary`, `smash`, `sneak`, `drop`, `swap`) with per-action commit windows (`delay_after_ticks`) shared by recovery, evasion, and far-chase approach.
- **`MinionBotAI`** — concrete `HeroBotAI` subclass for summoned minions (constructed via `Strategy.fromConfig` from each minion's config block; `SpooderBuddyAI` adds wall-climb behavior).

### Strategy Layer (`control/strategy/`)
- **`Strategy` / `ConfigStrategy`** — config-driven combat strategy interface and shared abstract base reading `preferred_distance`, `ability_ranges`, `far_distance`, `panic_distance`, ult HP gates, and `can_double_jump` (falling back to `templates.yml` `bot.strategy.*` where supported).
- Implementations: **`Aggressive`** (melee/close-range, never enters `EVADE`), **`Defensive`** (ranged/kiting), **`MidRange`** (hybrid).
- Safety helpers: **`DashSafety`** (rejects dashes that land over the void unless a recovery jump remains) and **`JumpSafety`** (vets chase jumps).

---

## Configuration

### Global Bot Settings (`src/main/resources/bots.yml` -> `util/BotConfig`)
- `disabled_heroes`: hero IDs bots will not roll (e.g. heroes whose mechanics require player-only packets like bow draws).
- `recovery` / `movement` / `prevention`: recovery anchor offset, recheck cadence, jump veto toggle, and gap-jump reach margin.
- `navigation` / `evade`: ring/backpedal slacks, path reissue cadence/thresholds, quiet velocity threshold, jump cooldown, and evade flee sampling/aiming parameters.
- `difficulty`: tiers `1`–`5`, mapping to tick input delay in `BotSettingsGUI`.

### Per-Hero & Minion Bot Config (`src/main/resources/heroes/<hero>.yml`)
Each hero YML (and minion block using `MinionBotAI`) defines (see [src/main/resources/example-hero.yml](../src/main/resources/example-hero.yml)):
- **`bot.strategy_type`**: `AGGRESSIVE`, `DEFENSIVE`, or `MIDRANGE` (resolved via `Strategy.fromConfig`).
- **`ai:`**: `min_target_duration`, `switch_threshold`, `search_radius`, `speed_modifier` (`1.0` = walk parity, `1.3` = sprint parity; Citizens' squared move-control math is inverted in `CitizensNpcService.setNavigationSpeed`), `input_delay`.
- **`movement.actions:`**: optional priority-ordered list of `{ action, priority, delay_after_ticks }` entries (defaults to `jump`). Slot names are validated on load; unknown names throw `IllegalArgumentException`.
- **`strategy:`**: `preferred_distance`, `far_distance`, `panic_distance`, `can_double_jump`, `ult_min_target_hp_percent`, `ult_min_self_hp_percent`, `ability_ranges.{primary,secondary,smash}`.

---

## Citizens & PacketEvents Notes

- Bots require **Citizens** to spawn. Without Citizens, `NpcServiceProvider` falls back to `NoOpNpcService` and bot spawning is unavailable.
- Smooth head rotation while kiting (`setNavigationLookAt`) uses Citizens' `RotationTrait` packet hook, which requires **PacketEvents** on the server; without PacketEvents it is a harmless cosmetic no-op.
- Bot display names are rolled from `src/main/resources/bot-names.yml` (loaded directly from the plugin jar by `BotNameManager`).
