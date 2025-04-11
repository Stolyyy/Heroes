package me.stolyy.heroes.heros.abilities;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.minigame.Game;
import me.stolyy.heroes.game.minigame.GameManager;
import me.stolyy.heroes.game.minigame.GameEnums;
import me.stolyy.heroes.heros.*;
import me.stolyy.heroes.heros.characters.Skullfire;
import me.stolyy.heroes.utility.Interactions;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;


public class AbilityListener implements Listener {

    private static final HashMap<Player, Long> jabCooldowns = new HashMap<>();
    private static final HashMap<Player, Integer> jabCooldown = new HashMap<>();
    private static final HashMap<Player, Double> jabReach = new HashMap<>();
    private static final HashMap<Player, Double> jabDamage = new HashMap<>();

    private static final Map<Player, Integer> maxDoubleJumps = new HashMap<>();
    private static final Map<Player, Integer> doubleJumpCount = new HashMap<>();
    private static final Map<Player, Boolean> canDoubleJump = new HashMap<>();

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
                    hero.useUltimateAbility();
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
    public void onPlayerUsePassive1(PlayerToggleSneakEvent event){
        Player p = event.getPlayer();
        if (!isPlayerEligible(p)) {
            return;
        }
        Hero hero = HeroManager.getHero(p);
        if(hero != null && !p.isSneaking()){
            hero.passiveAbility1();
            //p.sendMessage("Used Passive!");
        }
    }

