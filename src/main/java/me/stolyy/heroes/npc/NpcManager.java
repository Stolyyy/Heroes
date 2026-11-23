package me.stolyy.heroes.npc;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.npc.interactions.NpcInteractionListener;
import me.stolyy.heroes.npc.types.AnnouncerNpc;
import me.stolyy.heroes.npc.types.HeroDisplayNpc;
import me.stolyy.heroes.npc.types.QueueNpc;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry and lifecycle manager for all NPCs.
 * Loads NPC data from lobby.yml configuration file.
 */
public class NpcManager {

    private static final Map<String, Npc> npcs = new HashMap<>();
    private static boolean initialized = false;
    private static BukkitTask queueTextTask;

    /**
     * Initializes the NPC system.
     * Loads config and registers event listeners if FancyNPCs is available.
     */
    public static void initialize() {
        if (initialized) return;

        // Load NPC configuration
        NpcConfig.load();

        if (!NpcServiceProvider.isAvailable()) {
            Heroes.getInstance().getLogger().info("Citizens not found - NPC features disabled");
            initialized = true;
            return;
        }

        // Register interaction listener
        Bukkit.getPluginManager().registerEvents(new NpcInteractionListener(), Heroes.getInstance());
        Heroes.getInstance().getLogger().info("NPC system initialized with Citizens support");

        initialized = true;
    }

    /**
     * Spawns all lobby NPCs from the configuration file.
     * Should be called after a short delay to ensure world is loaded.
     */
    public static void spawnLobbyNpcs() {
        if (!NpcServiceProvider.isAvailable()) return;

        // Clean up any NPCs persisted by Citizens from previous runs
        NpcServiceProvider.get().removeAll();

        // Spawn announcer NPC
        spawnAnnouncerNpc();

        // Spawn hero NPCs
        spawnHeroNpcs();

        // Spawn queue NPCs (1v1/2v2/custom) and keep their count lines fresh once a second.
        spawnQueueNpcs();

        Heroes.getInstance().getLogger().info("Spawned " + npcs.size() + " lobby NPCs from config");
    }

    private static void spawnQueueNpcs() {
        for (NpcConfig.QueueNpcData data : NpcConfig.getQueueNpcData()) {
            registerAndSpawn(new QueueNpc(data));
        }
        if (queueTextTask != null) queueTextTask.cancel();
        queueTextTask = Bukkit.getScheduler().runTaskTimer(Heroes.getInstance(), () -> {
            for (Npc npc : npcs.values()) {
                if (npc instanceof QueueNpc queueNpc) queueNpc.updateText();
            }
        }, 20L, 20L);
    }

    /**
     * hero config key → the lobby display NPC's live entity, for every spawned hero NPC.
     * HeroNpcLevelTags uses this to float each viewer's own hero level under the NPCs' names.
     */
    public static Map<String, org.bukkit.entity.LivingEntity> heroNpcEntities() {
        Map<String, org.bukkit.entity.LivingEntity> entities = new HashMap<>();
        for (Npc npc : npcs.values()) {
            if (!(npc instanceof HeroDisplayNpc heroNpc) || heroNpc.getHandle() == null) continue;
            org.bukkit.entity.LivingEntity entity =
                    NpcServiceProvider.get().getBukkitEntity(heroNpc.getHandle());
            if (entity != null) entities.put(heroNpc.getHeroName(), entity);
        }
        return entities;
    }

    /**
     * Spawns the announcer NPC from config.
     */
    private static void spawnAnnouncerNpc() {
        NpcConfig.AnnouncerData data = NpcConfig.getAnnouncerData();
        if (data == null) {
            Heroes.getInstance().getLogger().warning("No announcer NPC configured in lobby.yml");
            return;
        }

        AnnouncerNpc announcer = new AnnouncerNpc(data);
        registerAndSpawn(announcer);
    }

    /**
     * Spawns all hero display NPCs from config.
     */
    private static void spawnHeroNpcs() {
        List<NpcConfig.HeroNpcData> heroData = NpcConfig.getHeroNpcData();
        if (heroData.isEmpty()) {
            Heroes.getInstance().getLogger().warning("No hero NPCs configured in lobby.yml");
            return;
        }

        for (NpcConfig.HeroNpcData data : heroData) {
            HeroDisplayNpc heroNpc = new HeroDisplayNpc(data);
            registerAndSpawn(heroNpc);
        }
    }

    /**
     * Registers an NPC and spawns it.
     */
    private static void registerAndSpawn(Npc npc) {
        npcs.put(npc.getId(), npc);
        npc.spawn();
    }

    /**
     * Handles an interaction with an NPC by ID.
     * @param npcId the NPC ID
     * @param player the player who interacted
     */
    public static void handleInteraction(String npcId, Player player) {
        Npc npc = npcs.get(npcId);
        if (npc != null) {
            npc.handleInteraction(player);
        }
    }

    /** Right-click variant — see {@link Npc#handleSecondaryInteraction}. */
    public static void handleSecondaryInteraction(String npcId, Player player) {
        Npc npc = npcs.get(npcId);
        if (npc != null) {
            npc.handleSecondaryInteraction(player);
        }
    }

    /**
     * Gets an NPC by ID.
     * @return the NPC, or null if not found
     */
    public static Npc getNpc(String id) {
        return npcs.get(id);
    }

    /**
     * Removes and despawns an NPC by ID.
     */
    public static void remove(String id) {
        Npc npc = npcs.remove(id);
        if (npc != null) {
            npc.despawn();
        }
    }

    /**
     * Clears and despawns all managed NPCs.
     */
    public static void clear() {
        if (queueTextTask != null) {
            queueTextTask.cancel();
            queueTextTask = null;
        }
        for (Npc npc : npcs.values()) {
            npc.despawn();
        }
        npcs.clear();

        // Also tell the service to clean up
        NpcServiceProvider.get().removeAll();

        Heroes.getInstance().getLogger().info("Cleared all NPCs");
    }

    /**
     * Reloads NPC configuration and respawns all NPCs.
     */
    public static void reload() {
        clear();
        NpcConfig.reload();
        spawnLobbyNpcs();
    }

    /**
     * @return the number of registered NPCs
     */
    public static int getNpcCount() {
        return npcs.size();
    }
}
