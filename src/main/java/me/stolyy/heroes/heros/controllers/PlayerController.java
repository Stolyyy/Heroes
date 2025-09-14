package me.stolyy.heroes.heros.controllers;

import net.kyori.adventure.text.Component;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public class PlayerController implements Controller {
    private final Player owner;

    public PlayerController(Player owner) {
        this.owner = owner;
    }

    @Override
    public Player getOwner() {
        return owner;
    }

    @Override
    public void setMaxHealth(double maxHealth) {
        Objects.requireNonNull(owner.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(maxHealth);
        owner.setHealthScale(20.0);
        owner.setHealthScaled(true);
    }

    @Override
    public void setHealth(double health) {
        owner.setHealth(health);
    }

    @Override
    public void sendMessage(Component component){
        owner.sendMessage(component);
    }

    @Override
    public EntityEquipment getEquipment(){
        return owner.getEquipment();
    }

    @Override
    public void setReach(double reach){
        Objects.requireNonNull(owner.getAttribute(Attribute.ENTITY_INTERACTION_RANGE)).setBaseValue(reach);
    }
}
