package me.stolyy.heroes.game;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.util.ErrorReporter;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.game.map.GameMap;
import me.stolyy.heroes.game.map.GameMapManager;
import me.stolyy.heroes.game.rules.GameModeConfig;
import me.stolyy.heroes.game.rules.GameModePreset;
import me.stolyy.heroes.game.rules.OnKillAction;
import me.stolyy.heroes.game.rules.WinCondition;
import me.stolyy.heroes.util.GameModeConfigManager;
import me.stolyy.heroes.game.settings.GameSettings;
import me.stolyy.heroes.game.settings.PlayerSettings;
import me.stolyy.heroes.progression.EloCalculator;
import me.stolyy.heroes.progression.HeroLevel;
import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.progression.ProgressionConfig;
import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import me.stolyy.heroes.vfx.ui.gui.HeroSelectGUI;
import me.stolyy.heroes.vfx.ui.visual.GameVisuals;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.hero.HeroType;
import me.stolyy.heroes.vfx.cosmetic.CosmeticManager;
import me.stolyy.heroes.vfx.glow.TeamGlow;
import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.control.Controller;
import me.stolyy.heroes.control.PlayerController;
import me.stolyy.heroes.control.ai.BaseBotAI;
import me.stolyy.heroes.control.strategy.Aggressive;
import me.stolyy.heroes.control.strategy.Defensive;
import me.stolyy.heroes.control.strategy.MidRange;
import me.stolyy.heroes.control.strategy.Strategy;
import me.stolyy.heroes.hero.minion.MinionHero;
import me.stolyy.heroes.hero.minion.MinionManager;
import me.stolyy.heroes.npc.NpcServiceProvider;
import me.stolyy.heroes.ability.Cooldown;
import me.stolyy.heroes.physics.Hitbox;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.physics.WallDetection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;


public class Game {

    private final GameMode gameMode;
    private final GameSettings settings;
    private final PlayerSettings defaultPlayerSettings;
    private final GameVisuals visuals;
    private final Map<TeamColor, Team> teams = new EnumMap<>(TeamColor.class);
    private final List<BukkitTask> activeTasks = new LinkedList<>();

    private GameState gameState;
    private BukkitTask gameTickTask;

    // A4: the mode's rules as data + behavior enums. Installed before start(); CLASSIC
    // reproduces today's behavior exactly.
    private GameModeConfig modeConfig = GameModeConfig.CLASSIC;
    private GameModePreset gameModePreset = GameModePreset.DEFAULT;
    // Boss mode: the designated boss (set by BossSetup.apply via the boss settings GUI).
    private UUID bossPlayer;
    // Evolution shared-order mode: the ONE shuffled hero order every participant copies
    // (built lazily in initHeroOrder when modeConfig.sharedEvolutionOrder() is on).
    private List<String> sharedEvolutionOrder;
    // A6 series hook: when set, end() notifies the series with the round winner (null =
    // draw) INSTEAD of scheduling clean() — the series controller owns cleanup timing.
    private java.util.function.Consumer<Team> roundEndCallback;
    // Match-log series identity, installed by MatchSeries for every round it creates (null = not a series).
    private String seriesId;
    private int seriesRound;
    // Epoch ms of the IN_PROGRESS flip; 0 while the game has not started.
    private long startedAt;

    private SmashCrystal activeCrystal;
    private int crystalTickCounter;
    // Periodic bot-only-game backstop counter — see gameTick's emptyCheckCounter block.
    private int emptyCheckCounter;

    private final Set<UUID> activePlayers = new HashSet<>();
    // Consecutive ticks each player's hitbox has been embedded in block collision geometry. Every
    // ability that teleports or mounts a player (Botmobile, ShoopRTL, karakot Teleport,
    // VoidTeleboom, RayOfDoom, TimeWarp) can bury them in a wall, and nothing digs them back out:
    // vanilla suffocation damage — the trigger behind /stuck — only fires for a FULL-CUBE block at
    // the eye position, so a head-height slab traps a player silently and forever.
    private final Map<UUID, Integer> trappedTicks = new HashMap<>();
    private static final int TRAPPED_GRACE_TICKS = 10;
    private static final double TRAPPED_ESCAPE_DISTANCE = 3.0;
    private static final double TRAPPED_ESCAPE_STEP = 0.1;
    // A1 lifecycle: every UUID whose hero this game ticks — INCLUDING eliminated (dormant)
    // heroes, whose independent abilities keep resolving. Pruned only by the gameTick
    // backstop (reference lost), removePlayer/removeBot, and clean(). Distinct from
    // activePlayers, which keeps meaning "alive / targetable / win-condition-relevant".
    private final Set<UUID> tickingHeroes = new HashSet<>();
    private final Map<UUID, GameBot> gameBots = new HashMap<>();
    private final Set<UUID> wrongWorldBotsLogged = new HashSet<>();
    // A3: per-participant state (settings copy, lives, kills, hero override, flags).
    // Entries are never removed — leavers are marked leftGame so end-of-game stats survive.
    private final Map<UUID, GamePlayer> gamePlayers = new HashMap<>();
    private final Map<UUID, TeamColor> originalTeams = new HashMap<>();
    // Kill credit tracking: victim UUID -> last damager UUID. Populated by the damage listener,
    // consumed in handleDeath when vanilla getKiller() returns null, cleared on respawn.
    private final Map<UUID, UUID> lastDamagers = new HashMap<>();
    // The last CREDITED hit on a victim: who landed it and with what. Written by
    // Interactions.applyDamage — the only site that knows the ability name — and read at kill time
    // and by the out-of-bounds void path. ONE map holding a record rather than two parallel maps,
    // so the damager and the ability can never drift apart. Cleared wherever lastDamagers is.
    private final Map<UUID, DamageCredit> lastDamageCredit = new HashMap<>();

    /** Who last damaged a victim and with which ability. {@code ability} is the stats row name. */
    public record DamageCredit(UUID damager, String ability) {}

    public Game(GameMap gameMap, GameMode gameMode) {
        this.gameMode = gameMode;

        GameMap map = GameMapManager.createWorld(gameMap);
        settings = new GameSettings(Objects.requireNonNull(map));
        defaultPlayerSettings = new PlayerSettings();
        visuals = new GameVisuals(this);
        gameState = GameState.WAITING;

        for (TeamColor color : TeamColor.values()) {
            teams.put(color, new Team(color));
        }
    }



