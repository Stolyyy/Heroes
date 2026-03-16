package me.stolyy.heroes.game.menus;

import me.stolyy.heroes.game.minigame.TeamSettings;
import me.stolyy.legacy.Equipment;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public abstract class AbstractTeamSettingsGUI extends GUI{
    protected final PartyModeGUI partyGUI;
    protected TeamSettings settings;

    public AbstractTeamSettingsGUI(Player player, int size, String title, TeamSettings settings, PartyModeGUI partyGUI) {
        super(player, size, title);
        this.settings = settings;
        this.partyGUI = partyGUI;
    }
    protected void update(){
        inventoryItems.put(9, Equipment.customItem(30, settings.lives() + " Lives"));
        inventoryItems.put(11, settings.maxHealth() > 100 ? createItem(Material.ENCHANTED_GOLDEN_APPLE, "Prestige Health: Enabled") : createItem(Material.APPLE, "Prestige Health: Disabled"));
        inventoryItems.put(13, settings.ultimatesEnabled() ? Equipment.customItem(7, "Ultimates: Enabled") : Equipment.customItem(6, "Ultimates: Disabled"));
        inventoryItems.put(15, settings.friendlyFire() ? createItem(Material.GOLDEN_SWORD, "Friendly Fire: Enabled") : createItem(Material.STONE_SWORD, "Friendly Fire: Disabled"));
        inventoryItems.put(17, settings.randomizeHeroes() ? Equipment.customItem(34, "Random Heroes: Enabled") : Equipment.customItem(35, "Random Heroes: Disabled"));
        super.update();
    }

    protected void handleLivesClick() {
        int newLives = switch (settings.lives()) {
            case 1 -> 2;
            case 2 -> 3;
            case 3 -> 4;
            case 4 -> 1;
            default -> 3;
        };
        settings.setLives(newLives);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        update();
    }

    protected void handlePrestigeHealthClick() {
        if (settings.maxHealth() == 100) {
            settings.setMaxHealth(120);
        } else {
            settings.setMaxHealth(100);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        update();
    }

    protected void handleUltimatesClick() {
        settings.setUltimatesEnabled(!settings.ultimatesEnabled());
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        update();
    }

    protected void handleFriendlyFireClick() {
        settings.setFriendlyFire(!settings.friendlyFire());
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        update();
    }

    protected void handleRandomHeroesClick() {
        settings.setRandomizeHeroes(!settings.randomizeHeroes());
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        update();
    }
}
