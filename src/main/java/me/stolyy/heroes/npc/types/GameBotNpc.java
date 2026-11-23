package me.stolyy.heroes.npc.types;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.control.strategy.Strategy;
import me.stolyy.heroes.npc.Npc;
import me.stolyy.heroes.npc.NpcService;
import me.stolyy.heroes.npc.NpcServiceProvider;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * NPC that represents an AI combatant in a game.
 * Unlike lobby NPCs, game bots have full Hero instances with abilities
 * and use BotController + Strategy for AI behavior.
 *
 * This is a placeholder for future implementation.
 * Game bots require:
 * - Hero instance with full abilities
 * - BotController with Strategy pattern
 * - ArmorStand hitbox entity for combat
 * - Per-tick position sync between NPC visual and hitbox
 * - Game lifecycle management (spawn on game start, despawn on end)
 */
public class GameBotNpc extends Npc {

    private static final String ID_PREFIX = "game_bot_";
    private static int nextBotId = 0;

    private final String botName;
    private Hero hero;
    private BotController controller;
    private Strategy strategy;

    /**
     * Creates a game bot NPC.
     * @param botName the bot's display name (already sanitized by BotNameManager: plain, ≤16 chars).
     *                This is the ONE name the bot has — nameplate, scoreboard, tab and kill feed all
     *                key off the entity name, and GameBot.getName() carries the same string, so
     *                every surface agrees. It is hero-agnostic on purpose: swapping the bot's hero
     *                in the settings GUI never needs to touch the NPC again.
     * @param location spawn location
     */
    public GameBotNpc(String botName, Location location) {
        super(ID_PREFIX + nextBotId++, location);
        this.botName = botName;
    }

    /**
     * @return the hero instance, or null if not initialized
     */
    public Hero getHero() {
        return hero;
    }

    /**
     * @return the bot controller, or null if not initialized
     */
    public BotController getController() {
        return controller;
    }

    /**
     * Sets the AI strategy for this bot.
     */
    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
        if (controller != null) {
            controller.setStrategy(strategy);
        }
    }

    @Override
    public void spawn() {
        NpcService service = NpcServiceProvider.get();
        if (!service.isAvailable()) return;

        handle = service.create(id, location);
        if (handle == null) return;

        // PLAIN name, ≤16 chars (BotNameManager.sanitize enforces it): a colored (or >16-char)
        // player-NPC name makes Citizens render its own TextDisplay hologram ON TOP of the
        // vanilla nametag — two nametags, and the hologram is a separate entity that hidePlayer
        // can't hide, so it stayed visible while the bot was respawning. The vanilla nametag is
        // the only one we want; its color comes from the game's hg_ scoreboard team, not the name.
        service.setDisplayName(handle, botName);

        // Configure NPC behavior: bots are combatants, so they belong in the tab list and wear
        // their name overhead like any player. Team color comes from the game's hg_ scoreboard
        // team, which keys both by entity name.
        service.setShowInTab(handle, true);
        service.setNameplateVisible(handle, true);
        service.setTurnToPlayer(handle, false); // Bots look at their targets, not players

        // Spawn the NPC
        service.spawn(handle);

        // Combat NPCs must be damageable — Citizens NPCs are protected by default (same as
        // MinionHero.spawn) — and Hitbox.isValidTarget only accepts Player victims in ADVENTURE.
        service.setProtected(handle, false);
        if (service.getBukkitEntity(handle) instanceof Player botPlayer) {
            botPlayer.setGameMode(org.bukkit.GameMode.ADVENTURE);
        }

        // TODO: Initialize hero, controller, and hitbox entity
        // This requires:
        // 1. Create Hero from HeroBuilder
        // 2. Create BotController
        // 3. Spawn ArmorStand hitbox
        // 4. Register tick handler for AI and position sync
    }

    @Override
    public void despawn() {
        // Clean up hero and controller
        if (hero != null) {
            hero.cleanup();
            hero = null;
        }
        controller = null;

        // TODO: Remove hitbox entity

        super.despawn();
    }

    @Override
    public void handleInteraction(Player player) {
        // Game bots don't respond to player interactions
        // They are controlled by AI
    }

    /**
     * Gets the underlying Bukkit entity for this bot NPC.
     * @return the LivingEntity, or null if not spawned
     */
    public org.bukkit.entity.LivingEntity getEntity() {
        if (handle == null) return null;
        return NpcServiceProvider.get().getBukkitEntity(handle);
    }

    /**
     * Gets the UUID of the underlying entity.
     * @return the UUID, or null if not spawned
     */
    public java.util.UUID getUUID() {
        org.bukkit.entity.LivingEntity entity = getEntity();
        return entity != null ? entity.getUniqueId() : null;
    }

    /**
     * Alias for {@link #despawn()} — removes this bot NPC from the world.
     */
    public void remove() {
        despawn();
    }

    /**
     * Called each tick to update bot AI and position.
     */
    public void tick() {
        if (controller == null || hero == null) return;

        // TODO: Implement bot AI tick
        // 1. Strategy decides actions
        // 2. BotController executes actions
        // 3. Sync NPC position to hitbox position
    }

    /**
     * Teleports both the NPC visual and hitbox to a location.
     */
    public void teleportWithHitbox(Location location) {
        if (handle != null) {
            NpcServiceProvider.get().teleport(handle, location);
        }
        // TODO: Teleport hitbox entity
    }

    /**
     * Resets the bot ID counter (for testing or server restart).
     */
    public static void resetIdCounter() {
        nextBotId = 0;
    }
}
