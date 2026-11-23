package me.stolyy.heroes.ability.kit.pochita;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.physics.Hitbox;
import me.stolyy.heroes.physics.Interactions;
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
        this.energyCostMax = me.stolyy.heroes.util.Configs.requireInt(this.config, "energy_cost_max");
        this.lowEnergyFloor = me.stolyy.heroes.util.Configs.requireDouble(this.config, "low_energy_floor");
        this.fullEnergyBonus = me.stolyy.heroes.util.Configs.requireDouble(this.config, "full_energy_bonus");
        this.activationSound = new SoundEffect(this.config.getConfigurationSection("visuals.activation_sound"));
        this.hitSound = new SoundEffect(this.config.getConfigurationSection("visuals.hit_sound"));
        this.waveVisual = new VisualEffect(this.config.getConfigurationSection("visuals.wave_visual"));
    }

    @Override
    public void perform(Hero hero) {
        LivingEntity self = hero.getController().getEntity();
        self.setVelocity(new Vector(0, 0, 0));

        if (hero.getPrimaryAbility() instanceof ChainRev rev && rev.isActive()) {
            rev.cancelRev(hero);
        }

        int consumed = Math.min((int) hero.getEnergy().getCurrentEnergy(), energyCostMax);
        if (consumed > 0) hero.getEnergy().tryConsume(hero.getController(), consumed);
        double scale = lowEnergyFloor + (1.0 - lowEnergyFloor) * (consumed / (double) energyCostMax);
        if (consumed >= energyCostMax) scale *= fullEnergyBonus;
        double scaledDamage = damage * scale;
        double scaledKnockback = knockback * scale;

        activationSound.play(hero.getController().getLocation(), hero.getController());
        waveVisual.play(hero.getController().getCenter());

        Hitbox.sphere(hero.getController().getCenter(), radius)
                .exclude(self)
                .excludeTeammates(self)
                .onHit(victim -> {
                    hitSound.play(victim.getEyeLocation(), hero.getController());
                    onHit(hero, victim);
                    Interactions.on(self, victim)
                            .abilityName(getName())
                            .damage(scaledDamage)
                            .knockback(scaledKnockback)
                            .execute();
                })
                .check();
    }
}
