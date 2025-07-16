package me.stolyy.heroes.hero.components;

import me.stolyy.heroes.hero.abilities.Ability;
import me.stolyy.heroes.hero.characters.Hero;
import me.stolyy.heroes.hero.configs.AmmoConfig;
import me.stolyy.heroes.utility.effects.CustomItems;
import me.stolyy.heroes.utility.effects.Sounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class Ammo{
    private final Hero hero;
    private final Player player;

    private final AmmoConfig config;
    private int currentAmmo;

    private Ability primary;

    public Ammo(Hero hero, AmmoConfig ammoConfig)
    {
        this.hero = hero;
        this.player = hero.getPlayer();
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
        Sounds.playSoundToWorld(player, primary.getConfig().sounds().get("reload"), 2.0f, 1.0f);
        player.sendMessage(Component.text("Reloading...", NamedTextColor.RED));
    }

    public void updateDisplay() {
        ItemStack primaryItem = player.getInventory().getItem(0);
        if (primaryItem != null) {
            CustomItems.rename(primaryItem, primary.getConfig().name() + " (" + currentAmmo + "/7)");
            CustomItems.setStack(player, 0, Math.min(1, currentAmmo));
        }
    }
}
