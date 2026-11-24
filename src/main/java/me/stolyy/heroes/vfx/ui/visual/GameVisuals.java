package me.stolyy.heroes.vfx.ui.visual;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GamePlayer;
import me.stolyy.heroes.game.Team;
import me.stolyy.heroes.game.TeamColor;
import me.stolyy.heroes.game.rules.WinCondition;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.hero.minion.MinionHero;
import me.stolyy.heroes.vfx.effect.ParticleBuilder;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import me.stolyy.heroes.vfx.ui.GuiText;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GameVisuals {
    private final Game game;
    private final Scoreboard scoreboard;
    private final Objective sidebar;
    private final Objective tabHealth;
    private final Objective belowNameHealth;

    private BukkitTask countdownTask;

    public GameVisuals(Game game) {
        this.game = game;

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        scoreboard = manager.getNewScoreboard();

        this.sidebar = scoreboard.registerNewObjective(
                "GameInfo", Criteria.DUMMY, GuiText.component("scoreboard.game.title"));
        this.sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);

        Component healthSymbol = GuiText.component("scoreboard.game.health-symbol");
        this.tabHealth = scoreboard.registerNewObjective("tabHealth", Criteria.DUMMY, healthSymbol);
        this.tabHealth.setDisplaySlot(DisplaySlot.PLAYER_LIST);

        this.belowNameHealth = scoreboard.registerNewObjective("belowNameHealth", Criteria.DUMMY, healthSymbol);
        this.belowNameHealth.setDisplaySlot(DisplaySlot.BELOW_NAME);
    }

    public void updateHealthScore(Player player, int health) {
        tabHealth.getScore(player.getName()).setScore(health);
        belowNameHealth.getScore(player.getName()).setScore(health);
    }

    public void reset() {
        cancelTasks();
        for(String entry : scoreboard.getEntries()){
            sidebar.getScore(entry).resetScore();
        }
        applyScoreboardToAll();
    }

    public void showCountdown() {
        if (countdownTask != null) countdownTask.cancel();
        final int start = GameConfigManager.getCountdownTime();

        countdownTask = new BukkitRunnable() {
            int time = start;
            @Override
            public void run() {
                if (time == 5) {
                    for (Player p : game.onlinePlayers()) {
                        SoundBuilder.of("announcer.startgame").volume(1).pitch(1).play(p);
                    }
                }
                if (time > 0) {
                    Title title = Titles.of(
                            Component.text(String.valueOf(time), NamedTextColor.YELLOW),
                            Component.empty(),
                            Titles.SHORT);
                    for (Player p : game.onlinePlayers()) {
                        p.showTitle(title);
                    }
                    time--;
                } else {
                    Title title = Titles.of(
                            GuiText.component("scoreboard.game.countdown-go"),
                            GuiText.component("scoreboard.game.countdown-started"),
                            Titles.SHORT);
                    for (Player p : game.onlinePlayers()) {
                        p.showTitle(title);
                    }
                    cancel();
                }
            }
        }.runTaskTimer(Heroes.getInstance(), 0L, 20L);
    }

    public void draw() {
        reset();
        Component drawComponent = GameConfigManager.getMessage("end.draw.title");
        String scoreText = LegacyComponentSerializer.legacySection().serialize(drawComponent);
        sidebar.getScore(scoreText).setScore(1);
        applyScoreboardToAll();

        for (Player p : game.onlinePlayers()) {
            for (Component line : GameConfigManager.getMessageList("end.draw.chat", Map.of())) {
                p.sendMessage(line);
            }
        }
    }

    public void win(Team team, Map<String, String> placeholders) {
        reset();
        Component winComponent = GameConfigManager.getMessage("end.victory.title", placeholders);
        String scoreText = LegacyComponentSerializer.legacySection().serialize(winComponent);
        sidebar.getScore(scoreText).setScore(1);
        applyScoreboardToAll();

        for (Player p : game.onlinePlayers()) {
            // Eliminated players sit on SPECTATOR by now — judge them by their original team
            // so a dead player whose teammate clinched the win still sees VICTORY.
            var color = game.originalColor(p.getUniqueId());
            if (color == null) color = game.playerColor(p.getUniqueId());
            Team playerTeam = game.getTeams().get(color);
            if (playerTeam == team) {
                p.showTitle(Titles.of(
                        GuiText.component("scoreboard.game.victory-title"), Component.empty(), Titles.END));
                // Celebration burst at each winner. Particles rather than a real firework —
                // firework explosions deal vanilla damage and could kill low-HP players.
                ParticleBuilder.of(Particle.FIREWORK)
                        .count(60)
                        .offset(0.5, 1.0, 0.5)
                        .extra(0.1)
                        .spawn(p.getLocation().add(0, 1, 0));
                for (Component line : GameConfigManager.getMessageList("end.victory.chat", placeholders)) {
                    p.sendMessage(line);
                }
            } else {
                p.showTitle(Titles.of(
                        GuiText.component("scoreboard.game.defeat-title"), Component.empty(), Titles.END));
                p.sendMessage(GameConfigManager.getMessage("end.defeat.title", placeholders));
            }
        }
    }

    /**
     * The viewer's own end-of-game recap: hero and duration, kills/deaths, damage dealt and taken,
     * each with its breakdown on hover. {@code xpByHero} is null when the player earned no XP.
     */
    public void sendPersonalStats(Player p, GamePlayer gamePlayer, Map<String, Integer> xpByHero) {
        long seconds = game.durationMs() / 1000;
        List<Component> headerHover = new ArrayList<>();
        headerHover.add(GameConfigManager.getMessage("end.stats.header-team",
                Map.of("team", game.originalColor(gamePlayer.uuid()).displayName())));
        if (xpByHero != null) {
            for (Map.Entry<String, Integer> xp : xpByHero.entrySet()) {
                headerHover.add(GameConfigManager.getMessage("end.stats.header-xp", Map.of(
                        "hero", HeroConfig.formatName(xp.getKey()), "xp", String.valueOf(xp.getValue()))));
            }
        }
        p.sendMessage(GameConfigManager.getMessage("end.stats.header", Map.of(
                        "hero", HeroConfig.formatName(gamePlayer.currentHeroName()),
                        "duration", String.format("%d:%02d", seconds / 60, seconds % 60)))
                .hoverEvent(HoverEvent.showText(Component.join(JoinConfiguration.newlines(), headerHover))));

        p.sendMessage(GameConfigManager.getMessage("end.stats.kills-deaths", Map.of(
                "kills", String.valueOf(gamePlayer.kills()),
                "deaths", String.valueOf(gamePlayer.deaths()))));

        double dealt = gamePlayer.damageDealt();
        List<Component> dealtHover = new ArrayList<>();
        dealtHover.add(GameConfigManager.getMessage("end.stats.dealt-void",
                Map.of("void_damage", damageText(gamePlayer.voidDamageDealt()))));
        List<Map.Entry<String, Double>> topAbilities = gamePlayer.damageByAbility().entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(3)
                .toList();
        for (int i = 0; i < topAbilities.size(); i++) {
            Map.Entry<String, Double> ability = topAbilities.get(i);
            dealtHover.add(GameConfigManager.getMessage("end.stats.dealt-ability", Map.of(
                    "rank", String.valueOf(i + 1),
                    "ability", ability.getKey(),
                    "damage", damageText(ability.getValue()),
                    "share", String.valueOf(Math.round(100 * ability.getValue() / dealt)))));
        }
        p.sendMessage(GameConfigManager.getMessage("end.stats.damage-dealt", Map.of("damage", damageText(dealt)))
                .hoverEvent(HoverEvent.showText(Component.join(JoinConfiguration.newlines(), dealtHover))));

        p.sendMessage(GameConfigManager.getMessage("end.stats.damage-taken",
                        Map.of("damage", damageText(gamePlayer.damageTaken())))
                .hoverEvent(HoverEvent.showText(GameConfigManager.getMessage("end.stats.taken-void",
                        Map.of("void_damage", damageText(gamePlayer.voidDamageTaken()))))));
    }

    private String damageText(double damage) {
        return String.format("%.1f", damage);
    }

    /**
     * One tick of the respawn countdown title, driven by Game.gameTick's pausable counter:
     * seconds > 0 shows "Respawning in N", 0 shows the welcome-back title.
     */
    public void showRespawnTick(Player player, int seconds) {
        if (seconds > 0) {
            player.showTitle(Titles.of(
                    GuiText.component("scoreboard.game.respawn-countdown", "seconds", seconds),
                    Component.empty(), Titles.SHORT));
        } else {
            player.showTitle(Titles.of(
                    GuiText.component("scoreboard.game.respawn-done-title"),
                    GuiText.component("scoreboard.game.respawn-done-subtitle"),
                    Titles.SHORT));
        }
    }

    public void update() {
        syncScoreboardTeams();

        for (String entry : scoreboard.getEntries()) {
            sidebar.getScore(entry).resetScore();
        }

        String timeStr = formatTime(game.secondsLeft());
        Component timeComponent = GuiText.component("scoreboard.game.time-line", "time", timeStr);
        String timeScoreText = LegacyComponentSerializer.legacySection().serialize(timeComponent);
        sidebar.getScore(timeScoreText).setScore(game.settings().timer());

        // Roster count, not onlinePlayers — bot NPCs get sidebar lines too.
        int line = game.rosterSize() + 1;

        line = switch (game.gameModePreset()) {
            case TDM -> renderTeamKillLines(line);
            case EVOLUTION -> renderEvolutionLines(line);
            case DEFAULT, SERIES, NUZLOCKE, IRONMAN, BOSS -> renderLivesLines(line);
        };

        List<Player> spectators = game.onlinePlayers().stream()
                .filter(p -> game.playerColor(p.getUniqueId()) == TeamColor.SPECTATOR)
                .toList();

        if (!spectators.isEmpty()) {
            sidebar.getScore("§f").setScore(--line);
            Component spectatorsHeader = GuiText.component("scoreboard.game.spectators-header");
            sidebar.getScore(LegacyComponentSerializer.legacySection().serialize(spectatorsHeader)).setScore(--line);

            for (Player p : spectators) {
                Component spectatorComponent = GuiText.component("scoreboard.game.spectator-entry", "player", p.getName());
                String spectatorScoreText = LegacyComponentSerializer.legacySection().serialize(spectatorComponent);
                sidebar.getScore(spectatorScoreText).setScore(--line);
            }
        }

        applyScoreboardToAll();
    }

    // Scoreboard teams give nametags and tab-list names their team color (players AND bot
    // NPCs — both are name-keyed entries). Memberships are re-derived from game state on
    // every update() — cheaper than hooking every join/leave/team-change path individually,
    // since update() already runs on all of them.
    private void syncScoreboardTeams() {
        for (TeamColor color : TeamColor.values()) {
            org.bukkit.scoreboard.Team sbTeam = scoreboard.getTeam("hg_" + color.name());
            if (sbTeam == null) {
                sbTeam = scoreboard.registerNewTeam("hg_" + color.name());
                sbTeam.color(color == TeamColor.SPECTATOR ? NamedTextColor.GRAY : color.textColor());
            }

            Set<String> current = new HashSet<>();
            for (UUID uuid : game.members(color)) {
                String name = game.liveEntityName(uuid);
                if (name != null) current.add(name);
            }
            // Team-owned minions ride along: the teammate outline colour resolves from team
            // membership client-side, so the minion's NPC (name entry) AND its body display
            // (uuid entry — non-player entities are team-keyed by UUID string) must be members.
            for (MinionHero minion : game.minionsOf(color)) {
                if (minion.getController() != null && minion.getController().getEntity() != null) {
                    current.add(minion.getController().getEntity().getName());
                }
                if (minion.getBodyDisplay() != null) {
                    current.add(minion.getBodyDisplay().getDisplay().getUniqueId().toString());
                }
            }
            for (String entry : new HashSet<>(sbTeam.getEntries())) {
                if (!current.contains(entry)) sbTeam.removeEntry(entry);
            }
            for (String name : current) {
                if (!sbTeam.hasEntry(name)) sbTeam.addEntry(name);
            }
        }
    }

    // Default sidebar: per-participant lines with remaining lives, grouped by team. Iterates the
    // team ROSTERS (name-resolved like syncScoreboardTeams), not onlinePlayers — bot NPCs are
    // participants with lives and were invisible on the old player-only sidebar.
    private int renderLivesLines(int line) {
        for (TeamColor color : TeamColor.values()) {
            if (color == TeamColor.SPECTATOR) continue;

            // Go by life count
            List<UUID> members = game.members(color).stream()
                    .filter(uuid -> game.liveEntityName(uuid) != null)
                    .sorted(Comparator.<UUID>comparingInt(uuid -> game.getGamePlayer(uuid).currentLives()).reversed())
                    .toList();

            if (!members.isEmpty()) {
                sidebar.getScore("§" + color.ordinal()).setScore(--line);
                for (UUID uuid : members) {
                    String livesStr = " ♥" + game.getGamePlayer(uuid).currentLives();
                    String killsStr = " ⚔" + game.getGamePlayer(uuid).kills();
                    Component playerComponent = Component.text(game.liveEntityName(uuid))
                            .color(color.textColor())
                            .append(Component.text(livesStr, NamedTextColor.WHITE))
                            .append(Component.text(killsStr, NamedTextColor.GRAY));
                    String playerScoreText = LegacyComponentSerializer.legacySection().serialize(playerComponent);
                    sidebar.getScore(playerScoreText).setScore(--line);
                }
            }
        }
        return line;
    }

    private int renderTeamKillLines(int line) {
        int killLimit = game.modeConfig().killLimit();
        for (TeamColor color : TeamColor.values()) {
            if (color == TeamColor.SPECTATOR) continue;

            // Roster-based: a team of only bots still plays and still scores.
            if (game.members(color).isEmpty()) continue;

            int killsCount = WinCondition.teamKills(game, color);
            String kills = killLimit > 0
                    ? killsCount + "/" + killLimit
                    : String.valueOf(killsCount);
            Component teamLine = Component.text(color.displayName() + " " + kills).color(color.textColor());
            sidebar.getScore(LegacyComponentSerializer.legacySection().serialize(teamLine)).setScore(--line);
        }
        return line;
    }

    private int renderEvolutionLines(int line) {
        int killLimit = game.modeConfig().killLimit();
        for (TeamColor color : TeamColor.values()) {
            if (color == TeamColor.SPECTATOR) continue;

            // Roster-based like renderLivesLines — bots evolve through hero orders too.
            List<UUID> members = game.members(color).stream()
                    .filter(uuid -> game.liveEntityName(uuid) != null)
                    .toList();
            if (members.isEmpty()) continue;

            sidebar.getScore("§" + color.ordinal()).setScore(--line);
            for (UUID uuid : members) {
                GamePlayer gamePlayer = game.getGamePlayer(uuid);
                String name = game.liveEntityName(uuid);
                List<String> heroOrder = gamePlayer.heroOrder();
                String text;
                if (heroOrder == null || heroOrder.isEmpty()) {
                    text = name;
                } else {
                    int total = killLimit > 0 ? Math.min(killLimit, heroOrder.size()) : heroOrder.size();
                    text = name + " — " + gamePlayer.currentHeroName()
                            + " (" + (gamePlayer.heroIndex() + 1) + "/" + total + ")";
                }
                if (text.length() > 40) text = text.substring(0, 40); // scoreboard line length limit
                Component playerComponent = Component.text(text).color(color.textColor());
                sidebar.getScore(LegacyComponentSerializer.legacySection().serialize(playerComponent)).setScore(--line);
            }
        }
        return line;
    }

    public void cancelTasks() {
        if (countdownTask != null) countdownTask.cancel();
    }

    private void applyScoreboardToAll() {
        Component header = GuiText.component("scoreboard.game.tab-header");
        Component footer = Component.text(LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")), NamedTextColor.GRAY);
        for (Player p : game.onlinePlayers()) {
            p.setScoreboard(scoreboard);
            p.sendPlayerListHeaderAndFooter(header, footer);
        }
    }

    private String formatTime(int seconds){
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }
}