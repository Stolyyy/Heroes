package me.stolyy.heroes.ability.kit.greenhood;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.physics.Hitbox;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DeathsGrasp extends BaseAbility {
    private final double launchStrength;
    private final int hitboxTicks;
    private final double liftKnockback;

    private final SoundEffect activationSound;
    private final SoundEffect hitSound;
    private final VisualEffect trailVisual;

    private int activeTicksLeft;
    private Location previousCenter;
    private final Set<UUID> hitVictims = new HashSet<>();

    public DeathsGrasp(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.launchStrength = Configs.requireDouble(this.config, "launch_strength");
        this.hitboxTicks = Configs.requireInt(this.config, "hitbox_ticks");
        this.liftKnockback = Configs.requireDouble(this.config, "lift_knockback");
        this.activationSound = new SoundEffect(this.config.getConfigurationSection("visuals.activation_sound"));
        this.hitSound = new SoundEffect(this.config.getConfigurationSection("visuals.hit_sound"));
        this.trailVisual = new VisualEffect(this.config.getConfigurationSection("visuals.trail_visual"));
    }

    @Override
    public boolean canExecute(Hero hero) {
        if (activeTicksLeft > 0) return false;
        return super.canExecute(hero);
    }

    @Override
    public void perform(Hero hero) {
        activationSound.play(hero.getController().getLocation(), hero.getController());

        Vector direction = hero.getController().getLocation().getDirection();
        hero.getController().setVelocity(direction.multiply(launchStrength));
        activeTicksLeft = hitboxTicks;
        previousCenter = hero.getController().getCenter();
        hitVictims.clear();
    }

    @Override
    protected void onTick(Hero hero) {
        if (activeTicksLeft <= 0) return;
        LivingEntity self = hero.getController() != null ? hero.getController().getEntity() : null;
        if (self == null) {
            activeTicksLeft = 0;
            return;
        }
        activeTicksLeft--;

        Location center = hero.getController().getCenter();
        Vector delta = center.toVector().subtract(previousCenter.toVector());
        double travelled = delta.length();
        Vector segmentDir = travelled > 1.0E-6 ? delta.clone().multiply(1 / travelled)
                : hero.getController().getLocation().getDirection();

        trailVisual.play(center);
        Hitbox.box(previousCenter, segmentDir, radius * 2, radius * 2, travelled + radius)
                .exclude(self)
                .excludeTeammates(self)
                .filter(victim -> !hitVictims.contains(victim.getUniqueId()))
                .onHit(victim -> {
                    hitVictims.add(victim.getUniqueId());
                    hitSound.play(victim.getEyeLocation(), hero.getController());
                    hero.triggerAbilityHit(this, victim);
                    Interactions.on(self, victim)
                            .abilityName(getName())
                            .damage(damage)
                            .knockback(knockback)
                            .verticalKnockback(liftKnockback)
                            .direction(segmentDir.clone().multiply(-1))
                            .execute();
                })
                .check();
        previousCenter = center;
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        activeTicksLeft = 0;
        hitVictims.clear();
    }
}
