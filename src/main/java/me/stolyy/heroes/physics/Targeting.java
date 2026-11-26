package me.stolyy.heroes.physics;

import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.hero.minion.MinionHero;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/** Enemy target queries scoped to active games and targetability rules. */
public final class Targeting {

    private Targeting() {}

    public static List<LivingEntity> enemies(LivingEntity self, boolean includeMinions) {
        return GameManager.getEnemies(self).stream()
                .filter(e -> GameManager.canTarget(self, e))
                .filter(e -> includeMinions || !(HeroManager.getHero(e) instanceof MinionHero))
                .toList();
    }

    public static @Nullable LivingEntity nearestEnemy(LivingEntity self, Location from, boolean includeMinions) {
        return enemies(self, includeMinions).stream()
                .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(from)))
                .orElse(null);
    }
}
