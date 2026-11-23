# Deployment: open beta on Paper 1.21.11 via Pelican

> Written 2026-10-28 for the week-long open beta. Fresh server on the Pelican panel, Minecraft 1.21.11,
> no ViaVersion (players must join on 1.21.11). The 26.3 migration happens after the beta; see
> `planning/RELEASE-PLAN.md`. Pelican facts below were checked against the pelican-eggs and pelican-dev
> sources on 2026-10-28; a few are marked "confirm" where the docs were silent.

## 0. Before touching anything

1. Zip the current `Heroes Server` folder as it is today (templates, `plugins/Heroes/`, `world/`). It is the
   only copy of the maps and the live configs.
2. Stop the old Windows server if it is running. The Gradle build writes straight onto
   `plugins/Heroes-1.0-SNAPSHOT.jar` in that folder, and overwriting a running jar causes `NoClassDefFoundError`.

## 1. Build the jar

The jar on the old server is from 2026-08-27; about 70 source files are newer, plus today's launch changes.

```bash
powershell.exe -Command "Set-Location 'C:\Users\willi\IdeaProjects\Heroes'; .\gradlew.bat build 2>&1"
```

The jar lands in `C:\Users\willi\OneDrive\Desktop\Heroes Server\plugins\Heroes-1.0-SNAPSHOT.jar`. Keep a copy
named with today's date for rollback.

## 2. Versions (checked 2026-10-28)

| Component | Old server | Use for the beta | Notes |
|---|---|---|---|
| Paper | 1.21.11-131 | **1.21.11-132** (final build, 2026-05-11) | One internal NBT fix after #131, no API change. Paper's 1.21.11 support ended 2026-06-15. The Pelican egg downloads it for you (§4). |
| Java | JDK 25 | `ghcr.io/pelican-eggs/yolks:java_25` | Temurin 25 on Ubuntu 24.04. Your Aikar flags were validated on 25. |
| Citizens | 2.0.41 b4086 | b4086 or **2.0.43 b4250** | b4250 (2026-09-11) is the last build that loads on 1.21.11; later builds are 26.x only. The Goal API was removed between them; Heroes does not use it. |
| **PacketEvents** | not installed | **install** (packetevents-spigot 2.x) | Citizens b4086+ softdepends on PacketEvents, not ProtocolLib. The bot head-rotation hook (`setNavigationLookAt`) is a silent no-op without it, which is the state the old server has been in since February. |
| ProtocolLib | 5.5.0-SNAPSHOT | **remove** | Nothing uses it any more. Glow is GlowingEntities shaded into the jar with its own packet injection. |
| LuckPerms | 5.5.53 | 5.5.53 or 5.5.88 | Same 5.5 API. |
| TCPShield plugin | not installed | 2.8.1 | Vendor lists testing only up to 1.20.2; verify on first boot (§8). |
| spark | installed | keep | |

Downloads: Citizens https://ci.citizensnpcs.co/job/Citizens2/4250/, PacketEvents https://modrinth.com/plugin/packetevents
(the Spigot/Paper platform jar), LuckPerms https://luckperms.net/download, TCPShield
https://github.com/TCPShield/RealIP/releases/tag/2.8.1.

## 3. Pelican facts you are building on

- **Wings is Linux + Docker only.** It will not run on Windows. The server files live in the server's volume on the
  node, so the OneDrive problem from the Windows setup disappears.
- **The Paper egg** (`pelican-eggs/minecraft`, `java/paper`): the install script downloads the Paper jar from the
  Fill API into `SERVER_JARFILE`, renames any existing jar to `.old`, and writes a default `server.properties` if
  none exists. It does **not** write `eula.txt`; the panel shows an accept prompt on first start.
- **Egg variables:** `MINECRAFT_VERSION` (set `1.21.11`), `BUILD_NUMBER` (set `132`), `SERVER_JARFILE`
  (default `server.jar`), `USER_AGENT` (admin-only, **must be non-empty or the install script exits**; set it
  on the egg to something like `heroes-beta/1.0`).
