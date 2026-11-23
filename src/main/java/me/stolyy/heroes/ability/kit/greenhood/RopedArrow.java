package me.stolyy.heroes.ability.kit.greenhood;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.listener.GreenHoodListener;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class RopedArrow extends BaseAbility {
    private final double speed;
    private final double pullStrength;
    private final int pullTicks;
    private final SoundEffect fireSound;
    private final SoundEffect contactSound;

    private Location pullTarget;
    private int pullRemaining;

    public RopedArrow(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.speed = this.config.getDouble("speed");
        this.pullStrength = this.config.getDouble("pull_strength");
        this.pullTicks = this.config.getInt("pull_ticks");
        this.fireSound = new SoundEffect(this.config.getConfigurationSection("visuals.fire_sound"));
        this.contactSound = new SoundEffect(this.config.getConfigurationSection("visuals.contact_sound"));
    }

    @Override
    public void perform(Hero hero) {
        if (!(hero.getController().getEntity() instanceof Player shooter)) return;
        fireSound.play(shooter.getLocation(), hero.getController());
        AbstractArrow arrow = shooter.launchProjectile(org.bukkit.entity.Arrow.class,
                shooter.getEyeLocation().getDirection().multiply(speed));
        arrow.setCritical(false);
        GreenHoodListener.tagArrow(arrow, "roped", 0);
    }

    public void onContact(Hero hero, Location hitLocation) {
        contactSound.play(hitLocation, hero.getController());
        pullTarget = hitLocation.clone();
        pullRemaining = pullTicks;
    }

    @Override
    protected void onTick(Hero hero) {
        if (pullRemaining <= 0) return;
        pullRemaining--;

        LivingEntity self = hero.getController().getEntity();
        if (self == null) {
            pullRemaining = 0;
            return;
        }
        Vector diff = pullTarget.toVector().subtract(self.getLocation().toVector());
        if (diff.length() < 1.5) {
            pullRemaining = 0;
            return;
        }
        self.setVelocity(diff.normalize().multiply(pullStrength));
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        pullRemaining = 0;
        pullTarget = null;
    }
}
