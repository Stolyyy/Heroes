package me.stolyy.heroes;

import me.stolyy.heroes.Games.Game;
import me.stolyy.heroes.Game.GameEnums;
import me.stolyy.heroes.Games.GameManager;
import me.stolyy.heroes.heros.Hero;
import me.stolyy.heroes.heros.HeroType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
    private final HeroManager heroManager;
    private final GameManager gameManager;

    private final HashMap<Player, Long> jabCooldowns = new HashMap<>();
    private final HashMap<Player, Integer> jabCooldown = new HashMap<>();
    private final HashMap<Player, Double> jabReach = new HashMap<>();
    private final HashMap<Player, Double> jabDamage = new HashMap<>();

    private final Map<Player, Integer> maxDoubleJumps = new HashMap<>();
    private final Map<Player, Integer> doubleJumpCount = new HashMap<>();
    private final Map<Player, Boolean> canDoubleJump = new HashMap<>();


    public AbilityListener(HeroManager heroManager, GameManager gameManager) {
        this.heroManager = heroManager;
        this.gameManager = gameManager;
    }

    //Primary
    @EventHandler
    public void onPlayerUsePrimary(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (!isPlayerEligibleForAbility(p)) {
            return;
        }
        Hero hero = heroManager.getHero(p);
        if(hero != null){
            if((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) && p.getInventory().getHeldItemSlot() == 0) {
                //p.sendMessage("Used Primary!");
                hero.usePrimaryAbility(p);
            }
        }
    }

    //Secondary & Ultimate
    @EventHandler
    public void onPlayerUseAbility(PlayerItemHeldEvent event) {
        Player p = event.getPlayer();
        if (!isPlayerEligibleForAbility(p)) {
            return;
        }
        Hero hero = heroManager.getHero(p);
        if(hero != null) {
            int newSlot = event.getNewSlot();
            if (newSlot == 1 || newSlot == 2) {
                event.setCancelled(true);
                if (newSlot == 1) {
                    //p.sendMessage("Used Secondary!");
                    hero.useSecondaryAbility(p);
                } else {
                    //p.sendMessage("Used Ultimate!");
                    hero.useUltimateAbility(p);
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
        if (!isPlayerEligibleForAbility(p)) {
            return;
        }
        Hero hero = heroManager.getHero(p);
        if(hero != null && !p.isSneaking()){
            hero.passiveAbility1(p);
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
        if (event.getDamager() instanceof Player) {
            Player p = (Player) event.getDamager();
            long currentTime = System.currentTimeMillis();
            long lastAttackTime = jabCooldowns.getOrDefault(p, 0L);
            if ((currentTime - lastAttackTime) < getJabCooldown(p)) {
                event.setCancelled(true);
                return;
            }
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
    private void performDoubleJump(Player p) {
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
    private Vector rotateAroundAxis(Vector vector, Vector axis, double angle) {
        angle = Math.toRadians(angle);
        Vector parallel = axis.multiply(axis.dot(vector));
        Vector perpendicular = vector.subtract(parallel);
        Vector crossProduct = axis.crossProduct(perpendicular);
        return parallel.add(perpendicular.multiply(Math.cos(angle))).add(crossProduct.multiply(Math.sin(angle)));
    }

    public int getMaxDoubleJumps(Player player) {
        return maxDoubleJumps.getOrDefault(player, 2);
    }

    public void setMaxDoubleJumps(Player player, int max) {
        maxDoubleJumps.put(player, max);
    }

    @EventHandler
    public void cancelFallDamage(EntityDamageEvent event) {
        if (event.getCause().equals(EntityDamageEvent.DamageCause.FALL)) {
            event.setCancelled(true);
        }
    }

    public void setJabCooldown(Player player, int cooldown) {
        jabCooldown.put(player, cooldown);
    }

    public int getJabCooldown(Player player) {
        return jabCooldown.getOrDefault(player, 500);
    }

    public void setJabReach(Player player, double reach) {
        jabReach.put(player, reach);
        player.getAttribute(Attribute.PLAYER_ENTITY_INTERACTION_RANGE).setBaseValue(getJabReach(player));
    }


    public double getJabReach(Player player) {
        return jabReach.getOrDefault(player, 5.5);
    }

    public void setJabDamage(Player player, double damage) {
        jabDamage.put(player, damage);
        player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(getJabDamage(player));
    }

    public double getJabDamage(Player player) {
        if(heroManager.getHero(player) == null) {
            return jabDamage.getOrDefault(player, 1.0);
        } else if(heroManager.getHero(player).getHeroType() == HeroType.MELEE) {
            return jabDamage.getOrDefault(player, 5.0);
        } else if(heroManager.getHero(player).getHeroType() == HeroType.HYBRID) {
            return jabDamage.getOrDefault(player, 4.0);
        } else if(heroManager.getHero(player).getHeroType() == HeroType.RANGED) {
            return jabDamage.getOrDefault(player, 2.0);
        } else {
            return jabDamage.getOrDefault(player, 1.0);
        }
    }

    private boolean isPlayerEligibleForAbility(Player player) {
        Game game = gameManager.getPlayerGame(player);
        return game != null &&
                game.getGameState() == GameEnums.GameState.IN_PROGRESS &&
                player.getGameMode() == GameMode.ADVENTURE &&
                game.isPlayerAlive(player);
    }
}
