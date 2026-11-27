package me.stolyy.heroes.ability.kit.voidcrawler;

import me.stolyy.heroes.ability.template.Hitscan;
import me.stolyy.heroes.game.PracticeManager;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.physics.WallDetection;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

public class VoidTeleboom extends Hitscan {
    private final double distanceToTeleportLocation;
    private final VisualEffect boomVisual;

    public VoidTeleboom(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.distanceToTeleportLocation = Configs.requireDouble(this.config, "distance_to_teleport_spot");
        this.boomVisual = new VisualEffect(Configs.requireSection(this.config, "boom_visual"));
    }

    @Override
    protected void onImpact(Hero hero, Location location, @Nullable LivingEntity victim, ImpactCause cause) {
        teleport(hero, location);
        boomVisual.play(hero.getController().getLocation());
    }

    private void teleport(Hero attacker, Location location) {
        Location playerLoc = attacker.getController().getLocation();
        Vector direction = playerLoc.getDirection().normalize();
        Location teleportLocation = location.clone().subtract(direction.multiply(distanceToTeleportLocation));
        teleportLocation.setYaw(playerLoc.getYaw());
        teleportLocation.setPitch(playerLoc.getPitch());
        if (!PracticeManager.isWithinPracticeBounds(attacker, teleportLocation)) {
            teleportLocation = playerLoc.clone();
        }
        WallDetection.safeTeleport(attacker.getController(), teleportLocation);
    }
}
