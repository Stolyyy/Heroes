package me.stolyy.heroes.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.progression.PlayerStatsManager;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Chat as {@code name: message} with the name coloured per viewer: teammates and opponents of the
 * same game see the sender's team colour; everyone else sees white for an in-game sender and green
 * for a lobby sender. A season champion's name is gold wherever the team colour does not apply.
 */
public class ChatFormatListener implements Listener {

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        event.renderer((source, displayName, message, viewer) ->
                Component.text(source.getName(), nameColor(source, viewer))
                        .append(Component.text(": ", NamedTextColor.GRAY))
                        .append(message));
    }

    private static NamedTextColor nameColor(Player sender, Audience viewer) {
        Game game = GameManager.getPlayerGame(sender);
        if (game != null && viewer instanceof Player viewerPlayer
                && GameManager.getPlayerGame(viewerPlayer) == game) {
            return game.playerColor(sender.getUniqueId()).textColor();
        }
        if (PlayerStatsManager.getStats(sender.getUniqueId()).isSeasonChampion()) return NamedTextColor.GOLD;
        return game != null ? NamedTextColor.WHITE : NamedTextColor.GREEN;
    }
}
