package me.stolyy.heroes.ability.kit.mrmonster;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.mechanics.StatusEffects;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MonsterGrab extends BaseAbility {
    private static final Set<UUID> GRABBED = ConcurrentHashMap.newKeySet();

    private final int armWindowTicks;
    private final int throwWindowTicks;
    private final int throwPrimaryLockoutTicks;
    private final double throwDamage;
    private final double throwKnockback;
    private final double throwLift;
    private final SoundEffect activationSound;
    private final SoundEffect grabSound;
    private final SoundEffect throwSound;
    private final StatusEffects.Spec victimStatus;
    private final StatusEffects.Spec selfStatus;

    private int armTicksLeft = -1;
    private boolean grabbing;
    private LivingEntity victim;
    private int throwTicksLeft;

    public static void initialize() {
        Bukkit.getPluginManager().registerEvents(new DismountListener(), Heroes.getInstance());
    }

    public static void clear() {
        GRABBED.clear();
    }

    public static boolean isGrabbedVictim(UUID uuid) {
        return GRABBED.contains(uuid);
    }

    /** Prevents grabbed victims from sneaking off Mr. Monster's shoulder mid-grab. */
    public static class DismountListener implements Listener {
        @EventHandler
        public void onDismount(EntityDismountEvent event) {
            if (event.getEntity() instanceof Player victim && isGrabbedVictim(victim.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    public MonsterGrab(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.armWindowTicks = Configs.requireInt(this.config, "arm_window_ticks");
        this.throwWindowTicks = Configs.requireInt(this.config, "throw_window_ticks");
        this.throwPrimaryLockoutTicks = Configs.requireInt(this.config, "throw_primary_lockout_ticks");
        this.throwDamage = Configs.requireDouble(this.config, "throw_damage");
        this.throwKnockback = Configs.requireDouble(this.config, "throw_knockback");
        this.throwLift = Configs.requireDouble(this.config, "throw_lift");
        this.activationSound = new SoundEffect(Configs.requireSection(this.config, "visuals.activation_sound"));
        this.grabSound = new SoundEffect(Configs.requireSection(this.config, "visuals.grab_sound"));
        this.throwSound = new SoundEffect(Configs.requireSection(this.config, "visuals.throw_sound"));
        this.victimStatus = StatusEffects.Spec.fromConfigIndefinite(this.config, "victim_status");
        this.selfStatus = StatusEffects.Spec.fromConfigIndefinite(this.config, "self_status");
    }

    @Override
    protected boolean canUse(Hero hero) {
        return !grabbing && armTicksLeft < 0 && super.canUse(hero);
    }

    @Override
    public void perform(Hero hero) {
        armTicksLeft = armWindowTicks;
        activationSound.play(hero.getController().getLocation(), hero.getController());
        hero.getController().sendActionBar(
                Component.text("Snatch armed — next jab grabs", NamedTextColor.GOLD));
    }

    @Override
    public void onJabHit(Hero hero, LivingEntity jabVictim) {
        if (armTicksLeft < 0 || grabbing) return;
        armTicksLeft = -1;
        startGrab(hero, jabVictim);
    }

    private void startGrab(Hero hero, LivingEntity grabbed) {
        grabbing = true;
        victim = grabbed;
        throwTicksLeft = throwWindowTicks;

        GRABBED.add(grabbed.getUniqueId());
        hero.getController().addPassenger(grabbed);

        StatusEffects.applyTo(hero.getController().getEntity(), selfStatus, this, hero, null);
        StatusEffects.applyTo(grabbed, victimStatus, this, hero, null);

        hero.setPrimaryOverride(() -> executeThrow(hero));
        grabSound.play(hero.getController().getLocation(), hero.getController());
    }

    @Override
    protected void onTick(Hero hero) {
        if (armTicksLeft > 0) {
            armTicksLeft--;
            if (armTicksLeft == 0) armTicksLeft = -1;
        }

        if (!grabbing) return;

        if (hero.getController() == null || hero.getController().getEntity() == null
                || victim == null || victim.isDead() || !victim.isValid()
                || !hero.getController().hasPassenger(victim)) {
            release(hero);
            return;
        }

        throwTicksLeft--;
        hero.getController().sendActionBar(Component.text(
                String.format("Throw! %.1fs", Math.max(0, throwTicksLeft) / 20.0),
                NamedTextColor.GOLD));
        if (throwTicksLeft <= 0) {
            release(hero);
        }
    }

    private void executeThrow(Hero hero) {
        if (!grabbing) return;
        LivingEntity thrown = victim;
        Vector direction = hero.getController().getEyeLocation().getDirection();
        release(hero);

        hero.suppressPrimary(throwPrimaryLockoutTicks);
        throwSound.play(hero.getController().getLocation(), hero.getController());
        hit(hero, thrown)
                .damage(throwDamage)
                .knockback(throwKnockback)
                .verticalKnockback(throwLift)
                .direction(direction)
                .execute();
    }

    private void release(Hero hero) {
        if (!grabbing) return;
        grabbing = false;
        LivingEntity held = victim;
        victim = null;
        if (held != null) {
            GRABBED.remove(held.getUniqueId());
            if (hero.getController() != null) hero.getController().removePassenger(held);
        }
        StatusEffects.removeAllFrom(this);
        hero.clearPrimaryOverride();
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        armTicksLeft = -1;
        release(hero);
    }

    @Override
    public void onOwnerLost(Hero hero) {
        super.onOwnerLost(hero);
        armTicksLeft = -1;
        release(hero);
    }
}
