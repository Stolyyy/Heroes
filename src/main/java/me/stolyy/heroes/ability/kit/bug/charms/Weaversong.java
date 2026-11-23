package me.stolyy.heroes.ability.kit.bug.charms;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroBuilder;
import me.stolyy.heroes.ability.kit.minions.spooderbuddy.SpooderBuddyAI;
import me.stolyy.heroes.hero.minion.MinionHero;
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

    public Weaversong(ConfigurationSection config) {
        this.count = config != null ? config.getInt("count", 2) : 2;
        double delaySeconds = config != null ? config.getDouble("respawn_delay", 10.0) : 10.0;
        this.respawnDelayMs = (long) (delaySeconds * 1000);
        this.minionConfig = config != null ? config.getConfigurationSection("minion") : null;
    }

    @Override
    protected void onActivate(Hero hero) {
        if (minionConfig == null) return;
        for (int i = 0; i < count; i++) {
            spawnOne(hero);
        }
    }

    @Override
    protected void onDeactivate(Hero hero) {
        for (MinionHero minion : minions) {
            minion.despawn();
        }
        minions.clear();
        respawnTimers.clear();
    }

    @Override
    public void tick(Hero hero) {
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
        if (minionConfig == null) return;
        MinionHero minion = HeroBuilder.buildMinion(minionConfig, hero);

        Location spawnLoc = hero.getController().getLocation().clone();
        double offsetX = ThreadLocalRandom.current().nextDouble(-1.0, 1.0);
        double offsetZ = ThreadLocalRandom.current().nextDouble(-1.0, 1.0);
        spawnLoc.add(offsetX, 0, offsetZ);

        minion.spawn(spawnLoc, new SpooderBuddyAI(minionConfig));

        minions.add(minion);
    }
}