- **Every start rewrites `server.properties`:** `server-ip` to empty, `server-port` and `query.port` to the
  server's primary allocation. Do not hand-edit the port; set the allocation to 25565.
- **Default startup command** is `java -Xms128M -XX:MaxRAMPercentage=95.0 -Dterminal.jline=false
  -Dterminal.ansi=true -jar {{SERVER_JARFILE}}`. No Aikar flags. You replace it in §5.
- **Crash detection** is on node-wide by default: a server that exits without a stop from the panel is restarted,
  unless it crashed twice within 60 seconds (boot-loop guard). A clean exit also counts as a crash. No restart
  loop script needed.
- **Uploads:** the web file manager caps at 100 MiB per file by default (node setting "Upload Limit"). SFTP on the
  node's port 2022 has no cap. Use SFTP for `templates/` (about 820 MB) and `world/`.
- **Schedules:** cron, with tasks Send Command, Send Power Action, Create Backup (with an ignore list), Delete
  Files. **Backups** go to the node's local storage by default, with a per-server count limit set by the admin.

## 4. Create the server in Pelican

1. **Node FQDN:** the Tailscale name or IP of the Wings box (scheme http). The panel and your browser use it
   to reach the Wings API and console on port 8080, so it must be reachable from wherever you open the panel.
   Not `heroes.stolyy.com` (that resolves to TCPShield, which only proxies Minecraft traffic) and not the LAN IP
   (unreachable over Tailscale). Players never see the node FQDN.
2. **Node allocation:** IP = the Wings box's **LAN** address (the one the router forwards to; `0.0.0.0` also
   works), port **25565**. Wings binds the container port to that IP, so a Tailscale IP here would leave the game
   port invisible to the router. Router forwards TCP 25565 to the box; the TCPShield backend is
   `<home public IP>:25565`; `heroes.stolyy.com` is the CNAME to TCPShield. Optional: set the allocation alias
   to `heroes.stolyy.com` so the panel shows the public address.
3. **Create the server** from the Paper egg. Primary allocation 25565. Memory limit **7168 MB** (the JVM gets 6 GB
   in §5; the rest is JVM overhead headroom so Docker never OOM-kills it). Disk at least 6 GB (templates, world,
   instance copies, backups). Docker image `ghcr.io/pelican-eggs/yolks:java_25`.
4. **Variables:** `MINECRAFT_VERSION=1.21.11`, `BUILD_NUMBER=132`, `SERVER_JARFILE=server.jar`. Set `USER_AGENT`
   on the egg as admin.
5. Let the install run and check the install log shows `paper-1.21.11-132` being fetched. If the version or build is
   mistyped the script silently falls back to empty values, so read the log.
6. **Do not start it yet.**

## 5. Startup command and files

**Startup tab → Startup Command.** Replace the default with the Aikar set from the old `run.bat`, keeping the two
`-Dterminal` flags the egg needs for console output:

```
java -Xms6G -Xmx6G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -Dusing.aikars.flags=https://mcflags.emc.gs -Daikar.new.flags=true -Dterminal.jline=false -Dterminal.ansi=true -jar {{SERVER_JARFILE}} nogui
```

Keep `-Xmx` about 1 GB under the panel memory limit. If the Startup Command field is read-only for your user, an
admin edits it on the egg or grants the permission.

**Upload over SFTP** (host = node, port 2022, user `<panel username>.<server short id>`, your panel password).
Everything below goes into the server root (`/home/container`). Linux is **case-sensitive**: folder and file names
must match the YML references exactly (`world-folder: "apex_template"`, `heroes/<name>.yml`).

