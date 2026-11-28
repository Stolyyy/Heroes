# MODELS.md — Custom Model Data Manifest

Hand-off document for texture work (last regenerated 2026-07-11 against live server configs —
at runtime `plugins/Heroes/` is authoritative because `saveResource(false)` never overwrites
existing files; `src/main/resources/heroes/` holds fresh-install defaults).

**Key convention:** `ItemBuilder.modelData(hero, texture, skin)` → string key
`<hero>_<texture>_<skin>`, lowercased (`HeroConfig.assetKey` strips spaces from display names).
`ItemBuilder.applyModelData(item, rawKey)` is the raw-key variant (map icons).
The `resourcepack/*.json` files are item-model *select* definitions keyed on these strings —
one file per **base material** (`stick.json` → `minecraft:stick`, etc.). A key only renders
if the item's base material has a selector file containing a `when` case for it.

**Where keys come from (verified in code):**
- Worn helmet: `CosmeticManager.applyCurrentSkin` → `<name>_head_<skinId>` on **SPONGE**
  (`<name>` = the yml `name:` field, lowercased; `skinId` = selected skin: `default`,
  `masterskin`, or Pochita's temporary `hell`).
- Hotbar slot 0 weapon: `<name>_item_<skinId>` on **STICK**, unless the skin has a
  `hand:` override (`material:`/`hero:`/`texture:`/`skin:`) — only Green Hood uses one (**BOW**).
- Hotbar slots 1–2: `all_<secondary|ultimate>_<ready|notready>` on **STICK**
  (`CosmeticManager.setupAbilitySlot` + `CooldownManager`).
- Ability visuals: every `model: {hero, texture[, skin]}` block in a hero yml, spawned via
  `VisualEffect`/`ItemDisplayBuilder` — the display ItemStack base is **STONE**, always
  (`ItemDisplayBuilder.modelData` hardcodes `Material.STONE`). **`skin` defaults to the
  literal string `"default"`** — ability visuals do NOT follow the selected skin, so every
  `_masterskin` variant in stone.json is currently unreachable (see Open flags).
- GUI buttons: `modelData("gui", section, name)` → `gui_<section>_<name>` on whatever
  material each GUI file picks (listed per key below).
- Map icons: raw `icon-model-key` from each live map yml, applied to the map's
  `display-item` material.
- Minions: `CosmeticManager.applyMinion` — owner-skin `piece_visuals.<piece>.head` override
  first, then the minion yml's `skin.head` override, then `<minionName>_head_default`.

**Statuses:**
- `EXISTS` — a `when` case for this key is present in the selector JSON.
- `MISSING` — the key is emitted at runtime but has **no** case in its selector file
  (renders the fallback model). Art AND a json entry needed.
- `ORPHAN` — case sits in the JSON but nothing emits the key anymore (safe to delete or
  keep as reserved art).
- `NO FILE` — the key's base material has no selector JSON at all yet.

Hero roster (19 configs; key prefix = yml `name:` lowercased):
blud, botmon, bug, bulk, cakemonster, cryomancer, greenhood, karakot, marauder, pochita,
pug, punishgers, railgunner, **sgt → name "Sgt Shield" → prefix `sgt shield` (bug!)**,
shoop, skullfire, spooderman, tinman, voidcrawler.

---

## 1. Hero hand items — `stick.json` (Green Hood → needs `bow.json`)

Keys: `<name>_item_<skinId>` for every skin id in the hero's `skins:` section.

| Key | Model Path | Status |
|---|---|---|
| blud_item_default / _masterskin | item/bludclub / item/bludclubms | EXISTS |
| botmon_item_default / _masterskin | item/botarang / item/botarangms | EXISTS |
| **bug_item_default / _masterskin** | suggest item/bugnail / item/bugnailms | **MISSING** — Bug is a full playable hero but has NO hand-item entries at all |
| bulk_item_default / _masterskin | item/bulkfist / item/abominationfist | EXISTS |
| cakemonster_item_default / _masterskin | item/rollingpin / item/cakepan | EXISTS |
| cryomancer_item_default / _masterskin | item/cryomancersword / item/cryomancerhammer | EXISTS |
| **greenhood_item_default / _masterskin** | item/greenhoodbow / item/greenhoodbowms | **NO FILE** — hand override is `material: BOW`; create `resourcepack/bow.json` (same select structure, fallback item/bow). The kit also puts a plain ARROW named "Quiver" in slot 9 (no key; intentional) |
| karakot_item_default / _masterskin | item/karakotfist / item/karakotfistms | EXISTS |
| marauder_item_default / _masterskin | item/lightsaber / item/unstablesabre | EXISTS |
| pochita_item_default / _masterskin | item/pochita / item/pochitams | EXISTS |
| **pochita_item_hell** | suggest item/pochitahell | **MISSING** — Hero Of Hell (smash) swaps to skin id `hell` for 10 s and re-applies the whole loadout |
| pug_item_default / _masterskin | item/pugfist / item/pugrattle | EXISTS |
| punishgers_item_default / _masterskin | item/punishgers / item/punishgersms | EXISTS |
| **railgunner_item_default / _masterskin** | reuse item/railgun / item/railgunms | **MISSING** — emitted prefix is `railgunner` (yml `name: Railgunner`); the pack still has the old `cluck_item_default` (item/railgun), `cluck_item_soldier` (item/mosin), `cluck_item_masterskin` (item/railgunms) — all three now ORPHAN for hand-item purposes (no hero named Cluck, and no `soldier` skin exists in railgunner.yml). Either rekey the json entries to `railgunner_...` or rename the yml. Note `cluck_item_default` IS still emitted as an ability visual — on STONE, see section 3 |
| **sgt shield_item_default / _masterskin** | item/sgtshield / item/sgtshieldms | **BROKEN** — yml `name: Sgt Shield` emits a key containing a space (`sgt shield_item_default`); the pack entries `sgt_item_default/_masterskin` never match. Fix = rename the yml `name:` to `Sgt` (or add space-key cases) |
| shoop_item_default / _masterskin | item/shooplaser / item/shooplaserms | EXISTS |
| skullfire_item_default / _masterskin | item/skullfiredeagle / item/skullfiredeaglems | EXISTS |
| skullfire_item_ult | item/skullfiredeagleflame | ORPHAN — no `ult` skin id exists and Blazing Deagle no longer swaps the hand model; keep the art if you want to re-wire it |
| spooderman_item_default / _masterskin | item/spoodermanfist / item/vanoomfist | EXISTS |
| tinman_item_default / _masterskin | item/tinmangun / item/mstinmangun | EXISTS |
| voidcrawler_item_default / _masterskin | item/voidsword / item/redsword | EXISTS |
| mario_item_default | item/mario | ORPHAN — no mario hero config exists (see Open flags) |
| — | item/sniper | orphaned model path from the old duplicate-case bug; unreferenced |

---

## 2. Hero heads — `sponge.json`

Keys: `<name>_head_<skinId>` (worn helmet, always SPONGE unless a `head:` override sets a
material — no hero currently does).

| Key | Model Path | Status |
|---|---|---|
| blud_head_default / _masterskin | block/bludhead / block/bludmshead | EXISTS |
| botmon_head_default / _masterskin | block/botmonhead / block/botmonmshead | EXISTS |
| **bug_head_default / _masterskin** | suggest block/bughead / block/bugmshead | **MISSING** — also breaks the Bug lobby NPC (section 7) |
| bulk_head_default / _masterskin | block/bulkhead / block/abominationhead | EXISTS |
| cakemonster_head_default / _masterskin | block/cakehead / block/cakemshead | EXISTS |
| cryomancer_head_default / _masterskin | block/cryomancerhead / block/cryomancermshead | EXISTS |
| greenhood_head_default / _masterskin | block/greenhoodhead / block/greenhoodmshead | EXISTS |
| karakot_head_default / _masterskin | block/karakothead / block/karakotmshead | EXISTS |
| marauder_head_default / _masterskin | block/marauderhead / block/maraudermshead | EXISTS |
| pochita_head_default / _masterskin | block/pochitahead / block/pochitamshead | EXISTS |
| **pochita_head_hell** | suggest block/pochitahellhead | **MISSING** — Hero Of Hell skin swap |
| pug_head_default / _masterskin | block/pughead / block/pugmshead | EXISTS |
| punishgers_head_default / _masterskin | block/punishgershead / block/punishgersmshead | EXISTS |
| **railgunner_head_default / _masterskin** | reuse block/cluck / block/cluckms | **MISSING** — same prefix problem as the hand item; `cluck_head_default` (block/cluck), `cluck_head_soldier` (block/soldier), `cluck_head_masterskin` (block/cluckms) are ORPHAN |
| **sgt shield_head_default / _masterskin** | block/sgthead / block/sgtmshead | **BROKEN** — space-in-key bug; pack entries `sgt_head_default/_masterskin` never match |
| shoop_head_default / _masterskin | block/shoophead / block/shoopmshead | EXISTS |
| skullfire_head_default / _masterskin | block/skullfirehead / block/skullfiremshead | EXISTS |
| spooderman_head_default / _masterskin | block/spoodermanhead / block/vanoomhead | EXISTS |
| tinman_head_default / _masterskin | block/tinmanhead / block/tinmanmshead | EXISTS |
| voidcrawler_head_default / _masterskin | block/voidcrawlerhead / block/voidcrawlermshead | EXISTS |
| mario_head_cape / _fire / _ice / _metal / _masterskin | block/mario* / block/buck | ORPHAN — no mario hero, and no cape/fire/ice/metal skin ids exist anywhere |

### Minion heads (also sponge.json; from `minion.skin.head` overrides + `piece_visuals`)

| Key | Emitter | Model Path | Status |
|---|---|---|---|
| spooderman_spooderbuddy_default | Spooder Buddy (SpooderFriends passive; also the buddies Cave Spooderman spawns) | block/spooderbuddy | EXISTS |
| spooderman_spooderbuddy_masterskin | B21.2 — owner on masterskin: `spooderman.yml skins.masterskin.piece_visuals.spooderbuddy.head` | block/vanoombuddy | EXISTS |
| **spooderman_spooder_default** | bug.yml Weaverling minion (`weaversong.minion.skin.head` → hero spooderman, texture `spooder`) | — | **MISSING** — likely a typo for `spooderbuddy`; either fix bug.yml or add the entry |
| **pug_basher_default** | pug.yml Basher minion head | suggest block/pugbasher | **MISSING** |
| **pug_princess_default** | pug.yml PRINCESS minion head | suggest block/pugprincess | **MISSING** |

> BotSettingsGUI additionally emits `<configKey>_head_default` (e.g. `railgunner_head_default`,
> `sgt_head_default`) on a **PLAYER_HEAD** item — NO FILE (no `player_head.json`). Note the
> prefix here is the *file* key, so it disagrees with the worn-helmet prefix for sgt
> (`sgt` vs `sgt shield`). Low priority — it's a settings-menu icon.

---

## 2.5. Worn armor — `equipment/*.json` (and optional item models)

Chest, legs, and boots render on the player body (and on lobby hero display NPCs via
`CosmeticManager.buildArmorEquipment`) through Minecraft's `equippable` data component rather
than `custom_model_data`.

**Hero YML syntax (optional, per piece under `skins.<skin>.armor.<chest|legs|boots>`):**
```yaml
chest:
  material: CHAINMAIL_CHESTPLATE
  equipment: cryomancer_masterskin   # sets equippable.asset_id (default namespace: minecraft)
  model: true                        # optional: also sets custom_model_data <hero>_chest_<skin>
```

**Naming convention:** `<hero>_<skin>` (lowercase asset key, e.g. `cryomancer_masterskin`).
Invalid keys throw an error at load/equip time naming the hero, skin, and piece.

**Resource pack folder layout** (default `minecraft` namespace, matching `sponge.json` / `stick.json` / `stone.json`):
- Equipment definition: `assets/minecraft/equipment/<hero>_<skin>.json`
  ```json
  {
    "layers": {
      "humanoid": [
        { "texture": "minecraft:<hero>_<skin>" }
      ],
      "humanoid_leggings": [
        { "texture": "minecraft:<hero>_<skin>" }
      ]
    }
  }
  ```
- Body layer texture (chest + boots): `assets/minecraft/textures/entity/equipment/humanoid/<hero>_<skin>.png`
- Leggings layer texture (legs): `assets/minecraft/textures/entity/equipment/humanoid_leggings/<hero>_<skin>.png`
- Optional inventory/item model (`model: true`): add `<hero>_chest_<skin>`, `<hero>_legs_<skin>`,
  and `<hero>_boots_<skin>` cases to `resourcepack/<material>.json` (e.g. `chainmail_chestplate.json`).

**Skins needing an equipment set:**

| Key | Base armor | Status |
|---|---|---|
| **cryomancer_masterskin** (and/or `cryomancer_default`) | `CHAINMAIL_*` (live config; #19) | **MISSING** — both Cryomancer skins currently share vanilla chainmail |

*(Add rows here as other non-leather or custom-patterned hero skins opt in to `equipment:`.)*

---

## 3. Ability visuals / projectiles / displays — `stone.json`

All `model:` blocks in ability `visuals:` spawn ItemDisplays whose ItemStack is **STONE**
(`ItemDisplayBuilder`). The `skin` component is always the literal `default` unless the yml
sets one explicitly (none do), so only `_default` keys are live.

| Key | Emitter (live yml) | Model Path | Status |
|---|---|---|---|
| blud_boomerang_default | Blud Boomerang projectile | item/bludclub | EXISTS (stopgap — dedicated thrown model welcome) |
| **botmon_botrang_default** | Botarang primary projectile (`texture: botrang`) | block/botarang | **MISSING** — pack has `botmon_botarang_default/_masterskin` (one-letter mismatch, both ORPHAN). Fix the yml texture or rekey the json |
| botmon_botbomb_default | Botbomb sub-projectile | block/botbomb | EXISTS |
| botmon_grapple_default | Grapple line display | block/grapple | EXISTS |
| botmon_botmobile_default | The Botmobile ridden display (scale 1.2) | block/botmobile | EXISTS (`_masterskin` unreachable) |
| **bug_dash_default** | Mothwing Cloak dash display | suggest block/bugdash | **MISSING** |
| **bug_spirit_default** | Focus neutral-spell projectile (scale 2.0) | suggest block/bugspirit | **MISSING** |
| bulk_car_default | Bulk Car projectile | block/bulkcar | EXISTS (`_masterskin` unreachable; explosion debris is now plain RED_SANDSTONE/GLASS — no keys) |
| **cake_cake_default** | Regurgicake + Cake Storm projectiles + Good Baker defecake (yml uses `hero: cake`) | block/cake | **MISSING** — pack's `cakemonster_cake_default` is ORPHAN (prefix mismatch). Either change the ymls to `hero: cakemonster` or rekey the json |
| **cake_dash_default** | Rolling Pin dash display | suggest block/cakedash | **MISSING** (same prefix issue) |
| **cluck_item_default** | Railgun primary beam display (railgunner.yml reuses hero cluck / texture item) | reuse item/railgun | **MISSING from stone.json** — the identical key in stick.json does NOT apply here (different base material). Add a stone entry, or give the beam its own texture (e.g. `cluck_beam`) |
| cluck_satchel_default | Satchel projectile | block/egg | EXISTS |
| cluck_rocket_default | Clusterbuck rocket | block/rocket | EXISTS (placeholder path from B14.3 — point at real rocket art) |
| karakot_ki_default | Ki Blast projectile | block/ki | EXISTS |
| karakot_kame_default / karakot_kametrail_default | Kame Do projectile / trail | block/kame / block/kametrail | EXISTS |
| karakot_ki_ult | — | block/kiult | ORPHAN — True Power no longer swaps the ki model |
| marauder_lightning_default | Force Lightning trail | block/lightning | EXISTS |
| marauder_lightning_skinplacehold | — | block/lightning | ORPHAN (placeholder skin id, nothing emits it) |
| marauder_sabre_default | Saber Throw projectile | item/lightsaber | EXISTS (`_masterskin` entry unreachable) |
| pug_mini_default / _masterskin | — | block/pugmini | ORPHAN — pug minis are now real minions wearing sponge heads (`pug_basher/princess_default`, section 2); no display model in pug.yml |
| sgt_shield_default | Ricochet seeking-shield projectile | item/sgtshield | EXISTS |
| shoop_laser_default | Laser projectile | block/laser | EXISTS (`_masterskin` unreachable) |
| shoop_rtl_default | RTL display (scale 1.5) | block/rtl | EXISTS |
| shoop_ult_default | Ult display (scale 2.0) | block/shoopult | EXISTS |
| skullfire_grenade_default | Grenade projectile | block/grenade | EXISTS (`_masterskin` unreachable) |
| **punishgers_molotov_default** | Molotov grenade projectile (4.0.1b: own visuals instead of skullfire's) | suggest block/molotov | **MISSING** — renders as the base item until the pack has it |
| **punishgers_stun_grenade_default** | Stun grenade projectile (4.0.1b) | suggest block/stungrenade | **MISSING** — renders as the base item until the pack has it |
| greenhood_tripmine_default | Tripmine marker (yml `visuals.tripmine_display`; currently a PARTICLE effect) | suggest block/tripmine | **NOT YET EMITTED** — switching `tripmine_display` to `type: ITEM_DISPLAY` with `model: {hero: greenhood, texture: tripmine}` makes the mine a model once the texture exists |
| spooderman_web_default | Shooting Ropes trail | block/web | EXISTS (`_masterskin` unreachable) |
| tinman_laser_default | Laser projectile | block/tinblast | EXISTS (`_masterskin` unreachable) |
| tinman_punch_default | Punch projectile + ult missile (`missile_model` reuses it) | block/tinpunch | EXISTS (`_masterskin` unreachable) |
| mario_projectile_default | — | block/mario | ORPHAN |

**Heroes with NO model-based ability visuals (particles/sounds only — nothing needed):**
- **Green Hood** (NEW): Arrow/Barrage fire real arrow entities; **Ballista** (secondary) is a
  hitscan — normal shot uses CRIT trail + EXPLOSION hit, the **overcharged** variant uses
  FLAME trail + EXPLOSION_EMITTER (all particles, no model keys); Gravity Arrow bursts a
  PORTAL ring; Roped Arrow / Tripmine are sound+particle.
- **Spooderman's Cave Spooderman** (NEW smash): activation sound + stat buffs only; the
  on-demand buddies are SpooderFriends minions (sponge head keys, section 2).
- **Pochita** (Chain Rev / Chain Slash / Chain Hook — particles), **Punishgers**,
  **Cryomancer**, **Voidcrawler**, **Blud's tracking + bludgeon modes** (particles),
  **Sgt's Shield Of Justice + Parry** (particles), **Bulk dash/smash/slam**, **Bug's
  up/down spells**.
- Config gap: **tinman.yml Overload `wave_visual`** declares `type: ITEM_DISPLAY` with a stray
  `texture: placeholder` and no `model:` block — it parses to nothing and spawns no display.
  Fix the yml if it should have art (key would be `tinman_<texture>_default`).

---

## 4. UI cooldown icons — `stick.json`

Slots 1–2 hotbar items (base STICK), set by `CosmeticManager.setupAbilitySlot` and flipped
live by `CooldownManager`.

| Key | Model Path | Status |
|---|---|---|
| all_secondary_ready | item/ui/secondary_ready | EXISTS (entry added B10 — confirm art) |
| all_secondary_notready | item/ui/secondary_notready | EXISTS (entry added B10 — confirm art) |
| all_ultimate_ready | item/ui/ultimate_ready | EXISTS (entry added B10 — confirm art) |
| all_ultimate_notready | item/ui/ultimate_notready | EXISTS (entry added B10 — confirm art) |

> RESOLVED 2026-07-10: hotbar items (weapon slot AND icon slots) are plain `minecraft:stick`.
> The CARROT_ON_A_STICK / consumable plan was dropped (B22.2); no extra selector file needed.
> Green Hood's BOW is the only non-stick hand item.

---

## 5. GUI keys — grouped by base material

Only sponge/stick/stone selector files exist. Every key below on any other material is
**NO FILE** until you create `resourcepack/<material>.json` (same select structure).

### Already covered by sponge.json — EXISTS

- `gui_heroes_<hero>` ×19 (HeroSelectGUI portraits, SPONGE): blud, botmon, bug, bulk,
  cakemonster, cryomancer, greenhood, karakot, marauder, pochita, pug, punishgers,
  railgunner, sgt, shoop, skullfire, spooderman, tinman, voidcrawler
  → `item/gui/heroes/<hero>` (portrait art still needed for most).
  These use the config-file key, so `gui_heroes_sgt` is correct (no space bug here).
- `gui_heroes_eliminated_<hero>` ×19 (series pick GUI) → all point at shared
  `item/gui/hero_eliminated` (split per-hero later if you want grayed portraits).

### NO FILE — everything below (grouped by material chosen in code)

| Base material | Keys (source) |
|---|---|
| ARROW | gui_navigation_prev, gui_navigation_next (GameMapGUI), gui_navigation_back (GameModeGUI, BotSettingsGUI, Tdm/Evolution/Series/Nuzlocke/Ironman/BossMode settings GUIs) |
| BARRIER | gui_general_close (HeroSelect, GameMap, GameSettings, PlayerSettings, CustomGame, Spectate, BugCharms) |
| LIME_STAINED_GLASS_PANE | gui_general_confirm (GameMapGUI) |
| RED_STAINED_GLASS_PANE | gui_general_confirm_disabled (GameMapGUI), gui_bot_remove (BotSettingsGUI) |
| GRAY_STAINED_GLASS_PANE | gui_general_filler (shared background filler, GUI.java) |
| GREEN_STAINED_GLASS_PANE | gui_bot_change_hero (BotSettingsGUI), gui_player_change_hero (PlayerSettingsGUI) |
| LIGHT_GRAY_STAINED_GLASS_PANE | gui_team_red_empty, _blue_empty, _green_empty, _yellow_empty (CustomGameGUI) |
| RED/BLUE/GREEN/YELLOW CONCRETE | gui_team_red / _blue / _green / _yellow (CustomGameGUI headers) |
| COMPASS | gui_customgame_gamemode |
| **YELLOW_STAINED_GLASS_PANE** | **gui_customgame_gamemode_accent** (NEW — accent panes around the gamemode button) |
| **EMERALD** | **gui_customgame_add_bot** (NEW) |
| NETHER_STAR | gui_customgame_settings, gui_maps_random, gui_settings_ultimate_on, gui_lobby_custom |
| DIAMOND | gui_customgame_start |
| GRAY_CONCRETE | gui_gamemode_default |
| RED_CONCRETE | gui_gamemode_boss |
| BLUE_CONCRETE | gui_gamemode_tdm |
| GREEN_CONCRETE | gui_gamemode_evolution |
| **CYAN_CONCRETE** | **gui_gamemode_series** (NEW) |
| PURPLE_CONCRETE | gui_gamemode_nuzlocke |
| ORANGE_CONCRETE | gui_gamemode_ironman |
| PAPER | gui_settings_lives (PlayerRows), gui_charms_* (see below) |
| **GOLDEN_APPLE** | **gui_settings_health** (NEW — replaces the removed gui_settings_prestige_on/off) |
| COAL | gui_settings_ultimate_off |
| GOLDEN_SWORD / STONE_SWORD | gui_settings_friendly_on / _off |
| ENDER_PEARL / ENDER_EYE | gui_settings_random_on / _off |
| END_CRYSTAL / GLASS | gui_settings_crystals_on / _off (GameSettingsGUI) |
| OAK_SIGN | gui_settings_map |
| RED_SANDSTONE_WALL / SANDSTONE_WALL | gui_settings_walls_on / _off |
| CLOCK | gui_settings_timer |
| RED_BED | gui_series_leave (HeroSelectGUI, series mode) |
| NAME_TAG | gui_player_reset_hero (HeroSelectGUI override mode) |
| HOPPER | gui_maps_filter (GameMapGUI) |
| IRON_SWORD | gui_lobby_1v1, gui_lobby_2v2 (LobbyMenuGUI) |
| PLAYER_HEAD | gui_lobby_hero_select (LobbyMenuGUI); also BotSettingsGUI's `<hero>_head_default` display (section 2 note) |
| REDSTONE | gui_lobby_settings (LobbyMenuGUI) |
| LIGHT_GRAY_CONCRETE | gui_charms_notch_used (BugCharmsGUI) |
| WHITE_CONCRETE | gui_charms_notch_free (BugCharmsGUI) |

### Bug charm keys (BugCharmsGUI, PAPER) — NEW to this manifest

`gui_charms_<texture>` ×15; texture defaults to the enum name lowercased (overridable per
charm via `charms.<name>_texture` in bug.yml — none currently set):
`gui_charms_dashmaster, _grubsong, _heavy_blow, _kingsoul, _mark_of_pride, _nail_master,
_quick_slash, _shaman_stone, _sharp_shadow, _soul_eater, _soul_twister, _sprintmaster,
_steady_body, _strength, _weaversong` — suggest `item/gui/charms/<name>`. All NO FILE.

### GUI items with NO model key at all (nothing to do unless you want art there)

Tdm kill-limit/start-HP/re-pick, Series best-of/re-pick, Nuzlocke stocks/HP-carry/lock-in/
eliminate-target, Ironman stocks/clear-target, BossMode lives/HP/lives-per-enemy, Spectate
player heads, PlayerSettingsGUI player head, TDM/Series toggles (ENDER_PEARL/EYE), and
**locked heroes** (GRAY_DYE, HeroSelectGUI) — assign e.g. `gui_heroes_locked_<hero>` if you
want locked art.

Removed since the last pass: `gui_settings_prestige_on/_off` (prestige toggle deleted; the
slot is now Starting Health = `gui_settings_health`), `gui_customgame_bots_disabled` (the
bots-disabled barrier is gone; Add Bot is always shown).

> Simplification option still stands: re-base all GUI buttons onto one or two materials so
> you only need one or two selector files.

---

## 6. Map icons — raw keys (`map_<name>_default`)

Every live map yml carries `icon-model-key`; GameMapGUI applies it to the map's
`display-item`. **All 45 are NO FILE** (none of these materials has a selector json).
Suggested art path: `item/maps/<name>`. Strong candidate for the "re-base onto one
material" simplification — one selector file instead of ~45.

| Key | Base material |
|---|---|
| map_agora_default | STONE_BRICK_SLAB |
| map_apex_default | RED_WOOL |
| map_arcade_default | RED_TERRACOTTA |
| map_athens_default | IRON_SWORD |
| map_atlantis_default | DARK_PRISMARINE |
| map_bastion_default | SPRUCE_PLANKS |
| map_bawk_default | COOKED_CHICKEN |
| map_blossom_default | TORCHFLOWER |
| map_burgers_default | BEEF |
| map_circled_default | MAGMA_CREAM |
| map_cobalt_default | LIGHT_BLUE_TERRACOTTA |
| map_cogsworth_default | OAK_PLANKS |
| map_color_clash_default | ORANGE_TERRACOTTA |
| map_cosmos_default | ENDER_PEARL |
| map_courtyard_default | LIGHT_BLUE_STAINED_GLASS |
| map_darkside_default | BLACKSTONE |
| map_deep_blue_default | SAND |
| map_dojo1_default | NETHER_BRICKS |
| map_dojo2_default | CHISELED_NETHER_BRICKS |
| map_dystopia_default | AMETHYST_SHARD |
| map_equinox_default | OAK_WOOD |
| map_gateway_default | QUARTZ_BLOCK |
| map_grave_default | COBBLESTONE_STAIRS |
| map_grove_default | GRASS_BLOCK |
| map_gunmetal_default | GRANITE |
| map_harmony_default | PALE_OAK_LOG |
| map_luxor_default | SANDSTONE |
| map_marsh1_default | LILY_PAD |
| map_marsh2_default | AZALEA |
| map_mosaic_default | DARK_OAK_PLANKS |
| map_penis_default | REDSTONE_BLOCK |
| map_quareen_default | CHISELED_SANDSTONE |
| map_reconcile_default | STONE_BRICK_STAIRS |
| map_remains_default | CHISELED_STONE_BRICKS |
| map_royale_default | DIAMOND_SWORD |
| map_shard_default | ECHO_SHARD |
| map_skyline_default | CYAN_STAINED_GLASS |
| map_solaris_default | TUFF_BRICKS |
| map_sparta_default | GOLDEN_HELMET |
| map_strawberry_default | BEETROOT |
| map_subzero_default | SNOW_BLOCK |
| map_swift_default | BROWN_TERRACOTTA |
| map_toybox_default | SMOOTH_QUARTZ |
| map_triplets_default | GREEN_TERRACOTTA |
| map_western_default | ACACIA_PLANKS |

---

## 7. NPC / lobby

- **Hero display NPCs** (`lobby.yml` → `HeroDisplayNpc`): 7 NPCs, each wearing a **SPONGE**
  helmet keyed from the yml's `armor.head` block (`<hero>_head_default`):
  bulk, pug, voidcrawler, skullfire, shoop, spooderman → EXISTS (same entries as section 2);
  **bug → `bug_head_default` MISSING** — the Bug NPC currently shows a plain sponge.
- **Announcer NPC**: holds a plain BOOK — no model key, nothing needed.
- **Leaderboard**: TextDisplay entity stack — no models involved.

---

## Known bugs — fixed in Batch 10 (still true)

1. **stick.json** — duplicate `cluck_item_default` case (pointed at `item/sniper`) deleted;
   railgun renders from `item/railgun`. `item/sniper` remains orphaned art.
2. **stone.json** — `cakemonster_cake_default` was missing its closing brace; the entire
   file was invalid JSON. Fixed and revalidated. (The key itself is now an orphan — the
   live ymls emit `cake_cake_default`; see section 3.)
3. **marauder.yml** — `texture: null` on saberthrow emitted `marauder_null_default`;
   renamed to `sabre` in the **live** yml + stone.json entries added. (The copy under
   `src\main\resources\heroes\marauder.yml` still says `texture: null` — runtime loads the
   live folder, so this only matters if resources are ever re-copied over the live files.)

## Open flags (updated 2026-07-11)

- **Naming mismatches to decide** (rename yml vs rekey json — each breaks the other side):
  1. `sgt.yml name: Sgt Shield` → all head/item keys contain a space and match nothing.
  2. `railgunner.yml name: Railgunner` vs pack prefix `cluck` for head/item.
  3. cakemonster ability models use `hero: cake` vs pack prefix `cakemonster`.
  4. botmon primary uses `texture: botrang` vs pack `botarang`.
- **Bug (the hero) has zero head/item/ability-model entries** — the biggest single art gap
  (bug_head_*, bug_item_*, bug_dash_default, bug_spirit_default) and it breaks the lobby NPC.
- **Ability visuals never follow skins**: `VisualEffect` hardcodes the model skin to
  `"default"` when the yml omits it. All `_masterskin` cases in stone.json are unreachable
  until either the ymls gain explicit `skin:` fields or the code substitutes the wearer's
  skin id. Decide before drawing master-skin projectile art.
- **mario: RESOLVED** — there is no mario hero config (live or resources); every `mario_*`
  key (heads ×5, item, projectile) is an orphan. `mario_head_masterskin → block/buck`
  included. Delete or keep as reserved art.
- **`cluck_item_soldier` / `cluck_head_soldier`**: no `soldier` skin exists in
  railgunner.yml — orphans (item/mosin, block/soldier art unreferenced).
- **`skullfire_item_ult` / `karakot_ki_ult`**: orphans — neither ult swaps models anymore.
- **tinman Overload `wave_visual`**: malformed (no `model:` block) — currently renders
  nothing at all; needs a yml fix before any art shows.
- ~~carrot_on_a_stick vs stick~~ — RESOLVED 2026-07-10: everything hotbar is plain STICK;
  only Green Hood's **bow.json** selector file is still outstanding.
- `marauder_lightning_skinplacehold` — orphan placeholder, left as-is.