    //GAMESTATE METHODS
    public void start() {
        if (gameState != GameState.WAITING) return;

        GameManager.addToActive(this);
        setGameState(GameState.STARTING);
        visuals.showCountdown();

        // Populate active players set and record original teams at countdown start
        for (Player p : onlinePlayers(false)) {
            activePlayers.add(p.getUniqueId());
            Team team = playerTeam(p.getUniqueId());
            if (team != null) originalTeams.put(p.getUniqueId(), team.color());
        }

        for(Player p : onlinePlayers(false)) {
            GamePlayer gamePlayer = gamePlayers.get(p.getUniqueId());
            gamePlayer.seedLives();
            String heroName;
            if (modeConfig.onKill() == OnKillAction.ADVANCE_HERO) {
                // Evolution: everyone starts at the head of their own hero order.
                initHeroOrder(gamePlayer);
                heroName = gamePlayer.heroOrder().get(0);
            } else {
                heroName = gamePlayer.resolveHeroName(); // override > randomize > persistent selection
            }
            if (heroName == null || !HeroConfig.exists(heroName)) {
                // Assign a random hero if none selected — from THEIR unlocked pool, since
                // selectHero now enforces the unlock gate.
                List<String> available = HeroConfig.getAvailableNames().stream()
                        .filter(name -> HeroManager.isHeroUnlockedFor(p, name))
                        .toList();
                if (!available.isEmpty()) {
                    heroName = available.get(new Random().nextInt(available.size()));
                    // Persist directly rather than via selectHero: the pool above already applied
                    // the unlock gate, and selectHero's in-game branch would tell the player this
                    // is their NEXT game's hero when it is in fact the one they're about to play.
                    PlayerStatsManager.getStats(p).setSelectedHero(heroName.toLowerCase());
                    p.sendMessage(Component.text("No hero selected — you're playing "
                            + HeroConfig.formatName(heroName) + ".", NamedTextColor.YELLOW));
                }
            }
            gamePlayer.setCurrentHeroName(heroName);
            initializePlayerStats(p, heroName);
            Hero hero = HeroManager.createHero(heroName, new PlayerController(p));
            CosmeticManager.apply(hero.getController(), hero);
            tickingHeroes.add(p.getUniqueId());

            GameEffects.restrictPlayer(p);
            GameEffects.applyEffects(p, gamePlayer.settings());
        }

        // Initialize bot heroes. Iterates a SNAPSHOT because the failure path below calls
        // removeBot, which mutates gameBots.
        for (Map.Entry<UUID, GameBot> entry : new HashMap<>(gameBots).entrySet()) {
            UUID botUUID = entry.getKey();
            GameBot bot = entry.getValue();
            try {
                LivingEntity entity = NpcServiceProvider.get().getBukkitEntity(bot.getNpcHandle());
                // Not a null-guard-as-fallback: an unspawned or non-Player NPC cannot be a
                // participant, and a half-initialized bot left in the roster keeps the game
                // from ever ending. Fail loudly through the catch below instead.
                if (!(entity instanceof Player botEntity)) {
                    throw new IllegalStateException("NPC entity is missing or not a Player");
                }

                GamePlayer botGamePlayer = gamePlayers.get(botUUID);
                botGamePlayer.seedLives();
                String botHeroName = bot.getHeroName();
                if (modeConfig.onKill() == OnKillAction.ADVANCE_HERO) {
                    initHeroOrder(botGamePlayer);
                    botHeroName = botGamePlayer.heroOrder().get(0);
                }
                botGamePlayer.setCurrentHeroName(botHeroName);
                BotController controller = new BotController(bot.getNpcHandle());
                Hero hero = HeroManager.createHero(botHeroName, controller);

                // B28.1: config-driven bot AI. Hero ymls may carry root-level bot.strategy_type,
                // strategy: and ai: blocks; heroes without them fall back to type-based archetypes.
                org.bukkit.configuration.file.YamlConfiguration heroConfig = HeroConfig.getConfig(botHeroName);
                String strategyType = heroConfig != null ? heroConfig.getString("bot.strategy_type", null) : null;
                if (strategyType == null) {
                    HeroType type = hero.getAttributes().getType();
                    strategyType = switch (type) {
                        case MELEE -> "AGGRESSIVE";
                        case RANGED -> "DEFENSIVE";
                        default -> "MIDRANGE";
                    };
                }
                org.bukkit.configuration.ConfigurationSection strategyCfg =
                        heroConfig != null ? heroConfig.getConfigurationSection("strategy") : null;
                if (strategyCfg == null) strategyCfg = new MemoryConfiguration();
                Strategy strategy = switch (strategyType.toUpperCase()) {
                    case "AGGRESSIVE" -> new Aggressive(strategyCfg);
                    case "DEFENSIVE" -> new Defensive(strategyCfg);
                    default -> new MidRange(strategyCfg);
                };
                controller.setStrategy(strategy);

                // BaseBotAI reads ai.* paths, so it gets the full hero config.
                org.bukkit.configuration.ConfigurationSection aiCfg =
                        heroConfig != null ? heroConfig : new MemoryConfiguration();
                BaseBotAI botAI = new BaseBotAI(aiCfg, strategy) {};
                // Difficulty tier (BotSettingsGUI, 1-5) overrides the hero yml's ai.input_delay.
                int tierDelay = me.stolyy.heroes.util.BotConfig.difficultyInputDelay(bot.getDifficultyTier());
                if (tierDelay >= 0) botAI.setInputDelayTicks(tierDelay);
                controller.setAI(botAI);

                // Bot bug plays a user-authored charm preset (bug.yml charm_presets), rolled per
                // bot per game. TRANSIENT registration only — the persistent setter writes
                // PlayerStats, and NPC uuids must never mint stat files. Rolled for every bot
                // (not just bug) so ADVANCE_HERO rotations into bug are covered too.
                java.util.Set<me.stolyy.heroes.ability.kit.bug.Charms> charmPreset =
                        me.stolyy.heroes.ability.kit.bug.BugCharms.rollRandomPreset();
                if (!charmPreset.isEmpty()) {
                    HeroManager.setEquippedCharmsTransient(botUUID, charmPreset);
                }

                activePlayers.add(botUUID);
                tickingHeroes.add(botUUID);
                Team botTeam = playerTeam(botUUID);
                if (botTeam != null) originalTeams.put(botUUID, botTeam.color());

                CosmeticManager.apply(hero.getController(), hero);
                // The same two calls the player loop makes. applyEffects is the ONLY writer of the
                // settings health onto an entity (MAX_HEALTH base + setHealth top-up + health
                // scale + noDamageTicks), and bots never received it — which is why they entered
                // matches on vanilla 20 HP while their MAX_HEALTH attribute read 100.
                GameEffects.restrictPlayer(botEntity);
                GameEffects.applyEffects(botEntity, botGamePlayer.settings());
            } catch (Exception e) {
                ErrorReporter.report(this, "Failed to initialize bot " + bot.getName() + " (" + botUUID
                                + ") — removing it from the game", e);
                removeBot(botUUID);
                NpcServiceProvider.get().remove(bot.getNpcHandle().id());
            }
        }

        // Roster is final for the round and every entity now exists — outline each viewer's own team.
        refreshGlow();

        // Everyone is placed and the world is live — pull in armed auto-spectators now, so they
        // watch from the first countdown second.
        AutoSpectate.onGameStarting(this);

        BukkitTask startTask = Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> {
            setGameState(GameState.IN_PROGRESS);
            startedAt = System.currentTimeMillis();
            visuals.startTimer();
            // Start ability cooldowns for all active heroes (players + bots)
            for (UUID uuid : new HashSet<>(activePlayers)) {
                Hero hero = HeroManager.getHero(uuid);
                if (hero != null) {
                    hero.resetStats(hero.getController());
                    startAbilityCooldowns(hero);
                }
            }
            // Re-apply settings AFTER resetStats — applyAttributes just clobbered MAX_HEALTH
            // back to the hero base while current hp kept the settings value (the "50/100
            // half-hearts until first death" bug) — then seed the dummy health objectives
            // from the player's REAL health, not the attribute max.
            for (UUID uuid : new HashSet<>(activePlayers)) {
                Player p = resolveParticipantEntity(uuid);
                if (p == null) continue;
                GamePlayer gamePlayer = gamePlayers.get(uuid);
                if (gamePlayer == null) continue;
                GameEffects.applyEffects(p, gamePlayer.settings());
                visuals.updateHealthScore(p, (int) p.getHealth());
            }
            // activePlayers, not onlinePlayers: bots are restricted at init now, and
            // onlinePlayers cannot see them — they would stay frozen for the whole round.
            for (UUID uuid : new HashSet<>(activePlayers)) {
                Player p = resolveParticipantEntity(uuid);
                if (p == null) continue;
                GameEffects.unRestrictPlayer(p);
                // The toggle-flight double-jump primer is for REAL players only: their second
                // jump press fires PlayerToggleFlightEvent, which MovementListener cancels into
                // a double jump. A bot has no client to press anything — mayfly on the NPC just
                // sits armed until the server engages flight at a jump apex, and the bot hovers
                // there forever (bots double-jump via performJump directly; no primer needed).
                if (me.stolyy.heroes.physics.Interactions.isRealPlayer(p)) p.setAllowFlight(true);
                SpawnProtection.arm(p);
            }
            gameTickTask = Bukkit.getScheduler().runTaskTimer(Heroes.getInstance(), this::gameTick, 0L, 1L);
            activeTasks.add(gameTickTask);
        }, 20L * GameConfigManager.getCountdownTime());
        activeTasks.add(startTask);
    }

    public void end(GameEndReason reason) {
        end(reason, null);
    }

    /** Ends with an explicitly injected winner (KILL_TARGET, series rounds) instead of discovery. */
    public void endWithWinner(Team winner) {
        end(GameEndReason.LAST_TEAM_STANDING, winner);
    }

    private void end(GameEndReason reason, Team injectedWinner) {
        if (gameState == GameState.ENDED) return;
        gameState = GameState.ENDED;  // moved up — blocks all IN_PROGRESS-guarded code

        // Despawn any minions belonging to this game so they don't outlive it as orphaned NPCs.
        MinionManager.despawnForGame(this);

        activeTasks.forEach(BukkitTask::cancel);
        activeTasks.clear();
        visuals.cancelTasks();

        if (activeCrystal != null) {
            activeCrystal.remove();
            activeCrystal = null;
        }

        // Anyone caught mid-respawn is invisible, frozen (movement/jump zeroed) and in ADVENTURE at a
        // respawn point. Left alone they stay that way for the whole cleanup delay and get dragged
        // back by the out-of-bounds rescue every time they drift. Park them as spectators, exactly
        // like handleDeath does for the victim of the winning kill.
        for (GamePlayer gamePlayer : gamePlayers.values()) {
            if (!gamePlayer.respawning()) continue;
            gamePlayer.setRespawning(false);
            Player p = Bukkit.getPlayer(gamePlayer.uuid());
            if (p == null) continue;
            refreshVisibility(p);
            GameEffects.unRestrictPlayer(p);
            p.setGameMode(org.bukkit.GameMode.SPECTATOR);
            teleportToSpawn(gamePlayer.uuid(), TeamColor.SPECTATOR);
        }
        for (Player p : onlinePlayers()) TeamGlow.clearFor(p);

        Team winningTeam = injectedWinner;
        // A cancelled round produces no result at all: no winner, no sound/visuals, no summary
        // and — the point of the whole reason — nothing written to stats, XP or elo.
        if (reason == GameEndReason.CANCELLED) {
            Component cancelMsg = Component.text("Game force-ended by an admin.", NamedTextColor.RED);
            for (Player p : onlinePlayers()) p.sendMessage(cancelMsg);
        } else {
            if (winningTeam == null) {
                if(reason == GameEndReason.TIMEOUT){
                    winningTeam = modeConfig.winCondition().timeoutWinner(this);
                } else {
                    Optional<Team> winner = teams.values().stream()
                            .filter(t -> t.color() != TeamColor.SPECTATOR && !t.playerUUIDs().isEmpty())
                            .findFirst();
                    if (winner.isPresent()) {
                        winningTeam = winner.get();
                    }
                }
            }

            if (winningTeam != null) {
                playWinSound(winningTeam);
                Map<String, String> endPlaceholders = buildEndPlaceholders(winningTeam);
                visuals.win(winningTeam, endPlaceholders);
                sendSummaryToLosers(winningTeam, endPlaceholders);
            } else {
                visuals.draw();
            }
            recordWinLoss(winningTeam);
            MatchLog.write(this, reason, winningTeam);
        }

        // Refresh lobby leaderboard with updated ELO
        if (Heroes.getLeaderboardDisplay() != null) {
            Heroes.getLeaderboardDisplay().refresh();
        }
        // Levels move on game end too (XP awards) — keep the level board current alongside elo.
        if (Heroes.getLevelLeaderboardDisplay() != null) {
            Heroes.getLevelLeaderboardDisplay().refresh();
        }

        GameManager.removeFromActive(this);
        if (roundEndCallback != null) {
            roundEndCallback.accept(winningTeam);
        } else {
            Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), this::clean, 20L * GameConfigManager.getCleanupDelay());
        }
    }

    /** A6: installs the series round-end hook. See {@link #roundEndCallback}. */
    public void setRoundEndCallback(java.util.function.Consumer<Team> callback) {
        this.roundEndCallback = callback;
    }

    /**
     * Plays exactly one win sound per game end: the chosen winning player's hero jingle
     * (yml root {@code visuals.win_sound}), heard by every player in the game.
     */
    private void playWinSound(Team winningTeam) {
        UUID rep = pickWinRepresentative(winningTeam);
        if (rep == null) return;
        Hero repHero = HeroManager.getHero(rep);
        if (repHero == null || repHero.getWinSound() == null) return;
        for (Player p : onlinePlayers()) {
            repHero.getWinSound().play(p);
        }
    }

    /**
     * Picks whose hero supplies the win sound, among the winning team:
     * sole survivor if only one is still active, else most kills, then most lives
     * remaining, then random.
     *
     * <p>Works off the ROSTER, not resolved Players: mapping through Bukkit::getPlayer dropped
     * every bot, so an all-bot winning team had no representative and no win sound played at all.
     */
    private UUID pickWinRepresentative(Team winningTeam) {
        List<UUID> members = List.copyOf(winningTeam.playerUUIDs());
        if (members.isEmpty()) return null;

        List<UUID> alive = members.stream()
                .filter(activePlayers::contains)
                .toList();
        if (alive.size() == 1) return alive.get(0);
        List<UUID> pool = alive.isEmpty() ? members : alive;

        int bestKills = pool.stream().mapToInt(uuid -> gamePlayers.get(uuid).kills()).max().orElse(0);
        List<UUID> topKills = pool.stream()
                .filter(uuid -> gamePlayers.get(uuid).kills() == bestKills)
                .toList();
        if (topKills.size() == 1) return topKills.get(0);

        int bestLives = topKills.stream().mapToInt(uuid -> gamePlayers.get(uuid).currentLives()).max().orElse(0);
        List<UUID> topLives = topKills.stream()
                .filter(uuid -> gamePlayers.get(uuid).currentLives() == bestLives)
                .toList();
        return topLives.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(topLives.size()));
    }



    //PLAYER METHODS
    public void addPlayer(Player player, TeamColor teamColor) {
        // Single choke point for entering a game in ANY role (spectate, custom join, queue pop,
        // series transfer): a practicing player exits practice HERE, before game state applies.
        // The lazy region-check exit used to fire on the first move INSIDE the arena instead —
        // and its removeEffects() call put fresh spectators back into ADVENTURE mode.
        PracticeManager.exitPractice(player, false);

        if(gameState != GameState.WAITING) teamColor = TeamColor.SPECTATOR;

        Team team = teams.get(teamColor);
        team.add(player.getUniqueId());
        // Claim ownership BEFORE anything asks "whose game is this player in". GameManager's
        // spectateGame/startQueuedGame/changeMap all register only after addPlayer returns, so the
        // visibility pass below would otherwise compute against a stale null and hide a fresh
        // spectator from the match they were just attached to. Idempotent with their own put().
        GameManager.registerEntity(player.getUniqueId(), this);

        GamePlayer existing = gamePlayers.get(player.getUniqueId());
        if (existing == null) {
            GamePlayer created = new GamePlayer(
                    player.getUniqueId(), teamColor, new PlayerSettings(defaultPlayerSettings), null);
            // Late joiners inherit the already-selected preset's start HP (see pushStartHpToSettings).
            if (modeConfig.startHp() > 0) created.settings().setHealth(modeConfig.startHp());
            gamePlayers.put(player.getUniqueId(), created);
        } else {
            existing.setTeam(teamColor);
            existing.markRejoined();
        }

        if(teamColor == TeamColor.SPECTATOR) {
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        } else {
            player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            GameEffects.restrictPlayer(player);
        }

        teleportToSpawn(player.getUniqueId(), teamColor);
        refreshVisibility(player);
        visuals.update();
        refreshGlow();

        // Participant joins void the joiner's own auto-spectate arm; spectator seats don't
        // (the auto-spectate fire itself lands here with SPECTATOR).
        if (teamColor != TeamColor.SPECTATOR) AutoSpectate.onJoinedGame(player);
    }

    public void removePlayer(Player player) {
        Team team = playerTeam(player.getUniqueId());
        if(team == null) return;

        // Leaving an in-progress game as an active (non-spectator, non-eliminated) player
        // counts as a death. Credit the kill to the last damager if we have one.
        if (gameState == GameState.IN_PROGRESS
                && team.color() != TeamColor.SPECTATOR
                && activePlayers.contains(player.getUniqueId())) {
            creditLeaveAsKill(player);
        }

        GamePlayer gamePlayer = gamePlayers.get(player.getUniqueId());
        gamePlayer.setRespawning(false);  // clears the respawn-invisibility half of the rule below
        gamePlayer.markLeft(); // entry stays for end-of-game stats

        activePlayers.remove(player.getUniqueId());
        tickingHeroes.remove(player.getUniqueId());
        team.remove(player.getUniqueId());
        GameManager.unregisterEntity(player.getUniqueId());
        // They are a lobby player from here on: re-derive both directions and drop their outlines.
        TeamGlow.clearFor(player);
        refreshVisibility(player);
        // Despawn the leaver's minions before cleanup() clears the ability ticks that own them.
        Hero leavingHero = HeroManager.getHero(player.getUniqueId());
        if (leavingHero != null) MinionManager.despawnAll(leavingHero);
        HeroManager.removePlayer(player);
        GameEffects.removeEffects(player);
        GameEffects.unRestrictPlayer(player);
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());

        visuals.update();
        refreshGlow();
        checkEnd();
    }

    /**
     * Seats a bot on a team. Returns false — with NOTHING mutated — when the NPC has no live
     * Player entity or the game/team is wrong, so the caller can destroy the orphan NPC instead
     * of leaving it standing in the world attached to nothing.
     */
    public boolean addBot(GameBot bot, TeamColor teamColor) {
        if (gameState != GameState.WAITING) return false;
        Team team = teams.get(teamColor);
        if (team == null) return false;

        LivingEntity entity = NpcServiceProvider.get().getBukkitEntity(bot.getNpcHandle());
        if (!(entity instanceof Player)) {
            Heroes.getInstance().getLogger().severe("Cannot add bot " + bot.getName()
                    + ": its NPC has no spawned Player entity");
            return false;
        }

        UUID botUUID = entity.getUniqueId();
        team.add(botUUID);
        gameBots.put(botUUID, bot);
        GamePlayer botGamePlayer = new GamePlayer(botUUID, teamColor, new PlayerSettings(defaultPlayerSettings), bot);
        if (modeConfig.startHp() > 0) botGamePlayer.settings().setHealth(modeConfig.startHp());
        gamePlayers.put(botUUID, botGamePlayer);
        GameManager.registerEntity(botUUID, this);
        // AFTER the maps are populated: teleportToSpawn reads the roster for its offset and
        // resolves the entity through gamePlayers. The bot was created at whatever location the
        // caller had on hand (red's spawn), which is why every bot used to stand on red.
        teleportToSpawn(botUUID, teamColor);
        refreshGlow();
        // hg_ team membership NOW, not at the first death: tab/nametag color comes from the
        // viewer's scoreboard team of the entity name, and syncScoreboardTeams only ran from
        // update() — a bot added after the last player joined stayed white until someone died.
        visuals.update();
        refreshBotListing((Player) entity);
        return true;
    }

    public void removeBot(UUID botUUID) {
        for (Team team : teams.values()) {
            team.remove(botUUID);
        }
        GamePlayer gamePlayer = gamePlayers.get(botUUID);
        if (gamePlayer != null) gamePlayer.markLeft();
        gameBots.remove(botUUID);
        GameManager.unregisterEntity(botUUID);
        HeroManager.removeHero(botUUID);
        activePlayers.remove(botUUID);
        tickingHeroes.remove(botUUID);
        // A bot dropping out shrinks a team exactly like a player leaving does; without this the
        // last enemy bot could disappear (failed init, lost entity) and the game would never end.
        if (gameState == GameState.IN_PROGRESS) checkEnd();
    }

    /** Team move for bots (pre-start lobby): membership plus the physical move to the new spawn. */
    public void changeBotTeam(UUID botUUID, TeamColor teamColor) {
        Team oldTeam = playerTeam(botUUID);
        if (oldTeam == null || oldTeam.color() == teamColor) return;
        oldTeam.remove(botUUID);
        teams.get(teamColor).add(botUUID);
        gamePlayers.get(botUUID).setTeam(teamColor);
        teleportToSpawn(botUUID, teamColor);
        visuals.update();
        refreshGlow();
    }

    public void changeTeam(Player player, TeamColor teamColor) {
        Team oldTeam = playerTeam(player.getUniqueId());
        if(oldTeam == null || oldTeam.color() == teamColor) return;
        else oldTeam.remove(player.getUniqueId());

        teams.get(teamColor).add(player.getUniqueId());
        gamePlayers.get(player.getUniqueId()).setTeam(teamColor);

        if (teamColor == TeamColor.SPECTATOR) {
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            GameEffects.unRestrictPlayer(player);
            // Void deaths land here mid-fall; the out-of-bounds rescue in GameListener is
            // IN_PROGRESS-gated, so a game-ending elimination would otherwise leave the
            // spectator falling forever.
            teleportToSpawn(player.getUniqueId(), TeamColor.SPECTATOR);
        } else {
            if (gameState == GameState.WAITING) GameEffects.restrictPlayer(player);
            player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            teleportToSpawn(player.getUniqueId(), teamColor);
        }

        visuals.update();
        // Authoritative per viewer: a player who just became a SPECTATOR is cleared by this call.
        refreshGlow();
    }



    //GAME LOGIC
    public void handleDeath(Player player, LivingEntity killer) {
        if (isRespawning(player.getUniqueId())) return;
        if (gameState != GameState.IN_PROGRESS) return;

        // Minions never reach participant bookkeeping. This is an EARLY RETURN, not a null guard on
        // gamePlayers: a guarded fall-through would still reach changeTeam(player, SPECTATOR) on a
        // lives-based death and permanently insert the NPC into a real Team roster. Unfixed, the
        // NPE at gamePlayer.subtractLife() below is a live crash LOOP — DamageCause.VOID is not
        // cancelled, Tether drags a minion out over the edge, and the cancelled death event revives
        // it at full HP under the world, so it dies again every void tick until Citizens reaps it.
        if (HeroManager.getHero(player) instanceof MinionHero minion) {
            handleMinionDeath(minion, player);
            return;
        }

        Team team = playerTeam(player.getUniqueId());
        if (team == null || team.color() == TeamColor.SPECTATOR) return;
        GamePlayer gamePlayer = gamePlayers.get(player.getUniqueId());
        gamePlayer.addDeath();
        if (modeConfig.deathOutcome().consumesLife()) gamePlayer.subtractLife();

        // Captured ONCE, before anything can move the entity: both branches below hand it to the
        // hero's death hooks and the respawn branch places the player relative to it.
        Location deathLocation = player.getLocation().clone();

        // Persist death to the victim's PlayerStats so the lobby sidebar reflects it.
        // (Bot deaths land here too — NPC "players" must not get stats files.)
        if (Interactions.isRealPlayer(player)) PlayerStatsManager.getStats(player).recordDeath();

        Hero hero = HeroManager.getHero(player);

        // Fall back to our tracked last damager if vanilla kill tracking returned null.
        // This catches "void stall" deaths where the vanilla killer reference has expired.
        if (killer == null) {
            UUID lastUuid = lastDamagers.get(player.getUniqueId());
            if (lastUuid != null) {
                org.bukkit.entity.Entity e = Bukkit.getEntity(lastUuid);
                if (e instanceof LivingEntity le) killer = le;
            }
        }

        // Trigger kill on the killer's hero + broadcast kill message
        if (killer != null) {
            // Resolve credit ONCE, here. A minion is an NPC with no GamePlayer, no stats file and no
            // hero of the owner's — routing it to the owner at this single choke point is what makes
            // the kill count, the stats, the XP, the feed and the mode rules agree on who scored.
            UUID creditId = creditedId(killer.getUniqueId());
            Player creditPlayer = Bukkit.getPlayer(creditId);

            GamePlayer killerGamePlayer = gamePlayers.get(creditId);
            if (killerGamePlayer != null) killerGamePlayer.addKill();
            // Persist kill to the killer's PlayerStats. Bot killers are Player-typed NPCs — the
            // isRealPlayer check keeps them out of the stats files.
            if (creditPlayer != null && Interactions.isRealPlayer(creditPlayer)) {
                // File the kill under the ability that actually landed it, not under whatever the
                // killer's stats happened to have named last.
                DamageCredit credit = lastDamageCredit.get(player.getUniqueId());
                PlayerStatsManager.getStats(creditPlayer)
                        .recordKill(credit != null ? credit.ability() : null);
                if (Interactions.isRealPlayer(player)) awardXp(creditPlayer, scaleXp(ProgressionConfig.getKillReward()));
            }
            // The OWNER's hero, not the minion's: a minion's controller is a bot, and a PLAYER-scoped
            // kill sound fired on it is dropped silently, so the summoner heard nothing.
            Hero killerHero = HeroManager.getHero(creditId);
            if (killerHero != null) {
                killerHero.triggerKill(player);
            }
            // participantName covers the offline owner and the bot cases; the raw entity name is only
            // reached for a killer with no GamePlayer at all.
            String killerName = killerGamePlayer != null
                    ? participantName(killerGamePlayer) : killer.getName();
            Component killMsg = Component.text(killerName, feedColor(creditId))
                    .append(Component.text(" killed ", NamedTextColor.GRAY))
                    .append(Component.text(player.getName(), feedColor(player.getUniqueId())));
            for (Player p : onlinePlayers()) p.sendMessage(killMsg);

            if (killerGamePlayer != null) {
                modeConfig.onKill().apply(killerGamePlayer, gamePlayer, this);
                // Kill-count win conditions resolve at kill time — a KILL_TARGET game has no
                // elimination path to trigger checkEnd otherwise. No-op under CLASSIC (the
                // victim is still in activePlayers here, so LAST_TEAM_STANDING can't be met).
                if (checkEnd()) {
                    // The game just ended: this victim never reaches the elimination/respawn
                    // handling below. Rescue them now (they died mid-fall in ADVENTURE —
                    // left alone they keep taking void damage against a torn-down game and
                    // the next, uncancelled death event errors out).
                    double maxHP = Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue();
                    player.setHealth(maxHP);
                    player.setGameMode(org.bukkit.GameMode.SPECTATOR);
                    teleportToSpawn(player.getUniqueId(), TeamColor.SPECTATOR);
                    visuals.update();
                    return;
                }
            }
        } else {
            Component deathMsg = Component.text(player.getName(), feedColor(player.getUniqueId()))
                    .append(Component.text(" died", NamedTextColor.GRAY));
            for (Player p : onlinePlayers()) p.sendMessage(deathMsg);
        }

        // ORDER RULE for both branches: every authoritative state write lands BEFORE the hero's
        // death hooks. The hooks used to run FIRST, so a single throwing cosmetic listener skipped
        // the spectator switch / respawn countdown below it AND the win check — the "dead bots that
        // never respawn and games that never end" cascade.
        if(modeConfig.deathOutcome().shouldEliminate(gamePlayer, this)) {
            // Elimination: the hero goes DORMANT instead of being torn down (A1 lifecycle).
            // In-flight projectiles keep resolving (and can still kill — the bus stays live),
            // minions live out their durations, planted objects stay. Full cleanup happens on
            // logout (gameTick backstop / removePlayer) or at game end (clean()).
            activePlayers.remove(player.getUniqueId());
            // The cancelled death left them at ~0 HP — restore before the spectator switch.
            double maxHP = Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue();
            player.setHealth(maxHP);
            changeTeam(player, TeamColor.SPECTATOR);
            lastDamagers.remove(player.getUniqueId()); // no respawn coming; clear now
            lastDamageCredit.remove(player.getUniqueId());
            player.sendMessage("You have been eliminated!");
            if (hero != null) {
                hero.eliminate(deathLocation); // triggerDeath + dormant flag; NO teardown
            }
        } else {
            // Respawn path: restore health first so teleport works on a living player
            double maxHP = Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue();
            player.setHealth(maxHP);
            visuals.updateHealthScore(player, (int) maxHP);

            // Mark respawning BEFORE teleport to prevent move handler interference. The tick-driven
            // countdown (gameTick decrements it; paused while the re-pick GUI is open, so picking
            // never races the timer) is seeded in the same breath — a dead participant flagged
            // respawning with no ticks left would be hidden and frozen forever.
            int respawnSeconds = GameConfigManager.getRespawnTime();
            gamePlayer.setRespawning(true);
            gamePlayer.setRespawnTicksLeft(20 * respawnSeconds);

            // Respawn placement deliberately uses the RAW killer, not the credited owner: you should
            // come back away from the thing that killed you, which for a summon is the summon.
            teleportParticipant(player.getUniqueId(), player,
                    getRespawnLocation(player, killer, deathLocation));

            // Hide during respawn — one rule, so teammates can keep seeing (and outlining) them.
            refreshVisibility(player);
            refreshGlow();

            GameEffects.restrictPlayer(player);
            player.setAllowFlight(false);
            modeConfig.onRespawn().onCountdownStart(player, this);

            visuals.showRespawnTick(player, respawnSeconds);
            if (hero != null) {
                hero.triggerDeath(deathLocation);
            }
        }
        visuals.update();
        // Unconditional, one call for both branches: a death is the event that empties a team, and
        // the kill-time checkEnd above only fires for kill-count win conditions with a live killer.
        checkEnd();
    }

    private void startAbilityCooldowns(Hero hero) {
        if (hero.getSmashAbility() == null) return;
        hero.getSmashAbility().getCooldownObject().start();
        // STOCK smash ammo starts full from construction — drain it so ults charge up
        // from zero over the regen time instead of opening the game banked. (STOCK
        // secondaries like the satchel keep their full spawn stock.)
        if (hero.getSmashAbility() instanceof me.stolyy.heroes.ability.template.BaseAbility base
                && base.getAmmo() != null
                && base.getAmmo().getMode() == me.stolyy.heroes.ability.Ammo.Mode.STOCK) {
            base.getAmmo().drain();
        }
    }

    /**
     * Mid-game hero swap (Evolution advance, TDM respawn re-pick). The old hero is fully
     * torn down first — after the swap HeroManager resolves this UUID to the NEW hero, so
     * nothing would ever tick the old instance's independent abilities again (in-flight
     * projectiles from the old hero die with it; letting them linger would leak frozen
     * displays). Preserves current health capped at the new max — no free heal on advance.
     */
    public void swapHero(GamePlayer gamePlayer, String heroName) {
        Hero oldHero = HeroManager.getHero(gamePlayer.uuid());
        if (oldHero == null) return;
        Controller controller = oldHero.getController();

        // Ult-charge carryover (TDM/Evolution toggle): capture the old smash's charge
        // PERCENTAGE before teardown; re-applied against the new smash's own duration below.
        double ultCharge = 0;
        if (modeConfig.ultChargeCarryover() && oldHero.getSmashAbility() != null) {
            Cooldown oldCd = oldHero.getSmashAbility().getCooldownObject();
            long duration = oldCd.getCooldownDuration();
            if (duration > 0) {
                ultCharge = Math.max(0, Math.min(1.0,
                        1.0 - oldCd.getTimeRemaining() / (double) duration));
            }
        }

        MinionManager.despawnAll(oldHero);
        oldHero.cleanup();

        Hero newHero = HeroManager.createHero(heroName, controller);
        gamePlayer.setCurrentHeroName(heroName);
        CosmeticManager.apply(controller, newHero);
        startAbilityCooldowns(newHero);

        if (ultCharge > 0 && newHero.getSmashAbility() != null) {
            Cooldown newCd = newHero.getSmashAbility().getCooldownObject();
            newCd.reduceCooldown((long) (ultCharge * newCd.getCooldownDuration()));
        }

        // The block itself covers bots too — an NPC IS a Player entity, and this is what heals them
        // correctly across an Evolution advance. Only the stats write is participant-gated, on the
        // GamePlayer flag rather than entity metadata (which Citizens strips on death): an NPC uuid
        // reaching PlayerStatsManager mints a ghost stats file per bot per swap.
        if (controller.getEntity() instanceof Player player) {
            // Kill/XP attribution follows the ACTIVE hero: kills credit the hero held at
            // kill time, end-of-game XP credits the final hero.
            if (!gamePlayer.isBot()) {
                PlayerStatsManager.getStats(player).setCurrentContext(gameMode, heroName);
            }

            double hpBefore = player.getHealth();
            GameEffects.applyEffects(player, gamePlayer.settings());
            double max = Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue();
            player.setHealth(Math.min(hpBefore, max));
            visuals.updateHealthScore(player, (int) player.getHealth());
        }
    }

    /**
     * Evolution hero sequence: admin-only heroes never enter the rotation; shuffled unless
     * the yml says otherwise. With sharedEvolutionOrder every participant gets a copy of ONE
     * shuffle (built lazily on first use, per game).
     */
    private void initHeroOrder(GamePlayer gamePlayer) {
        List<String> order;
        if (modeConfig.sharedEvolutionOrder()) {
            if (sharedEvolutionOrder == null) sharedEvolutionOrder = buildEvolutionOrder();
            order = new ArrayList<>(sharedEvolutionOrder);
        } else {
            order = buildEvolutionOrder();
        }
        // Evolution reads killLimit as a LADDER LENGTH — how many heroes you must climb. Trimming
        // the per-player COPY (never sharedEvolutionOrder, which the next player copies from) is what
        // makes the sidebar's "i/N" the actual win condition now that the team-kill end is gone.
        // killLimit is game-wide and frozen before start(), so every ladder ends up the same length,
        // which keeps team-advance and HERO_EXHAUSTION's cross-player index comparison meaningful.
        int ladder = modeConfig.killLimit();
        if (ladder > 0 && ladder < order.size()) order = new ArrayList<>(order.subList(0, ladder));
        gamePlayer.setHeroOrder(order);
        gamePlayer.setHeroIndex(0);
    }

    private List<String> buildEvolutionOrder() {
        List<String> order = new ArrayList<>(HeroConfig.getAvailableNames().stream()
                .filter(name -> !HeroConfig.isAdminOnly(name))
                .toList());
        if (GameModeConfigManager.getEvolutionRandomizeOrder()) {
            Collections.shuffle(order);
        }
        return order;
    }

    /**
     * Credits the departing player's "death" to their last damager (if any) when they leave
     * mid-game. Increments the killer's GamePlayer kill count so the stat survives even if the
     * killer is offline, fires onKill and broadcasts the message only if the killer is
     * currently resolvable.
     */
    private void creditLeaveAsKill(Player victim) {
        // Leaver always gets a death recorded, regardless of whether a killer is attributable.
        PlayerStatsManager.getStats(victim).recordDeath();

        UUID victimId = victim.getUniqueId();
        UUID killerId = lastDamagers.remove(victimId);
        DamageCredit credit = lastDamageCredit.remove(victimId);
        if (killerId == null) return;

        // lastDamagers can legitimately hold a minion, so this path needs the same resolution as
        // handleDeath — otherwise leaving mid-fight is a way to erase a summoner's kill.
        UUID creditId = creditedId(killerId);
        GamePlayer killerGamePlayer = gamePlayers.get(creditId);
        if (killerGamePlayer == null) return;
        killerGamePlayer.addKill();

        // Persist kill + XP to the killer's PlayerStats if they're a real Player — leaving
        // shouldn't rob the killer of XP. (No damager on record = no kill = no XP.)
        Player creditPlayer = Bukkit.getPlayer(creditId);
        if (creditPlayer != null && Interactions.isRealPlayer(creditPlayer)) {
            PlayerStatsManager.getStats(creditPlayer)
                    .recordKill(credit != null ? credit.ability() : null);
            awardXp(creditPlayer, scaleXp(ProgressionConfig.getKillReward()));
        }
        Hero killerHero = HeroManager.getHero(creditId);
        if (killerHero != null) {
            killerHero.triggerKill(victim);
        }
        Component msg = Component.text(participantName(killerGamePlayer), feedColor(creditId))
                .append(Component.text(" killed ", NamedTextColor.GRAY))
                .append(Component.text(victim.getName(), feedColor(victim.getUniqueId())))
                .append(Component.text(" (left)", NamedTextColor.GRAY));
        for (Player p : onlinePlayers()) p.sendMessage(msg);
    }

    /**
     * A minion died. It has no GamePlayer, no Team roster entry and no stats file, so it gets
     * NOTHING — no life consumed, no death stat, no kill credit, no kill feed, no XP — and its
     * pending kill-credit entries are dropped so those maps don't leak one NPC uuid per spawn.
     *
     * <p>despawn() is the single teardown hook all three spawners already trust (it unregisters
     * MinionManager + GameManager.entityGames + HeroManager, then destroys the NPC). It runs one
     * tick later on purpose: we are inside the (cancelled) death event for this NPC and Citizens
     * still has its own removal pending on the same entity, so tearing the NPC out from under that
     * is what the deferral avoids. Repeat calls are harmless — every step is a remove against a
     * map/set/registry that no longer holds the id.
     */
    private void handleMinionDeath(MinionHero minion, Player entity) {
        lastDamagers.remove(entity.getUniqueId());
        lastDamageCredit.remove(entity.getUniqueId());
        Bukkit.getScheduler().runTask(Heroes.getInstance(), minion::despawn);
    }

    private void handleRespawn(Player player) {
        GamePlayer gamePlayer = gamePlayers.get(player.getUniqueId());
        gamePlayer.setRespawning(false);
        refreshVisibility(player);  // visible again to everyone their game lets see them
        if(gameState != GameState.IN_PROGRESS) return;

        // Everything below restores a COMBATANT — applyEffects alone would put them back into
        // ADVENTURE. Someone who became a spectator while the countdown ran stays a spectator.
        Team team = playerTeam(player.getUniqueId());
        if (team == null || team.color() == TeamColor.SPECTATOR) return;

        // Fresh respawn: clear pending kill credit so a subsequent death-without-damage
        // (e.g., void fall before any enemy touches them) isn't credited to a stale damager.
        lastDamagers.remove(player.getUniqueId());
        lastDamageCredit.remove(player.getUniqueId());

        GameEffects.unRestrictPlayer(player);
        // Real players only — see the game-start loop: mayfly on a clientless NPC becomes
        // permanent hover at its next jump apex.
        if (me.stolyy.heroes.physics.Interactions.isRealPlayer(player)) player.setAllowFlight(true);

        // TDM respawn re-pick: a hero chosen during the countdown applies now (swapHero
        // preserves HP, but the full-heal applyEffects below overrides it — correct here).
        String pendingHero = gamePlayer.consumePendingHero();
        if (pendingHero != null && !pendingHero.equals(gamePlayer.currentHeroName())) {
            swapHero(gamePlayer, pendingHero);
        }

        GameEffects.applyEffects(player, gamePlayer.settings());
        visuals.updateHealthScore(player, (int) player.getHealth());

        Hero hero = HeroManager.getHero(player);
        if (hero != null) {
            CosmeticManager.apply(hero.getController(), hero);
        }

        // The hero may have been swapped above, so the entity's outline state is re-derived here
        // rather than assumed to have survived the respawn.
        refreshGlow();

        modeConfig.onRespawn().apply(player, this);

        // Spawn points are predictable — protect the fresh respawn until they act or commit
        // (time / distance / aggression, see SpawnProtection).
        SpawnProtection.arm(player);
    }

    /**
     * Roster-change hook for the teammate outlines, gated on the game phase: while WAITING the customs
     * GUI churns teams constantly and the outlines carry no information (everyone is standing in the
     * setup lobby), so nothing is sent. The startGame call runs under STARTING and applies the first
     * real outlines; every mid-game roster change re-derives from there.
     */
    private void refreshGlow() {
        if (gameState == GameState.WAITING) return;
        TeamGlow.refreshTeams(this);
    }

    /**
     * Minion spawn hook: minions aren't roster members, so neither the scoreboard-team sync
     * (outline/nametag colour) nor the glow refresh would ever see a fresh one on their own —
     * both are roster-change driven. Called from {@code MinionHero.spawn}.
     */
    public void onMinionSpawned() {
        visuals.update();
        refreshGlow();
    }

    public boolean canStart() {
        if (gameState != GameState.WAITING) return false;
        long teamsWithParticipants = teams.values().stream()
                .filter(t -> t.color() != TeamColor.SPECTATOR)
                .filter(t -> !t.playerUUIDs().isEmpty())
                .count();
        boolean hasRealPlayer = teams.values().stream()
                .filter(t -> t.color() != TeamColor.SPECTATOR)
                .flatMap(t -> t.playerUUIDs().stream())
                .anyMatch(uuid -> org.bukkit.Bukkit.getPlayer(uuid) != null);
        return teamsWithParticipants >= 2 && hasRealPlayer;
    }

    public boolean checkEnd() {
        if(gameState == GameState.ENDED) return true;

        // The mode's WinCondition owns the counting (LAST_TEAM_STANDING mirrors the old
        // teams-with-active-players check exactly). The end() call stays here.
        WinCondition winCondition = modeConfig.winCondition();
        if (winCondition.isMet(this)) {
            Team winner = winCondition.winningTeam(this);
            if (winner != null) endWithWinner(winner);
            else end(GameEndReason.LAST_TEAM_STANDING);
            return true;
        }
        return false;
    }

    private void gameTick() {
        if (gameState != GameState.IN_PROGRESS) return;

        // FIRST, before the hero/minion loops: whoever left the arena this tick must die on the
        // spot, and nothing below is allowed to delay that.
        enforceBounds();

        // Bot-only backstop: GameManager.leaveGame ends the game reactively when the last human
        // leaves, but a game where every remaining human simply logs out (crash, force-close)
        // never fires that path — it would otherwise run bots against each other forever.
        // onlinePlayers() (spectator-inclusive) naturally excludes bots, so an empty result here
        // means no human is left in any capacity. Checked every 100 ticks; cheap enough to not
        // need finer granularity.
        emptyCheckCounter++;
        if (emptyCheckCounter >= 100) {
            emptyCheckCounter = 0;
            if (onlinePlayers().isEmpty()) {
                end(GameEndReason.FORFEIT);
                return;
            }
        }

        // Pausable respawn countdowns: frozen while the re-pick GUI is open, so the pick
        // never races the timer (and the GUI can never still be open at respawn). Matched on the
        // re-pick flag, not the type — /hero opens the same class for a next-game pick, and that
        // must not let a dead player stall their own respawn.
        for (GamePlayer gamePlayer : gamePlayers.values()) {
            if (!gamePlayer.respawning() || gamePlayer.respawnTicksLeft() <= 0) continue;
            // APPROVED per-entity isolation (same class as the hero loop below): this was the
            // ONE gameTick loop without it. A throw between the ticks write and the flag clear
            // left respawning=true with 0 ticks — a state the guard above skips FOREVER. That
            // was the "one of four bots never respawns" wedge: exactly one participant
            // latched, everyone else kept respawning fine.
            try {
                Player respawner = resolveParticipantEntity(gamePlayer.uuid());
                if (respawner == null) continue;
                if (GUIManager.getOpenGui(respawner) instanceof HeroSelectGUI g && g.isRespawnPick()) continue;

                int left = gamePlayer.respawnTicksLeft() - 1;
                gamePlayer.setRespawnTicksLeft(left);
                if (left <= 0) {
                    // Cleared HERE, atomically with the ticks hitting 0 — never downstream of
                    // anything that can throw. handleRespawn's own clear is now redundant.
                    gamePlayer.setRespawning(false);
                    visuals.showRespawnTick(respawner, 0);
                    handleRespawn(respawner);
                } else if (left % 20 == 0) {
                    visuals.showRespawnTick(respawner, left / 20);
                }
            } catch (Exception e) {
                ErrorReporter.report(this, "Respawn tick failed for " + gamePlayer.uuid(), e);
            }
        }

        // Tick all heroes registered to this game — INCLUDING eliminated (dormant) ones,
        // whose independent abilities (in-flight projectiles, minion reapers) keep resolving.
        for (UUID uuid : new HashSet<>(tickingHeroes)) {
            Hero hero = HeroManager.getHero(uuid);
            if (hero == null) {
                tickingHeroes.remove(uuid);
                continue;
            }
            // Backstop: hero reference lost (logout/despawn) without passing through
            // removePlayer. Full teardown — the departed owner's independent state expires.
            Controller controller = hero.getController();
            if (controller == null || controller.getEntity() == null) {
                MinionManager.despawnAll(hero);
                hero.cleanup();
                HeroManager.unregister(uuid);
                tickingHeroes.remove(uuid);
                // A bot losing its entity is never normal (players log out; NPCs are destroyed by
                // us) — say so, and route it through removeBot so the roster shrinks and the win
                // check runs. Human leavers reach removePlayer via PlayerLeaveListener.
                if (isBotParticipant(uuid)) {
                    Heroes.getInstance().getLogger().severe(
                            "Bot " + uuid + " lost its entity mid-game — removing it from the game");
                    removeBot(uuid);
                }
                continue;
            }
            // APPROVED per-entity isolation (one of four in this batch): one throwing hero used to
            // abort every remaining hero, the whole minion loop and the crystal tick for that tick.
            try {
                hero.tick();
                // Eliminated bots stay in tickingHeroes for their independent abilities (Hero.tick
                // handles dormancy itself), but the bot-state calls below must NOT run for them:
                // ensureCombatState was flipping the eliminated spectator back to ADVENTURE every
                // tick and tickAI marched it back into the arena — an unhittable "respawn".
                if (!controller.isPlayer() && !hero.isEliminated()) {
                    BotController bot = (BotController) controller;
                    // Citizens re-creates NPC entities in SURVIVAL on chunk churn, and bots fire no
                    // PlayerMoveEvent — both have to be polled here or they never happen at all.
                    bot.ensureCombatState(hero);
                    bot.updateGroundState(hero);
                    bot.tickAI(hero);
                }
            } catch (Exception e) {
                ErrorReporter.report(this, "Hero tick failed for " + uuid, e);
            }
        }

        // Tick minions belonging to this game. Minions self-register with GameManager.entityGames
        // on spawn (via MinionHero.spawn), so we use that as the per-game filter.
        for (MinionHero minion : new ArrayList<>(MinionManager.getActiveMinions())) {
            Controller mc = minion.getController();
            if (mc == null) continue;
            if (GameManager.getEntityGame(mc.getUniqueId()) != this) continue;
            // Same approved isolation as the hero loop above.
            try {
                minion.tick();
                if (mc instanceof BotController bot) bot.tickAI(minion);
            } catch (Exception e) {
                ErrorReporter.report(this, "Minion tick failed for " + mc.getUniqueId(), e);
            }
        }

        // Built from activePlayers rather than onlinePlayers: bots are invisible to Bukkit's online
        // list, so they could never be dug out of a wall or pick up a smash crystal.
        Set<Player> alivePlayers = new HashSet<>();
        for (UUID uuid : new HashSet<>(activePlayers)) {
            Player p = resolveParticipantEntity(uuid);
            if (p != null) alivePlayers.add(p);
        }

        tickCrystals(alivePlayers);
        unstickTrappedPlayers(alivePlayers);
    }

    /**
     * Kills every participant who has left the arena box. The PlayerMoveEvent rescue only covers
     * real players: a bot fires no move event, so its only out-of-bounds death was vanilla void
     * damage at y=-128 — a 128-block dead zone below the map floor (~y=0.2) it spent seconds
     * falling through, and no death at all when it was knocked out sideways. Swept every tick.
     */
    private void enforceBounds() {
        for (UUID uuid : new HashSet<>(activePlayers)) {
            GamePlayer gamePlayer = gamePlayers.get(uuid);
            if (gamePlayer == null || gamePlayer.respawning()) continue;
            Player entity = resolveParticipantEntity(uuid);
            if (entity == null) continue;
            if (!outsideArena(entity.getLocation())) continue;

            // Void damage = the HP the fall still had left to take off them, read BEFORE the death
            // throws it away. Credit resolves through the last credited hit (a minion routes to its
            // owner via creditedId); no credited hit means nobody to bill, so record nothing.
            DamageCredit credit = lastDamageCredit.get(uuid);
            if (credit != null) {
                Interactions.recordVoidDamage(Bukkit.getPlayer(creditedId(credit.damager())),
                        credit.ability(), entity.getHealth());
                tallyVoidDamage(credit.damager(), uuid, entity.getHealth());
            }

            if (gamePlayer.isBot()) {
                if (!entity.getWorld().equals(settings.map().world()) && wrongWorldBotsLogged.add(uuid)) {
                    Heroes.getInstance().getLogger().severe("Bot " + gamePlayer.bot().getName() + " is in world "
                            + entity.getWorld().getName() + " but the game runs in " + settings.map().world().getName());
                }
                // Bots must NEVER die through vanilla: Citizens' own death handler detaches the NPC
                // before ours runs and NMS removes the body regardless, orphaning every uuid-keyed
                // map. Go straight to our own handler instead of setHealth(0).
                handleDeath(entity, null);
            } else {
                entity.setHealth(0);
                entity.sendMessage("You went out of bounds!");
            }
        }
    }

    /**
     * Out-of-bounds test that is world-aware. Map bounds are plain coordinates and every game gets
     * its own world clone of the same template, so a bare contains() check passes for a player
     * standing at identical coordinates in a DIFFERENT game's arena — they'd never be rescued.
     * Being in the wrong world at all counts as outside.
     */
    public boolean outsideArena(Location location) {
        return !location.getWorld().equals(settings.map().world())
                || !mapBounds().contains(location.toVector());
    }

    /**
     * The Player entity behind a participant uuid. Bukkit.getPlayer resolves real connections only,
     * and an NPC bot is a Player entity that was never added to the online list — every init,
     * bounds and respawn path that used Bukkit.getPlayer alone silently skipped every bot. Returns
     * null when nobody is there (offline player, despawned NPC).
     */
    public Player resolveParticipantEntity(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) return player;
        GamePlayer gamePlayer = gamePlayers.get(uuid);
        if (gamePlayer == null || !gamePlayer.isBot()) return null;
        // Identity assert: the NPC handle is only trusted while it still resolves to the uuid
        // this participant was registered under. If Citizens ever hands back a different
        // entity, returning it would route this participant's respawn/teleport onto a
        // stranger (and clear the WRONG GamePlayer's flags) — report "entity gone" instead,
        // which the hero-loop backstop turns into a loud removeBot.
        return NpcServiceProvider.get().getBukkitEntity(gamePlayer.bot().getNpcHandle())
                instanceof Player botEntity && botEntity.getUniqueId().equals(uuid) ? botEntity : null;
    }

    /**
     * Digs out players whose hitbox is embedded in block collision geometry. Nothing else will —
     * see the trappedTicks field. Requires TRAPPED_GRACE_TICKS of continuous overlap so a fast
     * ability that clips a corner for a frame is left alone, then nudges along the shortest free
     * axis, falling back to a respawn point only when there is no way out within reach.
     */
    private void unstickTrappedPlayers(Set<Player> players) {
        for (Player player : players) {
            UUID uuid = player.getUniqueId();
            if (isRespawning(uuid) || !activePlayers.contains(uuid) || !WallDetection.isTrapped(player)) {
                trappedTicks.remove(uuid);
                continue;
            }
            if (trappedTicks.merge(uuid, 1, Integer::sum) < TRAPPED_GRACE_TICKS) continue;

            trappedTicks.remove(uuid);
            Vector out = WallDetection.escapeOffset(player, TRAPPED_ESCAPE_DISTANCE, TRAPPED_ESCAPE_STEP);
            if (out != null) teleportParticipant(uuid, player, player.getLocation().add(out));
            else teleportParticipant(uuid, player, getRespawnLocation(player, null, player.getLocation()));
        }
    }

    /** The active smash crystal's location, or null when none is up. Bots pathfind to it —
     *  the pickup itself is the alivePlayers overlap check in tickCrystals (V2 §11). */
    public Location activeCrystalLocation() {
        return activeCrystal != null ? activeCrystal.location() : null;
    }

    private void tickCrystals(Set<Player> alivePlayers) {
        if (!settings.smashCrystals()) return;

        if (activeCrystal != null) {
            // Only eligible players can pick up the crystal
            Set<Player> eligiblePlayers = alivePlayers.stream()
                    .filter(p -> activePlayers.contains(p.getUniqueId()))
                    .filter(p -> {
                        Hero hero = HeroManager.getHero(p);
                        if (hero == null || hero.getSmashAbility() == null) return false;
                        var cooldown = hero.getSmashAbility().getCooldownObject();
                        if (cooldown.isInUse()) return false;
                        return cooldown.getTimeRemaining() > 0;
                    })
                    .collect(Collectors.toSet());

            Player consumer = activeCrystal.tick(eligiblePlayers);
            if (consumer != null) {
                onCrystalPickup(consumer);
                activeCrystal.remove();
                activeCrystal = null;
                crystalTickCounter = 0;
            }
            return;
        }

        crystalTickCounter++;
        if (crystalTickCounter >= settings.crystalInterval() * 20) {
            Location spawnLoc = chooseCrystalLocation(alivePlayers);
            if (spawnLoc != null) {
                activeCrystal = new SmashCrystal(spawnLoc);
            }
            crystalTickCounter = 0;
        }
    }

    private Location chooseCrystalLocation(Set<Player> alivePlayers) {
        List<Location> candidates = settings.map().crystalLocations();
        if (candidates == null || candidates.isEmpty()) return null;

        Location bestLocation = null;
        double bestMinDist = -1;

        for (Location candidate : candidates) {
            double minDist = Double.MAX_VALUE;
            for (Player player : alivePlayers) {
                double dist = candidate.distanceSquared(player.getLocation());
                if (dist < minDist) {
                    minDist = dist;
                }
            }
            if (minDist > bestMinDist) {
                bestMinDist = minDist;
                bestLocation = candidate;
            }
        }
        return bestLocation;
    }

    private void onCrystalPickup(Player player) {
        Hero hero = HeroManager.getHero(player);
        if (hero != null && hero.getSmashAbility() != null) {
            hero.getSmashAbility().getCooldownObject().reduceCooldown(hero.getSmashAbility().getCooldown());
        }
    }

    public Team calculateWinnerByLives() {
        Team winningTeam = null;
        int maxLives = -1;
        boolean isDraw = false;

        for (Team team : teams.values()) {
            if (team.color() == TeamColor.SPECTATOR) continue;

            int totalLives = team.playerUUIDs().stream()
                    .mapToInt(uuid -> gamePlayers.get(uuid).currentLives())
                    .sum();
            if (totalLives > maxLives) {
                maxLives = totalLives;
                winningTeam = team;
                isDraw = false;
            } else if (totalLives == maxLives) {
                isDraw = true;
            }
        }

        if (isDraw || winningTeam == null) {
            return null;
        } else {
            return winningTeam;
        }
    }

    public void clean() {
        // Only modify global state for entities that still belong to this game.
        // If a player/bot has moved to another game (or been fully cleaned up elsewhere),
        // drop them from our local state silently — don't touch entityGames, HeroManager,
        // their scoreboard, or teleport them. Doing so would corrupt their new game.
        for (UUID uuid : getAllPlayerUUIDs()) {
            if (GameManager.getEntityGame(uuid) != this) {
                for (Team team : teams.values()) team.remove(uuid);
                activePlayers.remove(uuid);
                continue;
            }
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                removePlayer(p);
                Heroes.teleportToLobby(p);
            }
        }
        // Despawn all bots — same ownership guard for consistency
        for (Map.Entry<UUID, GameBot> entry : new HashMap<>(gameBots).entrySet()) {
            UUID botUuid = entry.getKey();
            if (GameManager.getEntityGame(botUuid) != this) continue;
            GameBot bot = entry.getValue();
            NpcServiceProvider.get().remove(bot.getNpcHandle().id());
            HeroManager.removeHero(botUuid);
            GameManager.unregisterEntity(botUuid);
        }
        gameBots.clear();
        // Eliminated (dormant) heroes linger by design; the removePlayer loop above tears
        // down everyone still registered to this game. Drop any remaining tick entries.
        tickingHeroes.clear();
        cleanupWorld();
    }

    /**
     * Removes a player from the game without teleporting them or triggering end checks.
     * Used when transferring players between games (e.g., map change).
     *
     * <p>Deliberately the ONE team-shrinking path with no {@link #checkEnd()}: the roster is being
     * moved wholesale to a replacement game, so a mid-transfer emptiness is not a win. Every other
     * path that can empty a team (both handleDeath branches, removePlayer, removeBot, the kill-count
     * hook) must end in checkEnd, since there is no periodic backstop in gameTick.
     */
    public void silentRemove(Player player) {
        Team team = playerTeam(player.getUniqueId());
        if (team == null) return;

        team.remove(player.getUniqueId());
        activePlayers.remove(player.getUniqueId());
        gamePlayers.get(player.getUniqueId()).markLeft();
        // Must drop the tick entry: the transfer target registers a NEW hero under the same
        // UUID, and this game's tick loop would otherwise double-tick it until clean().
        tickingHeroes.remove(player.getUniqueId());
        HeroManager.removePlayer(player);
        GameEffects.removeEffects(player);
        GameEffects.unRestrictPlayer(player);
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        // The transfer target re-derives both; leaving the old outlines up would strand them on
        // entity ids this game no longer owns.
        TeamGlow.clearFor(player);
    }

    /**
     * Cleans up the world without affecting players.
     * Used after players have been transferred to a new game.
     */
    public void cleanupWorld() {
        activeTasks.forEach(BukkitTask::cancel);
        activeTasks.clear();
        visuals.cancelTasks();

        if (activeCrystal != null) {
            activeCrystal.remove();
            activeCrystal = null;
        }

        if (settings.map() != null) {
            GameMapManager.deleteWorld(settings.map());
        }
    }

    /**
     * Places a participant on their team's spawn. Keyed on UUID, not on a Player: bots have no
     * entry in Bukkit's online list, so the old signature could not be called for them at all.
     */
    private void teleportToSpawn(UUID uuid, TeamColor teamColor) {
        Player entity = resolveParticipantEntity(uuid);
        if (entity == null) return;

        if (teamColor == null || teamColor == TeamColor.SPECTATOR) {
            teleportParticipant(uuid, entity, settings.map().spectatorLocation());
            return;
        }

        Team team = teams.get(teamColor);
        int teamIndex = teamColor.ordinal();
        // Index into the ROSTER, sorted for determinism. The old
        // `team.onlinePlayers().indexOf(player)` was -1 for every bot (they are not online
        // players), so bots all shared the un-offset base spawn, and the HashSet iteration order
        // meant a player's offset could change from one call to the next.
        List<UUID> roster = new ArrayList<>(team.playerUUIDs());
        roster.sort(Comparator.naturalOrder());
        int playerIndex = roster.indexOf(uuid);
        List<Vector> offsets = GameConfigManager.getSpawnOffsets();

        teleportParticipant(uuid, entity, settings.map().getSpawnForTeam(teamIndex, playerIndex, offsets));
    }

    /**
     * Moves a participant. Bots go through the NPC service rather than entity.teleport: the NPC's
     * stored location and its navigator are only updated by NPC.teleport, so a raw entity teleport
     * leaves the bot pathing home from where it used to be.
     */
    private void teleportParticipant(UUID uuid, Player entity, Location location) {
        if (isBotParticipant(uuid)) {
            NpcServiceProvider.get().teleport(gamePlayers.get(uuid).bot().getNpcHandle(), location);
        } else {
            entity.teleport(location);
        }
    }


    // --- VISIBILITY ---

    /**
     * The single per-viewer visibility rule: you see the participants of your own game and nobody
     * else; two players with no game see each other (the lobby). Call it whenever {@code player}
     * changes game, team or respawn state — it re-evaluates BOTH directions for that player.
     *
     * <p>This is what isolates spectators. The spectator teleport menu is built client-side from the
     * player list, so a player who is hidden is not offerable as a target — which is why a spectator
     * could previously jump into a stranger's match or the lobby. {@code SpectatorListener} is the
     * server-side fence behind it, for the cases the client list does not cover.
     *
     * <p>Respawn invisibility rides this same rule rather than owning a second hide/show pass:
     * {@code hidePlayer} is keyed on (plugin, viewer, target), so two systems writing it would
     * clobber each other — a blanket show from the respawn path would reveal a foreign player.
     */
    public static void refreshVisibility(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            applyVisibility(player, other);
            applyVisibility(other, player);
        }
        // Bots: their Citizens tab entries are broadcast server-wide (the entry must exist
        // for the profile to resolve), and Paper's per-viewer listing scopes WHO actually
        // sees them — only this viewer's own game. After applyVisibility on purpose:
        // listPlayer throws for a target the viewer can't see.
        for (Game game : GameManager.getActiveGames()) {
            for (Player botEntity : game.botEntities()) {
                applyListing(player, botEntity);
            }
        }
    }

    /** Tab-listing rule for bot NPC entities: listed only for players IN the bot's game. */
    private static void applyListing(Player viewer, Player botEntity) {
        Game viewerGame = GameManager.getPlayerGame(viewer);
        boolean sameGame = viewerGame != null
                && viewerGame == GameManager.getEntityGame(botEntity.getUniqueId());
        if (sameGame && viewer.canSee(botEntity)) {
            viewer.listPlayer(botEntity);
        } else {
            viewer.unlistPlayer(botEntity);
        }
    }

    /** Re-applies the listing rule for one bot against every online real player — on game
     *  join (addBot) and whenever Citizens re-creates the entity (chunk churn), because a
     *  fresh entity's tab entry starts globally listed again. */
    public static void refreshBotListing(Player botEntity) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!me.stolyy.heroes.physics.Interactions.isRealPlayer(viewer)) continue;
            applyListing(viewer, botEntity);
        }
    }

    /** Live bot NPC Player entities in this game (skips bots whose entity is gone). */
    public java.util.List<Player> botEntities() {
        java.util.List<Player> out = new java.util.ArrayList<>();
        for (UUID uuid : gameBots.keySet()) {
            Player p = resolveParticipantEntity(uuid);
            if (p != null) out.add(p);
        }
        return out;
    }

    private static void applyVisibility(Player viewer, Player target) {
        if (canSee(viewer, target)) viewer.showPlayer(Heroes.getInstance(), target);
        else viewer.hidePlayer(Heroes.getInstance(), target);
    }

    private static boolean canSee(Player viewer, Player target) {
        Game viewerGame = GameManager.getPlayerGame(viewer);
        if (viewerGame != GameManager.getPlayerGame(target)) return false;
        if (viewerGame == null) return true;  // both in the lobby
        return !viewerGame.hiddenWhileRespawning(viewer, target);
    }

    /**
     * A respawning player is invisible for the countdown. Teammates keep seeing the frozen body when
     * teammate-glow.show-respawning is on — that IS the "where is my teammate coming back" cue, and
     * it only works because the entity survives on their clients for the outline packets to address.
     */
    private boolean hiddenWhileRespawning(Player viewer, Player target) {
        if (!isRespawning(target.getUniqueId())) return false;
        if (!TeamGlow.isRespawnGlowEnabled()) return true;
        return !sameTeam(viewer.getUniqueId(), target.getUniqueId());
    }


    // --- TARGET VALIDATION ---

    /**
     * Checks if an entity is actively alive in this game.
     */
    public boolean isAlive(LivingEntity entity) {
        if (!Hitbox.isValidTarget(entity)) return false;
        if (gameState() != GameState.IN_PROGRESS) return false;

        if (entity instanceof Player player) {
            Team team = playerTeam(player.getUniqueId());
            if (team == null || team.color() == TeamColor.SPECTATOR) return false;
            if (isRespawning(player.getUniqueId())) return false;
        }
        return true;
    }

    /**
     * Full interaction check within this game.
     */
    public boolean canTarget(LivingEntity attacker, LivingEntity victim) {
        return canTarget(attacker, victim, null);
    }

    public boolean canTarget(LivingEntity attacker, LivingEntity victim, Boolean overrideFriendlyFire) {
        if (attacker == null || victim == null) return false;
        if (attacker.getUniqueId().equals(victim.getUniqueId())) return false;
        // Victim must be actively alive/targetable. The attacker only needs a live entity:
        // an eliminated (dormant) hero's in-flight projectiles still hit and can still kill
        // (A1 lifecycle). Spectators have no attack inputs, so this opens no exploit.
        if (!isAlive(victim)) return false;
        if (attacker.isDead()) return false;
        if(gameState != GameState.IN_PROGRESS) return false;

        // Invincibility is a victim-only immunity: an invincible minion can't be hit, but is still
        // free to attack. (Previously this lived in Hitbox.isValidTarget, which gated BOTH sides,
        // so invincible minions silently couldn't damage anyone.)
        Hero victimHero = HeroManager.getHero(victim);
        if (victimHero instanceof MinionHero victimMinion && victimMinion.isInvincible()) return false;

        // Compare COMBAT sides, not current teams: an eliminated player is parked on SPECTATOR,
        // but their lingering world state (molotov zones, in-flight projectiles — A1 lifecycle)
        // must still respect friendly fire against their old team. Falls back to the original
        // round color when a side sits on SPECTATOR.
        TeamColor attackerSide = combatColor(attacker.getUniqueId());
        TeamColor victimSide = combatColor(victim.getUniqueId());
        if (attackerSide != null && attackerSide == victimSide) {
            return (overrideFriendlyFire != null)
                    ? overrideFriendlyFire
                    : friendlyFire(attacker.getUniqueId());
        }
        return true;
    }

    /** The side an entity fights FOR: current team color, except SPECTATOR resolves to the color
     *  they started the round on (null for pure spectators, who never match anyone). */
    public TeamColor combatColor(UUID uuid) {
        Team team = playerTeam(uuid);
        TeamColor color = team != null ? team.color() : null;
        if (color != TeamColor.SPECTATOR) return color;
        return originalTeams.get(uuid);
    }

    /**
     * Returns all living entities (players, bots, AND minions) in this game that are on a
     * DIFFERENT team from the given UUID.
     */
    public List<LivingEntity> getEnemies(UUID entityUUID) {
        Team myTeam = playerTeam(entityUUID);
        List<LivingEntity> result = new ArrayList<>();
        collectByTeamRelation(entityUUID, myTeam, result, true);
        return result;
    }

    /**
     * Returns all living entities (players, bots, AND minions) in this game that are on the
     * SAME team as the given UUID, excluding itself.
     */
    public List<LivingEntity> getTeammates(UUID entityUUID) {
        Team myTeam = playerTeam(entityUUID);
        List<LivingEntity> result = new ArrayList<>();
        collectByTeamRelation(entityUUID, myTeam, result, false);
        return result;
    }

    /**
     * Walks both {@code activePlayers} and active minions for this game, adding entities whose
     * team relation to {@code myTeam} matches {@code wantEnemies}.
     */
    private void collectByTeamRelation(UUID self, Team myTeam, List<LivingEntity> sink, boolean wantEnemies) {
        for (UUID uuid : activePlayers) {
            if (uuid.equals(self)) continue;
            Team theirTeam = playerTeam(uuid);
            if (theirTeam == null) continue;
            boolean enemy = (theirTeam != myTeam);
            if (enemy != wantEnemies) continue;
            Hero hero = HeroManager.getHero(uuid);
            if (hero != null && hero.getController() != null) {
                LivingEntity entity = hero.getController().getEntity();
                if (entity != null && entity.isValid()) sink.add(entity);
            }
        }
        for (MinionHero minion : MinionManager.getActiveMinions()) {
            Controller mc = minion.getController();
            if (mc == null) continue;
            UUID uuid = mc.getUniqueId();
            if (uuid == null || uuid.equals(self)) continue;
            if (GameManager.getEntityGame(uuid) != this) continue;
            Team theirTeam = playerTeam(uuid);  // resolves to owner's team via fallback
            if (theirTeam == null) continue;
            boolean enemy = (theirTeam != myTeam);
            if (enemy != wantEnemies) continue;
            LivingEntity entity = mc.getEntity();
            if (entity != null && entity.isValid()) sink.add(entity);
        }
    }

    //GETTERS
    public boolean sameTeam(UUID first, UUID second){ return playerTeam(first) == playerTeam(second); }
    public boolean friendlyFire(UUID uuid){ return resolveGamePlayer(uuid).settings().friendlyFire(); }
    public boolean ultimateEnabled(UUID uuid) { return resolveGamePlayer(uuid).settings().ultimatesEnabled(); }
    public int lives(UUID uuid){ return resolveGamePlayer(uuid).currentLives(); }
    public TeamColor playerColor(UUID uuid) { return playerTeam(uuid).color(); }

    /**
     * The team the player was on at game start, or null if they were never a participant.
     * Eliminated players are moved to SPECTATOR, so end-of-game logic (victory titles,
     * win/loss stats) must use this instead of the live team color.
     */
    public TeamColor originalColor(UUID uuid) { return originalTeams.get(uuid); }

    public GamePlayer getGamePlayer(UUID uuid) { return gamePlayers.get(uuid); }
    public Map<UUID, GamePlayer> getGamePlayers() { return Collections.unmodifiableMap(gamePlayers); }
    public Set<UUID> getActivePlayers() { return Collections.unmodifiableSet(activePlayers); }

    public GameModeConfig modeConfig() { return modeConfig; }
    public GameModePreset gameModePreset() { return gameModePreset; }

    /** Installs a mode config directly. Must be called before start(). */
    public void setModeConfig(GameModeConfig modeConfig) {
        boolean startHpChanged = this.modeConfig.startHp() != modeConfig.startHp();
        this.modeConfig = modeConfig;
        if (startHpChanged && gameState == GameState.WAITING) pushStartHpToSettings();
    }

    /**
     * Preset entry point (GUI): stores the preset for display and installs its config
     * built from the gamemodes.yml defaults. Must be called before start().
     */
    public void setGameModePreset(GameModePreset preset) {
        this.gameModePreset = preset;
        setModeConfig(preset.buildConfig());
    }

    /**
     * Keeps every participant's PlayerSettings.health in sync with the mode's startHp so
     * the settings GUIs always show what the game will actually apply. startHp > 0 pushes
     * that value; startHp <= 0 (Default/Custom) resets to the game-config default so a
     * stale preset value doesn't linger. Per-player edits made AFTER a preset/startHp
     * change win at start — there is no start-time clobber anymore.
     */
    private void pushStartHpToSettings() {
        double hp = modeConfig.startHp() > 0 ? modeConfig.startHp() : new PlayerSettings().health();
        for (GamePlayer gamePlayer : gamePlayers.values()) {
            gamePlayer.settings().setHealth(hp);
        }
    }

    public UUID bossPlayer() { return bossPlayer; }
    public void setBossPlayer(UUID bossPlayer) { this.bossPlayer = bossPlayer; }

    /** Kill-feed name color: the participant's team color; RED for non-participants (minions). */
    private net.kyori.adventure.text.format.NamedTextColor feedColor(UUID uuid) {
        GamePlayer gamePlayer = gamePlayers.get(uuid);
        return gamePlayer != null && gamePlayer.team() != null
                ? gamePlayer.team().textColor() : NamedTextColor.RED;
    }

    /** Total kills for a team, including participants who have since left the game. */
    public int teamKills(TeamColor color) {
        int total = 0;
        for (GamePlayer gamePlayer : gamePlayers.values()) {
            if (gamePlayer.team() == color) total += gamePlayer.kills();
        }
        return total;
    }

    /** Team with the most kills, or null on a tie (KILL_TARGET timeout winner). */
    public TeamColor leadingTeamByKills() {
        TeamColor leader = null;
        int best = -1;
        boolean tie = false;
        for (TeamColor color : TeamColor.values()) {
            if (color == TeamColor.SPECTATOR) continue;
            int kills = teamKills(color);
            if (kills > best) {
                best = kills;
                leader = color;
                tie = false;
            } else if (kills == best) {
                tie = true;
            }
        }
        return tie ? null : leader;
    }

    /** Whether this participant is mid-respawn. Null-safe: not in this game = not respawning. */
    public boolean isRespawning(UUID uuid) {
        GamePlayer gamePlayer = gamePlayers.get(uuid);
        return gamePlayer != null && gamePlayer.respawning();
    }

    /**
     * Who answers for {@code uuid}'s output. A minion is an NPC with no GamePlayer, no stats file and
     * no team roster entry of its own, so its kills, damage and rule hooks belong to its owner;
     * players and bots answer for themselves. Every credit path walks this one resolver so kill
     * attribution and per-participant state can never drift apart.
     */
    public UUID creditedId(UUID uuid) {
        Hero hero = HeroManager.getHero(uuid);
        return hero instanceof MinionHero minion ? minion.getOwnerId() : uuid;
    }

    /**
     * Resolves per-participant state for players and bots directly; minions defer to their
     * owner's GamePlayer (mirrors playerTeam's minion fallback).
     */
    private GamePlayer resolveGamePlayer(UUID uuid) {
        GamePlayer gamePlayer = gamePlayers.get(uuid);
        return gamePlayer != null ? gamePlayer : gamePlayers.get(creditedId(uuid));
    }

    public GameVisuals getVisuals() { return visuals; }

    public long startedAt() { return startedAt; }
    public long durationMs() { return startedAt == 0 ? 0 : System.currentTimeMillis() - startedAt; }
    public String seriesId() { return seriesId; }
    public int seriesRound() { return seriesRound; }
    public boolean isSeriesRound() { return roundEndCallback != null; }

    /** MatchSeries stamps each round it creates so the match log can group them. */
    public void setSeriesInfo(String seriesId, int seriesRound) {
        this.seriesId = seriesId;
        this.seriesRound = seriesRound;
    }

    /**
     * Per-game damage tally, fed from the same site as the lifetime stats. The dealer resolves
     * through {@link #creditedId} so a minion's damage lands on its owner; damage dealt TO a
     * minion is not tallied (matching the lifetime rule), and a minion victim has no GamePlayer
     * so nothing is taken.
     */
    public void tallyDamage(UUID attacker, UUID victim, String ability, double damage) {
        if (!(HeroManager.getHero(victim) instanceof MinionHero)) {
            GamePlayer dealer = gamePlayers.get(creditedId(attacker));
            if (dealer != null) dealer.addDamageDealt(ability, damage);
        }
        GamePlayer taker = gamePlayers.get(victim);
        if (taker != null) taker.addDamageTaken(damage);
    }

    /** Per-game void damage: the HP the void finished off, credited to the last damager's owner. */
    public void tallyVoidDamage(UUID damager, UUID victim, double amount) {
        GamePlayer dealer = gamePlayers.get(creditedId(damager));
        if (dealer != null) dealer.addVoidDamageDealt(amount);
        GamePlayer taker = gamePlayers.get(victim);
        if (taker != null) taker.addVoidDamageTaken(amount);
    }
    public GameState gameState() { return gameState; }
    public GameMode gameMode() { return gameMode; }
    public GameSettings settings() { return settings; }
    public Map<TeamColor, Team> getTeams() { return Collections.unmodifiableMap(teams); }
    public Map<UUID, GameBot> getGameBots() { return Collections.unmodifiableMap(gameBots); }

    /** Record that {@code damager} just dealt damage to {@code victim} in this game. */
    public void recordDamager(UUID victim, UUID damager) {
        if (victim.equals(damager)) return;
        lastDamagers.put(victim, damager);
    }

    /** Returns the UUID of the last entity to damage {@code victim} in this game, or null. */
    public UUID getLastDamager(UUID victim) {
        return lastDamagers.get(victim);
    }

    /** Clears the last-damager entry for {@code victim}. Called on respawn and consumption. */
    public void clearDamager(UUID victim) {
        lastDamagers.remove(victim);
        lastDamageCredit.remove(victim);
    }

    /**
     * Records the ability-attributed hit behind {@code damager} -> {@code victim}. Called from
     * Interactions.applyDamage, the only place the ability name exists.
     */
    public void recordDamageCredit(UUID victim, UUID damager, String ability) {
        if (victim.equals(damager)) return;
        lastDamageCredit.put(victim, new DamageCredit(damager, ability));
    }

    /** The last credited hit on {@code victim}, or null if nothing has hit them since the last clear. */
    public DamageCredit lastDamageCredit(UUID victim) {
        return lastDamageCredit.get(victim);
    }

    public Set<Player> onlinePlayers(){ return onlinePlayers(true); }

    public Set<Player> onlinePlayers(boolean includeSpectators) {
        return teams.values().stream()
                .filter(team -> includeSpectators || team.color() != TeamColor.SPECTATOR)
                .flatMap(team -> team.onlinePlayers().stream())
                .collect(Collectors.toSet());
    }

    private Set<UUID> getAllPlayerUUIDs() {
        return teams.values().stream()
                .flatMap(team -> team.playerUUIDs().stream())
                .collect(Collectors.toSet());
    }

    public Team playerTeam(UUID uuid) {
        for (Team team : teams.values()) {
            if (team.contains(uuid)) return team;
        }
        // Minion resolution: minions aren't on a Team roster, but the owner injects their team
        // color once at spawn. Resolve from that cached color instead of re-walking the owner.
        Hero hero = HeroManager.getHero(uuid);
        if (hero instanceof MinionHero minion && minion.getTeamColor() != null) {
            return teams.get(minion.getTeamColor());
        }
        return null;
    }

    public Location getRespawnLocation(Player player, LivingEntity killer, Location deathLocation){
        Team team = playerTeam(player.getUniqueId());
        if(team == null || team.color() == TeamColor.SPECTATOR) {
            return settings.map().spectatorLocation();
        }
        // Location.distance THROWS across worlds, and either reference can be in another one: a
        // killer who was transferred out, or a death location whose world was already unloaded
        // (getWorld() then reads null through the weak reference). Neither may reach the search.
        World world = settings.map().world();
        Location reference = null;
        if (killer != null && world.equals(killer.getWorld())) reference = killer.getLocation();
        else if (world.equals(deathLocation.getWorld())) reference = deathLocation;
        Location base = reference == null
                ? settings.map().spawnLocations().get(0).clone()
                : settings.map().getFurthestSpawnFromLocation(reference);
        // Same roster offset teleportToSpawn applies: without it, every concurrent respawner
        // on a team came back stacked on the exact same block (4 bots = a shoving match on
        // one point, feeding knock-offs during the countdown).
        List<UUID> roster = new ArrayList<>(team.playerUUIDs());
        roster.sort(Comparator.naturalOrder());
        int playerIndex = roster.indexOf(player.getUniqueId());
        List<Vector> offsets = GameConfigManager.getSpawnOffsets();
        if (playerIndex >= 0 && playerIndex < offsets.size()) {
            base.add(offsets.get(playerIndex));
        }
        return base;
    }

    public BoundingBox mapBounds(){
        return settings.boundaries();
    }

    private void setGameState(GameState newState) {
        this.gameState = newState;
    }

    public PlayerSettings defaultTeamSettings() {
        return defaultPlayerSettings;
    }

    /**
     * Team-scope edit: applies one settings change to every GamePlayer currently on the
     * given team. This replaces Team's shared settings object — per-player copies mean a
     * later game-wide or per-player edit can't be clobbered by a stale save.
     */
    public void applySettings(TeamColor teamColor, java.util.function.Consumer<PlayerSettings> change) {
        for (GamePlayer gamePlayer : gamePlayers.values()) {
            if (gamePlayer.leftGame()) continue;
            if (gamePlayer.team() != teamColor) continue;
            change.accept(gamePlayer.settings());
        }
    }

    /**
     * Game-scope edit: applies one settings change to the game defaults (inherited by
     * anyone who joins later) and to every current GamePlayer, spectators included so
     * they carry correct values if dragged onto a team.
     */
    public void applySettingsAll(java.util.function.Consumer<PlayerSettings> change) {
        change.accept(defaultPlayerSettings);
        for (GamePlayer gamePlayer : gamePlayers.values()) {
            if (gamePlayer.leftGame()) continue;
            change.accept(gamePlayer.settings());
        }
    }

    private void initializePlayerStats(Player player, String heroName) {
        PlayerStats stats = PlayerStatsManager.getStats(player);
        if (heroName != null) {
            stats.setCurrentContext(gameMode, heroName);
        }
    }

    /** All XP in custom games is scaled down (default halved). */
    private int scaleXp(int amount) {
        if (gameMode != GameMode.CUSTOM) return amount;
        return (int) Math.round(amount * ProgressionConfig.getCustomXpMultiplier());
    }

    /** Per-game XP ledger for the end-of-game summary: player -> hero -> XP earned. */
    private final Map<UUID, Map<String, Integer>> xpEarned = new HashMap<>();

    /** Grants XP to the hero's GLOBAL level pool and processes level-ups. */
    private void awardXp(Player player, int amount) {
        if (amount <= 0) return;
        PlayerStats stats = PlayerStatsManager.getStats(player);
        if (stats.currentHero() == null) return;
        String heroName = stats.currentHero();
        xpEarned.computeIfAbsent(player.getUniqueId(), k -> new LinkedHashMap<>())
                .merge(heroName, amount, Integer::sum);
        int oldSmashLevel = stats.smashLevel();
        List<Integer> newLevels = stats.getHeroLevel(heroName).addXp(amount);
        handleLevelUps(player, heroName, newLevels, stats);
        grantSmashLevelTokens(player, stats, oldSmashLevel);
    }

    /**
     * Tokens track the TOTAL smash level (sum across heroes), one per cadence multiple
     * crossed: a level-3 shoop + level-2 botmon = smash level 5 = a token, even though
     * neither hero is individually level 5.
     */
    private void grantSmashLevelTokens(Player player, PlayerStats stats, int oldSmashLevel) {
        int cadence = ProgressionConfig.getTokenEveryNLevels();
        if (cadence <= 0) return;
        int earned = stats.smashLevel() / cadence - oldSmashLevel / cadence;
        if (earned <= 0) return;
        stats.addTokens(earned);
        player.sendMessage(Component.text(
                (earned == 1 ? "You earned an unlock token!" : "You earned " + earned + " unlock tokens!")
                        + " Balance: " + stats.getTokenBalance(), NamedTextColor.GOLD));
    }

    /** Personal stats recap for every real participant still in the game; runs once XP is final. */
    private void sendPersonalStats() {
        for (Map.Entry<UUID, TeamColor> entry : originalTeams.entrySet()) {
            if (entry.getValue() == TeamColor.SPECTATOR) continue;
            GamePlayer gamePlayer = gamePlayers.get(entry.getKey());
            if (gamePlayer.isBot() || gamePlayer.leftGame()) continue;
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null) continue;
            visuals.sendPersonalStats(p, gamePlayer, xpEarned.get(entry.getKey()));
        }
    }

    /** Post-game XP summary — one line per hero the player earned XP on this game. */
    private void sendXpSummaries() {
        for (Map.Entry<UUID, Map<String, Integer>> entry : xpEarned.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null) continue;
            PlayerStats stats = PlayerStatsManager.getStats(p);
            for (Map.Entry<String, Integer> heroXp : entry.getValue().entrySet()) {
                HeroLevel level = stats.getHeroLevel(heroXp.getKey());
                int required = ProgressionConfig.getXpForLevel(level.level());
                String heroDisplay = HeroConfig.formatName(heroXp.getKey());
                if (required == Integer.MAX_VALUE) {
                    p.sendMessage(Component.text("You earned " + heroXp.getValue() + " XP on "
                            + heroDisplay + ". Level " + level.level() + " (MAX).", NamedTextColor.AQUA));
                } else {
                    int pct = (int) Math.round(100.0 * level.xp() / required);
                    p.sendMessage(Component.text("You earned " + heroXp.getValue() + " XP on "
                            + heroDisplay + ". You are " + level.xp() + "/" + required + " (" + pct
                            + "%) of the way to level " + (level.level() + 1) + ".", NamedTextColor.AQUA));
                }
            }
        }
    }

    private void handleLevelUps(Player player, String heroName, List<Integer> newLevels, PlayerStats stats) {
        // Tokens are granted on SMASH-level multiples in grantSmashLevelTokens, not here.
        for (int level : newLevels) {
            player.sendMessage(Component.text(HeroConfig.formatName(heroName) + " reached level " + level + "!",
                    NamedTextColor.AQUA));
            if (level == ProgressionConfig.getMasterSkinLevel()) {
                stats.setSkinForHero(heroName, "masterskin");
                player.sendMessage(Component.text("You mastered " + HeroConfig.formatName(heroName)
                        + " — Master Skin unlocked!", NamedTextColor.LIGHT_PURPLE));
            }
        }
    }

    /**
     * The end-of-game summary (winner, team, and the top-killer lines with their kill counts) is
     * the "end.victory.chat" message list — and GameVisuals.win only sends it inside its
     * winning-team branch, so the losing side got the one-line "end.defeat.title" and never saw
     * the kills at all. Send the identical block to everyone who did NOT win, so both teams read
     * the same summary. Judged on the ORIGINAL team, like GameVisuals does, because eliminated
     * players are parked on SPECTATOR by now.
     */
    private void sendSummaryToLosers(Team winningTeam, Map<String, String> placeholders) {
        List<Component> lines = GameConfigManager.getMessageList("end.victory.chat", placeholders);
        for (Player p : onlinePlayers()) {
            TeamColor color = originalColor(p.getUniqueId());
            if (color == null) color = playerColor(p.getUniqueId());
            if (teams.get(color) == winningTeam) continue;
            for (Component line : lines) p.sendMessage(line);
        }
    }

    private Map<String, String> buildEndPlaceholders(Team winningTeam) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("team", winningTeam.color().displayName());

        // Winning team player names
        String teamPlayers = winningTeam.playerUUIDs().stream()
                .map(uuid -> participantName(gamePlayers.get(uuid)))
                .collect(Collectors.joining(", "));
        placeholders.put("team_players", teamPlayers);

        // Top killers (leavers keep their entries, so their kills still count here)
        List<GamePlayer> topKillers = gamePlayers.values().stream()
                .filter(gamePlayer -> gamePlayer.kills() > 0)
                .sorted(Comparator.comparingInt(GamePlayer::kills).reversed())
                .limit(3)
                .toList();

        for (int i = 0; i < 3; i++) {
            String idx = String.valueOf(i + 1);
            if (i < topKillers.size()) {
                GamePlayer gamePlayer = topKillers.get(i);
                placeholders.put("top" + idx + "_name", participantName(gamePlayer));
                placeholders.put("top" + idx + "_kills", String.valueOf(gamePlayer.kills()));
            } else {
                placeholders.put("top" + idx + "_name", "-");
                placeholders.put("top" + idx + "_kills", "0");
            }
        }

        return placeholders;
    }

    String participantName(GamePlayer gamePlayer) {
        Player p = Bukkit.getPlayer(gamePlayer.uuid());
        if (p != null) return p.getName();
        if (gamePlayer.bot() != null) return gamePlayer.bot().getName();
        String offline = Bukkit.getOfflinePlayer(gamePlayer.uuid()).getName();
        return offline != null ? offline : "Unknown";
    }

    private void recordWinLoss(Team winningTeam) {
        // Original teams, so eliminated players (moved to SPECTATOR) are counted correctly — and
        // keyed on UUID, not on an online Player: skipping whoever had disconnected is what let a
        // player dodge a loss by quitting. PlayerStats loads from disk for an offline UUID.
        for (Map.Entry<UUID, TeamColor> entry : originalTeams.entrySet()) {
            UUID uuid = entry.getKey();
            if (isBotParticipant(uuid)) continue;  // NPC UUIDs must never get a stats file
            Player p = Bukkit.getPlayer(uuid);
            PlayerStats stats = p != null ? PlayerStatsManager.getStats(p) : PlayerStatsManager.getStats(uuid);
            boolean won = winningTeam != null && entry.getValue() == winningTeam.color();
            if (won) stats.recordWin();
            else stats.recordLoss();

            // XP is the only half that still needs a live Player — it messages level-ups and grants
            // tokens. A leaver forfeits it; the result itself (W/L, rating) they do not.
            if (p == null) continue;
            // Playing XP needs presence at the end; win XP stays with the original team.
            boolean present = !gamePlayers.get(uuid).leftGame();
            awardXp(p, scaleXp((present ? ProgressionConfig.getGameReward() : 0)
                    + (won ? ProgressionConfig.getWinReward() : 0)));
        }

        sendPersonalStats();
        sendXpSummaries();

        if (gameMode.hasElo() && winningTeam != null) {
            updateElo(winningTeam);
        }

        for (UUID uuid : originalTeams.keySet()) {
            PlayerStatsManager.saveStats(uuid);
        }
    }

    /**
     * Rates the match off the ORIGINAL rosters, keyed on UUIDs. Two things depend on that: a player
     * who disconnected still takes the result (quitting used to skip them entirely — a free dodge),
     * and their rating still counts toward their side, so the teammate who stayed is rated against
     * the team that actually played rather than a rebased one-player average.
     */
    private void updateElo(Team winningTeam) {
        Map<TeamColor, List<UUID>> teamMembers = new HashMap<>();
        for (Map.Entry<UUID, TeamColor> entry : originalTeams.entrySet()) {
            // Bots were previously excluded for free by the offline skip; with UUID keys the guard
            // has to be explicit or NPC UUIDs would start earning rated stats files.
            if (isBotParticipant(entry.getKey())) continue;
            teamMembers.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        List<UUID> winners = teamMembers.getOrDefault(winningTeam.color(), List.of());
        if (winners.isEmpty()) return;
        List<Integer> winnerRatings = ratings(winners);

        for (Map.Entry<TeamColor, List<UUID>> entry : teamMembers.entrySet()) {
            if (entry.getKey() == winningTeam.color() || entry.getKey() == TeamColor.SPECTATOR) continue;
            List<UUID> losers = entry.getValue();
            // Every individual rating, not a pre-averaged pair: the calculator needs the spread
            // inside each team to size the match's K.
            var result = EloCalculator.calculate(gameMode, winnerRatings, ratings(losers));

            for (UUID uuid : winners) applyElo(uuid, result.winnerChange());
            for (UUID uuid : losers) applyElo(uuid, result.loserChange());
        }
    }

    private List<Integer> ratings(List<UUID> uuids) {
        List<Integer> result = new ArrayList<>(uuids.size());
        for (UUID uuid : uuids) result.add(PlayerStatsManager.getStats(uuid).getElo(gameMode));
        return result;
    }

    /** Applies one rating delta; reports it only if the player is still around to read it. */
    private void applyElo(UUID uuid, int change) {
        PlayerStats stats = PlayerStatsManager.getStats(uuid);
        int oldElo = stats.getElo(gameMode);
        stats.addElo(gameMode, change);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) sendEloChange(p, oldElo, stats.getElo(gameMode));
    }

    /** True for the NPC participants — bots must never touch the stats files or the ladder. */
    public boolean isBotParticipant(UUID uuid) {
        GamePlayer gamePlayer = gamePlayers.get(uuid);
        return gamePlayer != null && gamePlayer.isBot();
    }

    /** Post-game elo line: "Elo: 1000 → 1015 (+15)" — green gains, red losses. */
    private void sendEloChange(Player p, int oldElo, int newElo) {
        int delta = newElo - oldElo;
        NamedTextColor deltaColor = delta >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED;
        String sign = delta >= 0 ? "+" : "";
        p.sendMessage(Component.text("Elo: ", NamedTextColor.GRAY)
                .append(Component.text(oldElo + " → " + newElo, NamedTextColor.WHITE))
                .append(Component.text(" (" + sign + delta + ")", deltaColor)));
    }
}