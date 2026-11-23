package me.stolyy.heroes.vfx.cosmetic;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.hero.minion.MinionHero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.control.Controller;
import me.stolyy.heroes.control.PlayerController;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;

public class CosmeticManager {

    /**
     * Gets the default skin for a hero by name.
     * @param heroName the hero name (case-insensitive)
     * @return the default HeroSkin, or null if not found
     */
    public static HeroSkin getDefaultSkin(String heroName) {
        YamlConfiguration config = HeroConfig.getConfig(heroName);
        if (config == null) return null;

        ConfigurationSection skinsSection = config.getConfigurationSection("skins");
        if (skinsSection == null) return null;

        ConfigurationSection defaultSkin = skinsSection.getConfigurationSection("default");
        if (defaultSkin == null) return null;

        return new HeroSkin("default", defaultSkin);
    }

    /**
     * Looks up a named skin section in the hero's YML ({@code skins.<skinId>}).
     * Returns null when the hero has no such skin — callers keep the current skin.
     */
    public static HeroSkin getSkinById(Hero hero, String skinId) {
        YamlConfiguration config = HeroConfig.getConfig(hero.getName());
        if (config == null) return null;
        ConfigurationSection section = config.getConfigurationSection("skins." + skinId);
        if (section == null) return null;
        return new HeroSkin(skinId, section);
    }

    public static void apply(Controller controller, Hero hero) {
        // Per-player skin choice (master skin etc.) overrides the construction-time default.
        // Resolve UNCONDITIONALLY, including for "default" — this makes apply() mean "restore the
        // player's real skin", so a transient ability skin (Karakot's ult, Pochita's hell form) can
        // never survive a respawn/hero-swap re-apply if its own revert hook happens to run later.
        if (controller instanceof PlayerController pc) {
            String skinId = PlayerStatsManager.getStats(pc.getPlayer()).getSkinForHero(hero.getName());
            HeroSkin chosen = getSkinById(hero, skinId);
            if (chosen != null) hero.setSkin(chosen);
        }

        applyCurrentSkin(controller, hero);
    }

