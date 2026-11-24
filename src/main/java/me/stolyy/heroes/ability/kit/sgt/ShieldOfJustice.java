package me.stolyy.heroes.ability.kit.sgt;

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

public class ShieldOfJustice extends BaseAbility {
    private final SoundEffect activationSound;
    private final SoundEffect windupSound;
    private final SoundEffect hitSound;
    private final VisualEffect blastVisual;

    public ShieldOfJustice(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.activationSound = SoundEffect.optional(this.config, "visuals.activation_sound");
        this.windupSound = new SoundEffect(Configs.requireSection(this.config, "visuals.windup_sound"));
        this.hitSound = new SoundEffect(Configs.requireSection(this.config, "visuals.hit_sound"));
        this.blastVisual = new VisualEffect(Configs.requireSection(this.config, "visuals.blast_visual"));
    }

    @Override
    protected long windupMs(Hero hero) {
        return hero.findAbility(Parry.class).map(Parry::isCharged).orElse(false) ? 0L : windupMs;
    }

    @Override
    protected void onWindupStart(Hero hero) {
        windupSound.play(hero.getController().getLocation(), hero.getController());
    }

    @Override
    public void perform(Hero hero) {
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
                    hit(hero, victim)
                            .damage(damage)
                            .knockback(knockback)
                            .direction(direction)
                            .execute();
                })
                .check();
    }
}
