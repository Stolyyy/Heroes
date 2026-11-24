package me.stolyy.heroes.ability.kit.pochita;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.physics.Hitbox;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

public class ChainSlash extends BaseAbility {
    private final int energyCostMax;
    private final double lowEnergyFloor;
    private final double fullEnergyBonus;
    private final SoundEffect activationSound;
    private final SoundEffect hitSound;
    private final VisualEffect waveVisual;

    public ChainSlash(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.energyCostMax = Configs.requireInt(this.config, "energy_cost_max");
        this.lowEnergyFloor = Configs.requireDouble(this.config, "low_energy_floor");
        this.fullEnergyBonus = Configs.requireDouble(this.config, "full_energy_bonus");
        this.activationSound = new SoundEffect(Configs.requireSection(this.config, "visuals.activation_sound"));
        this.hitSound = new SoundEffect(Configs.requireSection(this.config, "visuals.hit_sound"));
        this.waveVisual = new VisualEffect(Configs.requireSection(this.config, "visuals.wave_visual"));
    }

    @Override
    public void perform(Hero hero) {
        hero.getController().setVelocity(new Vector(0, 0, 0));

        hero.findAbility(ChainRev.class)
                .filter(ChainRev::isActive)
                .ifPresent(rev -> rev.cancelRev(hero));

        int consumed = Math.min((int) hero.getEnergy().getCurrentEnergy(), energyCostMax);
        if (consumed > 0) hero.getEnergy().tryConsume(hero.getController(), consumed);
        double scale = lowEnergyFloor + (1.0 - lowEnergyFloor) * (consumed / (double) energyCostMax);
        if (consumed >= energyCostMax) scale *= fullEnergyBonus;
        double scaledDamage = damage * scale;
        double scaledKnockback = knockback * scale;

        activationSound.play(hero.getController().getLocation(), hero.getController());
        waveVisual.play(hero.getController().getCenter());

        LivingEntity self = hero.getController().getEntity();
        Hitbox.sphere(hero.getController().getCenter(), radius)
                .exclude(self)
                .excludeTeammates(self)
                .onHit(victim -> {
                    hitSound.play(victim.getEyeLocation(), hero.getController());
                    hit(hero, victim)
                            .damage(scaledDamage)
                            .knockback(scaledKnockback)
                            .execute();
                })
                .check();
    }
}