| Path | Notes |
|---|---|
| `eula.txt` | `eula=true`, or accept the panel prompt on first start. |
| `server.properties` | From the old server, edited per §6. The egg rewrites the port lines on every start. |
| `bukkit.yml`, `spigot.yml`, `config/paper-global.yml`, `config/paper-world-defaults.yml` | Keep the tuned values. |
| `world/` | The lobby. **Delete `world/playerdata/`, `world/stats/`, `world/advancements/`** inside it to wipe player data. Also delete `session.lock` and `uid.dat` if present. |
| `templates/` | All map template worlds. |
| `plugins/Heroes/` | `game-config.yml`, `gamemodes.yml`, `progression.yml`, `bots.yml`, `bot-names.yml`, `messages.yml`, `lobby.yml`, `heroes/`, `maps/`. These live files are ahead of the jar defaults, which never overwrite existing files. |
| `plugins/Heroes-1.0-SNAPSHOT.jar` | The fresh build from §1. |
| `plugins/Citizens.jar` + `plugins/Citizens/config.yml` | **Not `saves.yml`.** Heroes rebuilds every NPC from `lobby.yml` on boot and marks them non-persistent. |
| `plugins/packetevents-spigot-*.jar` | New. Citizens picks it up automatically. |
| `plugins/LuckPerms-*.jar` + `plugins/LuckPerms/config.yml` | Fresh H2 database. Re-create any mapper group after first boot. |
| `plugins/VoidGenerator.jar` | Tiny; keep so world generation matches the old server. |
| `plugins/spark-*.jar` | |
| `plugins/TCPShield-2.8.1.jar` | New. |

**Do not upload:** `run.bat`, `active_worlds/`, `plugins/Heroes/player-stats/`, `plugins/Heroes/example.yml`,
`plugins/Citizens/saves.yml`, `usercache.json`, `banned-*.json`, `whitelist.json`, `ops.json` (re-op yourself from
the console), `logs/`, `crash-reports/`, `cache/`, `libraries/`, `versions/`, `backupWorld.zip`, `plugins/spark/tmp*`,
ProtocolLib.

**Leave off production:** Multiverse-Core, FastAsyncWorldEdit, WorldEdit, AxiomPaper. Heroes uses none of them.
Multiverse's `worlds.yml` auto-loads two template worlds and three dead instance worlds on every boot: the dead
ones would be regenerated as blank worlds after the orphan sweep deletes their folders, and loaded templates get
copied while open. Axiom and FAWE let any op rewrite the lobby. Keep them on the mapping server.

## 6. `server.properties`

| Key | Set to | Why |
|---|---|---|
| `motd` | something real | Still "A Minecraft Server". |
| `max-players` | 20 to 30 | Bots and NPCs do not take slots. |
| `online-mode` | `true` | Keep. |
| `white-list` / `enforce-whitelist` | `false` | Open beta. |
| `require-resource-pack` | `true` | Custom models are the game. Players who decline are kicked with the prompt text. |
| `resource-pack-prompt` | "Smash Heroes needs its resource pack for hero models. Accept to play." | Shown on the accept dialog. |
| `resource-pack` / `resource-pack-sha1` | current values | Re-check the sha1 matches the pack actually hosted at the URL. Any pack change means new URL + new sha1 + restart, or clients keep the old pack. |
| `view-distance` | 8 | Maps are small; 14 wastes heap. |
| `simulation-distance` | 4 | No mobs anywhere. |
| `server-port` | leave | The egg sets it from the allocation. |
| `op-permission-level` | `4` is fine | Trim ops: level 4 can `/stop` and `/op`. Give friends level 2 or a LuckPerms group instead. |

Permission state as shipped: every admin command (`/bal`, `/token`, `/unlock`, `/fr`, `/fe`, `/ma`, `/mp`) sits
behind a `heroes.*` node that defaults to OP, and checks it explicitly. Admin-only heroes are hidden from
non-ops in `/hero` and the select GUI. The LuckPerms default group starts empty. Nothing else needs changing.

