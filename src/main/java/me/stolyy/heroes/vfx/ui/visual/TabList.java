package me.stolyy.heroes.vfx.ui.visual;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.util.GameConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Tab-list names carry a mode glyph for everyone attached to a live game, so lobby players can see
 * who is mid-match. The list name is a single global value per player (Paper has no per-viewer
 * override), so "refresh" updates the player's own entry for all viewers.
 */
public final class TabList {
    private TabList() {}

    public static void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) refresh(player);
    }

    public static void refresh(Player player) {
        if (!Interactions.isRealPlayer(player)) return;

        Game game = GameManager.getPlayerGame(player);
        Component name = game != null && GameManager.getActiveGames().contains(game)
                ? Component.text(player.getName()).append(Component.space())
                        .append(Component.text(glyph(game.gameMode()), NamedTextColor.GRAY))
                : Component.text(player.getName());

        // Changing the name sends a list update to everyone; skip no-ops so a lobby-wide refresh
        // does not become N-squared packets.
        if (!name.equals(player.playerListName())) player.playerListName(name);
    }

    private static String glyph(GameMode mode) {
        String key = switch (mode) {
            case ONE_V_ONE -> "1v1";
            case TWO_V_TWO -> "2v2";
            case CUSTOM -> "custom";
        };
        return Configs.requireString(GameConfigManager.getMessagesConfig(), "tablist.glyph." + key);
    }
}
