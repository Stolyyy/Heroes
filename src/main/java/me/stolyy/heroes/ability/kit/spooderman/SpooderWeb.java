package me.stolyy.heroes.ability.kit.spooderman;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Hitscan;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

public class SpooderWeb extends Hitscan {
    private final double movementMultiplier;
    private final long missPenaltyMs;

    private boolean hitLandedThisShot;

    public SpooderWeb(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.movementMultiplier = Configs.requireDouble(this.config, "movement_multiplier");
        this.missPenaltyMs = Configs.requireLong(this.config, "miss_penalty_ms");
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
        return hero.findAbility(CaveSpooderman.class).map(CaveSpooderman::isActive).orElse(false);
    }

    @Override
    protected void afterHit(Hero attacker, LivingEntity victim, Location hitLoc) {
        hitLandedThisShot = true;
        super.afterHit(attacker, victim, hitLoc);
    }

    @Override
    public void onHitWall(Hero hero, Location location) {
        super.onHitWall(hero, location);
        Location playerLoc = hero.getController().getLocation();
        Vector toWall = location.toVector().subtract(playerLoc.toVector());
        if (toWall.lengthSquared() > 0.0001) toWall.normalize();
        hero.getController().setVelocity(toWall.multiply(movementMultiplier));
    }
}
