package me.stolyy.heroes.vfx.ui.visual;

import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.npc.NpcManager;
import me.stolyy.heroes.npc.NpcServiceProvider;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.vfx.cosmetic.CosmeticManager;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Locale;
import java.util.Map;

/**
 * Sends per-viewer equipment packets so each lobby player sees every hero display NPC wearing
 * whichever skin that viewer currently has selected.
 */
public final class HeroNpcSkins {
    private static boolean initialized;

    private HeroNpcSkins() {}

    public static void initialize() {
        if (initialized) return;
        Bukkit.getPluginManager().registerEvents(new TrackListener(), Heroes.getInstance());
        initialized = true;
    }

    /** Re-sends viewer-selected skin equipment for every lobby hero NPC in {@code viewer}'s world. */
    public static void refresh(Player viewer) {
        if (!NpcServiceProvider.isAvailable() || !Interactions.isRealPlayer(viewer) || !viewer.isOnline()) return;
        for (Map.Entry<String, LivingEntity> entry : NpcManager.heroNpcEntities().entrySet()) {
            LivingEntity npc = entry.getValue();
            if (npc.isValid() && npc.getWorld().equals(viewer.getWorld())) {
                apply(viewer, entry.getKey(), npc);
            }
        }
    }

    /** Re-sends viewer-selected skin equipment for one hero display NPC. */
    public static void refresh(Player viewer, String heroName) {
        if (!NpcServiceProvider.isAvailable() || !Interactions.isRealPlayer(viewer) || !viewer.isOnline()) return;
        String key = heroName.toLowerCase(Locale.ROOT);
        LivingEntity npc = NpcManager.heroNpcEntities().get(key);
        if (npc != null && npc.isValid() && npc.getWorld().equals(viewer.getWorld())) {
            apply(viewer, key, npc);
        }
    }

    public static void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    private static void apply(Player viewer, String heroName, LivingEntity npc) {
        String skinId = PlayerStatsManager.getStats(viewer).getSkinForHero(heroName);
        HeroSkin skin = CosmeticManager.resolveSkin(heroName, skinId);
        if (skin == null) return;
        viewer.sendEquipmentChange(npc, CosmeticManager.buildArmorEquipment(heroName, skin));
    }

    private static final class TrackListener implements Listener {
        @EventHandler
        public void onTrack(PlayerTrackEntityEvent event) {
            Player viewer = event.getPlayer();
            if (!NpcServiceProvider.isAvailable() || !Interactions.isRealPlayer(viewer)) return;
            String heroName = NpcManager.heroNameForEntity(event.getEntity());
            if (heroName == null || !(event.getEntity() instanceof LivingEntity npc)) return;
            // Delay 1 tick so our override lands after Paper sends the entity's initial spawn equipment.
            Bukkit.getScheduler().runTask(Heroes.getInstance(), () -> {
                if (viewer.isOnline() && npc.isValid() && npc.getWorld().equals(viewer.getWorld())) {
                    apply(viewer, heroName, npc);
                }
            });
        }
    }
}
