package me.stolyy.heroes.ability.kit.minions.princess;

import me.stolyy.heroes.control.ai.MinionBotAI;
import me.stolyy.heroes.control.strategy.Defensive;
import org.bukkit.configuration.ConfigurationSection;

public class PrincessAI extends MinionBotAI {
    public PrincessAI(ConfigurationSection config) {
        super(config, new Defensive(config.getConfigurationSection("strategy")));
    }
}
