package me.stolyy.heroes.ability.kit.spooderman;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroBuilder;
import me.stolyy.heroes.hero.minion.MinionHero;
import me.stolyy.heroes.ability.kit.minions.spooderbuddy.SpooderBuddyAI;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.hero.minion.MinionManager;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class SpooderFriends extends BaseAbility {

    private record ActiveMinion(MinionHero minion, long spawnTime) {}

    private int jabCounter;
    private final int jabsPerMinion;
    private final int maxMinions;
    private final long minionDuration;
    private final ConfigurationSection minionConfig;
    private final List<ActiveMinion> activeMinions = new ArrayList<>();

    public SpooderFriends(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.jabsPerMinion = Configs.requireInt(this.config, "jabs_per_minion");
        this.maxMinions = Configs.requireInt(this.config, "max_minions");
        this.minionDuration = (long) (Configs.requireDouble(this.config, "minion_duration") * 1000);
        this.minionConfig = Configs.requireSection(this.config, "minion");
    }

    @Override
    public void perform(Hero hero) {
    }

    @Override
    public void onJabHit(Hero hero, LivingEntity victim) {
        jabCounter++;
        if (jabCounter >= jabsPerMinion) {
            jabCounter = 0;
            trySpawnBuddy(hero, hero.getController().getLocation());
        }
    }

    public boolean trySpawnBuddy(Hero hero, Location base) {
        if (MinionManager.getMinions(hero).size() >= maxMinions) return false;
        if (minionConfig == null) return false;

        MinionHero minion = HeroBuilder.buildMinion(minionConfig, hero);

        Location spawnLoc = base.clone();
        double offsetX = ThreadLocalRandom.current().nextDouble(-1.0, 1.0);
        double offsetZ = ThreadLocalRandom.current().nextDouble(-1.0, 1.0);
        spawnLoc.add(offsetX, 0, offsetZ);

        minion.spawn(spawnLoc, new SpooderBuddyAI(minionConfig));

        activeMinions.add(new ActiveMinion(minion, System.currentTimeMillis()));
        return true;
    }

    @Override
    protected void onTick(Hero hero) {
        long now = System.currentTimeMillis();
        Iterator<ActiveMinion> it = activeMinions.iterator();
        while (it.hasNext()) {
            ActiveMinion active = it.next();
            if (now - active.spawnTime() > minionDuration) {
                active.minion().despawn();
                it.remove();
            }
        }
    }

    @Override
    public boolean isIndependent() { return true; }
}
