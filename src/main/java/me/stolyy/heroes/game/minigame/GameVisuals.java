package me.stolyy.heroes.game.minigame;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.utility.effects.Sounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

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
        countdownTask = new BukkitRunnable() {
            int time = start;
            @Override
            public void run() {
                if(time == 5) {
                    for(Player p : game.allPlayers()) Sounds.playSoundToPlayer(p, "announcer.startgame", 1, 1);
                }
                if (time > 0) {
                    for (Player p : game.allPlayers()) {
                        p.sendTitle(NamedTextColor.YELLOW + String.valueOf(time), "", 5, 20, 5);
                    }
                    time--;
                } else {
                    for (Player p : game.allPlayers()) {
                        p.sendTitle(NamedTextColor.GREEN + "GO!", "Match Started", 5, 40, 5);
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
        sidebar.getScore(NamedTextColor.GRAY + "DRAW").setScore(1);
        applyScoreboardToAll();
    }

    public void win(GameTeam team) {
        reset();
        sidebar.getScore(team.color().chatColor() + team.color().name() + " WINS!").setScore(1);
        applyScoreboardToAll();
    }

    public void respawning(Player player) {
        new BukkitRunnable() {
            int timer = Game.RESPAWN_TIME;
            @Override
            public void run() {
                if (timer > 0) {
                    player.sendTitle(NamedTextColor.RED + "Respawning in " + timer, "", 5, 20, 5);
                    timer--;
                } else {
                    player.sendTitle(NamedTextColor.GREEN + "Welcome Back!","Go!", 5, 20, 5);
                    cancel();
                }
            }
        }.runTaskTimer(Heroes.getInstance(), 0L, 20L);
    }

    public void update() {
        for (String entry : scoreboard.getEntries()) {
            sidebar.getScore(entry).resetScore();
        }
        int line = 0;
        String timeStr = formatTime(timeLeft);
        sidebar.getScore(NamedTextColor.YELLOW + "Time: " + timeStr).setScore(game.settings().timer());
        for (GameEnums.TeamColor color : GameEnums.TeamColor.values()) {
            if (color == GameEnums.TeamColor.SPECTATOR) continue;
            List<Player> list = game.allPlayers().stream()
                    .filter(p -> game.playerTeam(p) == color)
                    .sorted(Comparator.comparingInt(p -> game.lives((Player) p)).reversed())
                    .toList();
            for (Player p : list) {
                String label = p.getName() + " [" + game.lives(p) + "]";
                sidebar.getScore(color.chatColor() + " " + label).setScore(--line);
            }
        }
        // Spectators at bottom
        for (Player p : game.allPlayers()) {
            if (game.playerTeam(p) == GameEnums.TeamColor.SPECTATOR) {
                sidebar.getScore(NamedTextColor.GRAY + p.getName()).setScore(--line);
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