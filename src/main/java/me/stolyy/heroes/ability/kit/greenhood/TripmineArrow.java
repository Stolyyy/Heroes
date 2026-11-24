package me.stolyy.heroes.ability.kit.greenhood;

import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.ability.template.Planted;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.physics.Hitbox;
import me.stolyy.heroes.util.Configs;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class TripmineArrow extends BaseAbility implements ArmedShot {
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
        this.timerTicks = Configs.requireInt(this.config, "tripmine_timer_ticks");
        this.mineRadius = Configs.requireDouble(this.config, "tripmine_radius");
        this.mineDamage = Configs.requireDouble(this.config, "tripmine_damage");
        this.mineKnockback = Configs.requireDouble(this.config, "tripmine_knockback");
        this.triggerDelayTicks = Configs.requireInt(this.config, "trigger_delay_ticks");
        this.minForce = Configs.requireDouble(this.config, "min_force");
        this.forceMultiplier = Configs.requireDouble(this.config, "force_multiplier");
        this.armSound = new SoundEffect(Configs.requireSection(this.config, "visuals.arm_sound"));
        this.triggerSound = new SoundEffect(Configs.requireSection(this.config, "visuals.trigger_sound"));
        this.explosionSound = new SoundEffect(Configs.requireSection(this.config, "visuals.explosion_sound"));
        this.markerVisual = new VisualEffect(Configs.requireSection(this.config, "visuals.tripmine_display"));
        this.explosionVisual = new VisualEffect(Configs.requireSection(this.config, "visuals.explosion_visual"));
    }

    @Override
    public boolean isArmed() {
        return armed;
    }

    @Override
    public void disarm() {
        armed = false;
    }

    @Override
    public void perform(Hero hero) {
        armed = true;
        ArmedShot.disarmOthers(hero, this);
        armSound.play(hero.getController().getLocation(), hero.getController());
        hero.getController().sendActionBar(Component.text("Tripmine armed — next shot", NamedTextColor.RED));
    }

    @Override
    public boolean fireFromBow(Hero hero, GreenHoodBow bow, AbstractArrow arrow, Vector aim, Vector carried,
                               double charge, int overchargeTicks, double force) {
        armed = false;
        double speed = bow.launchSpeed(force, minForce, forceMultiplier);
        bow.launch(arrow, aim, speed, carried, new ArrowShot() {
            @Override
            public void onEntityHit(Hero h, LivingEntity victim, AbstractArrow a) {
                Location contact = a.getLocation();
                a.remove();
                mines.add(new Mine(h, contact, victim));
            }

            @Override
            public void onGroundHit(Hero h, AbstractArrow a) {
                Location contact = a.getLocation();
                a.remove();
                mines.add(new Mine(h, contact, null));
            }
        });
        return true;
    }

    @Override
    protected void onTick(Hero hero) {
        for (Mine mine : new ArrayList<>(mines)) {
            if (mine.tick()) mines.remove(mine);
        }
    }

    @Override
    public boolean isIndependent() {
        return true;
    }

    @Override
    public void onOwnerLost(Hero hero) {
        for (Mine mine : mines) {
            mine.planted.cleanup();
        }
        mines.clear();
    }

    @Override
    public void onDeath(Hero hero) {
        disarm();
        super.onDeath(hero);
    }

    private class Mine {
        private final Hero owner;
        private final Planted planted;
        private int ticksLeft = timerTicks;
        private int fuseTicksLeft = -1;

        Mine(Hero owner, Location location, @Nullable LivingEntity attached) {
            this.owner = owner;
            this.planted = new Planted(location, attached, null);
            if (attached != null) this.fuseTicksLeft = triggerDelayTicks;
        }

        boolean tick() {
            LivingEntity self = owner.getController() != null ? owner.getController().getEntity() : null;
            if (self == null) return true;

            planted.sync();
            Location loc = planted.location();

            if (fuseTicksLeft >= 0) {
                markerVisual.play(loc);
                if (--fuseTicksLeft < 0) {
                    detonate(self, loc);
                    return true;
                }
                return false;
            }

            ticksLeft--;
            if (ticksLeft % 5 == 0) markerVisual.play(loc);

            boolean triggered = !Hitbox.sphere(loc, mineRadius)
                    .exclude(self)
                    .excludeTeammates(self)
                    .get().isEmpty();

            if (triggered) triggerSound.play(loc, owner.getController());

            if (triggered || ticksLeft <= 0) {
                if (triggerDelayTicks > 0) {
                    fuseTicksLeft = triggerDelayTicks;
                    return false;
                }
                detonate(self, loc);
                return true;
            }
            return false;
        }

        private void detonate(LivingEntity self, Location loc) {
            explosionSound.play(loc, owner.getController());
            explosionVisual.play(loc);
            Hitbox.sphere(loc, mineRadius)
                    .exclude(self)
                    .excludeTeammates(self)
                    .onHit(victim -> TripmineArrow.this.hit(owner, victim)
                            .damage(mineDamage)
                            .knockback(mineKnockback)
                            .fromLocation(loc)
                            .execute())
                    .check();
        }
    }
}
