package me.stolyy.heroes.ability.template;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.hero.Hero;
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

/**
 * Frustum-cone blast template supporting optional multi-tick lingering detection and ring/cone
 * particle visuals.
 */
public abstract class Cone extends BaseAbility {
    protected double range;
    // Frustum option: the cone starts this wide at the caster instead of tapering to a point —
    // point-apex cones make close-range hits feel like threading a needle. 0 = classic cone.
    protected double minRadius;
    protected double spacing;
    protected boolean piercesWalls;

    protected SoundEffect activationSound, hitSound;
    protected VisualEffect coneVisual, ringVisual, hitVisual;

    // Linger (active_duration > 0): the cone keeps checking for this many ticks, following the
    // caster's aim, with each victim hit at most once per cast (trackHits). Visuals replay once
    // per cast; visuals.replay_delay_ticks sets how many ticks after the cast (0 disables, absent
    // = the linger midpoint). The replay is deliberately decoupled from the linger hitbox: vanilla
    // particles live several ticks client-side (NOTE ~6), so a short linger's midpoint replay
    // stacked both batches into one over-dense cone — the pulse must be schedulable past the
    // first batch's fade even if the hitbox is already gone. active_duration 0 = single-tick cone.
    private Hitbox lingerHitbox;
    private Vector lingerDirection;
    private int lingerTicksLeft;
    private int replayTicksLeft;
    private final int replayDelayTicks;

    public Cone(ConfigurationSection config, HeroSkin skin) {
        this(config, skin, null);
    }

    protected Cone(ConfigurationSection config, HeroSkin skin, AbilityType explicitType) {
        super(config, skin, explicitType);
        this.range = Configs.orTemplate(this.config, "range", "cone.range");
        this.minRadius = Configs.orTemplate(this.config, "min_radius", "cone.min_radius");
        this.spacing = Configs.orTemplate(this.config, "visuals.spacing", "cone.spacing");
        if (!Double.isFinite(this.spacing) || this.spacing <= 0) {
            throw new IllegalArgumentException("Cone visuals.spacing must be > 0, got " + this.spacing);
        }
        this.piercesWalls = Configs.orTemplateBoolean(this.config, "pierces_walls", "cone.pierces_walls");
        this.replayDelayTicks = Configs.orTemplateInt(this.config, "visuals.replay_delay_ticks", "cone.replay_delay_ticks");

        this.activationSound = SoundEffect.optional(this.config, "visuals.activation_sound");
        this.hitSound = SoundEffect.optional(this.config, "visuals.hit_sound");

        this.coneVisual = VisualEffect.optional(this.config, "visuals.cone_visual");
        this.ringVisual = VisualEffect.optional(this.config, "visuals.ring_visual");
        this.hitVisual = VisualEffect.optional(this.config, "visuals.hit_visual");
    }

    @Override
    public void perform(Hero hero) {
        activationSound.play(hero.getController().getLocation(), hero.getController());
        Location start = hero.getController().getEyeLocation();
        lingerDirection = start.getDirection();
        // The onHit lambda reads the FIELD so lingering ticks knock back along the current aim.
        Hitbox hitbox = Hitbox.cone(start, lingerDirection, minRadius, radius, range)
                .exclude(hero.getController().getEntity())
                .checkWalls(!piercesWalls)
                .trackHits()
                .onHit((victim) -> onHit(hero, victim, lingerDirection));
        hitbox.excludeTeammates(hero.getController().getEntity());
        hitbox.check();
        displayVisuals(hero, start, lingerDirection);

        int totalTicks = (int) (activeDuration / Heroes.MS_PER_TICK);
        if (totalTicks > 1) {
            lingerHitbox = hitbox;
            lingerTicksLeft = totalTicks - 1; // this tick already checked
            replayTicksLeft = replayDelayTicks < 0 ? totalTicks - (totalTicks / 2) : replayDelayTicks;
        }
    }

    @Override
    protected final void onTick(Hero hero) {
        if (lingerHitbox != null || replayTicksLeft > 0) {
            if (hero.getController() == null || hero.getController().getEntity() == null) {
                clearLinger();
            } else {
                Location eye = hero.getController().getEyeLocation();
                if (lingerHitbox != null) {
                    lingerDirection = eye.getDirection();
                    lingerHitbox.moveTo(eye).reorient(lingerDirection).check();
                    if (--lingerTicksLeft <= 0) {
                        lingerHitbox = null;
                        lingerTicksLeft = 0;
                    }
                }
                // The replay may outlive the linger hitbox (see field comment) — it follows current aim.
                if (replayTicksLeft > 0 && --replayTicksLeft == 0) {
                    displayVisuals(hero, eye, eye.getDirection());
                }
            }
        }
        onConeTick(hero);
    }

    protected void onConeTick(Hero hero) {}

    private void clearLinger() {
        lingerHitbox = null;
        lingerTicksLeft = 0;
        replayTicksLeft = 0;
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        clearLinger();
    }

    protected void displayVisuals(Hero hero, Location start, Vector direction) {
        // Cone Visual loop — same frustum taper as the hitbox, so what you see is what hits.
        for (double d = spacing; d <= range; d += spacing) {
            double r = minRadius + (d / range) * (radius - minRadius);
            Location loc = start.clone().add(direction.clone().multiply(d));
            coneVisual.play(loc, direction, r, hero.getSkin().getId());
        }
        
        // Ring at end
        Location endLoc = start.clone().add(direction.clone().multiply(range));
        ringVisual.play(endLoc, direction, radius, hero.getSkin().getId());
    }

    protected void applyInteraction(Hero hero, LivingEntity victim, Vector direction) {
        Interactions.InteractionBuilder interaction = hit(hero, victim);
        double dmg = calcDamage(hero, victim);
        double kb = calcKnockback(hero, victim);
        interaction.damage(dmg).knockback(kb).direction(direction).execute();
    }

    protected void onHit(Hero hero, LivingEntity victim, Vector direction) {
        hitSound.play(victim.getEyeLocation(), hero.getController());
        hitVisual.play(victim.getEyeLocation());
        applyInteraction(hero, victim, direction);
    }
}