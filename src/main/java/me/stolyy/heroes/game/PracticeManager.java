package me.stolyy.heroes.game;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.hero.minion.MinionManager;
import me.stolyy.heroes.control.PlayerController;
import me.stolyy.heroes.npc.NpcHandle;
import me.stolyy.heroes.npc.NpcServiceProvider;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.vfx.cosmetic.CosmeticManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lobby practice range: /practice gives you your selected hero inside the
 * configured practice-arenas (game-config.yml) with full ability use — no game needed.
 * Target dummies spawn at configured points via the NPC layer; they take damage and
 * knockback (they have no Hero — Interactions.applyKnockback special-cases them with
 * DUMMY_WEIGHT) and respawn a few seconds after dying.
 *
 * <p>Leaving every arena on foot, /practice again, or logging out ends practice mode.
 */
public class PracticeManager {
    private static final int DUMMY_RESPAWN_TICKS = 60;
    private static final double DUMMY_HEALTH = 100.0;
    // Dummies have no Hero and therefore no stat block; Interactions.applyKnockback uses this as
    // their weight so they eat knockback like a mid-roster hero (pug 2.5, karakot 2.0, shoop 3.0).
    public static final double DUMMY_WEIGHT = 2.0;

    private static final Map<UUID, PracticeArena> practicing = new HashMap<>();
    private static final Set<UUID> dummyIds = new HashSet<>();
    private static final List<DummySlot> dummies = new ArrayList<>();

    // --- Lifecycle (wired from Heroes) ---

    public static void initialize() {
        Bukkit.getPluginManager().registerEvents(new QuitListener(), Heroes.getInstance());
        Bukkit.getScheduler().runTaskTimer(Heroes.getInstance(), PracticeManager::tick, 1L, 1L);
    }

    /** Spawns every arena's configured target dummies. Called after worlds are loaded. */
    public static void spawnDummies() {
        for (PracticeArena arena : GameConfigManager.getPracticeArenas()) {
            for (Location loc : arena.dummyLocations()) {
                DummySlot slot = new DummySlot(loc);
                slot.spawn();
                dummies.add(slot);
            }
        }
    }

    public static void clear() {
        for (DummySlot slot : dummies) {
            slot.despawn();
        }
        dummies.clear();
        dummyIds.clear();
        practicing.clear();
    }

    // --- Player state ---

    public static boolean isPracticing(Player player) {
        return practicing.containsKey(player.getUniqueId());
    }

    /** The arena a practicing player is currently standing in; null when they are not practicing. */
    public static PracticeArena arenaOf(Player player) {
        return practicing.get(player.getUniqueId());
    }

    /** Practice combat gate: a practicing player hitting a target dummy (no game involved). */
    public static boolean isPracticeHit(LivingEntity attacker, LivingEntity victim) {
        return practicing.containsKey(attacker.getUniqueId()) && dummyIds.contains(victim.getUniqueId());
    }

    /** True if this entity is a practice dummy. Dummies live outside any Game by design. */
    public static boolean isDummy(LivingEntity entity) {
        return dummyIds.contains(entity.getUniqueId());
    }

    /** The arena whose spawn is closest to the player; the first arena when none shares their world. */
    public static PracticeArena nearestArena(Player player) {
        List<PracticeArena> arenas = GameConfigManager.getPracticeArenas();
        PracticeArena best = arenas.get(0);
        double bestDistance = Double.MAX_VALUE;
        for (PracticeArena arena : arenas) {
            Location spawn = arena.spawn();
            if (!spawn.getWorld().equals(player.getWorld())) continue;
            double distance = spawn.distanceSquared(player.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = arena;
            }
        }
        return best;
    }

    public static void enterPractice(Player player, PracticeArena arena) {
        if (GameManager.isPlayerInGame(player)) {
            player.sendMessage(Component.text("Leave your game before entering practice.", NamedTextColor.RED));
            return;
        }
        String heroName = HeroManager.getHeroName(player);
        if (heroName == null || !HeroConfig.exists(heroName)) {
            player.sendMessage(Component.text("Select a hero first!", NamedTextColor.RED));
            return;
        }

        Hero hero = HeroManager.createHero(heroName, new PlayerController(player));
        // Games do this at countdown end; without it practice players kept vanilla's 3.0 entity
        // interaction range while in-game players run at jab_range (5.5), so practice exercised a
        // structurally different reach and hit path from the thing it is meant to rehearse.
        hero.resetStats(hero.getController());
        CosmeticManager.apply(hero.getController(), hero);
        // Hero construction raised MAX_HEALTH (e.g. to 100) but left current health at the old
        // 20 — the scaled bar sat at ~20% until first heal. Games fix this via applyEffects;
        // practice has no settings to apply, so top up directly.
        AttributeInstance practiceMaxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (practiceMaxHealth != null) player.setHealth(practiceMaxHealth.getValue());
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        player.teleport(arena.spawn());
        // Prime the double jump — games do this at countdown end and on every respawn;
        // without it the toggle-flight jump is dead until the first landing.
        player.setAllowFlight(true);

        practicing.put(player.getUniqueId(), arena);
        player.sendMessage(Component.text("Practice mode (" + arena.name() + ") — abilities are live. /practice to leave.",
                NamedTextColor.GREEN));
    }

