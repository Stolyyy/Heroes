package me.stolyy.heroes.ability.kit.pug;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroBuilder;
import me.stolyy.heroes.hero.minion.MinionHero;
import me.stolyy.heroes.ability.kit.minions.basher.BasherAI;
import me.stolyy.heroes.ability.kit.minions.princess.PrincessAI;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public class PugIntruders extends BaseAbility {
    private boolean isActive = false;
    private final List<MinionHero> summonedMinions = new ArrayList<>();

    private final ConfigurationSection basherConfig;
    private final ConfigurationSection princessConfig;
    private final SoundEffect activationSound;

    public PugIntruders(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.basherConfig = this.config.getConfigurationSection("basher");
        this.princessConfig = this.config.getConfigurationSection("princess");
        this.activationSound = new SoundEffect(this.config.getConfigurationSection("visuals.activation_sound"));
    }

    @Override
    public void perform(Hero hero) {
        isActive = true;
        activationSound.play(hero.getController());

        Location heroLoc = hero.getController().getLocation();

        if (basherConfig != null) {
            spawnMinion(hero, basherConfig, new BasherAI(basherConfig), heroLoc.clone().add(1, 0, 0));
        }
        if (princessConfig != null) {
            spawnMinion(hero, princessConfig, new PrincessAI(princessConfig), heroLoc.clone().add(-1, 0, 0));
        }
    }

    private void spawnMinion(Hero hero, ConfigurationSection minionConfig,
                             me.stolyy.heroes.control.ai.BotAI ai, Location spawnLoc) {
        MinionHero minion = HeroBuilder.buildMinion(minionConfig, hero);
        // Invincibility now comes from the minion section's mandatory `invincible:` key, parsed in
        // HeroBuilder.buildMinion, instead of being hardcoded for these two.
        // spawn() wires the AI, injects the owner's game/team, and registers the minion.
        // Game.gameTick drives the AI tick centrally — no manual tickAI loop needed here.
        minion.spawn(spawnLoc, ai);

        summonedMinions.add(minion);
    }

    @Override
    protected void onTick(Hero hero) {
        if (!isActive) return;

        if (!getCooldownObject().isInUse()) {
            onExpire(hero, null);
        }
    }

    @Override
    public void onExpire(Hero hero, Location location) {
        if (!isActive) return;
        isActive = false;
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
