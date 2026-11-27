package me.stolyy.heroes.hero.mechanics;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

public class MovementListener implements Listener {

    @EventHandler
    public void onDoubleJump(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.ADVENTURE) {
            Hero hero = HeroManager.getHero(p);
            if (hero != null) {
                e.setCancelled(true);
                hero.getMobility().performJump(hero);
            } else {
                e.setCancelled(true);
                if (LobbyMobility.isInLobby(p)) {
                    if (!LobbyMobility.performJump(p)) {
                        p.setAllowFlight(false);
                    }
                } else {
                    p.setFlying(false);
                    p.setAllowFlight(false);
                }
            }
        }
    }

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
            } else if (LobbyMobility.isInLobby(p) && p.isOnGround()) {
                LobbyMobility.onLand(p);
            }
        }
    }
}
