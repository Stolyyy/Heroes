package me.stolyy.heroes.ability.kit.spooderman;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Dash;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.physics.Interactions;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

public class SpooderDash extends Dash {
    private final double angle;

    public SpooderDash(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.angle = config.getDouble("kb_angle");
    }

    @Override
    protected void applyInteraction(Hero hero, LivingEntity victim, Vector dashDir) {
        Interactions.on(hero.getController().getEntity(), victim)
            .abilityName(getName())
            .damage(calcDamage(hero, victim))
            .knockback(calcKnockback(hero, victim))
            .pitch(angle)
            .direction(dashDir)
            .execute();
    }

    @Override
    protected void onHit(Hero hero, LivingEntity victim, Vector dashDir) {
        hitSound.play(victim.getLocation());
        applyInteraction(hero, victim, dashDir);
        hero.triggerAbilityHit(this, victim);
    }
}
