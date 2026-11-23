package me.stolyy.heroes.ability.kit.greenhood;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.listener.GreenHoodListener;
import me.stolyy.heroes.physics.Hitbox;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;


public class TripmineArrow extends BaseAbility {
    private final int timerTicks;
    private final double mineRadius;
    private final double mineDamage;
    private final double mineKnockback;

    private final int triggerDelayTicks;
    private final double minForce;
    private final double forceMultiplier;
    private final SoundEffect armSound;
    private final SoundEffect triggerSound;
    private final SoundEffect explosionSound;
    private final VisualEffect markerVisual;
    private final VisualEffect explosionVisual;

    private boolean armed;
    private final List<Mine> mines = new ArrayList<>();

    public TripmineArrow(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.timerTicks = this.config.getInt("tripmine_timer_ticks");
        this.mineRadius = this.config.getDouble("tripmine_radius");
        this.mineDamage = this.config.getDouble("tripmine_damage");
        this.mineKnockback = this.config.getDouble("tripmine_knockback");
        this.triggerDelayTicks = me.stolyy.heroes.util.Configs.requireInt(this.config, "trigger_delay_ticks");
        this.minForce = me.stolyy.heroes.util.Configs.requireDouble(this.config, "min_force");
        this.forceMultiplier = me.stolyy.heroes.util.Configs.requireDouble(this.config, "force_multiplier");
        this.armSound = new SoundEffect(this.config.getConfigurationSection("visuals.arm_sound"));
        this.triggerSound = new SoundEffect(this.config.getConfigurationSection("visuals.trigger_sound"));
        this.explosionSound = new SoundEffect(this.config.getConfigurationSection("visuals.explosion_sound"));
        this.markerVisual = new VisualEffect(this.config.getConfigurationSection("visuals.tripmine_display"));
        this.explosionVisual = new VisualEffect(this.config.getConfigurationSection("visuals.explosion_visual"));
    }

    @Override
    public void perform(Hero hero) {
        armed = true;
        GreenHoodListener.disarmOthers(hero, this);
        armSound.play(hero.getController().getLocation(), hero.getController());
        hero.getController().sendActionBar(Component.text("Tripmine armed — next shot", NamedTextColor.RED));
    }

    public boolean isArmed() {
        return armed;
    }

    public double minForce() { return minForce; }
    public double forceMultiplier() { return forceMultiplier; }

    public void disarm() {
        armed = false;
    }

    /** Takes over the bow's own arrow from the shoot event; see {@link GreenHoodListener#onShoot}. */
    public void fire(AbstractArrow arrow, Vector aim, Vector carried, double speed) {
        armed = false;
        GreenHoodListener.aim(arrow, aim, speed, carried, "tripmine", 0);
    }

    public void plant(Hero hero, Location location, AbstractArrow arrow) {
        plant(hero, location, arrow, null);
    }

    public void plant(Hero hero, Location location, AbstractArrow arrow, LivingEntity victim) {
        arrow.remove();
        mines.add(new Mine(hero, location.clone(), victim));
    }

    @Override
    protected void onTick(Hero hero) {
        mines.removeIf(Mine::tick);
    }

    @Override
    public boolean isIndependent() {
        return true;
    }

    @Override
    public void onOwnerLost(Hero hero) {
        mines.clear();
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        armed = false;
    }

    private class Mine {
        private final Hero owner;
        private final Location location;

        private final LivingEntity attached;
        private final Vector offset;
        private int ticksLeft = timerTicks;

        private int fuseTicksLeft = -1;

        Mine(Hero owner, Location location, LivingEntity attached) {
            this.owner = owner;
            this.location = location;
            this.attached = attached;
            this.offset = attached != null
                    ? location.toVector().subtract(attached.getLocation().toVector())
                    : null;
            if (attached != null) this.fuseTicksLeft = triggerDelayTicks;
        }

        boolean tick() {
            LivingEntity self = owner.getController() != null ? owner.getController().getEntity() : null;
            if (self == null) return true;

            if (attached != null && attached.isValid()) {
                Location live = attached.getLocation().add(offset);
                location.setX(live.getX());
                location.setY(live.getY());
                location.setZ(live.getZ());
            }

            if (fuseTicksLeft >= 0) {
                markerVisual.play(location);
                if (--fuseTicksLeft < 0) {
                    detonate(self);
                    return true;
                }
                return false;
            }

            ticksLeft--;
            if (ticksLeft % 5 == 0) markerVisual.play(location);

            boolean triggered = !Hitbox.sphere(location, mineRadius)
                    .exclude(self)
                    .excludeTeammates(self)
                    .get().isEmpty();

            if (triggered) triggerSound.play(location, owner.getController());

            if (triggered || ticksLeft <= 0) {
                if (triggerDelayTicks > 0) {
                    fuseTicksLeft = triggerDelayTicks;
                    return false;
                }
                detonate(self);
                return true;
            }
            return false;
        }

        private void detonate(LivingEntity self) {
            explosionSound.play(location, owner.getController());
            explosionVisual.play(location);
            Hitbox.sphere(location, mineRadius)
                    .exclude(self)
                    .excludeTeammates(self)
                    .onHit(victim -> {
                        owner.triggerAbilityHit(TripmineArrow.this, victim);
                        Interactions.on(self, victim)
                                .abilityName(TripmineArrow.this.getName())
                                .damage(mineDamage)
                                .knockback(mineKnockback)
                                .fromLocation(location)
                                .execute();
                    })
                    .check();
        }
    }
}
