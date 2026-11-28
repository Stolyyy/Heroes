package me.stolyy.heroes.vfx.cosmetic;

import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.control.Controller;
import me.stolyy.heroes.control.PlayerController;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.hero.minion.MinionHero;
import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.util.ColorParser;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CosmeticManager {
    public static final int SLOT_PRIMARY = 0;
    public static final int SLOT_SECONDARY = 1;
    public static final int SLOT_SMASH = 2;
    public static final int PASSIVE_LAST = 5;
    public static final int PASSIVE_FIRST = 8;
    public static final int SLOT_QUIVER = 9;
    public static final int INV_TOP_START = 9;
    public static final int CHARM_CLEAR_START = 10;
    public static final int INV_TOP_END = 17;

    // Session-only admin skin previews: player UUID -> (lowercase hero key -> lowercase skin id).
    // Cleared on player quit and plugin disable; never written to player stats files.
    private static final Map<UUID, Map<String, String>> SKIN_PREVIEWS = new HashMap<>();

    public static void setPreview(UUID playerId, String heroId, String skinId) {
        SKIN_PREVIEWS.computeIfAbsent(playerId, k -> new HashMap<>())
                .put(heroId.toLowerCase(Locale.ROOT), skinId.toLowerCase(Locale.ROOT));
    }

    public static @Nullable String getPreview(UUID playerId, String heroId) {
        Map<String, String> byHero = SKIN_PREVIEWS.get(playerId);
        return byHero != null ? byHero.get(heroId.toLowerCase(Locale.ROOT)) : null;
    }

    public static boolean clearPreview(UUID playerId, String heroId) {
        Map<String, String> byHero = SKIN_PREVIEWS.get(playerId);
        if (byHero == null) return false;
        boolean removed = byHero.remove(heroId.toLowerCase(Locale.ROOT)) != null;
        if (byHero.isEmpty()) SKIN_PREVIEWS.remove(playerId);
        return removed;
    }

    /** Clears all session previews for {@code playerId} and returns the hero keys that were cleared. */
    public static Set<String> clearAllPreviews(UUID playerId) {
        Map<String, String> removed = SKIN_PREVIEWS.remove(playerId);
        return removed != null ? Set.copyOf(removed.keySet()) : Set.of();
    }

    public static void clearPreviews() {
        SKIN_PREVIEWS.clear();
    }

    /**
     * Looks up a named skin section in the hero's YML ({@code skins.<skinId>}).
     * Returns null when the hero has no such skin — callers keep the current skin.
     */
    public static @Nullable HeroSkin getSkinById(Hero hero, String skinId) {
        return findSkin(heroConfigKey(hero.getName()), skinId);
    }

    /** Looks up a skin by hero config key and skin id, returning null if absent. */
    public static @Nullable HeroSkin findSkin(String heroId, String skinId) {
        if (heroId == null || skinId == null) return null;
        YamlConfiguration config = HeroConfig.getConfig(heroId);
        if (config == null) return null;
        String normalized = skinId.toLowerCase(Locale.ROOT);
        ConfigurationSection section = config.getConfigurationSection("skins." + normalized);
        if (section == null) return null;
        return new HeroSkin(normalized, section);
    }

    /** Returns every non-hidden skin declared on {@code heroId}, in YML order. */
    public static List<HeroSkin> selectableSkins(String heroId) {
        YamlConfiguration config = HeroConfig.getConfig(heroId);
        if (config == null) return List.of();
        ConfigurationSection skins = config.getConfigurationSection("skins");
        if (skins == null) return List.of();
        List<HeroSkin> result = new ArrayList<>();
        for (String skinId : skins.getKeys(false)) {
            ConfigurationSection section = skins.getConfigurationSection(skinId);
            if (section == null || section.getBoolean("hidden", false)) continue;
            result.add(new HeroSkin(skinId.toLowerCase(Locale.ROOT), section));
        }
        return result;
    }

    /**
     * Resolves {@code stats}' selected skin for {@code heroId}, enforcing ownership and non-hidden
     * rules. If the saved selection is missing, hidden, or unowned, resets it to {@code default}.
     */
    public static @Nullable HeroSkin resolveOwnedSkin(PlayerStats stats, String heroId) {
        String key = heroId.toLowerCase(Locale.ROOT);
        String selected = stats.getSkinForHero(key);
        HeroSkin chosen = findSkin(key, selected);
        if (chosen == null || chosen.isHidden() || !stats.ownsSkin(key, chosen.getId())) {
            if (!"default".equalsIgnoreCase(selected)) {
                stats.setSkinForHero(key, "default");
            }
            return findSkin(key, "default");
        }
        return chosen;
    }

    /**
     * Resolves {@code player}'s active skin for {@code heroId}: a session-only admin preview takes
     * precedence (skipping ownership, still rejecting hidden skins); otherwise delegates to
     * {@link #resolveOwnedSkin}.
     */
    public static @Nullable HeroSkin resolveEffectiveSkin(Player player, String heroId) {
        String key = heroId.toLowerCase(Locale.ROOT);
        String previewId = getPreview(player.getUniqueId(), key);
        if (previewId != null) {
            HeroSkin preview = findSkin(key, previewId);
            if (preview != null && !preview.isHidden()) {
                return preview;
            }
            clearPreview(player.getUniqueId(), key);
        }
        return resolveOwnedSkin(PlayerStatsManager.getStats(player), key);
    }

    /** Resolves a hero's skin section by ID, falling back to {@code skins.default} if missing. */
    public static @Nullable HeroSkin resolveSkin(String heroId, String skinId) {
        HeroSkin skin = findSkin(heroId, skinId);
        return skin != null ? skin : findSkin(heroId, "default");
    }

    private static String heroConfigKey(String heroName) {
        if (HeroConfig.exists(heroName)) return heroName.toLowerCase(Locale.ROOT);
        for (String key : HeroConfig.getAvailableNames()) {
            if (HeroConfig.formatName(key).equalsIgnoreCase(heroName)) return key;
        }
        return heroName.toLowerCase(Locale.ROOT);
    }

    /** Builds the four armor slots (HEAD/CHEST/LEGS/FEET) for {@code skin}, using empty items when unarmored. */
    public static Map<EquipmentSlot, ItemStack> buildArmorEquipment(String heroId, HeroSkin skin) {
        String assetHero = HeroConfig.assetKey(heroId);
        Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
        equipment.put(EquipmentSlot.HEAD, HeroSkin.modelItem(
                skin.getHeadOverride(), Material.SPONGE, assetHero, "head", skin.getId()).build());
        ConfigurationSection armor = skin.getArmor();
        if (armor != null) {
            equipment.put(EquipmentSlot.CHEST, parseArmorPiece(
                    armor, "chest", EquipmentSlot.CHEST, Material.LEATHER_CHESTPLATE,
                    assetHero + " Chest", assetHero, skin.getId()).build());
            equipment.put(EquipmentSlot.LEGS, parseArmorPiece(
                    armor, "legs", EquipmentSlot.LEGS, Material.LEATHER_LEGGINGS,
                    assetHero + " Legs", assetHero, skin.getId()).build());
            equipment.put(EquipmentSlot.FEET, parseArmorPiece(
                    armor, "boots", EquipmentSlot.FEET, Material.LEATHER_BOOTS,
                    assetHero + " Boots", assetHero, skin.getId()).build());
        } else {
            equipment.put(EquipmentSlot.CHEST, ItemStack.empty());
            equipment.put(EquipmentSlot.LEGS, ItemStack.empty());
            equipment.put(EquipmentSlot.FEET, ItemStack.empty());
        }
        return equipment;
    }

    /** Builds the hero's primary held item for {@code skin} (used by the lobby Skin Museum). */
    public static ItemStack buildPrimaryItem(String heroId, HeroSkin skin) {
        String assetHero = HeroConfig.assetKey(heroId);
        ConfigurationSection handOverride = skin.getHandOverride();
        Material material = handOverride != null
                ? parseMaterial(handOverride.getString("material"), Material.STICK)
                : Material.STICK;
        ItemBuilder builder = ItemBuilder.from(material).unbreakable(true);
        if (handOverride != null) {
            builder.modelData(
                    handOverride.getString("hero", assetHero),
                    handOverride.getString("texture", "item"),
                    handOverride.getString("skin", "default"));
        } else {
            builder.modelData(assetHero, "item", skin.getId());
        }
        return builder.build();
    }

    /**
     * Resolves the skin for a hero that was just built for {@code controller} (match start, hero
     * swap, practice entry) and fixes it as the hero's base skin. Like the hero pick, a skin
     * chosen mid-match takes effect in the next game: respawns go through {@link #reapply}, which
     * restores the base skin instead of reading the selection again.
     */
    public static void apply(Controller controller, Hero hero) {
        if (controller instanceof PlayerController pc) {
            String heroKey = heroConfigKey(hero.getName());
            HeroSkin chosen = resolveEffectiveSkin(pc.getPlayer(), heroKey);
            if (chosen != null) hero.setSkin(chosen);
        }
        hero.setBaseSkin(hero.getSkin());

        applyCurrentSkin(controller, hero);
    }

    /**
     * Re-applies the skin the hero was built with. Called on respawn so a transient ability skin
     * cannot survive it if a revert hook is skipped or delayed, without picking up a skin the
     * player selected after the hero was built.
     */
    public static void reapply(Controller controller, Hero hero) {
        hero.setSkin(hero.getBaseSkin());
        applyCurrentSkin(controller, hero);
    }

    /** Applies {@code hero.getSkin()} without reading player stats (for transient ability swaps). */
    public static void applyCurrentSkin(Controller controller, Hero hero) {
        HeroSkin skin = hero.getSkin();
        if (controller == null || skin == null) return;

        applyArmor(controller, skin, hero.getName());

        controller.setHelmet(HeroSkin.modelItem(
                        skin.getHeadOverride(), Material.SPONGE, hero.getName(), "head", skin.getId())
                .name(Component.text(hero.getName() + " Head"))
                .build());

        setupAbilitySlot(controller, hero.getPrimaryAbility(), SLOT_PRIMARY, hero);
        setupAbilitySlot(controller, hero.getSecondaryAbility(), SLOT_SECONDARY, hero);
        setupAbilitySlot(controller, hero.getSmashAbility(), SLOT_SMASH, hero);

        // Bow-wielding heroes need an arrow in the inventory for the bow to draw (never consumed).
        ConfigurationSection handOverride = skin.getHandOverride();
        if (handOverride != null && "BOW".equalsIgnoreCase(handOverride.getString("material"))) {
            controller.setInventoryItem(SLOT_QUIVER, ItemBuilder.from(Material.ARROW)
                    .name(Component.text("Quiver"))
                    .build());
        } else {
            controller.setInventoryItem(SLOT_QUIVER, null);
        }

        // Clear passive and top inventory rows before populating so series hero swaps don't leak cards.
        for (int slot = PASSIVE_LAST; slot <= PASSIVE_FIRST; slot++) {
            controller.setInventoryItem(slot, null);
        }
        for (int slot = CHARM_CLEAR_START; slot <= INV_TOP_END; slot++) {
            controller.setInventoryItem(slot, null);
        }

        for (Map.Entry<Integer, Ability> entry : resolvePassiveSlots(hero).entrySet()) {
            int passiveSlot = entry.getKey();
            Ability passive = entry.getValue();

            List<Component> lore = new ArrayList<>();
            if (passive.getDescription() != null && !passive.getDescription().isEmpty()) {
                lore.add(Component.text(passive.getDescription(), NamedTextColor.GRAY));
            }
            lore.add(Component.text(activationHint(passive.getType()), NamedTextColor.DARK_GRAY));
            lore.addAll(passive.passiveCardExtras(controller));
            passive.onCosmeticsApplied(controller);

            controller.setInventoryItem(passiveSlot, ItemBuilder.from(Material.PAPER)
                    .name(Component.text(passive.getName(), NamedTextColor.GOLD))
                    .lore(lore)
                    .maxDamage(100)
                    .modelData("gui", "passive", passive.getType().name().toLowerCase().replace('_', '-'))
                    .build());
        }
    }

    /**
     * Assigns hotbar slots {@code 8..5} to a hero's displayable passives (slot 4 stays free for
     * respawn re-pick), skipping unauthored names. Shared with cooldown visuals.
     */
    public static Map<Integer, Ability> resolvePassiveSlots(Hero hero) {
        Map<Integer, Ability> result = new LinkedHashMap<>();
        int passiveSlot = PASSIVE_FIRST;
        for (Ability passive : hero.getPassiveAbilities()) {
            if (passiveSlot < PASSIVE_LAST) break;
            String passiveName = passive.getName();
            if (passiveName == null || passiveName.isEmpty() || "Unknown Ability".equals(passiveName)) continue;
            result.put(passiveSlot--, passive);
        }
        return result;
    }

    /** Re-keys the slot-0 held item's model in place while preserving durability and stack count. */
    public static void setHeldModel(Hero hero, String texture) {
        setHeldModel(hero, texture, resolvedHeldSkin(hero));
    }

    /** Variant of {@link #setHeldModel(Hero, String)} overriding the skin key half. */
    public static void setHeldModel(Hero hero, String texture, String skin) {
        if (hero.getController() == null) return;
        ItemStack held = hero.getController().getInventoryItem(SLOT_PRIMARY);
        if (held == null) return;
        HeroSkin heroSkin = hero.getSkin();
        ConfigurationSection handOverride = heroSkin != null ? heroSkin.getHandOverride() : null;
        String heroKey = handOverride != null ? handOverride.getString("hero", hero.getName()) : hero.getName();
        ItemBuilder.applyModelData(held, heroKey, texture, skin);
    }

    public static String resolvedHeldSkin(Hero hero) {
        HeroSkin skin = hero.getSkin();
        ConfigurationSection handOverride = skin != null ? skin.getHandOverride() : null;
        if (handOverride != null) return handOverride.getString("skin", "default");
        return skin != null ? skin.getId() : "default";
    }

    public static String defaultHeldTexture(Hero hero) {
        HeroSkin skin = hero.getSkin();
        ConfigurationSection handOverride = skin != null ? skin.getHandOverride() : null;
        return handOverride != null ? handOverride.getString("texture", "item") : "item";
    }

    private static String activationHint(AbilityType type) {
        return switch (type) {
            case PASSIVE_SNEAK -> "Activate: SNEAK";
            case PASSIVE_DROP -> "Activate: Q (drop)";
            case PASSIVE_SWAP -> "Activate: F (swap)";
            case PASSIVE -> "Always active";
            case PRIMARY, SECONDARY, SMASH ->
                    throw new IllegalArgumentException("Unexpected non-passive AbilityType for passive card: " + type);
        };
    }

    /**
     * Applies skin equipment to a minion. Head resolution priority: owner's piece override for
     * {@code pieceName}, then the minion's own head override, then {@code <hero>_head_default}.
     */
    public static void applyMinion(Controller controller, Hero hero, String pieceName) {
        HeroSkin skin = hero.getSkin();
        if (controller == null || skin == null) return;

        applyArmor(controller, skin, hero.getName());

        if (resolveMinionBody(hero, pieceName) != null) return;

        ConfigurationSection headOverride = null;
        if (pieceName != null && hero instanceof MinionHero minion && minion.getOwner() != null) {
            HeroSkin ownerSkin = minion.getOwner().getSkin();
            if (ownerSkin != null) {
                ConfigurationSection piece = ownerSkin.getPieceOverride(pieceName);
                if (piece != null) headOverride = piece.getConfigurationSection("head");
            }
        }
        if (headOverride == null) headOverride = skin.getHeadOverride();

        controller.setHelmet(HeroSkin.modelItem(
                        headOverride, Material.SPONGE, hero.getName(), "head", "default")
                .build());
    }

    /** Resolves a minion's full-body {@code body:} config, preferring the owner's piece override. */
    public static ConfigurationSection resolveMinionBody(Hero hero, String pieceName) {
        if (pieceName != null && hero instanceof MinionHero minion && minion.getOwner() != null) {
            HeroSkin ownerSkin = minion.getOwner().getSkin();
            if (ownerSkin != null) {
                ConfigurationSection piece = ownerSkin.getPieceOverride(pieceName);
                if (piece != null) {
                    ConfigurationSection bodyOverride = piece.getConfigurationSection("body");
                    if (bodyOverride != null) return bodyOverride;
                }
            }
        }
        HeroSkin skin = hero.getSkin();
        return skin != null ? skin.getBodyOverride() : null;
    }

    static ItemStack minionBodyItem(ConfigurationSection body, String heroName) {
        return HeroSkin.modelItem(body, Material.SPONGE, heroName, "body", "default").build();
    }

    private static void applyArmor(Controller controller, HeroSkin skin, String heroName) {
        ConfigurationSection armor = skin.getArmor();
        if (armor == null) return;
        String assetHero = HeroConfig.assetKey(heroName);
        controller.setChestplate(parseArmorPiece(
                armor, "chest", EquipmentSlot.CHEST, Material.LEATHER_CHESTPLATE,
                heroName + " Chest", assetHero, skin.getId()).build());
        controller.setLeggings(parseArmorPiece(
                armor, "legs", EquipmentSlot.LEGS, Material.LEATHER_LEGGINGS,
                heroName + " Legs", assetHero, skin.getId()).build());
        controller.setBoots(parseArmorPiece(
                armor, "boots", EquipmentSlot.FEET, Material.LEATHER_BOOTS,
                heroName + " Boots", assetHero, skin.getId()).build());
    }

    private static void setupAbilitySlot(Controller controller, Ability ability, int slot, Hero hero) {
        if (ability == null) return;

        Material material = Material.STICK;
        if (slot == SLOT_PRIMARY) {
            HeroSkin skin = hero.getSkin();
            ConfigurationSection handOverride = skin != null ? skin.getHandOverride() : null;
            if (handOverride != null) {
                material = parseMaterial(handOverride.getString("material"), Material.STICK);
            }
        }

        ItemBuilder builder = ItemBuilder.from(material)
                .name(Component.text(ability.getName()))
                .maxDamage(100);

        if (material == Material.BOW) {
            builder.enchant(Enchantment.INFINITY, 1).enchantGlint(false).unbreakable(true);
        }

        if (ability.getDescription() != null && !ability.getDescription().isEmpty()) {
            builder.lore(Component.text(ability.getDescription()));
        }

        if (slot == SLOT_PRIMARY) {
            HeroSkin skin = hero.getSkin();
            ConfigurationSection handOverride = skin != null ? skin.getHandOverride() : null;
            if (handOverride != null) {
                builder.modelData(handOverride.getString("hero", hero.getName()),
                        handOverride.getString("texture", "item"),
                        handOverride.getString("skin", "default"));
            } else if (skin != null) {
                builder.modelData(hero.getName(), "item", skin.getId());
            }
        } else {
            String type = (slot == SLOT_SECONDARY) ? "Secondary" : "Ultimate";
            boolean ready = ability.getCooldownObject().isReady((int) hero.getEnergy().getCurrentEnergy());
            builder.modelData(hero.getName(), type, ready ? "Ready" : "NotReady");
        }

        controller.setInventoryItem(slot, builder.build());
    }

    private static ItemBuilder parseArmorPiece(ConfigurationSection armorSection, String key, EquipmentSlot slot,
                                               Material defaultMat, String name, String assetHero, String skinId) {
        Material material = defaultMat;
        String colorData = null;
        String equipmentKey = null;
        boolean customModel = false;

        if (armorSection.contains(key)) {
            if (armorSection.isConfigurationSection(key)) {
                ConfigurationSection sec = armorSection.getConfigurationSection(key);
                material = parseMaterial(sec.getString("material"), defaultMat);
                colorData = sec.getString("color");
                equipmentKey = sec.getString("equipment");
                customModel = sec.getBoolean("model", false);
            } else if (armorSection.isString(key)) {
                colorData = armorSection.getString(key);
            } else {
                throw new IllegalArgumentException("Malformed armor entry '" + key + "' at '"
                        + armorSection.getCurrentPath() + "': expected color string or {material, color, equipment, model} section");
            }
        }

        if (colorData == null && material == defaultMat && equipmentKey == null && !customModel) {
            return ItemBuilder.from(material).name(Component.text(name));
        }

        ItemBuilder builder = ItemBuilder.from(material).unbreakable(true).name(Component.text(name));
        if (colorData != null) {
            builder.color(ColorParser.parse(colorData));
        }
        if (equipmentKey != null) {
            if (equipmentKey.isEmpty() || !Key.parseable(equipmentKey)) {
                throw new IllegalArgumentException("Invalid equipment asset key '" + equipmentKey
                        + "' for hero '" + assetHero + "', skin '" + skinId + "', piece '" + key + "'");
            }
            builder.equippable(Key.key(equipmentKey), slot);
        }
        if (customModel) {
            builder.modelData(assetHero, key, skinId);
        }
        return builder;
    }

    private static Material parseMaterial(String name, Material fallback) {
        return name == null ? fallback : Material.valueOf(name.toUpperCase());
    }
}
