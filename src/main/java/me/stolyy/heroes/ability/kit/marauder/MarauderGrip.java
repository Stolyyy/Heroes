package me.stolyy.heroes.ability.kit.marauder;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Hitscan;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.physics.Stun;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

public class MarauderGrip extends Hitscan {
    private final Stun stun;

    public MarauderGrip(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        long stunDuration = (long) (config.getDouble("stun_duration") * 1000);
        this.stun = new Stun(stunDuration, this::pull);
    }

    @Override
    protected void applyInteraction(Hero attacker, LivingEntity victim, Location hitLoc) {
        Interactions.on(attacker.getController().getEntity(), victim)
            .abilityName(getName())
            .damage(calcDamage(attacker, victim))
            .execute();
        stun.apply(attacker, victim);
    }

    @Override
    public void onHit(Hero attacker, LivingEntity victim, Location hitLoc) {
        hitSound.play(attacker.getController().getLocation(), attacker.getController());
        hitVisual.play(victim.getBoundingBox().getCenter().toLocation(victim.getWorld()));
        attacker.triggerAbilityHit(this, victim);
        applyInteraction(attacker, victim, hitLoc);
    }

    @Override
    protected void onTick(Hero hero) {
        stun.tick(hero);
    }

    private void pull(Hero attacker, LivingEntity victim) {
        Interactions.on(attacker.getController().getEntity(), victim).abilityName(getName()).knockback(knockback).staticKnockback().execute();
    }
}
