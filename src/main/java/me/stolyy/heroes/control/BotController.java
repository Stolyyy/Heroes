package me.stolyy.heroes.control;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.control.ai.BotAI;
import me.stolyy.heroes.control.strategy.Strategy;
import me.stolyy.heroes.npc.NpcHandle;
import me.stolyy.heroes.npc.NpcService;
import me.stolyy.heroes.npc.NpcServiceProvider;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Objects;
import java.util.UUID;

public class BotController implements Controller {
    private final NpcHandle npcHandle;
    private final NpcService npcService;
    private BotAI ai;
    private Strategy strategy;
    private boolean wasOnGround;

    public BotController(NpcHandle handle) {
        this.npcHandle = handle;
        this.npcService = NpcServiceProvider.get();
    }

    @Override
    public LivingEntity getEntity() {
        return npcService.getBukkitEntity(npcHandle);
    }

    @Override
    public UUID getUniqueId() {
        LivingEntity entity = getEntity();
        return entity != null ? entity.getUniqueId() : null;
    }

    @Override
    public Location getLocation() {
        LivingEntity entity = getEntity();
        return entity != null ? entity.getLocation() : null;
    }

    @Override
    public Location getEyeLocation() {
        LivingEntity entity = getEntity();
        return entity != null ? entity.getEyeLocation() : null;
    }

    @Override
    public Location getCenter() {
        LivingEntity entity = getEntity();
        return entity != null ? entity.getBoundingBox().getCenter().toLocation(entity.getWorld()) : null;
    }

    @Override
    public void setAllowFlight(boolean allow) { /* no-op for bots */ }

    @Override
    public boolean isOnGround() {
        LivingEntity entity = getEntity();
        return entity != null && entity.isOnGround();
    }

    @Override
    public BoundingBox getBoundingBox() {
        LivingEntity entity = getEntity();
        return entity != null ? entity.getBoundingBox() : null;
    }

    @Override
    public Vector getVelocity() {
        LivingEntity entity = getEntity();
        return entity != null ? entity.getVelocity() : new Vector();
    }

    @Override
    public boolean isPlayer() {
        return false;
    }

    @Override
    public boolean isSneaking() {
        LivingEntity entity = getEntity();
        return entity != null && entity.isSneaking();
    }

    @Override
    public void setVelocity(Vector velocity) {
        LivingEntity entity = getEntity();
        if (entity != null) entity.setVelocity(velocity);
    }

    @Override
    public void teleport(Location location) {
        // Must go through Citizens rather than entity.teleport: the NPC's stored location and its
        // Navigator state are only updated on NPC.teleport, so a raw entity teleport leaves the NPC
        // believing it is still where it was and pathing back from a stale origin.
        npcService.teleport(npcHandle, location);
    }

    @Override
    public void healToFull() {
        LivingEntity entity = getEntity();
        if (entity == null) return;
        entity.setHealth(Objects.requireNonNull(entity.getAttribute(Attribute.MAX_HEALTH)).getValue());
    }

    // --- Combat state ---

    /**
     * Re-asserts the combat-ready entity state. Citizens re-creates the NPC entity in SURVIVAL on
     * chunk load and always-keep-loaded is false, so this cannot be set once at spawn — it has to be
     * re-asserted while the bot is in play. Same for MOVEMENT_SPEED: Citizens' BasicMobAI resets
     * the BASE value to 0.3 on every entity re-creation, and player-NPC navigation reads exactly
     * that base (see CitizensNpcService.setNavigationSpeed) — left unpinned, the bot's pace
     * drifts after chunk churn.
     */
    public void ensureCombatState(Hero hero) {
        if (!(getEntity() instanceof org.bukkit.entity.Player p)) return;

        double intended = hero.getAttributes().getMovementSpeed()
                * hero.getAttributes().getSpeedMultiplier();
        var speedAttr = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null && Math.abs(speedAttr.getBaseValue() - intended) > 1e-6) {
            speedAttr.setBaseValue(intended);
        }

        // A bot must never carry the players' toggle-flight double-jump primer: with no client
        // to fire (and MovementListener to cancel) PlayerToggleFlightEvent, mayfly on an NPC
        // becomes literal hover the first time the server engages flight at a jump apex.
        // setFlying(false) FIRST — CraftPlayer only rejects setFlying(true) without mayfly.
        if (p.getAllowFlight() || p.isFlying()) {
            p.setFlying(false);
            p.setAllowFlight(false);
        }

