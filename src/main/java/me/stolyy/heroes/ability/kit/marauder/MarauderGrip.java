package me.stolyy.heroes.ability.kit.marauder;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.mechanics.StatusEffects;
import me.stolyy.heroes.ability.template.Hitscan;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.physics.Interactions;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

public class MarauderGrip extends Hitscan {
    private final StatusEffects.Spec status;

    public MarauderGrip(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.status = StatusEffects.Spec.fromConfig(this.config);
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
        StatusEffects.applyTo(victim, status, this, attacker, () -> pull(attacker, victim));
    }

    @Override
    protected void afterHit(Hero attacker, LivingEntity victim, Location hitLoc) {}

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        StatusEffects.removeAllFrom(this);
    }

    @Override
    public void onOwnerLost(Hero hero) {
        super.onOwnerLost(hero);
        StatusEffects.removeAllFrom(this);
    }

    private void pull(Hero attacker, LivingEntity victim) {
        Interactions.on(attacker.getController().getEntity(), victim).abilityName(getName()).knockback(knockback).staticKnockback().execute();
    }
}