## 7. Schedules and backups (Pelican)

- **Daily restart** (recommended at your quietest hour): one schedule, cron `0 5 * * *`, tasks in order: Send
  Command `say Server restarting in 60 seconds` → Send Power Action `restart` with a 60 second offset. The plugin
  sends in-game players to the lobby and saves stats on `stop`.
- **Daily backup:** a second schedule (or a task on the same one, before the restart) with Create Backup. Ignore
  list: `templates`, `active_worlds`, `logs`, `cache`, `libraries`, `versions`, `world/region` is fine to keep.
  The thing that matters is `plugins/Heroes/player-stats` and `plugins/LuckPerms`. Ask the admin to set the
  per-server backup limit to at least 8 so a week fits. Lock the first good backup so rotation cannot delete it.
- Stats also flush on every quit, every 5 minutes, and on `stop`.

## 8. TCPShield

1. Drop `TCPShield-2.8.1.jar` in `plugins/`, boot once, then edit `plugins/TCPShield/config.yml`:
   `only-allow-proxy-connections: true`. Without it anyone who learns the node's IP skips the shield.
2. In the TCPShield panel confirm the backend is `<public IP>:25565` and `heroes.stolyy.com` points at TCPShield.
3. Test from outside your network (phone hotspot): the domain connects, the raw IP is refused.
4. If the plugin fails to load on 1.21.11, drop `only-allow-proxy-connections`, keep the shield for DDoS, and add
   a firewall rule on the node that allows port 25565 only from TCPShield's published IP ranges.

## 9. First boot checklist

Console tab, watch for:

- `Deleted 0 orphaned instance world(s)` (more on later boots after a crash).
- `Spawned N lobby NPCs from config` with N matching `lobby.yml`.
- Citizens logs PacketEvents as found, not missing.
- No `ERROR`, `Exception`, or `Missing:` lines. TCPShield enabled without error.
- The panel shows the server as online once `)! For help, type` prints.

Then in game:

- Fresh account sees the first-join onboarding text; a second join sees the one-liner. Edit both under
  `onboarding:` in `plugins/Heroes/messages.yml`.
- Resource pack prompt appears and models render.
- `/join 1v1` with a friend over the public domain, play to the end, `/stats` shows the result.
- Queue AFK: stand still in queue. At 4:30 idle a title, chat line and sound start a 30 second countdown; at 5:00
  you are removed. Any input cancels it. Tune `queue.afk-timeout-seconds` and `queue.afk-warning-seconds` in
  `game-config.yml`.
- Bots turn their heads toward you while kiting (that is PacketEvents working).
- `/bal` and `/ma` from a non-op account are refused.
- The round-6 verify list: `/bal` edit on a passives path, `/mp 1v1 add/remove`, `/stats <offline name>`,
  autospec across two games.

## 10. Updating during the week

1. Build on the dev machine (§1).
2. Take a panel backup (or at least download `plugins/Heroes/`) and keep the previous jar as `.old`.
3. Stop the server from the panel, upload the new jar over SFTP or the file manager (it is about 1 MB, under the
   limit), start it. Never upload over a running jar.
4. Rollback: stop, rename the `.old` jar back, restore the config backup, start.
5. **Do not press Reinstall** on the Settings tab: the egg's install script renames your Paper jar to `.old` and
   re-downloads, which is harmless, but it also drops a default `server.properties` only if yours is missing.

Live YML edits (`plugins/Heroes/**.yml`) need a restart to load; they are never overwritten by a jar swap.

## 11. Daily watch

- `spark tps` and `spark health`: MSPT, and whether memory climbs between the daily restarts.
- `active_worlds/` folder count with no game running: should be 0.
- Citizens NPC count (`/npc list`) against `lobby.yml` + dummies.
- Console for `ERROR`, `Exception`, `Missing:`.
- Last night's backup exists in the Backups tab and is not past the limit.
- Bug reports from players.
