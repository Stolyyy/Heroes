package me.stolyy.heroes.hero;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AbilityListener implements Listener {

    // Hold-to-fire primaries. The vanilla client re-sends a use-item packet every ~4 ticks
    // (~200ms) while the use button is held — never faster. A fresh click (no packet within
    // HELD_WINDOW_MS before it) fires the primary exactly once. A second consecutive packet
    // inside the window means the button is being HELD, which engages the 1-tick sampler so
    // held fire lands on an ability's exact gate cadence instead of the packet grid. The sampler
    // disengages as soon as packets stop arriving inside the window.
    private static final long HELD_WINDOW_MS = 210;
    private static final Map<UUID, Long> lastUseMs = new HashMap<>();
    private static final Set<UUID> heldFiring = new HashSet<>();
    private static final Map<UUID, Integer> lastPrimaryFireTick = new HashMap<>();
    private static BukkitTask heldFireTask;

    private static void ensureHeldFireTask() {
        if (heldFireTask != null) return;
        heldFireTask = Bukkit.getScheduler().runTaskTimer(Heroes.getInstance(), () -> {
            if (heldFiring.isEmpty()) {
                heldFireTask.cancel();
                heldFireTask = null;
                return;
            }
            long now = System.currentTimeMillis();
            Iterator<UUID> it = heldFiring.iterator();
            while (it.hasNext()) {
                UUID uuid = it.next();
                Long last = lastUseMs.get(uuid);
                if (last == null || now - last > HELD_WINDOW_MS) {
                    it.remove(); // button released
                    lastPrimaryFireTick.remove(uuid);
                    continue;
                }
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) tryFirePrimary(p);
            }
            if (heldFiring.isEmpty() && heldFireTask != null) {
                heldFireTask.cancel();
                heldFireTask = null;
            }
        }, 1L, 1L);
    }

    public static void clear() {
        if (heldFireTask != null) {
            heldFireTask.cancel();
            heldFireTask = null;
        }
        lastUseMs.clear();
        heldFiring.clear();
        lastPrimaryFireTick.clear();
    }

    /**
     * True while the use button is considered down: a use packet arrived within the last
     * {@link #HELD_WINDOW_MS}. True from the FIRST click (unlike the held-firing state,
     * which needs two consecutive packets) — hold-sustained abilities (ChainRev) key off this.
     */
    public static boolean isUseHeld(UUID uuid) {
        Long last = lastUseMs.get(uuid);
        return last != null && System.currentTimeMillis() - last <= HELD_WINDOW_MS;
    }

    private static void tryFirePrimary(Player p) {
        if (!GameManager.canUseAbility(p)) return;
        if (p.getInventory().getHeldItemSlot() != 0) return;
        int now = Bukkit.getCurrentTick();
        Integer last = lastPrimaryFireTick.get(p.getUniqueId());
        if (last != null && last == now) return; // packet handler and sampler share a tick
        lastPrimaryFireTick.put(p.getUniqueId(), now);
        Hero hero = HeroManager.getHero(p);
        if (hero != null) hero.usePrimary();
    }

    private static void onUsePacket(Player p) {
        if (p.getInventory().getHeldItemSlot() != 0) return;
        ItemStack held = p.getInventory().getItemInMainHand();
        if (held.getType() == Material.BOW) return; // Green Hood draws; GreenHoodListener owns the shot
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        Long prev = lastUseMs.put(uuid, now);
        if (prev != null && now - prev <= HELD_WINDOW_MS) {
            heldFiring.add(uuid); // consecutive packets: the sampler owns firing from here
            ensureHeldFireTask();
        } else {
            tryFirePrimary(p); // fresh click: exactly one shot
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastUseMs.remove(uuid);
        heldFiring.remove(uuid);
        lastPrimaryFireTick.remove(uuid);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (!GameManager.canUseAbility(p)) return;

        // In-game players never operate interactable blocks (furnaces, trapdoors, doors,
        // buttons...). DENY suppresses only the block's own interaction; the item-use path
        // (a bow draw) still proceeds.
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        onUsePacket(p);
    }

    /**
     * Holding fire with the crosshair over an entity in reach produces interact-entity
     * packets instead of use-item packets — keep the held-use window alive through them.
     */
    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player p = event.getPlayer();
        if (!GameManager.canUseAbility(p)) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        onUsePacket(p);
    }

    @EventHandler
    public void onPlayerUseAbility(PlayerItemHeldEvent event) {
        Player p = event.getPlayer();
        if (!GameManager.canUseAbility(p)) return;

        Hero hero = HeroManager.getHero(p);
        if (hero != null) {
            int newSlot = event.getNewSlot();
            if (newSlot == 1 || newSlot == 2) {
                event.setCancelled(true);
                if (newSlot == 1) {
                    hero.useSecondary();
                } else {
                    Game game = GameManager.getPlayerGame(p);
                    if (game != null && game.ultimateEnabled(p.getUniqueId())) {
                        hero.useSmash();
                    } else {
                        p.sendMessage(Component.text("Your team has ultimate abilities disabled!", NamedTextColor.RED));
                    }
                }

                // Force back to slot 0 after short delay to allow visual click
                Heroes.getScheduler().runTaskLater(Heroes.getInstance(), () -> p.getInventory().setHeldItemSlot(0), 1L);
            }
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player p = event.getPlayer();
        if (!GameManager.canUseAbility(p) || p.isSneaking()) return; // Trigger on start-sneak only

        Hero hero = HeroManager.getHero(p);
        if (hero != null) {
            hero.onSneak();
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player p = event.getPlayer();
        if (!GameManager.canUseAbility(p)) {
            // Cancel drop for anyone in a game even if not "eligible" (e.g. pre-game)
            if (GameManager.isPlayerInGame(p)) {
                event.setCancelled(true);
                // Resync: cancelling a drop of a stacked item (ammo display) leaves the client
                // predicting a partial drop that "re-picks-up" into a new slot.
                p.updateInventory();
            }
            return;
        }

        event.setCancelled(true);
        p.updateInventory();
        Hero hero = HeroManager.getHero(p);
        if (hero != null) {
            hero.onDrop();
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        if (!GameManager.canUseAbility(p)) {
            if (GameManager.isPlayerInGame(p)) event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        Hero hero = HeroManager.getHero(p);
        if (hero != null) {
            hero.onSwap();
        }
    }
}
