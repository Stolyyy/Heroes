# Adding a Hero

This guide walks through every file and convention involved in adding a playable hero to Heroes. Start from [src/main/resources/example-hero.yml](../src/main/resources/example-hero.yml) as your template.

---

## 1. Naming Conventions (Three Forms)

A hero has three related identifiers — keep them consistent:

1. **Hero ID (filename):** `src/main/resources/heroes/<id>.yml` (lowercase, no spaces, e.g. `voidcrawler`, `karakot`, `cakemonster`). Used by `HeroConfig`, `/hero <id>`, and `PlayerStats`.
2. **Display name (`name:` in YML):** Player-facing name (e.g. `Void Crawler`, `CakeMonster`).
3. **Asset key (`HeroConfig.assetKey`):** `name:` lowercased with spaces stripped (e.g. `voidcrawler`, `cakemonster`). Used as the `<hero>` prefix in resource-pack model keys (`<hero>_<texture>_<skin>`). Make sure every `model.hero` field inside the hero YML matches this asset key.

> **Important:** Ability `name:` fields are used as keys in `PlayerStats` (`abilities.<name>`). Do not rename an ability's display `name:` after players have recorded stats with it, or their per-ability history will be orphaned.

---

## 2. Create the Kit Classes

1. Create a package `src/main/java/me/stolyy/heroes/ability/kit/<hero>/`.
2. Implement each custom ability extending a template in `ability/template/` (`BaseAbility`, `Hitscan`, `Projectile`, `Cone`, `Dash`, `Shockwave`).
   - Constructor signature must match `(ConfigurationSection config, HeroSkin skin)` and pass both to `super(config, skin)`.
   - Always read config values from `this.config` (not the raw constructor `config` parameter) so per-skin ability overrides apply.
   - Read required values with `Configs.require*` — no silent fallbacks.
   - If the kit builds its own `Hitbox`, add the practice-arena victim filter and clamp its own ranges (see [docs/DEVELOPMENT.md](DEVELOPMENT.md) §6, "Practice arena").
   - If the ability owns inner template instances, delegate `tick`, `onDeath`, and `onOwnerLost` to them, and override `isIndependent()` if detached projectiles/hazards should keep resolving while the hero is eliminated.
3. Register every ability type (lowercase key) in `AbilityRegistry.registerAll()`:
   - Add the kit package import at the top of `AbilityRegistry.java`.
   - Call `HeroBuilder.registerAbility("<type>", MyAbility::new);` for each ability.
   - If the hero uses a custom `DoubleJump` subclass, register it with `HeroBuilder.registerMobility("<key>", MyMobility::new);` and set `mobility.type: <key>` in the YML.

---

## 3. Create the Hero YML

Copy `src/main/resources/example-hero.yml` to `src/main/resources/heroes/<id>.yml`:

- `heroes/default.yml` supplies hero-level defaults (`stats`, `mobility`, `combat`, `energy`, `unlock`, death/kill sounds). Only override what differs.
- `abilities.primary`, `abilities.secondary`, `abilities.smash`: each requires `type:` matching a key in `AbilityRegistry`.
- `abilities.passives`: YAML list; each entry needs `type:` and `activation:` (`TICK`, `SNEAK`, `DROP`, or `SWAP`).
- `skins.default` and `skins.masterskin`: armor pieces (`chest`, `legs`, `boots`) as leather RGB dyes (`R,G,B`) or sections with optional `material:`, `color:`, `equipment:` (custom worn-armor texture asset key in the resource pack), and `model: true` (sets item `custom_model_data` to `<asset>_<chest|legs|boots>_<skin>`), plus optional `hand:` or `piece_visuals:` overrides.
- `unlock:`: `default-unlocked`, `token-cost`, and `smash-level-requirement`.
- `admin-only: true`: optional flag that hides work-in-progress heroes from non-ops in `/hero` and `HeroSelectGUI`.
- `bot:`, `ai:`, `movement:`, `strategy:`: per-hero bot config (see [docs/BOTS.md](BOTS.md)). If the hero cannot be played by bots yet (e.g. player-packet bow mechanics), add `<id>` to `disabled_heroes:` in `src/main/resources/bots.yml`. Bots never roll `admin-only` or `disabled_heroes` heroes (`BotConfig.eligibleHeroes()`).

> **Reminder:** `Heroes.extractHeroResources()` calls `saveResource(path, false)` on startup, so an existing test server's `plugins/Heroes/heroes/<id>.yml` is never overwritten automatically. When editing an existing hero's YML, either edit the live file directly (or `/balance`) or delete the live copy before restarting.

