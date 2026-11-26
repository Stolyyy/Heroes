package me.stolyy.heroes.npc;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.npc.types.AnnouncerNpc;
import me.stolyy.heroes.npc.types.HeroDisplayNpc;
import me.stolyy.heroes.npc.types.QueueNpc;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Registry and lifecycle manager for lobby NPCs configured in {@code lobby.yml}. */
public class NpcManager {

    private static final Map<String, Npc> npcs = new HashMap<>();
    private static boolean initialized;
    private static BukkitTask queueTextTask;

    public static void initialize() {
        if (initialized) return;

        NpcConfig.load();

        if (!NpcServiceProvider.isAvailable()) {
            Heroes.getInstance().getLogger().info("Citizens not found - NPC features disabled");
            initialized = true;
            return;
        }

        Bukkit.getPluginManager().registerEvents(new NpcInteractionListener(), Heroes.getInstance());
        Heroes.getInstance().getLogger().info("NPC system initialized with Citizens support");
        initialized = true;
    }

    /** Sweeps stale persisted NPCs and spawns configured announcer, hero, and queue NPCs. */
    public static void spawnLobbyNpcs() {
        if (!NpcServiceProvider.isAvailable()) return;

        NpcServiceProvider.get().removeAll();
        spawnAnnouncerNpc();
        spawnHeroNpcs();
        spawnQueueNpcs();

        Heroes.getInstance().getLogger().info("Spawned " + npcs.size() + " lobby NPCs from config");
    }

    private static void spawnQueueNpcs() {
        List<NpcConfig.QueueNpcData> queueData = NpcConfig.getQueueNpcData();
        for (NpcConfig.QueueNpcData data : queueData) {
            registerAndSpawn(new QueueNpc(data));
        }
        if (queueTextTask != null) {
            queueTextTask.cancel();
            queueTextTask = null;
        }
        if (queueData.isEmpty()) return;

        queueTextTask = Bukkit.getScheduler().runTaskTimer(Heroes.getInstance(), () -> {
            boolean anyQueueNpc = false;
            for (Npc npc : npcs.values()) {
                if (npc instanceof QueueNpc queueNpc) {
                    queueNpc.updateText();
                    anyQueueNpc = true;
                }
            }
            if (!anyQueueNpc && queueTextTask != null) {
                queueTextTask.cancel();
                queueTextTask = null;
            }
        }, 20L, 20L);
    }

    /** Returns each hero display NPC's live entity keyed by lowercase hero config ID. */
    public static Map<String, LivingEntity> heroNpcEntities() {
        Map<String, LivingEntity> entities = new HashMap<>();
        for (Npc npc : npcs.values()) {
            if (!(npc instanceof HeroDisplayNpc heroNpc) || heroNpc.getHandle() == null) continue;
            LivingEntity entity = NpcServiceProvider.get().getBukkitEntity(heroNpc.getHandle());
            if (entity != null) entities.put(heroNpc.getHeroName(), entity);
        }
        return entities;
    }

    private static void spawnAnnouncerNpc() {
        NpcConfig.AnnouncerData data = NpcConfig.getAnnouncerData();
        if (data == null) {
            Heroes.getInstance().getLogger().warning("No announcer NPC configured in lobby.yml");
            return;
        }
        registerAndSpawn(new AnnouncerNpc(data));
    }

    private static void spawnHeroNpcs() {
        List<NpcConfig.HeroNpcData> heroData = NpcConfig.getHeroNpcData();
        if (heroData.isEmpty()) {
            Heroes.getInstance().getLogger().warning("No hero NPCs configured in lobby.yml");
            return;
        }
        for (NpcConfig.HeroNpcData data : heroData) {
            registerAndSpawn(new HeroDisplayNpc(data));
        }
    }

    private static void registerAndSpawn(Npc npc) {
        npcs.put(npc.getId(), npc);
        npc.spawn();
    }

    public static void handleInteraction(String npcId, Player player) {
        Npc npc = npcs.get(npcId);
        if (npc == null) {
            Heroes.getInstance().getLogger().warning("Interaction with unknown NPC id: " + npcId);
            return;
        }
        npc.handleInteraction(player);
    }

    /** Right-click interaction handler; see {@link Npc#handleSecondaryInteraction}. */
    public static void handleSecondaryInteraction(String npcId, Player player) {
        Npc npc = npcs.get(npcId);
        if (npc == null) {
            Heroes.getInstance().getLogger().warning("Secondary interaction with unknown NPC id: " + npcId);
            return;
        }
        npc.handleSecondaryInteraction(player);
    }

    public static void clear() {
        if (queueTextTask != null) {
            queueTextTask.cancel();
            queueTextTask = null;
        }
        for (Npc npc : npcs.values()) {
            npc.despawn();
        }
        npcs.clear();
        NpcServiceProvider.get().removeAll();
        Heroes.getInstance().getLogger().info("Cleared all NPCs");
    }
}
