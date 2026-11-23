package me.stolyy.heroes.ability.kit.mrmonster;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MonsterGrab extends BaseAbility {
    private static final Set<UUID> GRABBED = ConcurrentHashMap.newKeySet();

    public static boolean isGrabbedVictim(UUID uuid) {
        return GRABBED.contains(uuid);
    }

    private final int armWindowTicks;
    private final int throwWindowTicks;
    private final int throwPrimaryLockoutTicks;
    private final double throwDamage;
    private final double throwKnockback;
    private final double throwLift;
    private final SoundEffect activationSound;
    private final SoundEffect grabSound;
    private final SoundEffect throwSound;

    private int armTicksLeft = -1;
    private boolean grabbing;
    private LivingEntity victim;
    private int throwTicksLeft;

    public MonsterGrab(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.armWindowTicks = Configs.requireInt(this.config, "arm_window_ticks");
        this.throwWindowTicks = Configs.requireInt(this.config, "throw_window_ticks");
        this.throwPrimaryLockoutTicks = Configs.requireInt(this.config, "throw_primary_lockout_ticks");
        this.throwDamage = Configs.requireDouble(this.config, "throw_damage");
        this.throwKnockback = Configs.requireDouble(this.config, "throw_knockback");
        this.throwLift = Configs.requireDouble(this.config, "throw_lift");
        this.activationSound = new SoundEffect(this.config.getConfigurationSection("visuals.activation_sound"));
        this.grabSound = new SoundEffect(this.config.getConfigurationSection("visuals.grab_sound"));
        this.throwSound = new SoundEffect(this.config.getConfigurationSection("visuals.throw_sound"));
    }

    @Override
    public boolean canExecute(Hero hero) {
        if (grabbing || armTicksLeft >= 0) return false;
        return super.canExecute(hero);
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
        LivingEntity self = hero.getController().getEntity();
        grabbing = true;
        victim = grabbed;
        throwTicksLeft = throwWindowTicks;

        GRABBED.add(grabbed.getUniqueId());
        self.addPassenger(grabbed);

        hero.setActionsLocked(true);
        Hero victimHero = HeroManager.getHero(grabbed.getUniqueId());
        if (victimHero != null) victimHero.setActionsLocked(true);

        hero.setPrimaryOverride(() -> executeThrow(hero));
        grabSound.play(self.getLocation(), hero.getController());
    }

    @Override
    protected void onTick(Hero hero) {
        if (armTicksLeft > 0) {
            armTicksLeft--;
            if (armTicksLeft == 0) armTicksLeft = -1;
        }

        if (!grabbing) return;
        LivingEntity self = hero.getController() != null ? hero.getController().getEntity() : null;

        if (self == null || victim == null || victim.isDead() || !victim.isValid()
                || !self.getPassengers().contains(victim)) {
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
        LivingEntity self = hero.getController().getEntity();
        release(hero);

        hero.suppressPrimary(throwPrimaryLockoutTicks);
        throwSound.play(self.getLocation(), hero.getController());
        Interactions.on(self, thrown)
                .abilityName(getName())
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
            LivingEntity self = hero.getController() != null ? hero.getController().getEntity() : null;
            if (self != null) self.removePassenger(held);
            Hero victimHero = HeroManager.getHero(held.getUniqueId());
            if (victimHero != null) victimHero.setActionsLocked(false);
        }
        hero.setActionsLocked(false);
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
