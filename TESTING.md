# Playtest Bug Tracker

## How to Use
1. **During playtesting**: Add bugs to [New Bugs](#new-bugs) — just a quick line per bug
2. **After playtesting**: Tell Claude to read TESTING.md and fix bugs
3. **After fixes**: Claude moves completed items to [Recently Completed](#recently-completed)

Format: `- [ ] Short description. (Details, steps to reproduce, error messages if any)`

---

## Newest Session

-practice doesnt rly work -- cant use abilties and u fly instead of dfouble jumping
-ensure that all heroes are available in GUI for ironman/nuzlocke (even if u dont have those heroes) and ensure u can play them in evolution too.
-no way of seeing the levels on each hero (should prob be in lore)
-ymls need to be adjusted for new heroes + abilities + visuals on default
-finish coding the remaining ablities
-token earning message in chat
-need hero names above lobby heroes
-need to recreate lobby, add barriers, and define training area
-fix smash crystal display
-need a list of exactly what textures we need to find still
-green hood needs delay between barrage arrows
-arrows should follow vanilla trajectory
-clusterbuck should load it up and then fire on next right click
-gravtiy arrow should work like tripmine -- activate to load that arrow then next one you shoot is a gravity arrow.
-pochita right click should only move while holding it down. make it consume energy while active and start cooldown once right click is lifted
-leaderboards in lobby are stacking on restart
-sgt starts with ult
-sgt ricochet shouldn't bounce to/seek players it already hit.
-prob make sgt dash only do the delay after parry hit
-parry should be energy based
-needs indicator when next attack is powered
-make ricochet easier to pick up when close to player
-check pug minion ability cooldowns
-pug dash can be spammed (it ignores its cooldown)
-cake dash can be spammed too
-defecake doesnt throw out any projectile
-time warp needs an initial energy cost
-AbilityListener has problems with the firing logic. it can probably simply just call execute as that checks if the ability can be fired or not
-don't need the chat messages for not ready yet if its a primary
-time warp needs a ring around the return location
-xp progress in chat after game (You gained - xp, you are now at - / - for level - (-%)
-clear inventory when leaving practice
-add bot functionality to customs
-satchel shouldn't consume a charge when exploding an existing one
-satchel should be stock ammo, so it charges 1 at a time.
-evolution should have a number of wins variable
-evolution needs a diff scoreboard display thats not lives. TDM too
-xp being granted to the wrong hero after evolving in evolution, likely an issue with other gamemodes as well but havent checked
-bulk car visuals need cleanup once hero is null
-dying to void on last life (if it makes the game end) makes some errors in the console and makes u start taking void damage









[]-Still two stacked leaderboards in lobby
[]-projectiles look vertical
?-dj momentum reset
[]-tin ult goes through walls
?-tin ult still not rising enough
[]-tin ult is choppy
-minions arent moving and cant hit, but are using abilities
[]-botmobile is choppy
[]-grapple thing wrong direction
[]-web wrong direction
?-defecake still not doing anything
?-need 0 hp buffer
[]-dash hitbox buffer not really working
[]-tweaking done stachels
--bigger radius
--ding sound
--stronger satchel strength, different scaling?
-wins counting in custom
[]-web does kb not pull
[]-no more messages about primary cooldown in chat pls
-no kill sound
-no reload on death
-no reload on q
-ensure projectile cleanup on elimination
-pressing q with multiple stacks
[]-no ding on grenade
[]-no triple shot reset
[]-triple shot shoots 3 at once
-u can interact with blast furnaces and stuff
[]-/hero -> bug doesnt select it
-heavy blow needs coding
[]-gaining soul when u hit someone with secondary (sepcifically neutral, bug with nail master)
[]-red stuff in frostbreath
?-aoe is not working
[]-car explosion visual is a little too fast/spread and choppy
-ult sounds arent global
[]-can go into a custom if u arent leader
[]-sphere needs wall detection (is there a wall in this sphere)











## New Bugs

<!-- Add bugs here during playtesting. One line each. -->

[]- glitch with persistent games
[]- bulk car explosion visuals not working
- need a way for minion hit sounds to be played to their owner
- check if minions ever get attributes applied
- pug ult doesnt move around (stuck in air) and cant interact (but do try to jab)
[]- if spectating someone and you go in their pov, you "respawn" when they die
[]- party should disband if alone and no invites
[]- need a sound played on kill
[]- kills should go to whoever damaged them last, unless they were not damaged. mc killer detection has a timeout period where if you dont touch them for a while you dont get the kill
[]- tracking projectiles should be abstracted (tinman ult + saber throw)
[]- abstract the stun mechanic
[]- tracking is a bit off when it gets close to the player

# new features to be added
- add hitboxes hack but server side (jabs)
[]- maybe an option to do /join custom {map} to join a custom game with a map pre-selected
[]- use the cake monster ult format for duration abilities (other ults)
[]- recreate charm system -- use a charm abstract class with boolean equipped and methods equip and unequip. map the enum to the charms
[]- fix the respawn logic with resetting stuff (energy / stats) -- energy stays the same, stats should be reset by 'ending' abilities that modified them.
[]- botmon ult teleport with entity flag
[]- material override in equipment

- **IMPORTANT** Refactor the Bug charms section in the YML to match the new format

# edge cases
[]- kills are being tracked per game but not updated in stats / lobby scoreboard

---

## Known Issues (Backlog)

<!-- Bugs that are acknowledged but deferred or need more info. -->

- Skullfire grenade fires weird — likely YML config values, not code
- Projectile model textures broken — stone.json has syntax error at ~line 28 (missing closing brace); fix in resource pack
- Tether passives need to be added to pug/spooder minion YMLs (tether ability is registered, just needs config)


---

## Recently Completed

<!-- Cleared at the start of each session. -->

### Crashes & NPEs
- BugCharms NPE on game join — `initialize()` called before controller set; deferred charm loading to first `tick()` via `lateInitialize()`
- MarauderSaberThrow NaN crash — near-zero return vector caused NaN after normalize; raised threshold to 0.01 + added NaN guard with cleanup
- PugIntruders NPE — minion entities not registered in GameManager; now registered on spawn and unregistered on cleanup

### Core System Fixes
- AOE passes null ability — `Aoe.detonate()` passed null to `triggerAbilityHit`; added `Ability sourceAbility` field to Aoe constructor, callers pass `this`
- CLIP ammo only 1 shot then full cooldown — `cooldown.start(activeDuration)` set `lastUsed` triggering full cooldown; added `Cooldown.startActive()` that only sets active phase without cooldown timer
- Uppercase sound keys crash — `ENTITY_EXPERIENCE_ORB_PICKUP` rejected by Minecraft; `SoundBuilder` now lowercases all string sound keys (YML entries still need manual lowercase)
- Primary "on cooldown" chat spam — `sendNotReadyVisuals()` fired every tick; added 1-second throttle + added cooldown time in seconds to action bar
- Ult title flicker — sent every tick with fade-in; now sent every 20 ticks with `times(0, 30, 5)` (no fade-in)
- Unbreakable hiding durability bar — `unbreakable(true)` on ability items hid cooldown progress; removed from ability slot items, kept on head/armor
- Choppy projectile movement — ItemDisplay entities had no interpolation; added `interpolationDuration(2)` on spawn + `setInterpolationDelay(0)` each tick

### Broken Abilities
- Botmobile not moving — velocity was set on ItemDisplay, not the player; now moves player directly, display follows via teleport
- ShoopRTL not spawning projectile — `perform()` overrode parent without calling `super.perform(hero)`; added the call
- Ki Blast wrong hand order — fired right then left; renamed `isLeftShot` → `isRightShot` and swapped initial value

### Individual Ability Fixes
- GoodBaker puts primary on cooldown — `Cooldown.reset()` set `lastUsed = now`; added `forceReady()` method that zeros everything
- VoidTeleboom changes facing direction — teleport location didn't preserve yaw/pitch; captured and set them before teleport
- Defecake uses 100 energy — CakePoop shared config section with Defecake, double-consuming energy; overrode `startCooldown()` in CakePoop to skip energy
- Dash hitbox disappears too fast — added configurable `hitbox_grace_ticks` (default 4) that continues collision checks after dash movement ends
- Pug Pounce no upfront energy cost — only had passive drain; added upfront `energy_cost` consumption in `perform()` before charging begins
- Tinman ult follows to respawn — missiles retargeted respawning players; added respawn filter to target validity check and `findClosestEnemy()`
- SpooderWeb KB direction off — pull went through standard direction processing; added `.rawDirection()` to Interactions that bypasses normalize + pitch-angle adjustments (web only)
- VoidShadowFury darkness — added `.excludeTeammates()` to hitbox scan + debug logging to diagnose if `isInUse()` is failing
- Botclaw no sneak cancel — `GrappleCancel` class existed but was never registered; added `isSneaking()` check directly in `tickGrapple()` + max range auto-cancel

### Edge Cases
- Player hidden after game ends during respawn — `handleRespawn()` skipped `showPlayer()` when game ended; added un-hide logic in `end()` and `removePlayer()`
- Smash crystal text stays after pickup — text entity not explicitly removed; now removed before display entity
- Health display goes to 0 — `GameVisuals.reset()` cleared health objectives at game end; removed health score clearing from `reset()`

### New Features
- `/forceready primary|secondary|smash|all` admin command for testing
- Configurable reload sound — `Ammo` loads `reload_sound` from config, played when clip empties
- `/balance <hero> default` — reloads hero config from disk
- Joining party unqueues player — `removeFromAllQueues()` called on party accept
- Force leave all queues on game join — `GameManager` removes from queues before adding to any game
- Pain sounds on hit — `{heroname}.pain` played via `onDamageTaken` listener in HeroBuilder
- Jab knockback configurable — added `jab_knockback` to `default.yml` and `CombatStats`, replaces hardcoded `1.5`
- ShoopSmash periodic loop sound — `loopSound` + `loopSoundInterval` fields, same pattern as Botmobile
- Bot button disabled in CustomGameGUI — replaced with barrier item
- Team swap GUI empty slots — filled with light gray glass panes, clickable for team reassignment
- Charm data persistence — saved as string list in player stats YML files, loaded on demand
- 1v1 party rejection message — added to messages.yml

# Visual & Sounds
- jab hit sounds
- jab swing sounds (on a cooldown)
- item displays that are partially in blocks (like shockwave visuals) are very dark
- eventually I'd like some more modularity with the visuals and sounds where I could put multiple entries per each one.
- as an example, for a trail visual, I could have one normal particle and an oriented ring around it. This would currently require coding another variable, but I'd love for it to be something i could do from the ymls directly by modifying the classes for visuals
- same thing as the last entry but with sounds. i'd like to be able to support multiple sounds for the same thing (i.e. two sounds played on hit)
- will eventually need some more trail customizability. some trails may stay as long as the projectile is up, others might fade as they follow it, spacing might need to be based on distance to create a continuous model stream, etc.


---

# Testing Checklist

## Lobby & Menus
- [X] Spawn at lobby on join
- [X] Lobby scoreboard shows: ELO, wins, kills, selected hero
- [X] Hero NPCs display correct skins (sponge head, colored armor)
- [X] Clicking hero NPC selects hero + updates scoreboard
- [X] `/hero <name>` selects hero + updates scoreboard
- [X] Announcer NPC opens game mode GUI
- [X] 1v1 queue join/leave
- [X] 2v2 queue join/leave (solo)
- [X] 2v2 queue join/leave (party of 2)
- [X] Queue timer displays correctly
- [X] Custom game GUI opens, settings work
- [ ] Add/remove bots in custom game
- [X] Map selection GUI works
- [ ] Team settings GUI works
- [X] Bug charms GUI opens when selecting Bug hero

## Game Flow
- [X] Queue pops and teleports players to arena
- [X] Countdown timer displays (7s default)
- [X] Hero created on game start (equipment, abilities active)
- [X] Players set to Adventure mode
- [X] Game starts after countdown
- [X] Game timer displays on scoreboard
- [X] Smash crystal spawns periodically
- [X] Smash crystal pickup charges ultimate
- [X] Death triggers respawn timer (7s)
- [X] Respawn teleports to team spawn
- [X] Lives decrement on death
- [X] Elimination when lives = 0
- [X] Game ends when one team remains
- [X] Winner message shows team + player names
- [X] Kill stats display correctly (top 3)
- [X] ELO updates after ranked game (1v1/2v2)
- [X] Players teleported to lobby after game
- [X] Player stats reset properly (health, effects, flight)

## Combat & Mechanics
- [X] Jab (left click) deals damage + knockback
- [X] Jab does NOT cause recursion crash
- [X] Double jump works (2 jumps from default.yml)
- [X] Double jump resets on land
- [X] Energy regenerates over time
- [X] Energy bar displays correctly
- [ ] Cooldowns display on items
- [X] Cooldown prevents ability re-use
- [X] Knockback scales with damage taken (smash-style)
- [X] Fall into void = death
- [ ] Can't damage teammates
- [ ] Can't damage during respawn invincibility
- [ ] Spectating works after elimination

## Ability Templates
- [X] **Projectile**: Fires, travels, hits players, hits walls, expires at range
- [X] **Projectile visuals**: Single display entity (no stacking)
- [ ] **Projectile trail**: Trail particles spawn along path
- [X] **Projectile burst**: Multi-shot fires correctly
- [X] **Projectile AOE**: Explodes on hit/wall/expire if configured
- [X] **Hitscan**: Instant raycast hit detection
- [X] **Dash**: Movement + collision damage
- [X] **Cone**: Area damage in front of player
- [X] **Shockwave**: Ground slam AOE
- [ ] **Ammo**: Reload mechanic works

## Hero Testing
Test each hero's full kit: primary, secondary, mobility, passive, ultimate.

### Melee Heroes
- [X] **Bulk** — Smash (ult), Slam, Dash, Car (mobility)
- [ ] **Pug** — Bork, Pounce (charge), Dash, Intruders (ult)
- [ ] **Voidcrawler** — Teleboom, Shadow Fury (ult), Dash, Energy passive
- [ ] **Marauder** — Saber Throw, Lightning, Dark Jump, Grip, Ultimate

### Ranged Heroes
- [ ] **Shoop** — Primary (projectile), Secondary, RTL, Smash (ult)
- [ ] **Skullfire** — Deagle, Blazing Deagle, Grenade, Bullseye (ult), Mobility
- [ ] **Punishgers** — Weapon, Secondary
- [ ] **Railgunner** — Railgun, Satchel, Head Hunter, Ray of Doom (ult)
- [ ] **Tinman** — Laser, Rocket Punch, Seeking Missiles (ult), Overload

### Hybrid Heroes
- [ ] **Spooderman** — Web, Climb, Dash, Friends (minions), Ultimate
- [ ] **Bug** — Spell, Dash, Charms (passive), Void Beast (ult)
- [ ] **Cryomancer** — Frostbolt, Frostbreath, Frost Aura, Time Warp, Ultimate
- [ ] **Botmon** — Botrang, Botclaw, Botglide, Botmobile
- [ ] **Karakot** — Ki Blast, Kame Do, Teleport, Ultimate
- [ ] **Cake Monster** — Rolling Pin, Regurgicake, Defecake, Good Baker, Cake Storm (ult)

## Party System
- [X] `/party invite <player>` sends invite
- [X] `/party accept` joins party
- [ ] `/party leave` leaves party
- [ ] `/party kick <player>` removes member
- [X] `/party disband` disbands
- [ ] `/pc <message>` sends party chat
- [X] Party leader can queue for 2v2
- [ ] Party members placed on same team

## Commands
- [X] `/hero <name>` — select hero
- [X] `/join` — join game/queue
- [X] `/lobby` — return to lobby
- [ ] `/spectate` — spectate a game
- [ ] `/stuck` — unstuck command
- [ ] `/charms` — open charms GUI
- [ ] `/mapadmin` — map management (op only)
- [ ] `/balance` — balance testing (op only)

## Edge Cases
- [X] Player disconnects mid-game (cleanup, team update)
- [ ] Player disconnects during queue
- [ ] Player disconnects during countdown
- [ ] Trying to join queue while already in game
- [X] Selecting hero while in game
- [ ] All players leave a game
- [ ] Bot behavior in custom game
- [ ] Two games running simultaneously
