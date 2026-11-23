package me.stolyy.heroes.ability.kit.spooderman;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Hitscan;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.physics.Interactions;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

public class SpooderWeb extends Hitscan {
    private final double movementMultiplier;
    private final long missPenaltyMs;

    private Vector lastFireDirection = null;
    private boolean hitLandedThisShot;

    public SpooderWeb(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.movementMultiplier = config.getDouble("movement_multiplier");
        this.missPenaltyMs = config.getLong("miss_penalty_ms");
    }

    @Override
    public void perform(Hero hero) {
        hitLandedThisShot = false;
        super.perform(hero);
        if (!hitLandedThisShot && !ultWaivesPenalty(hero)) {
            cooldown.penalize(missPenaltyMs);
        }
    }

    private boolean ultWaivesPenalty(Hero hero) {
        return hero.getSmashAbility() instanceof CaveSpooderman ult && ult.isActive();
    }

    @Override
    protected Vector getDirection(Location location) {
        Vector dir = super.getDirection(location);
        lastFireDirection = dir.clone().normalize();
        return dir;
    }

    @Override
    protected void applyInteraction(Hero attacker, LivingEntity victim, Location hitLoc) {
        hitLandedThisShot = true;
        double dmg = calcDamage(attacker, victim);
        double kb = calcKnockback(attacker, victim);
        Vector pullDir = lastFireDirection != null ? lastFireDirection.clone().multiply(-1) : null;
        Interactions.on(attacker.getController().getEntity(), victim)
                .abilityName(getName())
                .damage(dmg)
                .knockback(kb)
                .direction(pullDir)
                .processDirection(false)
                .execute();
    }

    @Override
    public void onHitWall(Hero hero, Location location) {
        super.onHitWall(hero, location);
        Location playerLoc = hero.getController().getLocation();
        Vector toWall = location.toVector().subtract(playerLoc.toVector());
        if (toWall.lengthSquared() > 0.0001) toWall.normalize();
        hero.getController().getEntity().setVelocity(toWall.multiply(movementMultiplier));
    }
}
