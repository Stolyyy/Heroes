package me.stolyy.heroes.ability.kit.marauder;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Hitscan;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.physics.Stun;
import me.stolyy.heroes.util.Configs;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

public class MarauderGrip extends Hitscan {
    private final Stun stun;

    public MarauderGrip(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        long stunDuration = (long) (Configs.requireDouble(this.config, "stun_duration") * 1000);
        this.stun = new Stun(stunDuration, this::pull);
    }

    @Override
    protected void playHitEffects(Hero attacker, LivingEntity victim, Location hitLoc) {
        hitSound.play(attacker.getController().getLocation(), attacker.getController());
        hitVisual.play(victim.getBoundingBox().getCenter().toLocation(victim.getWorld()));
    }

    @Override
    protected void applyInteraction(Hero attacker, LivingEntity victim, Location hitLoc) {
        hit(attacker, victim)
                .damage(calcDamage(attacker, victim))
                .execute();
        stun.apply(victim);
    }

    @Override
    protected void afterHit(Hero attacker, LivingEntity victim, Location hitLoc) {}

    @Override
    protected void onRangedTick(Hero hero) {
        stun.tick(hero);
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        stun.releaseAll();
    }

    @Override
    public void onOwnerLost(Hero hero) {
        super.onOwnerLost(hero);
        stun.releaseAll();
    }

    private void pull(Hero attacker, LivingEntity victim) {
        Interactions.on(attacker.getController().getEntity(), victim).abilityName(getName()).knockback(knockback).staticKnockback().execute();
    }
}
