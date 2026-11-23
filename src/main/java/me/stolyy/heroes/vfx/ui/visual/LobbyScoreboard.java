package me.stolyy.heroes.vfx.ui.visual;

import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.progression.ModeStats;
import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.physics.Interactions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.Map;

public class LobbyScoreboard {

    // Invisible unique strings for blank lines (using color code sequences that render empty)
    private static final String BLANK_1 = "\u00a70\u00a7r";
    private static final String BLANK_2 = "\u00a71\u00a7r";
    private static final String BLANK_3 = "\u00a72\u00a7r";

    public static void apply(Player player) {
        // Citizens NPCs (game bots, minions, practice dummies) are Player-typed and transit the
        // server player list, so they reach the lobby path like any joining player. Handing one a
        // board materialises a stats file seeded with starting elo AND — via HeroNpcLevelTags
        // below — a hero-levels block for every hero, which is exactly the shape of the NPC files
        // sitting in player-stats today. Real players only, here and in the below-name loop.
        if (!Interactions.isRealPlayer(player)) return;

        PlayerStats stats = PlayerStatsManager.getStats(player);

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard scoreboard = manager.getNewScoreboard();

        Objective sidebar = scoreboard.registerNewObjective(
                "lobby", Criteria.DUMMY,
                Component.text("SMASH HEROES", NamedTextColor.GOLD, TextDecoration.BOLD)
        );
        sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Smash level under every nametag ("<level> ✫"). Per-player scoreboards only show
        // scores they contain, so this board carries an entry for EVERY online player.
        Objective belowName = scoreboard.registerNewObjective(
                "lobbyLevel", Criteria.DUMMY, Component.text("✫", NamedTextColor.GOLD));
        belowName.setDisplaySlot(DisplaySlot.BELOW_NAME);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!Interactions.isRealPlayer(other)) continue;
            belowName.getScore(other.getName())
                    .setScore(PlayerStatsManager.getStats(other).smashLevel());
        }

        // Hero display NPCs carry the VIEWER's own level for that hero ("✫ 3/20") — per-viewer
        // TextDisplays, not below-name scores: hex-coloured NPC names bypass vanilla nametag
        // rendering, so a scoreboard score never appears under them. See HeroNpcLevelTags.
        HeroNpcLevelTags.refresh(player);

        // B7/B23.6 layout: Smash Level on top, then per-mode PUBLIC stats (1v1 / 2v2 —
        // custom games are tracked in YAML but deliberately absent here).
        int score = 13;

        sidebar.getScore(toLegacy(Component.text("Smash Level: ", NamedTextColor.GOLD)
                .append(Component.text(stats.smashLevel(), NamedTextColor.WHITE))))
                .setScore(score--);

        sidebar.getScore(BLANK_1).setScore(score--);

        ModeStats oneVsOne = stats.getModeStats(GameMode.ONE_V_ONE);
        // Bold ELO line = the section header for this gamemode's block.
        sidebar.getScore(toLegacy(Component.text("1v1 ELO: ", NamedTextColor.YELLOW, TextDecoration.BOLD)
                .append(Component.text(stats.getElo(GameMode.ONE_V_ONE), NamedTextColor.WHITE))))
                .setScore(score--);
        sidebar.getScore(toLegacy(Component.text("1v1 Wins: ", NamedTextColor.GREEN)
                .append(Component.text(oneVsOne.totalWins(), NamedTextColor.WHITE))))
                .setScore(score--);
        sidebar.getScore(toLegacy(Component.text("1v1 Kills: ", NamedTextColor.GREEN)
                .append(Component.text(oneVsOne.totalKills(), NamedTextColor.WHITE))))
                .setScore(score--);

        sidebar.getScore(BLANK_2).setScore(score--);

        ModeStats twoVsTwo = stats.getModeStats(GameMode.TWO_V_TWO);
        sidebar.getScore(toLegacy(Component.text("2v2 ELO: ", NamedTextColor.YELLOW, TextDecoration.BOLD)
                .append(Component.text(stats.getElo(GameMode.TWO_V_TWO), NamedTextColor.WHITE))))
                .setScore(score--);
        sidebar.getScore(toLegacy(Component.text("2v2 Wins: ", NamedTextColor.GREEN)
                .append(Component.text(twoVsTwo.totalWins(), NamedTextColor.WHITE))))
                .setScore(score--);
        sidebar.getScore(toLegacy(Component.text("2v2 Kills: ", NamedTextColor.GREEN)
                .append(Component.text(twoVsTwo.totalKills(), NamedTextColor.WHITE))))
                .setScore(score--);

        sidebar.getScore(BLANK_3).setScore(score--);

        String heroName = stats.selectedHero();
        String heroDisplay = heroName != null ? HeroConfig.formatName(heroName) : "None";
        sidebar.getScore(toLegacy(Component.text("Hero: ", NamedTextColor.AQUA)
                .append(Component.text(heroDisplay, NamedTextColor.WHITE))))
                .setScore(score--);

        sidebar.getScore(toLegacy(Component.text("Tokens: ", NamedTextColor.AQUA)
                .append(Component.text(stats.getTokenBalance(), NamedTextColor.WHITE))))
                .setScore(score);

        player.setScoreboard(scoreboard);
        TabList.refreshAll();
    }

    /**
     * Re-applies the lobby board to everyone currently LOOKING at a lobby board (identified
     * by the "lobby" objective), so below-name levels stay current for other viewers when
     * someone enters the lobby or levels up. In-game players keep their game scoreboard.
     */
    public static void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getScoreboard().getObjective("lobby") != null) {
                apply(p);
            }
        }
    }

    private static String toLegacy(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }
}
