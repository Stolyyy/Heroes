package me.stolyy.heroes.heros.listeners;

import me.stolyy.heroes.heros.HeroCooldown;
import me.stolyy.heroes.heros.HeroManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

public class DoubleJumpListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
            player.setAllowFlight(true);
    }

    //Double Jump
    @EventHandler
    public void onDoubleJump(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();

        if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
            e.setCancelled(true); // Prevent flight mode
            HeroCooldown hc = HeroManager.getHero(p);
            if(hc.canDoubleJump())
                hc.doubleJump();
        }
    }

    //Double Jump reset
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
            HeroCooldown hc = HeroManager.getHero(p);
            if (p.isOnGround()) {
                hc.resetDoubleJumps();
                p.setAllowFlight(true);
            } else if (!hc.canDoubleJump()) {
                // Prevent entering flight mode if max double jumps are used
                p.setAllowFlight(false);
            }
        }
    }

    @EventHandler
    public void cancelFallDamage(EntityDamageEvent event) {
        if (event.getCause().equals(EntityDamageEvent.DamageCause.FALL)) {
            event.setCancelled(true);
        }
    }
}