    /**
     * Applies {@code hero.getSkin()} exactly as set — no per-player override lookup.
     * Use for temporary in-game skin swaps (e.g. Pochita's HeroOfHell) that
     * {@link #apply} would otherwise clobber back to the player's chosen skin.
     */
    public static void applyCurrentSkin(Controller controller, Hero hero) {
        HeroSkin skin = hero.getSkin();
        if (skin == null) return;

        // 1. Armor
        ConfigurationSection armor = skin.getArmor();
        if (armor != null) {
            String heroName = hero.getName();
            controller.setChestplate(parseArmorPiece(armor, "chest", Material.LEATHER_CHESTPLATE, heroName + " Chest").build());
            controller.setLeggings(parseArmorPiece(armor, "legs", Material.LEATHER_LEGGINGS, heroName + " Legs").build());
            controller.setBoots(parseArmorPiece(armor, "boots", Material.LEATHER_BOOTS, heroName + " Boots").build());
        }

        // 2. Head
        ConfigurationSection headOverride = skin.getHeadOverride();
        ItemBuilder headItem;
        if (headOverride != null) {
            Material headMat = parseMaterial(headOverride.getString("material"), Material.SPONGE);
            headItem = ItemBuilder.from(headMat).unbreakable(true)
                    .modelData(headOverride.getString("hero", hero.getName()), headOverride.getString("texture", "head"), headOverride.getString("skin", "default"));
        } else {
            headItem = ItemBuilder.from(Material.SPONGE).unbreakable(true)
                    .modelData(hero.getName(), "head", skin.getId());
        }
        controller.setHelmet(headItem.name(Component.text(hero.getName() + " Head")).build());

        // 3. Hotbar Initial Setup
        setupAbilitySlot(controller, hero.getPrimaryAbility(), 0, hero);
        setupAbilitySlot(controller, hero.getSecondaryAbility(), 1, hero);
        setupAbilitySlot(controller, hero.getSmashAbility(), 2, hero);

        // Bow-wielding heroes (Green Hood) need an arrow somewhere in the inventory for
        // the bow to draw. Nothing is ever consumed — the bow carries Infinity, so vanilla
        // fires an intangible copy that the kit re-aims and tags.
        ConfigurationSection handOverride2 = skin.getHandOverride();
        if (handOverride2 != null && "BOW".equalsIgnoreCase(handOverride2.getString("material"))) {
            controller.setInventoryItem(9, ItemBuilder.from(Material.ARROW)
                    .name(Component.text("Quiver"))
                    .build());
        } else {
            // Clear a previous hero's quiver (series hero swaps reuse the same inventory).
            controller.setInventoryItem(9, null);
        }

        // 4. Passive reference cards — inert info items filling the hotbar from slot 8
        // LEFTWARD (slot 4 stays free for the TDM re-pick compass). They can't fire
        // anything (primaries need slot 0, secondary/smash fire on scroll to 1/2, drops
        // and swaps are cancelled). Passives with unauthored yml names are skipped.
        // Clear the whole passive range first: series hero swaps reuse the same inventory, and
        // a hero with fewer passives would otherwise leave the previous hero's cards behind.
        for (int slot = 5; slot <= 8; slot++) {
            controller.setInventoryItem(slot, null);
        }
        // Also clear the main inventory's top row - BugCharms repopulates it with equipped
        // charm display items on late-init; other heroes must not inherit them on swap.
        for (int slot = 10; slot <= 17; slot++) {
            controller.setInventoryItem(slot, null);
        }

        for (java.util.Map.Entry<Integer, Ability> entry : resolvePassiveSlots(hero).entrySet()) {
            int passiveSlot = entry.getKey();
            Ability passive = entry.getValue();
            String passiveName = passive.getName();

            java.util.List<Component> lore = new java.util.ArrayList<>();
            if (passive.getDescription() != null && !passive.getDescription().isEmpty()) {
                lore.add(Component.text(passive.getDescription(), NamedTextColor.GRAY));
            }
            lore.add(Component.text(activationHint(passive.getType()), NamedTextColor.DARK_GRAY));

            if (passive instanceof me.stolyy.heroes.ability.kit.bug.BugCharms) {
                var equippedCharms = me.stolyy.heroes.hero.HeroManager.getEquippedCharms(controller.getUniqueId());
                if (!equippedCharms.isEmpty()) {
                    lore.add(Component.text("Equipped:", NamedTextColor.AQUA));
                    for (var charm : me.stolyy.heroes.ability.kit.bug.Charms.values()) {
                        if (equippedCharms.contains(charm)) {
                            lore.add(Component.text("\u2022 " + me.stolyy.heroes.hero.HeroConfig.formatName(charm.name()), NamedTextColor.WHITE));
                        }
                    }
                }
                // Charm display row (slots 9-17), owned here rather than by BugCharms so the clear
                // above and the repopulate below stay adjacent. BugCharms only populated on its
                // one-shot late-init, so the row went blank on the first respawn and never came back.
                populateCharmRow(controller, equippedCharms);
            }

            controller.setInventoryItem(passiveSlot, ItemBuilder.from(Material.PAPER)
                    .name(Component.text(passiveName, NamedTextColor.GOLD))
                    .lore(lore.toArray(new Component[0]))
                    .maxDamage(100)
                    .modelData("gui", "passive", passive.getType().name().toLowerCase().replace('_', '-'))
                    .build());
        }
    }

    /**
     * Assigns hotbar slots to a hero's displayable passives: slot 8 counting DOWN to slot 5
     * (slot 4 stays free for the TDM re-pick compass), skipping passives with unauthored yml
     * names ("Unknown Ability"). Shared by {@link #apply} (card layout) and
     * {@link me.stolyy.heroes.ability.CooldownManager} (cooldown visuals) so the two can never
     * disagree about which passive lives in which slot.
     */
    public static java.util.Map<Integer, Ability> resolvePassiveSlots(Hero hero) {
        java.util.Map<Integer, Ability> result = new java.util.LinkedHashMap<>();
        int passiveSlot = 8;
        for (Ability passive : hero.getPassiveAbilities()) {
            if (passiveSlot < 5) break;
            String passiveName = passive.getName();
            if (passiveName == null || passiveName.isEmpty() || "Unknown Ability".equals(passiveName)) continue;
            result.put(passiveSlot--, passive);
        }
        return result;
    }

    /** Inert one-per-charm display items filling the main inventory's top row from slot 9. */
    private static void populateCharmRow(Controller controller, java.util.Set<me.stolyy.heroes.ability.kit.bug.Charms> equipped) {
        int slot = 9;
        for (var charm : me.stolyy.heroes.ability.kit.bug.Charms.values()) {
            if (!equipped.contains(charm) || slot > 17) continue;
            controller.setInventoryItem(slot++, ItemBuilder.from(Material.PAPER)
                    .name(Component.text(HeroConfig.formatName(charm.name()), NamedTextColor.GOLD))
                    .lore(Component.text(me.stolyy.heroes.ability.kit.bug.CharmConfig.getDescription(charm), NamedTextColor.GRAY))
                    .modelData("gui", "charms", me.stolyy.heroes.ability.kit.bug.CharmConfig.getTexture(charm))
                    .build());
        }
    }

