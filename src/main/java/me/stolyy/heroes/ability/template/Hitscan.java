package me.stolyy.heroes.ability.template;

import me.stolyy.heroes.game.PracticeManager;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.control.Controller;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.physics.Hitbox;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public abstract class Hitscan extends RangedAbility {

    public Hitscan(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
    }

    @Override
    protected void fireSingleShot(Hero hero) {
        Controller controller = hero.getController();
        LivingEntity shooter = controller.getEntity();
        Location start = getStartLocation(hero);
        Vector direction = getDirection(start);

        fireSound.play(controller.getLocation(), controller);

        // A practicing owner's ray stops at the arena wall instead of reaching across the lobby.
        double reach = shooter instanceof Player owner && PracticeManager.isPracticing(owner)
                ? PracticeManager.arenaOf(owner).clampRange(start, direction.clone().normalize(), range)
                : range;

        Hitbox hitbox = Hitbox.cylinder(start, direction, Math.max(0.1, radius), reach)
                .exclude(shooter)
                .checkWalls(!piercesWalls)
                .pierce(piercesPlayers)
                .onHit((victim, loc) -> {
                    lastHitDistance = start.distance(loc);
                    currentDirection = direction.clone().normalize();
                    onHit(hero, victim, loc);
                })
                .onHitWall(loc -> onHitWall(hero, loc))
                .onExpire(loc -> onExpire(hero, loc));

        hitbox.excludeTeammates(shooter);

        // Set before the check, not only inside the hit lambda: RangedAbility.onHitWall now reads
        // currentDirection to give Aoe a real trajectory, and a shot that hits a WALL never runs
        // the hit lambda — so without this it would resolve against the previous shot's heading.
        currentDirection = direction.clone().normalize();
        hitbox.check();

        double finalDist = hitbox.getWallHitLocation() != null ? start.distance(hitbox.getWallHitLocation()) : reach;
        displayTrail(hero, start, direction, finalDist);
    }

    protected void displayTrail(Hero hero, Location start, Vector direction, double distance) {
        Vector dir = direction.clone().normalize();
        for (double d = 0; d <= distance; d += trailSpacing) {
            Location loc = start.clone().add(dir.clone().multiply(d));
            trailVisual.play(loc, dir, hero.getSkin().getId());
        }
    }
}
