package me.stolyy.heroes.heros.controllers;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

public interface Controller {
    Entity getOwner();

    void setMaxHealth(double maxHealth);

    void setHealth(double health);

    void setReach(double reach);

    EntityEquipment getEquipment();

    void sendMessage(Component component);
}