---

## 4. Status Effects

Abilities that crowd-control or restrict targets declare a status block (typically `status:`, or a custom key such as `victim_status:` / `self_status:`) and load it with `StatusEffects.Spec.fromConfig(config)` (requires `duration:` in seconds) or `StatusEffects.Spec.fromConfigIndefinite(config, key)` (for effects removed explicitly by the ability; rejects `duration:`).

### Three-Layer Resolution

Every toggle and feedback key resolves in three layers — highest wins:

1. **Ability block** (e.g. `abilities.secondary.status.*` in `heroes/<hero>.yml`)
2. **Named preset** (`templates.yml` `status.presets.<preset>.*`, if `preset:` is set)
3. **Defaults** (`templates.yml` `status.defaults.*` — all six toggles `false`, `loop_interval_ticks: 10`, default `apply_sound`)

- `preset:` is optional. `id:` names the effect — it selects the action bar label (`messages.yml` `status.indicator.<id>`) and is matched by `hero.status().has(id)`.
- `id:` defaults to the `preset:` name. A block with neither `preset:` nor `id:`, an unknown `preset:`, or an `id:` without a `messages.yml` `status.indicator.<id>` entry fails loudly during `HeroBuilder.validateAll()`.
- Never hardcode preset ids or toggle combinations in Java.
- Kit-side rules (apply after the ability's own `execute()`, remove with `StatusEffects.removeAllFrom(this)` in `onDeath` / `onOwnerLost`, no private stun flags) are in [docs/DEVELOPMENT.md](DEVELOPMENT.md) §6, "Status effects".

### Toggles (`true` enables the restriction / behaviour)

- `block_move` — prevents walking and ground-jumping; victim still falls and takes knockback.
- `block_jump` — blocks double jumps (including hero-specific mobility variants).
- `block_abilities` — blocks primary, secondary, smash, and passive inputs (an active primary override still fires).
- `block_jab` — blocks left-click jabs.
- `hold` — pins the victim in place (zero gravity, ignores knockback, undoes displacement); implies `block_move` and `block_jump`.
- `break_on_knockback` — ends the effect early when the victim takes knockback.

### Feedback Keys

All sound and visual blocks use the same `SoundEffect` / `VisualEffect` formats as ability `visuals:` (so `templates.yml` `sound:` and `visual:` defaults apply inside them). Visuals play at the victim's location; sounds play through `SoundEffect.play(location, controller)` so each block's `scope` controls who hears it. Feedback and action bar labels are skipped when an ability applies a status to its own caster, and practice dummies receive sounds and visuals too.

- `apply_sound` (always resolves via `status.defaults`) / `apply_visual` (optional) — played once when the effect starts; not replayed when the same source refreshes it.
- `end_sound` / `end_visual` (optional) — played when the effect ends by expiry, knockback break, or explicit source removal; skipped when cleared by death, respawn, elimination, or cleanup.
- `loop_sound` / `loop_visual` (optional) and `loop_interval_ticks` — played every `loop_interval_ticks` ticks while active.

---

## 5. Optional Wiring

- **Chat aliases:** Add shortcuts in `HeroConfig.HERO_ALIASES` if desired (must resolve to the lowercase hero ID filename).
- **Lobby NPC:** Add an entry under `heroes:` in `src/main/resources/lobby.yml` if you want a physical lobby display NPC for the hero (`HeroSelectGUI` lists all heroes dynamically without this).
- **Resource pack models & sounds:**
  - Models use string-keyed custom model data in `resourcepack/sponge.json` (`<asset>_head_<skin>`), `stick.json` (`<asset>_item_<skin>`), and `stone.json` (`<asset>_<texture>_default`). Missing keys safely fall back to the base vanilla item during development.
  - Document new keys in [docs/MODELS.md](MODELS.md).
  - `hurt_sound.sound` (and default `<asset>.pain`) can point to a custom pack sound or a vanilla Minecraft sound event.

---

## 6. Verify

1. Run `./gradlew compileJava` (or `.\gradlew.bat compileJava` on Windows).
2. Boot a test server — `HeroBuilder.validateAll()` builds every hero at enable time and logs any missing/unregistered keys immediately.
3. Run `/hero <id>` then `/practice` to test every ability against knockback dummies, and test a custom game with bots.
4. Use `/balance <id> <path> <value>` and `/balance reload` to tune numbers live, then mirror finalized numbers back into `src/main/resources/heroes/<id>.yml`.
