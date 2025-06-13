package me.stolyy.heroes.game.minigame;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.utility.effects.Sounds;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

public class GameVisuals {
    private final Game game;
    private final Scoreboard scoreboard;
    private final Objective sidebar;
    private final Objective tabHealth;
    private final Objective belowNameHealth;

    private BukkitTask countdownTask;
    private BukkitTask timerTask;
    private BukkitTask healthTask;
    private int timeLeft;

    public GameVisuals(Game game) {
        this.game = game;

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        scoreboard = manager.getNewScoreboard();

        this.sidebar = scoreboard.registerNewObjective("GameInfo", Criteria.DUMMY, Component.text(NamedTextColor.GOLD + "SMASH HEROES 4"));
        this.sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);

        this.tabHealth = scoreboard.registerNewObjective("tabHealth", Criteria.HEALTH, Component.text("❤"));
        this.tabHealth.setDisplaySlot(DisplaySlot.PLAYER_LIST);

        this.belowNameHealth = scoreboard.registerNewObjective("belowNameHealth", Criteria.HEALTH, Component.text("❤"));
        this.belowNameHealth.setDisplaySlot(DisplaySlot.BELOW_NAME);
    }

    public void reset() {
        cancelTasks();
        for(String entry : scoreboard.getEntries()){
            sidebar.getScore(entry).resetScore();
            tabHealth.getScoreboard().resetScores(entry);
            belowNameHealth.getScoreboard().resetScores(entry);
        }
        applyScoreboardToAll();
    }

    public void showCountdown() {
        if (countdownTask != null) countdownTask.cancel();
        final int start = 10;
        final Title.Times times = Title.Times.times(
                Duration.ofMillis(250),  // Fade in: 5 ticks
                Duration.ofMillis(1000), // Stay: 20 ticks
                Duration.ofMillis(250)   // Fade out: 5 ticks
        );

        countdownTask = new BukkitRunnable() {
            int time = start;
            @Override
            public void run() {
                if(time == 5) {
                    for(Player p : game.allPlayers()) Sounds.playSoundToPlayer(p, "announcer.startgame", 1, 1);
                }
                if (time > 0) {
                    Component mainTitle = Component.text(String.valueOf(time), NamedTextColor.YELLOW);
                    Component subtitle = Component.empty();
                    Title title = Title.title(mainTitle, subtitle, times);

                    for (Player p : game.allPlayers()) {
                        p.showTitle(title);
                    }
                    time--;
                } else {
                    Component mainTitle = Component.text("GO!", NamedTextColor.GREEN);
                    Component subtitle = Component.text("Match Started");
                    Title title = Title.title(mainTitle, subtitle, times);

                    for (Player p : game.allPlayers()) {
                        p.showTitle(title);
                    }
                    cancel();
                }
            }
        }.runTaskTimer(Heroes.getInstance(), 0L, 20L);
    }

    public void startTimer() {
        if (timerTask != null) timerTask.cancel();
        timeLeft = game.settings().timer();
        sidebar.getScore(formatTime(timeLeft)).setScore(timeLeft + 1);
        timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (timeLeft <= 0) {
                    game.endGame();
                    cancel();
                    reset();
                } else {
                    update();
                    timeLeft--;
                }
            }
        }.runTaskTimer(Heroes.getInstance(), 0L, 20L);
    }

    public void draw() {
        reset();
        Component drawComponent = Component.text("DRAW", NamedTextColor.GRAY);
        String scoreText = LegacyComponentSerializer.legacySection().serialize(drawComponent);
        sidebar.getScore(scoreText).setScore(1);
        applyScoreboardToAll();
    }

    public void win(GameTeam team) {
        reset();
        Component winComponent = Component.text(team.color().name() + " TEAM WINS!", team.color().chatColor());
        String scoreText = LegacyComponentSerializer.legacySection().serialize(winComponent);
        sidebar.getScore(scoreText).setScore(1);
        applyScoreboardToAll();
    }

    public void respawning(Player player) {
        final Title.Times times = Title.Times.times(
                Duration.ofMillis(250),  // Fade in: 5 ticks
                Duration.ofMillis(1000), // Stay: 20 ticks
                Duration.ofMillis(250)   // Fade out: 5 ticks
        );
        new BukkitRunnable() {
            int timer = Game.RESPAWN_TIME;
            @Override
            public void run() {
                if (timer > 0) {
                    Component mainTitle = Component.text("Respawning in ", NamedTextColor.RED)
                            .append(Component.text(timer, NamedTextColor.WHITE));
                    Component subtitle = Component.empty();
                    Title title = Title.title(mainTitle, subtitle, times);
                    player.showTitle(title);
                    timer--;
                } else {
                    Component mainTitle = Component.text("Welcome Back!", NamedTextColor.YELLOW)
                            .append(Component.text(timer, NamedTextColor.GOLD));
                    Component subtitle = Component.text("Go!", NamedTextColor.GREEN);
                    Title title = Title.title(mainTitle, subtitle, times);
                    player.showTitle(title);
                    cancel();
                }
            }
        }.runTaskTimer(Heroes.getInstance(), 0L, 20L);
    }

    public void update() {
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }

        String timeStr = formatTime(timeLeft);
        Component timeComponent = Component.text("Time: " + timeStr, NamedTextColor.YELLOW);
        String timeScoreText = LegacyComponentSerializer.legacySection().serialize(timeComponent);
        sidebar.getScore(timeScoreText).setScore(game.settings().timer());

        int line = game.allPlayers().size() + 1;

        for (GameEnums.TeamColor color : GameEnums.TeamColor.values()) {
            if (color == GameEnums.TeamColor.SPECTATOR) continue;

            // Go by life count
            List<Player> teamPlayers = game.allPlayers().stream()
                    .filter(p -> game.playerColor(p) == color)
                    .sorted(Comparator.comparingInt(p -> game.lives((Player) p)).reversed())
                    .toList();

            if (!teamPlayers.isEmpty()) {
                sidebar.getScore("§" + color.ordinal()).setScore(--line);
                for (Player p : teamPlayers) {
                    String livesStr = " [" + game.lives(p) + "♥]";
                    Component playerComponent = Component.text(p.getName())
                            .color(color.chatColor())
                            .append(Component.text(livesStr, NamedTextColor.WHITE));
                    String playerScoreText = LegacyComponentSerializer.legacySection().serialize(playerComponent);
                    sidebar.getScore(playerScoreText).setScore(--line);
                }
            }
        }

        List<Player> spectators = game.allPlayers().stream()
                .filter(p -> game.playerColor(p) == GameEnums.TeamColor.SPECTATOR)
                .toList();

        if (!spectators.isEmpty()) {
            sidebar.getScore("§f").setScore(--line); // Spacer
            Component spectatorsHeader = Component.text("Spectators", NamedTextColor.GRAY);
            sidebar.getScore(LegacyComponentSerializer.legacySection().serialize(spectatorsHeader)).setScore(--line);

            for (Player p : spectators) {
                Component spectatorComponent = Component.text("- " + p.getName(), NamedTextColor.DARK_GRAY);
                String spectatorScoreText = LegacyComponentSerializer.legacySection().serialize(spectatorComponent);
                sidebar.getScore(spectatorScoreText).setScore(--line);
            }
        }

        applyScoreboardToAll();
    }

    public void cancelTasks() {
        if (countdownTask != null) countdownTask.cancel();
        if (timerTask != null) timerTask.cancel();
        if (healthTask != null) healthTask.cancel();
    }

    private void applyScoreboardToAll() {
        for (Player p : game.allPlayers()) {
            p.setScoreboard(scoreboard);
            p.sendPlayerListHeaderAndFooter(
                    Component.text("SMASH HEROES 4", NamedTextColor.GOLD),
                    Component.text(LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")), NamedTextColor.GRAY));
        }
    }

    private String formatTime(int seconds){
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }
}