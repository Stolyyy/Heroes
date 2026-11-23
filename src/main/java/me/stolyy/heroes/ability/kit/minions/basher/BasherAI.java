package me.stolyy.heroes.ability.kit.minions.basher;

import me.stolyy.heroes.control.ai.MinionBotAI;
import me.stolyy.heroes.control.strategy.Aggressive;
import org.bukkit.configuration.ConfigurationSection;

public class BasherAI extends MinionBotAI {

    public BasherAI(ConfigurationSection config) {
        super(config, new Aggressive(config.getConfigurationSection("strategy")));
    }
}
