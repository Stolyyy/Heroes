package me.stolyy.heroes.game;

import me.stolyy.heroes.Heroes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class SpectatorListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onSpectateTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.SPECTATE) return;

        Player player = event.getPlayer();
        Game game = GameManager.getPlayerGame(player);
        Location lobby = Heroes.getLobbyLocation();
        World allowed = game != null ? game.settings().map().world()
                : (lobby != null ? lobby.getWorld() : null);

        Location to = event.getTo();
        boolean inside = to.getWorld().equals(allowed)
                && (game == null || game.settings().boundaries().contains(to.toVector()));
        if (inside) return;

        // Cancelling gives the client no feedback of its own — the menu just appears to do nothing.
        event.setCancelled(true);
        player.sendActionBar(Component.text("You can only spectate your own match.", NamedTextColor.RED));
    }

    /**
     * A viewer's hide list is keyed by UUID and outlives the target's connection, so someone who
     * reconnects stays invisible to whoever hid them last session. Re-evaluate both directions.
     * MONITOR so it runs after {@code PlayerJoinListener} has sent them to the lobby.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        PlayerVisibility.refreshVisibility(event.getPlayer());
    }
}
