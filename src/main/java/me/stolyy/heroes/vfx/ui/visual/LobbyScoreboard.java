package me.stolyy.heroes.vfx.ui.visual;

import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.progression.ModeStats;
import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.vfx.ui.GuiText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

/** Builds and refreshes the per-player lobby sidebar and below-name Smash Level scoreboard. */
public class LobbyScoreboard {

    private static final String BLANK_1 = "\u00a70\u00a7r";
    private static final String BLANK_2 = "\u00a71\u00a7r";
    private static final String BLANK_3 = "\u00a72\u00a7r";

    public static void apply(Player player) {
        // Citizens NPCs are Player-typed; skip them so lobby boards never create dummy stats files.
        if (!Interactions.isRealPlayer(player)) return;

        PlayerStats stats = PlayerStatsManager.getStats(player);

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard scoreboard = manager.getNewScoreboard();

        Objective sidebar = scoreboard.registerNewObjective(
                "lobby", Criteria.DUMMY, GuiText.component("scoreboard.lobby.title"));
        sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);

        Objective belowName = scoreboard.registerNewObjective(
                "lobbyLevel", Criteria.DUMMY, GuiText.component("scoreboard.lobby.below-name-symbol"));
        belowName.setDisplaySlot(DisplaySlot.BELOW_NAME);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!Interactions.isRealPlayer(other)) continue;
            belowName.getScore(other.getName())
                    .setScore(PlayerStatsManager.getStats(other).smashLevel());
        }

        HeroNpcLevelTags.refresh(player);

        int score = 13;

        sidebar.getScore(line("scoreboard.lobby.smash-level", stats.smashLevel())).setScore(score--);
        sidebar.getScore(BLANK_1).setScore(score--);

        ModeStats oneVsOne = stats.getModeStats(GameMode.ONE_V_ONE);
        sidebar.getScore(line("scoreboard.lobby.1v1-elo", stats.getElo(GameMode.ONE_V_ONE))).setScore(score--);
        sidebar.getScore(line("scoreboard.lobby.1v1-wins", oneVsOne.totalWins())).setScore(score--);
        sidebar.getScore(line("scoreboard.lobby.1v1-kills", oneVsOne.totalKills())).setScore(score--);

        sidebar.getScore(BLANK_2).setScore(score--);

        ModeStats twoVsTwo = stats.getModeStats(GameMode.TWO_V_TWO);
        sidebar.getScore(line("scoreboard.lobby.2v2-elo", stats.getElo(GameMode.TWO_V_TWO))).setScore(score--);
        sidebar.getScore(line("scoreboard.lobby.2v2-wins", twoVsTwo.totalWins())).setScore(score--);
        sidebar.getScore(line("scoreboard.lobby.2v2-kills", twoVsTwo.totalKills())).setScore(score--);

        sidebar.getScore(BLANK_3).setScore(score--);

        String heroName = stats.selectedHero();
        String heroDisplay = heroName != null
                ? HeroConfig.formatName(heroName)
                : GuiText.raw("scoreboard.lobby.hero-none");
        sidebar.getScore(line("scoreboard.lobby.hero", heroDisplay)).setScore(score--);
        sidebar.getScore(line("scoreboard.lobby.tokens", stats.getTokenBalance())).setScore(score);

        player.setScoreboard(scoreboard);
        TabList.refreshAll();
    }

    /** Re-applies the lobby board only to viewers currently displaying it, leaving live matches alone. */
    public static void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getScoreboard().getObjective("lobby") != null) {
                apply(p);
            }
        }
    }

    private static String line(String key, Object value) {
        return toLegacy(GuiText.component(key, "value", value));
    }

    private static String toLegacy(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }
}