    public static void exitPractice(Player player, boolean toLobby) {
        if (practicing.remove(player.getUniqueId()) == null) return;

        Hero hero = HeroManager.getHero(player);
        if (hero != null) MinionManager.despawnAll(hero);
        HeroManager.removePlayer(player);
        GameEffects.removeEffects(player);
        // The walked-out exit never routes through teleportToLobby (which owns the usual
        // inventory reset), so strip the kit items here in every case.
        player.getInventory().clear();
        // Practice primed the toggle-flight double jump with setAllowFlight(true). The hero is
        // gone by now, so MovementListener.onDoubleJump no longer cancels PlayerToggleFlightEvent
        // and the leftover mayfly bit is literal creative flight in the lobby. setFlying(false)
        // first: CraftPlayer only rejects setFlying(TRUE) without permission, so this never
        // throws, and it drops anyone already airborne before the permission goes away.
        player.setFlying(false);
        player.setAllowFlight(false);
        player.sendMessage(Component.text("Left practice mode.", NamedTextColor.YELLOW));
        if (toLobby) {
            Heroes.teleportToLobby(player);
        }
    }

    // --- Ticking ---

    private static void tick() {
        for (UUID uuid : new HashSet<>(practicing.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            Hero hero = HeroManager.getHero(uuid);
            if (p == null || hero == null) {
                if (p != null) {
                    p.setFlying(false);
                    p.setAllowFlight(false);
                }
                practicing.remove(uuid);
                continue;
            }
            // The arena boundary is enforced here rather than only from PlayerMoveEvent:
            // PlayerTeleportEvent carries its own HandlerList, so a blink that lands outside the
            // box (Karakot Teleport, VoidTeleboom, RayOfDoom, CryomancerTimeWarp) was invisible to
            // the move-based check until the player's next step. One authority, every tick,
            // whatever moved them. Safe to mutate `practicing` here — the loop walks a snapshot.
            PracticeArena arena = GameConfigManager.arenaAt(p.getLocation());
            if (arena == null) {
                exitPractice(p, false); // walked/blinked out of every arena — strip practice state, no teleport
                continue;
            }
            practicing.put(uuid, arena);
            hero.tick();
        }
        for (DummySlot slot : dummies) {
            slot.tick();
        }
    }

    // --- Lifecycle triggers ---

    public static class QuitListener implements Listener {
        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            if (practicing.remove(event.getPlayer().getUniqueId()) != null) {
                // abilities.mayfly is persisted in player.dat and PlayerQuitEvent fires before
                // PlayerList#save, so quitting mid-practice used to hand the flight permission back
                // on the next login — hero-less, in the lobby.
                event.getPlayer().setFlying(false);
                event.getPlayer().setAllowFlight(false);
            }
            // Hero teardown happens in PlayerLeaveListener via GameManager.detach ->
            // HeroManager.removePlayer is a no-op there for practice players, so do it here.
            HeroManager.removePlayer(event.getPlayer());
        }
    }

    /** One configured dummy point: keeps a target NPC alive there, respawning after death. */
    private static class DummySlot {
        private final Location location;
        private NpcHandle handle;
        private int respawnCountdown = -1;

        DummySlot(Location location) {
            this.location = location;
        }

        void spawn() {
            handle = NpcServiceProvider.get().create(
                    "dummy_" + UUID.randomUUID().toString().substring(0, 8), location);
            if (handle == null) return; // NPC backend unavailable
            NpcServiceProvider.get().setProtected(handle, false);
            NpcServiceProvider.get().setShowInTab(handle, false);
            // The generated "dummy_xxxxxxxx" id is not a name anyone should read overhead.
            NpcServiceProvider.get().setNameplateVisible(handle, false);
            LivingEntity entity = NpcServiceProvider.get().getBukkitEntity(handle);
            if (entity != null) {
                AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
                if (maxHealth != null) maxHealth.setBaseValue(DUMMY_HEALTH);
                entity.setHealth(DUMMY_HEALTH);
                // Hitbox.isValidTarget only accepts Player victims in ADVENTURE (same rule
                // GameBotNpc.spawn satisfies) — Citizens spawns NPCs in the server default mode,
                // which is why practice hits never registered on the dummies.
                if (entity instanceof Player dummyPlayer) {
                    dummyPlayer.setGameMode(org.bukkit.GameMode.ADVENTURE);
                }
                dummyIds.add(entity.getUniqueId());
            }
        }

        void despawn() {
            if (handle != null) {
                LivingEntity entity = NpcServiceProvider.get().getBukkitEntity(handle);
                if (entity != null) dummyIds.remove(entity.getUniqueId());
                NpcServiceProvider.get().remove(handle.id());
                handle = null;
            }
        }

        void tick() {
            if (respawnCountdown > 0) {
                respawnCountdown--;
                if (respawnCountdown == 0) {
                    respawnCountdown = -1;
                    spawn();
                }
                return;
            }
            if (handle == null) return;
            LivingEntity entity = NpcServiceProvider.get().getBukkitEntity(handle);
            if (entity == null || entity.isDead()) {
                despawn();
                respawnCountdown = DUMMY_RESPAWN_TICKS;
            }
        }
    }
}
