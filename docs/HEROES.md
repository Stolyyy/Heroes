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
- `skins.default` and `skins.masterskin`: leather armor RGB dyes (`R,G,B`) and optional `hand:` or `piece_visuals:` overrides.
- `unlock:`: `default-unlocked`, `token-cost`, and `smash-level-requirement`.
- `admin-only: true`: optional flag that hides work-in-progress heroes from non-ops in `/hero` and `HeroSelectGUI`.
- `bot:`, `ai:`, `movement:`, `strategy:`: per-hero bot config (see [docs/BOTS.md](BOTS.md)). If the hero cannot be played by bots yet (e.g. player-packet bow mechanics), add `<id>` to `disabled_heroes:` in `src/main/resources/bots.yml`.

> **Reminder:** `Heroes.extractHeroResources()` calls `saveResource(path, false)` on startup, so an existing test server's `plugins/Heroes/heroes/<id>.yml` is never overwritten automatically. When editing an existing hero's YML, either edit the live file directly (or `/balance`) or delete the live copy before restarting.

---

## 4. Optional Wiring

- **Chat aliases:** Add shortcuts in `HeroConfig.HERO_ALIASES` if desired (must resolve to the lowercase hero ID filename).
- **Lobby NPC:** Add an entry under `heroes:` in `src/main/resources/lobby.yml` if you want a physical lobby display NPC for the hero (`HeroSelectGUI` lists all heroes dynamically without this).
- **Resource pack models & sounds:**
  - Models use string-keyed custom model data in `resourcepack/sponge.json` (`<asset>_head_<skin>`), `stick.json` (`<asset>_item_<skin>`), and `stone.json` (`<asset>_<texture>_default`). Missing keys safely fall back to the base vanilla item during development.
  - Document new keys in [docs/MODELS.md](MODELS.md).
  - `hurt_sound.sound` (and default `<asset>.pain`) can point to a custom pack sound or a vanilla Minecraft sound event.

---

## 5. Verify

1. Run `./gradlew compileJava` (or `.\gradlew.bat compileJava` on Windows).
2. Boot a test server — `HeroBuilder.validateAll()` builds every hero at enable time and logs any missing/unregistered keys immediately.
3. Run `/hero <id>` then `/practice` to test every ability against knockback dummies, and test a custom game with bots.
4. Use `/balance <id> <path> <value>` and `/balance reload` to tune numbers live, then mirror finalized numbers back into `src/main/resources/heroes/<id>.yml`.