    //Initiate Jab and Double Jump related variables on join
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
            player.getAttribute(Attribute.PLAYER_ENTITY_INTERACTION_RANGE).setBaseValue(getJabReach(player));
            player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(getJabDamage(player));
            player.setAllowFlight(true);
            player.setMaximumNoDamageTicks(1);
            maxDoubleJumps.put(player, 2);
            doubleJumpCount.put(player, 0);
            canDoubleJump.put(player, true);
        }
    }

    //Handle jab cooldowns
    @EventHandler
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p && event.getEntity() instanceof Player t) {
            event.setCancelled(true);
            long currentTime = System.currentTimeMillis();
            long lastAttackTime = jabCooldowns.getOrDefault(p, 0L);
            if ((currentTime - lastAttackTime) < getJabCooldown(p)) {
                return;
            }
            if(HeroManager.getHero(p) instanceof HeroCooldown hc)
                hc.onPunch();
            Interactions.handleStaticInteraction(getJabDamage(p), 5, p, t);
            jabCooldowns.put(p, currentTime);
        }
    }

    //Double Jump
    @EventHandler
    public void onDoubleJump(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();

        if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
            e.setCancelled(true); // Prevent flight mode
            if (canDoubleJump.getOrDefault(p, false)) {
                int jumpsUsed = doubleJumpCount.getOrDefault(p, 0);
                int maxJumps = maxDoubleJumps.getOrDefault(p, 2);
                if (jumpsUsed < maxJumps) {
                    // Perform double jump
                    performDoubleJump(p);
                    doubleJumpCount.put(p, jumpsUsed + 1);

                    if (doubleJumpCount.get(p) >= maxJumps) {
                        canDoubleJump.put(p, false); // Prevent further double jumps
                    }
                }
            }
        }
    }

    //Double Jump reset
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
            if (p.isOnGround()) {
                Hero hero = HeroManager.getHero(p);
                maxDoubleJumps.put(p, 2);
                doubleJumpCount.put(p, 0);
                canDoubleJump.put(p, true);
                p.setAllowFlight(true); // Allow flight to initiate double jump
            } else if (doubleJumpCount.getOrDefault(p, 0) >= maxDoubleJumps.getOrDefault(p, 2)) {
                // Prevent entering flight mode if max double jumps are used
                p.setAllowFlight(false);
            }
        }
    }

    //code to actually do the double jump
    private static void performDoubleJump(Player p) {
        Vector upVector = new Vector(0, 1, 0);
        for (int i = 0; i < 12; i++) {
            double angle = 2 * Math.PI * i / 12;
            Vector offset = new Vector(Math.cos(angle) * 1, 0, Math.sin(angle) * 1);
            Vector rotated = rotateAroundAxis(offset, upVector, p.getLocation().getYaw());
            Location particleLocation = p.getLocation().clone().add(rotated);
            p.getWorld().spawnParticle(Particle.CLOUD, particleLocation, 1, 0, 0, 0, 0);
        }
        p.playSound(p.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.0f);
        p.setVelocity(p.getLocation().getDirection().multiply(0.90).setY(0.85));
    }
    //circle for a particle ring
    private static Vector rotateAroundAxis(Vector vector, Vector axis, double angle) {
        angle = Math.toRadians(angle);
        Vector parallel = axis.multiply(axis.dot(vector));
        Vector perpendicular = vector.subtract(parallel);
        Vector crossProduct = axis.crossProduct(perpendicular);
        return parallel.add(perpendicular.multiply(Math.cos(angle))).add(crossProduct.multiply(Math.sin(angle)));
    }

    public static int getMaxDoubleJumps(Player player) {
        return maxDoubleJumps.getOrDefault(player, 2);
    }

    public static void setMaxDoubleJumps(Player player, int max) {
        maxDoubleJumps.put(player, max);
    }

    @EventHandler
    public void cancelFallDamage(EntityDamageEvent event) {
        if (event.getCause().equals(EntityDamageEvent.DamageCause.FALL)) {
            event.setCancelled(true);
        }
    }

    public static void setJabCooldown(Player player, int cooldown) {
        jabCooldown.put(player, cooldown);
    }

    public static int getJabCooldown(Player player) {
        return jabCooldown.getOrDefault(player, 500);
    }

    public static void setJabReach(Player player, double reach) {
        jabReach.put(player, reach);
        player.getAttribute(Attribute.PLAYER_ENTITY_INTERACTION_RANGE).setBaseValue(getJabReach(player));
    }


    public static double getJabReach(Player player) {
        return jabReach.getOrDefault(player, 5.5);
    }

    public static void setJabDamage(Player player, double damage) {
        if(damage < 0){
            jabDamage.remove(player);
        } else
            jabDamage.put(player, damage);

        player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(getJabDamage(player));
    }

    public static double getJabDamage(Player player) {
        if(HeroManager.getHero(player) == null) {
            return jabDamage.getOrDefault(player, 1.0);
        } else if(HeroManager.getHero(player).heroType == HeroType.MELEE) {
            return jabDamage.getOrDefault(player, 5.0);
        } else if(HeroManager.getHero(player).heroType == HeroType.HYBRID) {
            return jabDamage.getOrDefault(player, 4.0);
        } else if(HeroManager.getHero(player).heroType == HeroType.RANGED) {
            return jabDamage.getOrDefault(player, 2.0);
        } else {
            return jabDamage.getOrDefault(player, 1.0);
        }
    }

    private static boolean isPlayerEligible(Player player) {
        Game game = GameManager.getPlayerGame(player);
        if (game == null) {
            //player.sendMessage(ChatColor.RED + "DEBUG: You are not in a game!");
            return false;
        }
        if (game.getGameState() != GameEnums.GameState.IN_PROGRESS) {
            //player.sendMessage(ChatColor.RED + "DEBUG: Game is not in progress! Current state: " + game.getGameState());
            return false;
        }
        if (player.getGameMode() != GameMode.ADVENTURE) {
            //player.sendMessage(ChatColor.RED + "DEBUG: You are not in adventure mode! Current mode: " + player.getGameMode());
            return false;
        }
        if (!game.getAlivePlayerList().contains(player)) {
            //player.sendMessage(ChatColor.RED + "DEBUG: You are not in the alive player list!");
            return false;
        }
        return true;
    }
}
