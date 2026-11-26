package me.stolyy.heroes.game.rules;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GamePlayer;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import me.stolyy.heroes.vfx.ui.gui.HeroSelectGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Respawn hooks: {@link #onCountdownStart} fires when the respawn countdown begins
 * (death with a respawn coming), {@link #apply} at the end of Game.handleRespawn once
 * the player is back in play.
 */
public enum RespawnAction {

    NONE,

    /**
     * TDM: the hero-select never auto-opens — the tagged "Change Hero" hotbar item is the
     * only way in (see RespawnRepickListener), and the respawn countdown PAUSES while the
     * pick GUI is open (Game.gameTick). The pick lands on the GamePlayer as pendingHero and
     * Game.handleRespawn performs the actual swap when the timer fires. No pick = keep the
     * current hero. The item is removed again in {@link #apply}.
     */
    OPEN_HERO_SELECT {
        @Override
        public void onCountdownStart(Player player, Game game) {
            giveRepickItem(player);
        }

        @Override
        public void apply(Player player, Game game) {
            removeRepickItem(player);
        }
    };

    // PDC tag marking the countdown-only "Change Hero" hotbar item.
    private static final NamespacedKey REPICK_KEY = new NamespacedKey(Heroes.getInstance(), "respawn_repick");
    // Middle slot — 8..5 belong to the passive reference cards (CosmeticManager).
    private static final int REPICK_SLOT = 4;

    /** Fired when the respawn countdown starts. */
    public void onCountdownStart(Player player, Game game) {
    }

    /** Fired at the end of Game.handleRespawn, after the player is back in play. */
    public void apply(Player player, Game game) {
    }

    /** Opens the respawn hero re-pick GUI (shared by the countdown open and the item reopen). */
    public static void openPickGui(Player player, Game game) {
        GamePlayer gamePlayer = game.getGamePlayer(player.getUniqueId());
        GUIManager.open(player, HeroSelectGUI.forRespawn(player, gamePlayer::setPendingHero));
    }

    public static boolean isRepickItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(REPICK_KEY, PersistentDataType.BYTE);
    }

    private static void giveRepickItem(Player player) {
        ItemStack item = ItemBuilder.from(Material.RECOVERY_COMPASS)
                .name("<gold>Change Hero")
                .lore(Component.text("Right-click to pick your next hero", NamedTextColor.GRAY))
                .build();
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(REPICK_KEY, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        player.getInventory().setItem(REPICK_SLOT, item);
    }

    private static void removeRepickItem(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (isRepickItem(player.getInventory().getItem(i))) {
                player.getInventory().setItem(i, null);
            }
        }
    }
}
