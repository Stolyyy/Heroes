package me.stolyy.heroes.ability.kit.marauder;

import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

public class MarauderSaberThrow extends Projectile {
    private ProjectileInstance activeSaber;

    public MarauderSaberThrow(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
    }

    @Override
    protected boolean canRecast(Hero hero) {
        return activeSaber != null && !activeSaber.isReturning();
    }

    @Override
    protected boolean tryRecast(Hero hero) {
        if (activeSaber != null && !activeSaber.isReturning()) {
            activeSaber.startReturn();
            return true;
        }
        return false;
    }

    @Override
    protected ProjectileInstance createInstance(Hero hero, Location start, ShotParams shot) {
        activeSaber = new SaberInstance(hero, start, shot);
        return activeSaber;
    }

    @Override
    protected void onInstanceEnded(Hero hero, ProjectileInstance instance) {
        if (instance == activeSaber) {
            activeSaber = null;
        }
    }

    private class SaberInstance extends ProjectileInstance {
        SaberInstance(Hero hero, Location start, ShotParams shot) {
            super(hero, start, shot);
        }

        @Override
        protected boolean returnPiercesWalls() {
            return true;
        }
    }
}
