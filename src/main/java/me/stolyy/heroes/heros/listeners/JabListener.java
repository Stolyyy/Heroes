package me.stolyy.heroes.heros.listeners;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.heros.HeroCooldown;
import me.stolyy.heroes.heros.HeroManager;
import me.stolyy.heroes.utility.Interactions;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class JabListener implements Listener {
    private static final Set<Player> jabCooldowns = new HashSet<>();

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
            HeroCooldown hc = HeroManager.getHero(player);
            Objects.requireNonNull(player.getAttribute(Attribute.PLAYER_ENTITY_INTERACTION_RANGE)).setBaseValue(hc.jabReach());
            Objects.requireNonNull(player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE)).setBaseValue(hc.jabDamage());
            player.setMaximumNoDamageTicks(1);
        }
    }

    //Handle jab cooldowns
    @EventHandler
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p && event.getEntity() instanceof Player t) {
            event.setCancelled(true);
            if(!Interactions.canInteract(p, t)) return;
            if(jabCooldowns.add(p)) {
                HeroCooldown hc = HeroManager.getHero(p);
                hc.jab(t);
                Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> jabCooldowns.remove(p), (long) (hc.jabCooldown() * 20));
            }
        }
    }
}
