package me.stolyy.heroes.control;

import me.stolyy.heroes.ability.kit.bug.BugCharms;
import me.stolyy.heroes.ability.kit.bug.Charms;
import me.stolyy.heroes.control.ai.HeroBotAI;
import me.stolyy.heroes.control.strategy.Strategy;
import me.stolyy.heroes.game.GameBot;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.util.BotConfig;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Set;
import java.util.UUID;

/** Wires a bot hero's config-driven strategy, AI controller, difficulty delay, and transient charms. */
public final class BotLoadout {

    private BotLoadout() {}

    public static void configure(UUID botUUID, GameBot bot, Hero hero, String heroName) {
        YamlConfiguration heroConfig = HeroConfig.getConfig(heroName);
        if (heroConfig == null) {
            throw new IllegalStateException("Missing hero config for bot hero '" + heroName + "'");
        }

        Strategy strategy = Strategy.fromConfig(heroConfig);
        HeroBotAI botAI = new HeroBotAI(heroConfig, strategy);
        int tierDelay = BotConfig.difficultyInputDelay(bot.getDifficultyTier());
        if (tierDelay >= 0) botAI.setInputDelayTicks(tierDelay);
        ((BotController) hero.getController()).setAI(botAI);

        Set<Charms> charmPreset = BugCharms.rollRandomPreset();
        if (!charmPreset.isEmpty()) {
            HeroManager.setEquippedCharmsTransient(botUUID, charmPreset);
        }
    }
}
