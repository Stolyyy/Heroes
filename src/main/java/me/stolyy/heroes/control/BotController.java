package me.stolyy.heroes.control;

import me.stolyy.heroes.control.ai.BotAI;
import me.stolyy.heroes.game.PlayerVisibility;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.mechanics.StatusEffects;
import me.stolyy.heroes.npc.NpcHandle;
import me.stolyy.heroes.npc.NpcService;
import me.stolyy.heroes.npc.NpcServiceProvider;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public class BotController implements Controller {
    private final NpcHandle npcHandle;
    private final NpcService npcService;
    private BotAI ai;
    private boolean wasOnGround;
    private boolean moveBlocked;

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
    public void setAllowFlight(boolean allow) {}

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
    public void setSneaking(boolean sneaking) {
        LivingEntity entity = getEntity();
        if (entity != null) entity.setSneaking(sneaking);
    }

    @Override
    public void setSprinting(boolean sprinting) {
        if (getEntity() instanceof Player p && p.isSprinting() != sprinting) {
            p.setSprinting(sprinting);
        }
    }

    @Override
    public void swingMainHand() {
        LivingEntity entity = getEntity();
        if (entity != null) entity.swingMainHand();
    }

    @Override
    public void setFallDistance(float distance) {
        LivingEntity entity = getEntity();
        if (entity != null) entity.setFallDistance(distance);
    }

    @Override
    public boolean isUseHeld() {
        // Bots have no held-mouse input; keep held channels active until energy or duration ends.
        return true;
    }

    @Override
    public boolean isDrawingBow() {
        return false;
    }

    @Override
    public Vector getMovementAxes() {
        return new Vector();
    }

    @Override
    public void addPassenger(Entity passenger) {
        LivingEntity entity = getEntity();
        if (entity != null) entity.addPassenger(passenger);
    }

    @Override
    public void removePassenger(Entity passenger) {
        LivingEntity entity = getEntity();
        if (entity != null) entity.removePassenger(passenger);
    }

    @Override
    public boolean hasPassenger(Entity passenger) {
        LivingEntity entity = getEntity();
        return entity != null && entity.getPassengers().contains(passenger);
    }

    @Override
    public void mount(Entity vehicle) {
        LivingEntity entity = getEntity();
        if (entity != null && vehicle != null) vehicle.addPassenger(entity);
    }

    @Override
    public void leaveVehicle() {
        LivingEntity entity = getEntity();
        if (entity != null) entity.leaveVehicle();
    }

    @Override
    public void addPotionEffect(PotionEffect effect) {
        LivingEntity entity = getEntity();
        if (entity != null) entity.addPotionEffect(effect);
    }

    @Override
    public void removePotionEffect(PotionEffectType type) {
        LivingEntity entity = getEntity();
        if (entity != null) entity.removePotionEffect(type);
    }

    @Override
    public void setGameMode(GameMode mode) {
        if (getEntity() instanceof Player p) p.setGameMode(mode);
    }

    @Override
    public void setVelocity(Vector velocity) {
        LivingEntity entity = getEntity();
        if (entity != null) entity.setVelocity(velocity);
    }

    @Override
    public void teleport(Location location) {
        // Route through Citizens so Navigator origin and stored NPC location stay synchronized.
        npcService.teleport(npcHandle, location);
    }

    @Override
    public void healToFull() {
        LivingEntity entity = getEntity();
        if (entity == null) return;
        entity.setHealth(Objects.requireNonNull(entity.getAttribute(Attribute.MAX_HEALTH)).getValue());
    }

    /**
     * Re-asserts combat mode and base movement speed whenever Citizens re-creates the NPC entity
     * across chunk loads.
     */
    public void ensureCombatState(Hero hero) {
        if (!(getEntity() instanceof Player p)) return;

        double intended = hero.getAttributes().getMovementSpeed();
        AttributeInstance speedAttr = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null && Math.abs(speedAttr.getBaseValue() - intended) > 1e-6) {
            speedAttr.setBaseValue(intended);
        }

        // Strip toggle-flight primer so NPCs without PlayerToggleFlightEvent handlers never hover.
        if (p.getAllowFlight() || p.isFlying()) {
            p.setFlying(false);
            p.setAllowFlight(false);
        }

        if (p.getGameMode() == GameMode.ADVENTURE) return;
        p.setGameMode(GameMode.ADVENTURE);
        p.setMaximumNoDamageTicks(1);
        npcService.setProtected(npcHandle, false);
        PlayerVisibility.refreshBotListing(p);
    }

    /** Polls ground transitions so double jumps recharge without {@code PlayerMoveEvent}. */
    public void updateGroundState(Hero hero) {
        boolean onGround = isOnGround();
        if (onGround && !wasOnGround) {
            hero.getMobility().onLand(hero);
            hero.triggerLand();
        }
        wasOnGround = onGround;
    }

    public void navigateTo(Location target) {
        if (moveBlocked) return;
        npcService.navigateTo(npcHandle, target);
    }

    public void navigateToEntity(LivingEntity target) {
        if (moveBlocked) return;
        npcService.navigateToEntity(npcHandle, target, true);
    }

    /** Must be called after every navigate call because Citizens resets local parameters per path. */
    public void setNavigationSpeed(float speedModifier) {
        npcService.setNavigationSpeed(npcHandle, speedModifier);
    }

    public void setNavigationLookAt(Supplier<Location> lookTarget) {
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

    public void setAI(BotAI ai) {
        if (this.ai != null && this.ai != ai) {
            this.ai.cleanup();
        }
        this.ai = ai;
    }
    public BotAI getAI() { return ai; }

    public void tickAI(Hero hero) {
        if (hero.isRespawning() || hero.isEliminated()) return;
        moveBlocked = hero.status().blocks(StatusEffects.Restriction.MOVE);
        if (moveBlocked && isNavigating()) {
            stopNavigation();
        }
        if (ai != null) ai.tick(hero, this);
    }

    public NpcHandle getNpcHandle() { return npcHandle; }

    @Override public void sendMessage(Component message) {}
    @Override public void sendActionBar(Component message) {}
    @Override public void sendTitle(Component title, Component subtitle) {}
    @Override public void sendTitle(Component title, Component subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {}
    @Override public void updateEnergyDisplay(float percent, int value) {}
    @Override public void updateItemCooldown(int slotIndex, float progress) {}

    @Override public ItemStack getInventoryItem(int slot) {
        return getEntity() instanceof Player p ? p.getInventory().getItem(slot) : null;
    }

    @Override public void setInventoryItem(int slot, ItemStack item) {
        if (getEntity() instanceof Player p) p.getInventory().setItem(slot, item);
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
