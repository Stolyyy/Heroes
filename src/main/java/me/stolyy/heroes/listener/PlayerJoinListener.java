package me.stolyy.heroes.listener;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.PlayerVisibility;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.vfx.ui.visual.TabList;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;
import java.util.Map;

public class PlayerJoinListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Read before the teleport: hasPlayedBefore flips once the server writes playerdata.
        boolean firstJoin = !player.hasPlayedBefore();
        Heroes.teleportToLobby(player);
        PlayerVisibility.refreshVisibility(player);
        TabList.refresh(player);
        sendOnboarding(player, firstJoin);
    }

    // Chat primer from messages.yml: onboarding.first-join for a fresh account, the shorter
    // onboarding.every-join after that. Delayed a second so it lands under the vanilla join line
    // and the resource-pack prompt instead of scrolling off behind them.
    private static void sendOnboarding(Player player, boolean firstJoin) {
        String path = firstJoin ? "onboarding.first-join" : "onboarding.every-join";
        List<Component> lines = GameConfigManager.getMessageList(path, Map.of("player", player.getName()));
        if (lines.isEmpty()) return;
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> {
            if (!player.isOnline()) return;
            for (Component line : lines) player.sendMessage(line);
        }, 20L);
    }
}
