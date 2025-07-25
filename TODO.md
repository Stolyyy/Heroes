# Heroes Plugin To-Do List

## 🗝️ Key
- [Utility] = Helper classes/utilities
- [Design] = Architecture & refactoring
- [Bug Fixing] = Fixing existing bugs
- [Game] = Core gameplay features
- [Ability] = Ability-specific logic
- [Character] = Character abilities or visuals
- [GUI] = User interface components
- [NPC] = Non-player character logic
- [Balance] = Game balance tuning
- [Outside] = External assets or research

## 🔥 Critical  Priority
- [X] [Utility] Particle class (ring, idk)
- [X] [Utility] Sound class
- [X] [Design] Implement Hitboxes where needed (Heroes and abilities)
- [X] [Design] move maps out of listeners and into the Heroes themselves

## 🟡 High Priority
- [X] [Design] Figure out relationship between heroes, abilities, ability interfaces, and abilityData 
- [X] [Design] Move cooldown logic to ability class
- [X] [Utility] Wall Detection using Ray Casting
- [X] [Design] Hitscan/Projectile Pierce options (Potentially options class?)
- [X] [Bug Fixing] Cooldown display (% done) is not displaying correctly (bc unbreakable?)
- [X?] [Bug Fixing] Game end logic is not correct (checkGameEnd)
- [X] [Ability] Fix gravity with projectiles and the collision with the ground
- [x] [Ability] Options classes for hitscan + projectile (and more if needed)
- [X] [Bug Fixing] players who were spectators and are being swapped to players need to be restricted (partyGUI)
- [?] [Bug Fixing] Option for hitboxes to penetrate walls or not


## 🔵 Medium Priority
- [X] [Game] add some level of vertical kb (especially when target is on ground) to all interactions
- [X] [Design] Cleanup interactions class
- [ ] [Design] Skullfire method extraction (into reloadable)
- [X] [Bug Fixing] End runnables when player dies or is not in game
- [X] [Bug Fixing] Reset stats when game ends/starts (so u dont carry 5 charges etc to new game), call stats?
- [X?] [Design] Listener Cleanup
- [X] [Bug Fixing] Cancel button in map selection
- [X?] [Bug Fixing] Scoreboard lives always display as 3 for the person it is displaying it to
- [X] [Bug Fixing] Shoop RTL not in walls (maybe subtract from the location vector)
- [X] [GUI/Character] Bug charm menu
- [X] [Character] Bug charm display in inventory (and descriptions)
- [X] [GUI] Team-specific settings (lives, friendly fire, ults, random heroes) 
- [X] [Game] Implement all the maps
- [X] [Game] Kill wall/ceiling options

## ⚪ Low Priority
- [X] [Game] reload when u die for characters that need it
- [X] [Bug Fixing] Dropping stacked items (i.e. skullfire's gun) puts a new item in your inventory
- [X] [Design] Refactor Minigame package to be more readable
- [X] [Design] Refactor Party package to be more readable
- [X] [Character] Skullfire energy display (for djs)
- [X] [Bug Fixing] Infinite ultimate is ready spam (spectator press 3 glitch?)
- [X] [Outside] Get OG maps (only good ones)
- [ ] [Character] Spooderman
- [X] [Character] Knight or Bulk
- [X] [Bug Fixing] Remove swapping heroes mid-game
- [ ] [Balance] Fine tune numbers (mostly kb)
- [ ] [Design] Stronger encapsulation (try to keep things within packages)
- [X] [GUI] Game settings GUI
- [ ] [GUI] New Hero Selection methods
- [ ] [GUI] Announcer GUI
- [ ] [NPC] npcs to join game (announcer)
- [ ] [Design] JSON Files for balancing
- [X] [Design] JSON Files for map locations


## 🔴 Testing Required
- [ ] [Pug] Pug's Ultimate
- [ ] [Spectating] Spectating (can you tp to players in lobby, activate abilities, etc)
- [ ] [Commands] All Commands
- [ ] [Wall Detection] make sure it works around tight spaces (i.e. shoop lazor)


## 💡 Backlog / Ideas
- [ ] Bloonslayer moveset
- [ ] Ultimates for HK, Spood, Punishgers
- [ ] Elo System
- [ ] Stats (API: kills, dmg, wins, etc)
- [ ] Leaderboard
- [ ] Level System
- [ ] Skins & Cosmetics (Kill effects, new textures)
- [ ] 16-24 Heroes
- [ ] Match Timer
- [ ] Bots
- [ ] boss mode (fight bosses as a team)
- [ ] coin system to buy new characters + cosmetics
