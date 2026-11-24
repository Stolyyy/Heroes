package me.stolyy.heroes.ability.kit.bug.charms;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroBuilder;
import me.stolyy.heroes.ability.kit.minions.spooderbuddy.SpooderBuddyAI;
import me.stolyy.heroes.hero.minion.MinionHero;
import me.stolyy.heroes.util.Configs;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Weaversong extends Charm {
    private final int count;
    private final long respawnDelayMs;
    private final ConfigurationSection minionConfig;

    private final List<MinionHero> minions = new ArrayList<>();
    private final List<Long> respawnTimers = new ArrayList<>();
    private boolean pendingSpawnAfterRespawn = false;

    public Weaversong(ConfigurationSection config) {
        this.count = Configs.requireInt(config, "count");
        double delaySeconds = Configs.requireDouble(config, "respawn_delay");
        this.respawnDelayMs = (long) (delaySeconds * 1000);
        this.minionConfig = Configs.requireSection(config, "minion");
    }

    @Override
    protected void onActivate(Hero hero) {
        if (hero.isRespawning() || hero.isEliminated()) {
            pendingSpawnAfterRespawn = true;
            return;
        }
        for (int i = 0; i < count; i++) {
            spawnOne(hero);
        }
    }

    @Override
    protected void onDeactivate(Hero hero) {
        despawnAll();
        pendingSpawnAfterRespawn = false;
    }

    @Override
    public void onDeath(Hero hero) {
        if (hero.isEliminated()) {
            despawnAll();
            pendingSpawnAfterRespawn = false;
        }
    }

    @Override
    public void onOwnerLost(Hero hero) {
        despawnAll();
        pendingSpawnAfterRespawn = false;
    }

    private void despawnAll() {
        for (MinionHero minion : minions) {
            minion.despawn();
        }
        minions.clear();
        respawnTimers.clear();
    }

    @Override
    public void tick(Hero hero) {
        if (hero.isEliminated()) return;

        Iterator<MinionHero> it = minions.iterator();
        while (it.hasNext()) {
            MinionHero minion = it.next();
            LivingEntity entity = minion.getController() != null ? minion.getController().getEntity() : null;
            if (entity == null || entity.isDead()) {
                minion.despawn();
                it.remove();
                respawnTimers.add(System.currentTimeMillis() + respawnDelayMs);
            }
        }

        if (hero.isRespawning()) return;

        if (pendingSpawnAfterRespawn) {
            pendingSpawnAfterRespawn = false;
            for (int i = 0; i < count; i++) {
                spawnOne(hero);
            }
            return;
        }

        long now = System.currentTimeMillis();
        Iterator<Long> timerIt = respawnTimers.iterator();
        while (timerIt.hasNext()) {
            if (now >= timerIt.next()) {
                timerIt.remove();
                spawnOne(hero);
            }
        }
    }

    private void spawnOne(Hero hero) {
        MinionHero minion = HeroBuilder.buildMinion(minionConfig, hero);

        Location spawnLoc = hero.getController().getLocation().clone();
        double offsetX = ThreadLocalRandom.current().nextDouble(-1.0, 1.0);
        double offsetZ = ThreadLocalRandom.current().nextDouble(-1.0, 1.0);
        spawnLoc.add(offsetX, 0, offsetZ);

        minion.spawn(spawnLoc, new SpooderBuddyAI(minionConfig));

        minions.add(minion);
    }
}
