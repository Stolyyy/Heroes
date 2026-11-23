package me.stolyy.heroes.vfx.cosmetic;

import org.bukkit.configuration.ConfigurationSection;
import java.util.HashMap;
import java.util.Map;

public class HeroSkin {
    private final String id;
    private final ConfigurationSection config;
    private final Map<String, ConfigurationSection> abilityOverrides = new HashMap<>();
    // B21.1: named per-piece cosmetic slots (piece_visuals: { spooderbuddy: { head: ... } })
    // so sub-entities re-texture with the skin without touching the hero's own pieces.
    private final Map<String, ConfigurationSection> pieceOverrides = new HashMap<>();

    public HeroSkin(String id, ConfigurationSection config) {
        this.id = id;
        this.config = config;

        ConfigurationSection visualSec = config.getConfigurationSection("ability_visuals");
        if (visualSec != null) {
            for (String key : visualSec.getKeys(false)) {
                abilityOverrides.put(key.toLowerCase(), visualSec.getConfigurationSection(key));
            }
        }

        ConfigurationSection pieceSec = config.getConfigurationSection("piece_visuals");
        if (pieceSec != null) {
            for (String key : pieceSec.getKeys(false)) {
                pieceOverrides.put(key.toLowerCase(), pieceSec.getConfigurationSection(key));
            }
        }
    }

    public String getId() { return id; }
    public ConfigurationSection getConfig() { return config; }

    public ConfigurationSection getAbilityOverride(String abilityName) {
        return abilityOverrides.get(abilityName.toLowerCase());
    }

    public ConfigurationSection getPieceOverride(String pieceName) {
        return pieceOverrides.get(pieceName.toLowerCase());
    }

    // Helpers for specific items
    // Returns null if no override — caller defaults to (heroName, "head"/"hand", skinId)
    public ConfigurationSection getArmor() { return config.getConfigurationSection("armor"); }
    public ConfigurationSection getHeadOverride() { return config.getConfigurationSection("head"); }
    public ConfigurationSection getHandOverride() { return config.getConfigurationSection("hand"); }
    /** Minion full-body display config (MinionBodyDisplay); null keeps the worn-helmet path. */
    public ConfigurationSection getBodyOverride() { return config.getConfigurationSection("body"); }
}
