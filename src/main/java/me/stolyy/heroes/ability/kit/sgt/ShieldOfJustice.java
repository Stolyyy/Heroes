package me.stolyy.heroes.ability.kit.sgt;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.physics.Hitbox;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

public class ShieldOfJustice extends BaseAbility {
    private final int windupTicks;
    private final SoundEffect activationSound;
    private final SoundEffect windupSound;
    private final SoundEffect hitSound;
    private final VisualEffect blastVisual;

    private int windupRemaining = -1;

    public ShieldOfJustice(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.windupTicks = Configs.requireInt(this.config, "windup_ticks");
        this.activationSound = new SoundEffect(this.config.getConfigurationSection("visuals.activation_sound"));
        this.windupSound = new SoundEffect(this.config.getConfigurationSection("visuals.windup_sound"));
        this.hitSound = new SoundEffect(this.config.getConfigurationSection("visuals.hit_sound"));
        this.blastVisual = new VisualEffect(this.config.getConfigurationSection("visuals.blast_visual"));
    }

    @Override
    public void perform(Hero hero) {
        Parry parry = Parry.of(hero);
        if (parry != null && parry.isCharged()) {
            blast(hero);
            return;
        }
        windupRemaining = windupTicks;
        windupSound.play(hero.getController().getLocation(), hero.getController());
    }

    @Override
    protected void onTick(Hero hero) {
        if (windupRemaining > 0) {
            windupRemaining--;
        } else if (windupRemaining == 0) {
            windupRemaining = -1;
            blast(hero);
        }
    }

    private void blast(Hero hero) {
        LivingEntity self = hero.getController().getEntity();
        activationSound.play(hero.getController().getLocation(), hero.getController());
        blastVisual.play(hero.getController().getCenter());

        Hitbox.sphere(hero.getController().getCenter(), radius)
                .exclude(self)
                .excludeTeammates(self)
                .onHit(victim -> {
                    hitSound.play(victim.getEyeLocation(), hero.getController());
                    Vector direction = victim.getBoundingBox().getCenter()
                            .subtract(self.getBoundingBox().getCenter());
                    Interactions.on(self, victim)
                            .abilityName(getName())
                            .damage(damage)
                            .knockback(knockback)
                            .direction(direction)
                            .execute();
                    onHit(hero, victim);
                })
                .check();
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        windupRemaining = -1;
    }
}
