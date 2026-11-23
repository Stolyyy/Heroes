package me.stolyy.heroes.ability.kit.voidcrawler;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Hitscan;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

public class VoidTeleboom extends Hitscan {
    private final double distanceToTeleportLocation;
    private final VisualEffect boomVisual;

    public VoidTeleboom(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.distanceToTeleportLocation = config.getDouble("distance_to_teleport_spot");
        this.boomVisual = new VisualEffect(config.getConfigurationSection("boom_visual"));
    }

    @Override
    public void onHit(Hero attacker, LivingEntity victim, Location hitLoc) {
        super.onHit(attacker, victim, hitLoc);
        teleport(attacker, hitLoc);
        boomVisual.play(attacker.getController().getLocation());
    }

    @Override
    public void onHitWall(Hero hero, Location location) {
        super.onHitWall(hero, location);
        teleport(hero, location);
        boomVisual.play(hero.getController().getLocation());
    }

    @Override
    public void onExpire(Hero hero, Location location) {
        super.onExpire(hero, location);
        teleport(hero, location);
        boomVisual.play(hero.getController().getLocation());
    }

    private void teleport(Hero attacker, Location location) {
        Location playerLoc = attacker.getController().getLocation();
        Vector direction = playerLoc.getDirection().normalize();
        Location teleportLocation = location.clone().subtract(direction.multiply(distanceToTeleportLocation));
        teleportLocation.setYaw(playerLoc.getYaw());
        teleportLocation.setPitch(playerLoc.getPitch());
        attacker.getController().getEntity().teleport(teleportLocation);
    }

}
