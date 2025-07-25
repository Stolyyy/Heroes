package me.stolyy.heroes.hero.components;

import me.stolyy.heroes.hero.characters.Hero;
import me.stolyy.heroes.hero.configs.AbilityConfig;
import me.stolyy.heroes.hero.configs.AbilityEnums;
import me.stolyy.heroes.hero.configs.EquipmentConfig;
import me.stolyy.heroes.hero.configs.HeroConfig;
import me.stolyy.heroes.utility.effects.CustomItems;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class Equipment {
    private final Hero hero;
    private final AbilityConfig secondaryConfig;
    private final AbilityConfig ultimateConfig;

    private final ItemStack head, chest, legs, boots;
    private final ItemStack primary, secondary, ultimate;

    public Equipment(Hero hero, HeroConfig heroConfig) {
        this.hero = hero;
        EquipmentConfig equipmentConfig = heroConfig.equipmentConfig();

        AbilityConfig primaryConfig = heroConfig.abilityConfigs().values().stream()
                .filter(a -> a.abilityType() == AbilityEnums.AbilityType.PRIMARY).findFirst()
                .orElseThrow(() -> new IllegalStateException("Hero '" + heroConfig.name() + "' is missing a PRIMARY ability config."));
        this.secondaryConfig = heroConfig.abilityConfigs().values().stream()
                .filter(a -> a.abilityType() == AbilityEnums.AbilityType.SECONDARY).findFirst()
                .orElseThrow(() -> new IllegalStateException("Hero '" + heroConfig.name() + "' is missing a SECONDARY ability config."));
        this.ultimateConfig = heroConfig.abilityConfigs().values().stream()
                .filter(a -> a.abilityType() == AbilityEnums.AbilityType.ULTIMATE).findFirst()
                .orElseThrow(() -> new IllegalStateException("Hero '" + heroConfig.name() + "' is missing an ULTIMATE ability config."));

        head = CustomItems.customItem(equipmentConfig.head(), equipmentConfig.headName());

        int[] chestColors = equipmentConfig.chestColors();
        chest = CustomItems.customArmor("chest", chestColors[0], chestColors[1], chestColors[2], equipmentConfig.chestName());
        int[] legsColors = equipmentConfig.legsColors();
        legs = CustomItems.customArmor("legs", legsColors[0], legsColors[1], legsColors[2], equipmentConfig.legsName());
        int[] feetColors = equipmentConfig.feetColors();
        boots = CustomItems.customArmor("boots", feetColors[0], feetColors[1], feetColors[2], equipmentConfig.feetName());

        primary = CustomItems.customItem((String) primaryConfig.visuals().get("model"), primaryConfig.name());
        CustomItems.addLore(primary, primaryConfig.description());
        secondary = CustomItems.customItem((String) secondaryConfig.visuals().get("chargingModel"), secondaryConfig.name());
        CustomItems.addLore(secondary, secondaryConfig.description());
        CustomItems.removeUnbreakable(secondary);
        ultimate = CustomItems.customItem((String) ultimateConfig.visuals().get("chargingModel"), ultimateConfig.name());
        CustomItems.addLore(ultimate, ultimateConfig.description());
        CustomItems.removeUnbreakable(ultimate);
    }
    public void equip() {
        Player player = hero.getPlayer();
        player.getEquipment().setHelmet(head);
        player.getEquipment().setChestplate(chest);
        player.getEquipment().setLeggings(legs);
        player.getEquipment().setBoots(boots);
        player.getEquipment().setItemInMainHand(primary);
        player.getInventory().setItem(0, primary.clone());
        player.getInventory().setItem(1, secondary.clone());
        player.getInventory().setItem(2, ultimate.clone());
    }

    public void setSecondaryCharging() {
        CustomItems.setItemModel(secondary, (String) secondaryConfig.visuals().get("chargingModel"));
    }
    public void setSecondaryReady() {
        CustomItems.setItemModel(secondary, (String) secondaryConfig.visuals().get("readyModel"));
    }

    public void setUltimateCharging() {
        CustomItems.setItemModel(ultimate, (String) ultimateConfig.visuals().get("chargingModel"));
        CustomItems.unEnchant(ultimate);
    }
    public void setUltimateReady() {
        CustomItems.setItemModel(ultimate, (String) ultimateConfig.visuals().get("readyModel"));
        CustomItems.enchant(ultimate);
    }
}
