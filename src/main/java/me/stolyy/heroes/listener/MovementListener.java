package me.stolyy.heroes.listener;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

public class MovementListener implements Listener {

    // Double Jump
    @EventHandler
    public void onDoubleJump(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.ADVENTURE) {
            Hero hero = HeroManager.getHero(p);
            if (hero != null) {
                e.setCancelled(true);
                hero.getMobility().performJump(hero);
            } else {
                // ADVENTURE with no hero is a lobby player: the only way mayfly is still set is a
                // stale grant (practice exit, game teardown mid-air), so it must never become
                // real flight. Map builders are in CREATIVE and spectators in SPECTATOR, so
                // neither is caught here; a live practice or game player always has a hero,
                // because both create the hero before granting flight.
                e.setCancelled(true);
                p.setAllowFlight(false);
            }
        }
    }

    // Double Jump reset & Fall prevention
    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (p.getGameMode() == GameMode.ADVENTURE) {
            Hero hero = HeroManager.getHero(p);
            if (hero != null) {
                if (p.isOnGround()) {
                    hero.getMobility().onLand(hero);
                    hero.triggerLand();
                } else if (!hero.getMobility().canJump(hero)) {
                    p.setAllowFlight(false);
                }
            }
        }
    }
}
