package me.stolyy.heroes.heros.components;

import me.stolyy.heroes.heros.abilities.Ability;
import me.stolyy.heroes.heros.characters.Hero;
import me.stolyy.heroes.heros.configs.AmmoConfig;
import me.stolyy.heroes.heros.controllers.Controller;
import me.stolyy.heroes.heros.controllers.PlayerController;
import me.stolyy.heroes.utility.effects.CustomItems;
import me.stolyy.heroes.utility.effects.Sounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class Ammo{
    private final Controller controller;

    private final AmmoConfig config;
    private int currentAmmo;

    private Ability primary;

    public Ammo(Hero hero, AmmoConfig ammoConfig)
    {
        this.controller = hero.getController();
        this.config = ammoConfig;
        this.primary = hero.getPrimary();

        currentAmmo = maxAmmo();
    }

    public int maxAmmo()
    {
        return config.maxAmmo();
    }

    public void shoot()
    {
        currentAmmo--;
        if(currentAmmo <= 0) reload();
        updateDisplay();
    }

    public void reload()
    {
        primary.getCooldown().start();
        Sounds.playSoundToWorld(controller.getOwner().getLocation(), primary.getConfig().sounds().get("reload"), 2.0f, 1.0f);
        controller.sendMessage(Component.text("Reloading...", NamedTextColor.RED));
    }

    public void updateDisplay()
    {
        if(!(controller instanceof PlayerController pc)) return;
        Player player = pc.getOwner();
        ItemStack primaryItem = player.getInventory().getItem(0);
        if (primaryItem != null) {
            CustomItems.rename(primaryItem, primary.getConfig().name() + " (" + currentAmmo + "/7)");
            CustomItems.setStack(player, 0, Math.min(1, currentAmmo));
        }
    }
}
