package me.stolyy.heroes.heros;

import me.stolyy.heroes.Games.Game;
import me.stolyy.heroes.Games.GameEnums;
import me.stolyy.heroes.Games.GameManager;
import me.stolyy.heroes.Hero;
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

    public final Map<Player, Integer> maxDoubleJumps = new HashMap<>();
    private final Map<Player, Integer> doubleJumpCount = new HashMap<>();
    private final Map<Player, Boolean> canDoubleJump = new HashMap<>();


    public AbilityListener(HeroManager heroManager, GameManager gameManager) {
        this.heroManager = heroManager;
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerUseAbility(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        Game game = gameManager.getPlayerGame(p);

        // Check if the player is in an active game
        if (game == null || game.getGameState() != GameEnums.GameState.IN_PROGRESS) {
            return;
        }
        Hero hero = heroManager.getPlayerCharacter(p);
        if(hero != null){
            if((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) && p.getInventory().getHeldItemSlot() == 0) {
                p.sendMessage("Used Primary!");
                hero.usePrimaryAbility(p);
            } else if((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) && p.getInventory().getHeldItemSlot() == 1) {
                p.sendMessage("Used Secondary!");
                hero.useSecondaryAbility(p);
            } else if((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) && p.getInventory().getHeldItemSlot() == 2) {
                p.sendMessage("Used Ultimate!");
                hero.useUltimateAbility(p);
            }
        }
    }

    @EventHandler
    public void onPlayerUsePassive1(PlayerToggleSneakEvent event){
        Player p = event.getPlayer();
        Game game = gameManager.getPlayerGame(p);

        // Check if the player is in an active game
        if (game == null || game.getGameState() != GameEnums.GameState.IN_PROGRESS) {
            return;
        }
        Hero hero = heroManager.getPlayerCharacter(p);
        if(hero != null && !p.isSneaking()){
            hero.passiveAbility1(p);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
            player.getAttribute(Attribute.PLAYER_ENTITY_INTERACTION_RANGE).setBaseValue(getJabReach(player));
            player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(getJabDamage(player));
            player.setAllowFlight(true);
            doubleJumpCount.put(player, 0);
            canDoubleJump.put(player, true);
            player.setMaximumNoDamageTicks(1);
        }
    }
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

    @EventHandler
    public void onDoubleJump(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();

        if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
            e.setCancelled(true); // Prevent flight mode
            if (canDoubleJump.getOrDefault(p, false)) {
                int jumpsUsed = doubleJumpCount.getOrDefault(p, 0);
                if (jumpsUsed < maxDoubleJumps.getOrDefault(p, 2)) {
                    // Set velocity for double jump
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
                    doubleJumpCount.put(p, jumpsUsed + 1);

                    if (doubleJumpCount.get(p) >= maxDoubleJumps.getOrDefault(p, 2)) {
                        canDoubleJump.put(p, false); // Prevent further double jumps
                    }
                }
            }
        }
    }

    private Vector rotateAroundAxis(Vector vector, Vector axis, double angle) {
        angle = Math.toRadians(angle);
        Vector parallel = axis.multiply(axis.dot(vector));
        Vector perpendicular = vector.subtract(parallel);
        Vector crossProduct = axis.crossProduct(perpendicular);
        return parallel.add(perpendicular.multiply(Math.cos(angle))).add(crossProduct.multiply(Math.sin(angle)));
    }

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
        if(heroManager.getPlayerCharacter(player).getHeroType() == HeroType.MELEE) {
            return jabDamage.getOrDefault(player, 5.0);
        } else if(heroManager.getPlayerCharacter(player).getHeroType() == HeroType.HYBRID) {
            return jabDamage.getOrDefault(player, 4.0);
        } else if(heroManager.getPlayerCharacter(player).getHeroType() == HeroType.RANGED) {
            return jabDamage.getOrDefault(player, 2.0);
        } else {
            return jabDamage.getOrDefault(player, 1.0);
        }
    }
}
