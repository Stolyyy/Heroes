package me.stolyy.legacy;


import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ArmorStands {
    public static ArmorStand summonArmorStand(Location location, int customModelData){
        ArmorStand armorStand = (ArmorStand) location.getWorld().spawnEntity(location.clone().subtract(0,1.85,0), EntityType.ARMOR_STAND);
        armorStand.setRotation(location.getPitch(), location.getYaw());
        armorStand.setVisible(false);
        armorStand.setGravity(false);
        armorStand.setMarker(true);
        ItemStack item = new ItemStack(Material.CARROT_ON_A_STICK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(customModelData);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        armorStand.getEquipment().setHelmet(item);

        return armorStand;
    }
}
