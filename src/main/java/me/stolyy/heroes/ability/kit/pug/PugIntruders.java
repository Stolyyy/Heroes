package me.stolyy.heroes.ability.kit.pug;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroBuilder;
import me.stolyy.heroes.hero.minion.MinionHero;
import me.stolyy.heroes.ability.template.ActiveAbility;
import me.stolyy.heroes.control.ai.BotAI;
import me.stolyy.heroes.control.ai.MinionBotAI;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public class PugIntruders extends ActiveAbility {
    private final List<MinionHero> summonedMinions = new ArrayList<>();

    private final ConfigurationSection basherConfig;
    private final ConfigurationSection princessConfig;

    public PugIntruders(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.basherConfig = Configs.requireSection(this.config, "basher");
        this.princessConfig = Configs.requireSection(this.config, "princess");
    }

    @Override
    protected void onActivate(Hero hero) {
        Location heroLoc = hero.getController().getLocation();
        spawnMinion(hero, basherConfig, new MinionBotAI(basherConfig), heroLoc.clone().add(1, 0, 0));
        spawnMinion(hero, princessConfig, new MinionBotAI(princessConfig), heroLoc.clone().add(-1, 0, 0));
    }

    private void spawnMinion(Hero hero, ConfigurationSection minionConfig, BotAI ai, Location spawnLoc) {
        MinionHero minion = HeroBuilder.buildMinion(minionConfig, hero);
        minion.spawn(spawnLoc, ai);
        summonedMinions.add(minion);
    }

    @Override
    protected void onDeactivate(Hero hero) {
        for (MinionHero minion : summonedMinions) {
            minion.despawn();  // unregisters from HeroManager + GameManager
        }
        summonedMinions.clear();
    }

    /**
     * Independent: this tick drives the ult-expiry despawn of the summoned (invincible)
     * minions. It must keep running while the owner is eliminated (dormant) — onDeath resets
     * the cooldown's active phase, so the very next dormant tick expires and despawns them.
     * Without this flag an eliminated pug's invincible minions would persist to game end.
     */
    @Override
    public boolean isIndependent() { return true; }
}
