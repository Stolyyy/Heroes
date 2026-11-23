package me.stolyy.heroes.vfx.glow;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.Team;
import me.stolyy.heroes.game.TeamColor;
import me.stolyy.heroes.hero.minion.MinionHero;
import me.stolyy.heroes.hero.minion.MinionManager;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.util.GameConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Teammate outlines for the game layer: every player sees their own teammates glowing in the team
 * colour, and nobody else sees it. Because the outline is per-viewer it leaks nothing to enemies —
 * the through-wall read is only ever of people you already share a scoreboard with. Spectators are
 * the exception by design: they see EVERY combatant outlined (each in its own team colour), since
 * they have no stake to leak to.
 *
 * <p>Holds all the bookkeeping the {@link GlowService} backend deliberately does not: which entity ids
 * are outlined for which viewer, so a roster change can undo exactly the outlines that went stale.
 *
 * <p>Wiring (all main thread):
 * <ul>
 *   <li>{@link #initialize()} in {@code Heroes#onEnable}, after {@code GameConfigManager.reload()} —
 *       the config toggle is read here.</li>
 *   <li>{@link #shutdown()} in {@code Heroes#onDisable}.</li>
 *   <li>{@link #refreshTeams(Map)} whenever a game's roster changes: round start (after the scoreboard
 *       teams exist), elimination, hero swap that respawns the entity, bot spawn, player join/leave.</li>
 *   <li>{@link #clearFor(Player)} wherever a player stops being a participant — the two
 *       {@code setScoreboard(getMainScoreboard())} sites in {@code Game}, and game end.</li>
 * </ul>
 * A missed clear is only visible to the one viewer who owns it (they would see stale outlines in the
 * lobby), never to anyone else — but it survives until they quit, so the clear points matter.
 *
 * <p><b>Respawning teammates.</b> {@code Game}'s respawn path hides the dead player from everyone with
 * {@code Player#hidePlayer}, which destroys the entity on every other client — there is nothing left to
 * outline, so no glow call can reach it. Outlining a respawning teammate therefore requires the respawn
 * hide loop to skip that player's own team; once it does, {@link #glow(Entity, Player)} works
 * on them normally (they stay alive, in ADVENTURE mode, standing frozen at the respawn point). See
 * {@link #isRespawnGlowEnabled()}.
 */
public final class TeamGlow {

    private TeamGlow() {}

    // viewer uuid -> entity ids currently outlined for that viewer. The backend is fire-and-forget
    // packets with no readable state, so this map is the only record of what has to be undone later.
    private static final Map<UUID, Set<Integer>> outlined = new HashMap<>();

    private static boolean respawnGlow;
    private static boolean initialized;

    public static void initialize() {
        if (initialized) return;
        respawnGlow = Configs.requireBoolean(GameConfigManager.getGameConfig(), "teammate-glow.show-respawning");
        GlowServiceProvider.get();
        Bukkit.getPluginManager().registerEvents(new QuitListener(), Heroes.getInstance());
        initialized = true;
    }

    public static void shutdown() {
        clearAll();
        GlowServiceProvider.shutdown();
        initialized = false;
    }

    /**
     * Whether a teammate should stay outlined through their respawn countdown. Only meaningful once the
     * respawn path keeps them visible to their own team; see the class javadoc.
     */
    public static boolean isRespawnGlowEnabled() {
        return respawnGlow;
    }

    /**
     * Refreshes every viewer in a game from its team map — the one call a roster change needs.
     * Authoritative: outlines a viewer holds that are not in their team any more are removed.
     *
     * <p>Team-owned minions glow too. They aren't roster members, so they're appended from the
     * minion registry by game + team color — and the outlined entity is the minion's BODY
     * DISPLAY when it has one (the visible model since the full-body rework; the NPC underneath
     * is an invisibility-potioned husk whose humanoid silhouette would glow next to the model),
     * falling back to the NPC entity for equipment-rendered minions.
     */
    public static void refreshTeams(Game game) {
        if (!GlowServiceProvider.isAvailable()) return;

        // Per-team combatant lists (roster + that team's minions), plus the union for spectators.
        // Targets come from allLivingEntities() so bots glow too; viewers from onlinePlayers()
        // (Bukkit.getPlayer resolves real connections only, so bot NPCs are never handed packets).
        Map<TeamColor, List<Entity>> combatants = new HashMap<>();
        List<Entity> everyone = new ArrayList<>();
        for (Map.Entry<TeamColor, Team> entry : game.getTeams().entrySet()) {
            if (entry.getKey() == TeamColor.SPECTATOR) continue;
            List<Entity> members = new ArrayList<>(entry.getValue().allLivingEntities());
            for (MinionHero minion : MinionManager.getActiveMinions()) {
                if (minion.getGame() != game || minion.getTeamColor() != entry.getKey()) continue;
                Entity glowTarget = minion.getBodyDisplay() != null
                        ? minion.getBodyDisplay().getDisplay()
                        : (minion.getController() != null ? minion.getController().getEntity() : null);
                if (glowTarget != null) members.add(glowTarget);
            }
            combatants.put(entry.getKey(), members);
            everyone.addAll(members);
        }

        for (Map.Entry<TeamColor, Team> entry : game.getTeams().entrySet()) {
            // Spectators see EVERY combatant glowing — the per-entity hg_ team still colours each
            // outline, so teams stay distinguishable. Players see their own team only; the outline
            // never leaks enemy positions to a combatant.
            List<Entity> targets = entry.getKey() == TeamColor.SPECTATOR
                    ? everyone : combatants.get(entry.getKey());
            for (Player viewer : entry.getValue().onlinePlayers()) {
                refresh(viewer, targets);
            }
        }
    }

    /**
     * Outlines {@code targets} for {@code viewer} alone, and drops any outline this viewer holds
     * that is no longer in the collection — the collection is authoritative, so switching a viewer
     * between a team view and the spectator everyone-view diffs cleanly.
     *
     * @param targets the entities this viewer should see outlined; the viewer themself is skipped
     */
    public static void refresh(Player viewer, Collection<? extends Entity> targets) {
        if (!GlowServiceProvider.isAvailable()) return;

        GlowService service = GlowServiceProvider.get();
        Set<Integer> previous = outlined.getOrDefault(viewer.getUniqueId(), Set.of());
        Set<Integer> current = new HashSet<>();
        for (Entity target : targets) {
            // Your own outline is pure noise — it only shows in third person.
            if (target.getUniqueId().equals(viewer.getUniqueId())) continue;
            // Metadata-only: the outline colour resolves from the hg_ nametag team the target is
            // already on for this viewer, so no colour is passed (see GlowService).
            service.glow(target, viewer);
            current.add(target.getEntityId());
        }
        for (int entityId : previous) {
            if (!current.contains(entityId)) service.clear(entityId, viewer);
        }

        if (current.isEmpty()) outlined.remove(viewer.getUniqueId());
        else outlined.put(viewer.getUniqueId(), current);
    }

    /**
     * Outlines a single entity for a single viewer, outside the roster flow. Tracked like any other
     * outline, so {@link #clearFor(Player)} still undoes it — but the next {@link #refresh} for this
     * viewer removes it unless the entity is one of their teammates.
     */
    public static void glow(Entity target, Player viewer) {
        if (!GlowServiceProvider.isAvailable()) return;
        GlowServiceProvider.get().glow(target, viewer);
        outlined.computeIfAbsent(viewer.getUniqueId(), uuid -> new HashSet<>()).add(target.getEntityId());
    }

    /**
     * Removes one entity's outline for one viewer.
     */
    public static void clear(Entity target, Player viewer) {
        if (!GlowServiceProvider.isAvailable()) return;
        GlowServiceProvider.get().clear(target.getEntityId(), viewer);
        outlined.computeIfPresent(viewer.getUniqueId(), (uuid, ids) -> {
            ids.remove(target.getEntityId());
            return ids.isEmpty() ? null : ids;
        });
    }

    /**
     * Removes every outline a viewer holds: game end, leaving. (Becoming a spectator no longer
     * clears — spectators get the everyone-view through the next refresh instead.)
     */
    public static void clearFor(Player viewer) {
        Set<Integer> ids = outlined.remove(viewer.getUniqueId());
        if (ids == null) return;
        GlowService service = GlowServiceProvider.get();
        for (int entityId : ids) {
            service.clear(entityId, viewer);
        }
    }

    /**
     * Removes every outline for every viewer. Plugin shutdown backstop.
     */
    public static void clearAll() {
        for (UUID uuid : Set.copyOf(outlined.keySet())) {
            Player viewer = Bukkit.getPlayer(uuid);
            if (viewer == null) outlined.remove(uuid);
            else clearFor(viewer);
        }
    }

    /**
     * A quitting viewer's outlines die with their connection; drop the bookkeeping so it cannot leak.
     * Public because Bukkit's generated event executors cannot reach a non-public listener class.
     */
    public static class QuitListener implements Listener {
        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            outlined.remove(event.getPlayer().getUniqueId());
        }
    }
}
