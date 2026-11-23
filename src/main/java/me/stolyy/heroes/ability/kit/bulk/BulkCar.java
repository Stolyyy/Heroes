package me.stolyy.heroes.ability.kit.bulk;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.effect.ItemDisplayBuilder;
import me.stolyy.heroes.physics.WallDetection;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class BulkCar extends Projectile {
    private final List<DebrisInstance> debrisInstances = new ArrayList<>();
    
    private final int debrisCount;
    private final double debrisSpeed;
    private final int debrisDuration;
    private final NavigableMap<Double, ItemStack> weightedDebris = new TreeMap<>();
    private double totalWeight = 0;

    public BulkCar(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);

        ConfigurationSection visuals = this.config.getConfigurationSection("visuals");
        ConfigurationSection debrisConf = visuals != null ? visuals.getConfigurationSection("explosion_debris") : null;
        
        if (debrisConf != null) {
            this.debrisCount = debrisConf.getInt("count", 10);
            this.debrisSpeed = debrisConf.getDouble("speed", 0.5);
            this.debrisDuration = debrisConf.getInt("duration", 40);
            
            List<Map<?, ?>> items = debrisConf.getMapList("items");
            for (Map<?, ?> itemMap : items) {
                ItemStack stack;
                if (itemMap.containsKey("material")) {
                    stack = ItemBuilder.from(Material.valueOf(((String) itemMap.get("material")).toUpperCase())).build();
                } else {
                    String hero = (String) itemMap.get("hero");
                    String texture = (String) itemMap.get("texture");
                    String skinName = itemMap.containsKey("skin") ? (String) itemMap.get("skin") : "default";
                    stack = ItemBuilder.from(Material.STICK)
                            .modelData(hero, texture, skinName)
                            .build();
                }

                double weight = itemMap.containsKey("ratio")
                        ? ((Number) itemMap.get("ratio")).doubleValue()
                        : 1.0;
                totalWeight += weight;
                weightedDebris.put(totalWeight, stack);
            }
        } else {
            this.debrisCount = 0;
            this.debrisSpeed = 0;
            this.debrisDuration = 0;
        }
    }

    @Override
    protected void onTick(Hero hero) {
        super.onTick(hero);
        debrisInstances.removeIf(DebrisInstance::tick);
    }

    @Override
    public void onHit(Hero attacker, LivingEntity victim, Location hitLoc) {
        super.onHit(attacker, victim, hitLoc);
        explosionVisuals(hitLoc);
    }

    @Override
    public void onHitWall(Hero attacker, Location location){
        super.onHitWall(attacker, location);
        explosionVisuals(location);
    }

    @Override
    public void onExpire(Hero attacker, Location location){
        super.onExpire(attacker, location);
        explosionVisuals(location);
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        clearDebris();
    }

    @Override
    public void onOwnerLost(Hero hero) {
        super.onOwnerLost(hero);
        clearDebris();
    }

    private void clearDebris() {
        for (DebrisInstance debris : debrisInstances) {
            debris.remove();
        }
        debrisInstances.clear();
    }

    private void explosionVisuals(Location location){
        if (weightedDebris.isEmpty() || debrisCount <= 0) return;

        for (int i = 0; i < debrisCount; i++) {
            ItemStack item = getRandomDebris();
            Vector velocity = Vector.getRandom().subtract(new Vector(0.5, 0.5, 0.5)).normalize().multiply(debrisSpeed);
            debrisInstances.add(new DebrisInstance(location.clone(), item, velocity, debrisDuration));
        }
    }

    private ItemStack getRandomDebris() {
        double value = ThreadLocalRandom.current().nextDouble() * totalWeight;
        return weightedDebris.ceilingEntry(value).getValue();
    }

    private static class DebrisInstance {
        private final ItemDisplay display;
        private final Vector velocity;
        private int life;

        public DebrisInstance(Location loc, ItemStack item, Vector velocity, int duration) {
            this.display = ItemDisplayBuilder.create(loc)
                    .item(item)
                    .brightness(15, 15)
                    .spawn();

            this.display.setTeleportDuration(1);
            this.velocity = velocity;
            this.life = duration;
        }

        public boolean tick() {
            if (life-- <= 0 || display.isDead()) {
                display.remove();
                return true;
            }
            
            Location next = display.getLocation().add(velocity);
            if (WallDetection.detectWall(next, 0.2)) {
                display.remove();
                return true;
            }
            
            display.teleport(next);
            return false;
        }

        public void remove() {
            display.remove();
        }
    }
}

