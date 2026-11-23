package me.stolyy.heroes.ability.kit.karakot;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public class KiBlast extends Projectile {
    private boolean isActive;
    private final double maxAngle;
    private boolean isLeftShot;
    private long elapsed;

    public KiBlast(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.maxAngle = config.getDouble("max_angle", 5.0);
    }

    @Override
    public void perform(Hero hero) {
        isLeftShot = true;
        fireShot(hero);
        isLeftShot = false;

        elapsed = 0;
        isActive = true;
    }

    @Override
    protected void onTick(Hero hero) {
        super.onTick(hero);

        if(!isActive) return;

        elapsed++;
        if(elapsed * 50L >= cooldown.getCooldownDuration() / 2){
            fireShot(hero);
            isActive = false;
        }
    }

    private void fireShot(Hero hero) {
        super.perform(hero);
    }

    @Override
    protected Vector getDirection(Location location) {
        Vector direction = super.getDirection(location);
        if (maxAngle <= 0) return direction;

        double angleRad = ThreadLocalRandom.current().nextDouble() * Math.toRadians(maxAngle);
        if (!isLeftShot) angleRad = -angleRad;

        Vector up = new Vector(0, 1, 0);
        return direction.clone().rotateAroundAxis(up, angleRad).normalize();
    }
}