    /**
     * Re-keys the slot-0 held item's model in place, keeping every other component (durability,
     * stack count) intact. Used for transient weapon-model swaps that must NOT go through the skin
     * system — Punishgers' weapon modes, Skullfire's burning deagle. Resolves the hero/skin halves
     * of the key exactly as {@link #setupAbilitySlot} does, so a hand-override skin stays consistent.
     *
     * <p>Note this does not survive a {@link #apply} (respawn, hero swap) — callers must re-assert
     * or reset their state in {@code onDeath}.
     */
    public static void setHeldModel(Hero hero, String texture) {
        setHeldModel(hero, texture, resolvedHeldSkin(hero));
    }

    /**
     * As above, overriding the SKIN half of the key instead of inheriting the hero's current skin.
     * Which half an ability varies is a property of how the art was authored, and the two heroes
     * doing this disagree: Punishgers keys its weapon modes on the texture half
     * ({@code punishgers_shotgun_default}) while Skullfire's burning deagle is keyed on the skin
     * half ({@code skullfire_item_ult}). Pick the overload that matches the pack case.
     */
    public static void setHeldModel(Hero hero, String texture, String skin) {
        if (!(hero.getController().getEntity() instanceof org.bukkit.entity.Player p)) return;
        org.bukkit.inventory.ItemStack held = p.getInventory().getItem(0);
        if (held == null) return;
        HeroSkin heroSkin = hero.getSkin();
        ConfigurationSection handOverride = heroSkin != null ? heroSkin.getHandOverride() : null;
        String heroKey = handOverride != null ? handOverride.getString("hero", hero.getName()) : hero.getName();
        ItemBuilder.applyModelData(held, heroKey, texture, skin);
    }

    /** The skin half of the slot-0 key {@link #setupAbilitySlot} would write. */
    public static String resolvedHeldSkin(Hero hero) {
        HeroSkin skin = hero.getSkin();
        ConfigurationSection handOverride = skin != null ? skin.getHandOverride() : null;
        if (handOverride != null) return handOverride.getString("skin", "default");
        return skin != null ? skin.getId() : "default";
    }

