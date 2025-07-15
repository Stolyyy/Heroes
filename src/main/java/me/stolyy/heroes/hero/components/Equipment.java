package me.stolyy.heroes.hero.components;

import me.stolyy.heroes.hero.characters.Hero;
import me.stolyy.heroes.hero.data.HeroData.EquipmentData;
import me.stolyy.heroes.utility.effects.CustomItems;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class Equipment {
    private final Hero hero;
    private final EquipmentData data;

    private final ItemStack head, chest, legs, boots;
    private final ItemStack primary, secondary, ultimate;

    public Equipment(Hero hero, EquipmentData equipmentData) {
        this.hero = hero;
        this.data = equipmentData;

        head = CustomItems.customItem(data.getHeadModel(), data.getHeadName());
        chest = CustomItems.customArmor("chest", data.getChestColor(), data.getChestName());
        legs = CustomItems.customArmor("legs", data.getLegsColor(), data.getLegsName());
        boots = CustomItems.customArmor("boots", data.getBootsColor(), data.getBootsName());

        primary = CustomItems.customItem(data.getWeaponModel(), data.getWeaponName());
        CustomItems.addLore(primary, data.getWeaponDescription());
        secondary = CustomItems.customItem(data.getSecondaryChargingModel(), data.getSecondaryName());
        CustomItems.addLore(secondary, data.getSecondaryDescription());
        CustomItems.removeUnbreakable(secondary);
        ultimate = CustomItems.customItem(data.getUltimateChargingModel(), data.getUltimateName());
        CustomItems.addLore(ultimate, data.getUltimateDescription());
        CustomItems.removeUnbreakable(ultimate);
    }
    public void equip() {
        Player player = hero.getPlayer();

        player.getEquipment().setHelmet(head);
        player.getEquipment().setChestplate(chest);
        player.getEquipment().setLeggings(legs);
        player.getEquipment().setBoots(boots);
        player.getEquipment().setItemInMainHand(primary);
        player.getInventory().setItem(0, primary);
        player.getInventory().setItem(1, secondary);
        player.getInventory().setItem(2, ultimate);
    }

    public void setSecondaryCharging() {
        CustomItems.setItemModel(secondary, data.getSecondaryChargingModel());
        PlayerInventory inv = hero.getPlayer().getInventory();
        inv.setItem(1, this.secondary);
    }
    public void setSecondaryReady() {
        CustomItems.setItemModel(secondary, data.getSecondaryReadyModel());
        PlayerInventory inv = hero.getPlayer().getInventory();
        inv.setItem(1, this.secondary);
    }

    public void setUltimateCharging() {
        CustomItems.setItemModel(ultimate, data.getUltimateChargingModel());
        CustomItems.unEnchant(ultimate);
        PlayerInventory inv = hero.getPlayer().getInventory();
        inv.setItem(2, this.ultimate);
    }
    public void setUltimateReady() {
        CustomItems.setItemModel(ultimate, data.getUltimateReadyModel());
        CustomItems.enchant(ultimate);
        PlayerInventory inv = hero.getPlayer().getInventory();
        inv.setItem(2, this.ultimate);
    }
}
