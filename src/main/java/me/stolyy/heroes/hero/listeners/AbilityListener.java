package me.stolyy.heroes.hero.listeners;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.minigame.Game;
import me.stolyy.heroes.game.minigame.GameManager;
import me.stolyy.heroes.game.minigame.GameEnums;
import me.stolyy.heroes.heros.*;
import me.stolyy.heroes.hero.components.PassiveDrop;
import me.stolyy.heroes.hero.components.UseSneak;
import me.stolyy.heroes.hero.components.UseSwap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitRunnable;



public class AbilityListener implements Listener {
    //Primary
    @EventHandler
    public void onPlayerUsePrimary(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (!isPlayerEligible(p)) {
            return;
        }
        Hero hero = HeroManager.getHero(p);
        if(hero != null){
            if((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) && p.getInventory().getHeldItemSlot() == 0) {
                //p.sendMessage("Used Primary!");
                hero.usePrimaryAbility();
            }
        }
    }

    //Secondary & Ultimate
    @EventHandler
    public void onPlayerUseAbility(PlayerItemHeldEvent event) {
        Player p = event.getPlayer();
        if (!isPlayerEligible(p)) {
            return;
        }
        Hero hero = HeroManager.getHero(p);
        if(hero != null) {
            int newSlot = event.getNewSlot();
            if (newSlot == 1 || newSlot == 2) {
                event.setCancelled(true);
                if (newSlot == 1) {
                    //p.sendMessage("Used Secondary!");
                    hero.useSecondaryAbility();
                } else {
                    //p.sendMessage("Used Ultimate!");
                    if(GameManager.getPlayerGame(p).ultimateEnabled(p))
                        hero.useUltimateAbility();
                     else
                        p.sendMessage(Component.text("Your team has ultimate abilities disabled!", NamedTextColor.RED));
                }
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        p.getInventory().setHeldItemSlot(0);
                    }
                }.runTaskLater(Heroes.getInstance(), 1L);
            }
        }
    }

    //Passive
    @EventHandler
    public void onPlayerUseShift(PlayerToggleSneakEvent event){
        Player p = event.getPlayer();
        if (!isPlayerEligible(p)) {
            return;
        }
        Hero hero = HeroManager.getHero(p);
        if(hero instanceof UseSneak hs && !p.isSneaking()){
            hs.usePassiveSneak();
            //p.sendMessage("Used Passive!");
        }
    }

    //Passive 2
    @EventHandler
    public void onPlayerUseDrop(PlayerDropItemEvent event) {
        Player p = event.getPlayer();
        if (!isPlayerEligible(p)) {
            return;
        }
        Hero hero = HeroManager.getHero(p);
        if(hero instanceof PassiveDrop hd && !p.isSneaking()){
            hd.usePassiveDrop();
            //p.sendMessage("Used Passive!");
        }
    }

    //Passive 3
    @EventHandler
    public void onPlayerUseSwap(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        if (!isPlayerEligible(p)) {
            return;
        }
        Hero hero = HeroManager.getHero(p);
        if(hero instanceof UseSwap hs && !p.isSneaking()){
            hs.useSwap();
            //p.sendMessage("Used Passive!");
        }
    }


    private static boolean isPlayerEligible(Player player) {
        Game game = GameManager.getPlayerGame(player);
        if (game == null) {
            //player.sendMessage(ChatColor.RED + "DEBUG: You are not in a game!");
            return false;
        }
        if (game.gameState() != GameEnums.GameState.IN_PROGRESS) {
            //player.sendMessage(ChatColor.RED + "DEBUG: Game is not in progress! Current state: " + game.gameState());
            return false;
        }
        if (player.getGameMode() != GameMode.ADVENTURE) {
            //player.sendMessage(ChatColor.RED + "DEBUG: You are not in adventure mode! Current mode: " + player.getGameMode());
            return false;
        }
        //player.sendMessage(ChatColor.RED + "DEBUG: You are not in the alive player list!");
        return game.onlinePlayers(false).contains(player);
    }
}
