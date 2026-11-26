package me.stolyy.heroes.game;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Manages Smash Crystal spawning, proximity pickup, and smash cooldown reduction inside a game. */
final class CrystalSpawner {
    private final Game game;
    private SmashCrystal activeCrystal;
    private int crystalTickCounter;

    CrystalSpawner(Game game) {
        this.game = game;
    }

    @Nullable Location activeCrystalLocation() {
        return activeCrystal != null ? activeCrystal.location() : null;
    }

    void tick(Set<Player> alivePlayers) {
        if (!game.settings().smashCrystals()) return;

        if (activeCrystal != null) {
            Set<Player> eligiblePlayers = alivePlayers.stream()
                    .filter(p -> game.aliveIds().contains(p.getUniqueId()))
                    .filter(p -> {
                        Hero hero = HeroManager.getHero(p);
                        if (hero == null || hero.getSmashAbility() == null) return false;
                        var cooldown = hero.getSmashAbility().getCooldownObject();
                        return !cooldown.isInUse() && cooldown.getTimeRemaining() > 0;
                    })
                    .collect(Collectors.toSet());

            Player consumer = activeCrystal.tick(eligiblePlayers);
            if (consumer != null) {
                onCrystalPickup(consumer);
                activeCrystal.remove();
                activeCrystal = null;
                crystalTickCounter = 0;
            }
            return;
        }

        crystalTickCounter++;
        if (crystalTickCounter >= game.settings().crystalInterval() * 20) {
            Location spawnLoc = chooseCrystalLocation(alivePlayers);
            if (spawnLoc != null) {
                activeCrystal = new SmashCrystal(spawnLoc);
            }
            crystalTickCounter = 0;
        }
    }

    void clear() {
        if (activeCrystal != null) {
            activeCrystal.remove();
            activeCrystal = null;
        }
    }

    private @Nullable Location chooseCrystalLocation(Set<Player> alivePlayers) {
        List<Location> candidates = game.settings().map().crystalLocations();
        if (candidates.isEmpty()) return null;

        Location bestLocation = null;
        double bestMinDist = -1;
        for (Location candidate : candidates) {
            double minDist = Double.MAX_VALUE;
            for (Player player : alivePlayers) {
                double dist = candidate.distanceSquared(player.getLocation());
                if (dist < minDist) minDist = dist;
            }
            if (minDist > bestMinDist) {
                bestMinDist = minDist;
                bestLocation = candidate;
            }
        }
        return bestLocation;
    }

    private void onCrystalPickup(Player player) {
        Hero hero = HeroManager.getHero(player);
        if (hero != null && hero.getSmashAbility() != null) {
            hero.getSmashAbility().getCooldownObject().reduceCooldown(hero.getSmashAbility().getCooldown());
        }
    }
}
