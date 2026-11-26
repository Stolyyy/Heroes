package me.stolyy.heroes.vfx.cosmetic;

import me.stolyy.heroes.vfx.effect.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class HeroSkin {
    private final String id;
    private final ConfigurationSection config;
    private final Map<String, ConfigurationSection> abilityOverrides = new HashMap<>();
    // Named per-piece cosmetic slots so sub-entities re-texture with the owner's skin.
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

    public ConfigurationSection getAbilityOverride(String abilityName) {
        return abilityOverrides.get(abilityName.toLowerCase());
    }

    public ConfigurationSection getPieceOverride(String pieceName) {
        return pieceOverrides.get(pieceName.toLowerCase());
    }

    public ConfigurationSection getArmor() { return config.getConfigurationSection("armor"); }
    public ConfigurationSection getHeadOverride() { return config.getConfigurationSection("head"); }
    public ConfigurationSection getHandOverride() { return config.getConfigurationSection("hand"); }
    /** Minion full-body display config; null keeps the worn-helmet path. */
    public ConfigurationSection getBodyOverride() { return config.getConfigurationSection("body"); }

    /**
     * Builds an unbreakable custom-modeled item from an optional YML override block
     * ({@code material}, {@code hero}, {@code texture}, {@code skin}), falling back to the given defaults.
     */
    public static ItemBuilder modelItem(@Nullable ConfigurationSection override, Material defaultMaterial,
                                        String defaultHero, String defaultTexture, String defaultSkin) {
        Material material = defaultMaterial;
        String hero = defaultHero;
        String texture = defaultTexture;
        String skin = defaultSkin;
        if (override != null) {
            String matName = override.getString("material");
            if (matName != null) {
                material = Material.valueOf(matName.toUpperCase());
            }
            hero = override.getString("hero", defaultHero);
            texture = override.getString("texture", defaultTexture);
            skin = override.getString("skin", defaultSkin);
        }
        return ItemBuilder.from(material)
                .unbreakable(true)
                .modelData(hero, texture, skin);
    }
}
