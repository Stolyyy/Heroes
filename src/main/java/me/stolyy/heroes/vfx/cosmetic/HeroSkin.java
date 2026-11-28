package me.stolyy.heroes.vfx.cosmetic;

import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.ui.GuiText;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class HeroSkin {
    public enum Rarity {
        NORMAL,
        RARE,
        LEGENDARY;

        /** Next rarity tier for shard upgrades, or {@code null} for {@link #LEGENDARY}. */
        public @Nullable Rarity next() {
            return switch (this) {
                case NORMAL -> RARE;
                case RARE -> LEGENDARY;
                case LEGENDARY -> null;
            };
        }

        public static Rarity parse(String raw, String skinId) {
            try {
                return Rarity.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Unknown rarity '" + raw + "' on skin '" + skinId
                        + "' (expected normal, rare, or legendary)");
            }
        }
    }

    private final String id;
    private final ConfigurationSection config;
    private final boolean hidden;
    private final @Nullable Rarity rarity;
    private final @Nullable String configuredName;
    private final Map<String, ConfigurationSection> abilityOverrides = new HashMap<>();
    // Named per-piece cosmetic slots so sub-entities re-texture with the owner's skin.
    private final Map<String, ConfigurationSection> pieceOverrides = new HashMap<>();

    public HeroSkin(String id, ConfigurationSection config) {
        this.id = id;
        this.config = config;
        this.hidden = config.getBoolean("hidden", false);
        this.configuredName = config.getString("name");

        boolean exemptFromRarity = id.equalsIgnoreCase("default")
                || id.equalsIgnoreCase("masterskin")
                || hidden;
        if (exemptFromRarity) {
            if (config.contains("rarity")) {
                throw new IllegalStateException("Skin '" + id + "' must not declare 'rarity' "
                        + "(default, masterskin, and hidden skins cannot have a rarity)");
            }
            this.rarity = null;
        } else {
            if (!config.contains("rarity")) {
                throw new IllegalStateException("Skin '" + id + "' is missing required 'rarity' "
                        + "(normal, rare, or legendary)");
            }
            this.rarity = Rarity.parse(Configs.requireString(config, "rarity"), id);
        }

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

    public boolean isHidden() { return hidden; }

    public @Nullable Rarity getRarity() { return rarity; }

    /** Configured display name ({@code skins.<id>.name}), or the prettified id (with master-name for masterskin). */
    public String getDisplayName() {
        if (configuredName != null && !configuredName.isEmpty()) return configuredName;
        if (id.equalsIgnoreCase("masterskin")) return GuiText.raw("gui.skins.master-name");
        return Arrays.stream(id.split("_"))
                .filter(part -> !part.isEmpty())
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT)
                        + part.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }

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
