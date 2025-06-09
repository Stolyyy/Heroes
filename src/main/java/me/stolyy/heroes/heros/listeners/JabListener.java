package me.stolyy.heroes.heros.listeners;

import me.stolyy.heroes.heros.HeroCooldown;
import me.stolyy.heroes.heros.HeroManager;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class JabListener implements Listener {
    //Initiate Jab and Double Jump related variables on joinGame
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
            HeroCooldown hc = HeroManager.getHero(player);
            player.getAttribute(Attribute.PLAYER_ENTITY_INTERACTION_RANGE).setBaseValue(hc.jabReach());
            player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(hc.jabDamage());
            player.setMaximumNoDamageTicks(1);
        }
    }

    //Handle jab cooldowns
    @EventHandler
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p && event.getEntity() instanceof Player t) {
            event.setCancelled(true);
            HeroCooldown hc = HeroManager.getHero(p);
            if(hc.canJab()) hc.jab(t);
        }
    }
}
