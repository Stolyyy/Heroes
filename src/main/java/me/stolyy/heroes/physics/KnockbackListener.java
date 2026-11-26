package me.stolyy.heroes.physics;

import io.papermc.paper.event.entity.EntityKnockbackEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Cancels VANILLA knockback while Interactions is mid-damage. applyDamage's cosmetic and
 * lethal {@code victim.damage(..., attacker)} calls fire vanilla hurt-knockback AFTER
 * applyKnockback has already setVelocity'd the intended vector — vanilla overwrote it,
 * giving grounded victims a fixed ~0.4-up pop (pulls cancelled to nothing) while airborne
 * victims kept the real knockback. With this cancel, ability knockback lands exactly as
 * computed on grounded and airborne targets alike.
 */
public class KnockbackListener implements Listener {

    @EventHandler
    public void onKnockback(EntityKnockbackEvent event) {
        if (Interactions.isProcessingDamage(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