    /** The slot-0 texture key {@link #setupAbilitySlot} would write — i.e. what to restore to. */
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
            default -> "Always active";
        };
    }

    /**
     * Applies skin equipment to a minion. The head resolves in priority order:
     * the OWNER's active skin's piece override for {@code pieceName} (B21.2 — a master-skin
     * Spooderman gets master-skin buddies), then the minion's own yml head override, then
     * the default hero-head key.
     */
    public static void applyMinion(Controller controller, Hero hero, String pieceName) {
        HeroSkin skin = hero.getSkin();
        if (skin == null) return;

        // Armor
        ConfigurationSection armor = skin.getArmor();
        if (armor != null) {
            String heroName = hero.getName();
            controller.setChestplate(parseArmorPiece(armor, "chest", Material.LEATHER_CHESTPLATE, heroName + " Chest").build());
            controller.setLeggings(parseArmorPiece(armor, "legs", Material.LEATHER_LEGGINGS, heroName + " Legs").build());
            controller.setBoots(parseArmorPiece(armor, "boots", Material.LEATHER_BOOTS, heroName + " Boots").build());
        }

        // Full-body display minions skip the helmet entirely — the ITEM_DISPLAY passenger
        // (MinionBodyDisplay, spawned by MinionHero) carries the whole visual.
        if (resolveMinionBody(hero, pieceName) != null) return;

        // Head: owner's per-piece override wins over the minion's own yml override.
        ConfigurationSection headOverride = null;
        if (pieceName != null && hero instanceof MinionHero minion && minion.getOwner() != null) {
            HeroSkin ownerSkin = minion.getOwner().getSkin();
            if (ownerSkin != null) {
                ConfigurationSection piece = ownerSkin.getPieceOverride(pieceName);
                if (piece != null) headOverride = piece.getConfigurationSection("head");
            }
        }
        if (headOverride == null) headOverride = skin.getHeadOverride();

        ItemBuilder headItem;
        if (headOverride != null) {
            Material headMat = parseMaterial(headOverride.getString("material"), Material.SPONGE);
            headItem = ItemBuilder.from(headMat).unbreakable(true)
                    .modelData(headOverride.getString("hero", hero.getName()), headOverride.getString("texture", "head"), headOverride.getString("skin", "default"));
        } else {
            headItem = ItemBuilder.from(Material.SPONGE).unbreakable(true)
                    .modelData(hero.getName(), "head", "default");
        }
        controller.setHelmet(headItem.build());
    }

    /**
     * Resolves a minion's full-body display config ({@code body:} block), mirroring the head's
     * priority order exactly: the OWNER's active skin's piece override for {@code pieceName},
     * then the minion's own yml skin. Null means the minion stays on the worn-helmet path.
     */
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

    /** Builds the ITEM_DISPLAY stack for a minion body — same model-key idiom as the head item. */
    static org.bukkit.inventory.ItemStack minionBodyItem(ConfigurationSection body, String heroName) {
        Material material = parseMaterial(body.getString("material"), Material.SPONGE);
        return ItemBuilder.from(material).unbreakable(true)
                .modelData(body.getString("hero", heroName), body.getString("texture", "body"), body.getString("skin", "default"))
                .build();
    }

    private static void setupAbilitySlot(Controller controller, Ability ability, int slot, Hero hero) {
        if (ability == null) return;

        // Primary slot may override the hand item's material (e.g. a hero that uses a bow
        // instead of the default stick). Other slots stay on the standard cooldown-icon item.
        // STICK matches the resource pack: stick.json holds both the hand-item and UI-icon
        // model keys. The item carries no consumable component — held right-click fire is
        // driven by AbilityListener's use-packet grace window, which avoids the client's
        // hardcoded movement slowdown while "using" an item.
        Material material = Material.STICK;
        if (slot == 0) {
            HeroSkin skin = hero.getSkin();
            ConfigurationSection handOverride = skin != null ? skin.getHandOverride() : null;
            if (handOverride != null) {
                material = parseMaterial(handOverride.getString("material"), Material.STICK);
            }
        }

        ItemBuilder builder = ItemBuilder.from(material)
                .name(Component.text(ability.getName()))
                .maxDamage(100);

        // Bow heroes (Green Hood): the shoot event is no longer cancelled, so vanilla would
        // drain the quiver and wear the bow. Infinity makes the quiver arrow intangible (and
        // stops the client predicting its consumption); unbreakable stops the per-shot
        // durability hit from breaking the bow mid-match. Glint suppressed so the item looks plain.
        if (material == Material.BOW) {
            builder.enchant(Enchantment.INFINITY, 1).enchantGlint(false).unbreakable(true);
        }

        if (ability.getDescription() != null && !ability.getDescription().isEmpty()) {
            builder.lore(Component.text(ability.getDescription()));
        }

        if (slot == 0) {
            HeroSkin skin = hero.getSkin();
            ConfigurationSection handOverride = skin != null ? skin.getHandOverride() : null;
            if (handOverride != null) {
                builder.modelData(handOverride.getString("hero", hero.getName()), handOverride.getString("texture", "item"), handOverride.getString("skin", "default"));
            } else if (skin != null) {
                builder.modelData(hero.getName(), "item", skin.getId());
            }
        } else {
            // Secondary/Smash setup with initial state
            String type = (slot == 1) ? "Secondary" : "Ultimate";
            boolean ready = ability.getCooldownObject().isReady((int) hero.getEnergy().getCurrentEnergy());
            builder.modelData(hero.getName(), type, ready ? "Ready" : "NotReady");
        }

        controller.setInventoryItem(slot, builder.build());
    }

    /**
     * Parse one armor piece's config. The piece may be specified as either:
     * <ul>
     *   <li>a flat string like {@code "34,34,68"} (color only — material falls back to {@code defaultMat})</li>
     *   <li>a section like {@code { material: IRON_CHESTPLATE, color: 34,34,68 }} where both fields are optional</li>
     * </ul>
     * The {@code color} field is only honored for leather armor (other armors silently ignore tint).
     */
    private static ItemBuilder parseArmorPiece(ConfigurationSection armorSection, String key, Material defaultMat, String name) {
        Material material = defaultMat;
        String colorData = null;

        if (armorSection.isConfigurationSection(key)) {
            ConfigurationSection sec = armorSection.getConfigurationSection(key);
            material = parseMaterial(sec.getString("material"), defaultMat);
            colorData = sec.getString("color");
        } else if (armorSection.isString(key)) {
            colorData = armorSection.getString(key);
        }

        if (colorData == null && material == defaultMat) {
            // No data at all → return un-customized piece (matches old "data == null" early return).
            return ItemBuilder.from(material).name(Component.text(name));
        }

        ItemBuilder builder = ItemBuilder.from(material).unbreakable(true).name(Component.text(name));
        if (colorData != null && colorData.contains(",")) {
            String[] rgb = colorData.split(",");
            if (rgb.length == 3) {
                builder.color(Color.fromRGB(
                    Integer.parseInt(rgb[0].trim()),
                    Integer.parseInt(rgb[1].trim()),
                    Integer.parseInt(rgb[2].trim())
                ));
            }
        }
        return builder;
    }

    /** Resolve a yml material string ("IRON_CHESTPLATE", "BOW") to a {@link Material}, or fall back. */
    private static Material parseMaterial(String name, Material fallback) {
        return name == null ? fallback : Material.valueOf(name.toUpperCase());
    }
}