        if (p.getGameMode() == GameMode.ADVENTURE) return;
        p.setGameMode(GameMode.ADVENTURE);
        p.setMaximumNoDamageTicks(1);
        npcService.setProtected(npcHandle, false);
        // A non-ADVENTURE gamemode means Citizens just re-created the entity (chunk churn):
        // its fresh tab entry is globally listed again — re-scope it to this bot's game.
        me.stolyy.heroes.game.Game.refreshBotListing(p);
    }

    /**
     * Edge-triggered landing detection. Bots never fire PlayerMoveEvent, which is the only thing
     * that resets double jumps for players, so the airborne->grounded transition has to be polled.
     */
    public void updateGroundState(Hero hero) {
        boolean onGround = isOnGround();
        if (onGround && !wasOnGround) {
            hero.getMobility().onLand(hero);
            hero.triggerLand();
        }
        wasOnGround = onGround;
    }

    // --- Navigation ---

    public void navigateTo(Location target) {
        npcService.navigateTo(npcHandle, target);
    }

    public void navigateToEntity(LivingEntity target) {
        npcService.navigateToEntity(npcHandle, target, true);
    }

    /**
     * Citizens LocalParameters are per-navigation — a new setTarget resets them to defaults — so
     * this must be re-applied after every navigate call, not once at spawn.
     */
    public void setNavigationSpeed(float speedModifier) {
        npcService.setNavigationSpeed(npcHandle, speedModifier);
    }

    /** Kiting facing (V2 §7): face this while navigating; null clears. Packet-level — needs
     *  ProtocolLib to be visible; server-side facing still belongs to navigation. */
    public void setNavigationLookAt(java.util.function.Supplier<Location> lookTarget) {
        npcService.setNavigationLookAt(npcHandle, lookTarget);
    }

    public void stopNavigation() {
        npcService.stopNavigation(npcHandle);
    }

    public boolean isNavigating() {
        return npcService.isNavigating(npcHandle);
    }

    public void lookAt(Location target) {
        npcService.lookAt(npcHandle, target);
    }

    // --- AI ---

    public void setAI(BotAI ai) { this.ai = ai; }
    public BotAI getAI() { return ai; }

    public void setStrategy(Strategy strategy) { this.strategy = strategy; }
    public Strategy getStrategy() { return strategy; }

    public void tickAI(Hero hero) {
        // A grabbed bot is a passenger with every input locked — its AI pausing entirely is
        // the bot equivalent of a player who can't press anything. Same for its own respawn
        // countdown: players can't act while respawning, so neither should the AI (it was
        // acquiring targets and swinging from the respawn point). Eliminated is permanent
        // respawning: an out-of-lives bot has no client left to press anything, ever.
        if (hero.actionsLocked() || hero.isRespawning() || hero.isEliminated()) return;
        if (ai != null) ai.tick(hero, this);
    }

    // --- NPC Handle ---

    public NpcHandle getNpcHandle() { return npcHandle; }

    // --- Stub implementations for Controller interface ---

    @Override public void sendMessage(Component message) {}
    @Override public void sendActionBar(Component message) {}
    @Override public void sendTitle(Component title, Component subtitle) {}
    @Override public void sendTitle(Component title, Component subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {}
    @Override public void playSound(Object sound, float volume, float pitch) {}
    @Override public void updateEnergyDisplay(float percent, int value) {}
    @Override public void updateItemCooldown(int slotIndex, float progress) {}

    @Override public void setInventoryItem(int slot, ItemStack item) {
        // NPC "players" have a real inventory; slot 0 is the selected hotbar slot, so writing it is
        // what puts the hero's ability item (and ult held-model swaps) visibly in the bot's hand.
        if (getEntity() instanceof org.bukkit.entity.Player p) p.getInventory().setItem(slot, item);
    }
    @Override public void setHelmet(ItemStack item) {
        LivingEntity entity = getEntity();
        if (entity != null && entity.getEquipment() != null) entity.getEquipment().setHelmet(item);
    }
    @Override public void setChestplate(ItemStack item) {
        LivingEntity entity = getEntity();
        if (entity != null && entity.getEquipment() != null) entity.getEquipment().setChestplate(item);
    }
    @Override public void setLeggings(ItemStack item) {
        LivingEntity entity = getEntity();
        if (entity != null && entity.getEquipment() != null) entity.getEquipment().setLeggings(item);
    }
    @Override public void setBoots(ItemStack item) {
        LivingEntity entity = getEntity();
        if (entity != null && entity.getEquipment() != null) entity.getEquipment().setBoots(item);
    }
}
