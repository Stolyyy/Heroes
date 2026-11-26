package me.stolyy.heroes.vfx.effect;

import me.stolyy.heroes.physics.WallDetection;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.util.SpecList;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

/** Spawns and ticks a weighted burst of flying ItemDisplay debris blocks/items that stop on walls. */
public final class DebrisBurst {
    private static final double WALL_CHECK_RADIUS = 0.2;

    private final int count;
    private final double speed;
    private final int durationTicks;
    private final NavigableMap<Double, ItemStack> weightedItems = new TreeMap<>();
    private final double totalWeight;
    private final List<DebrisPiece> active = new ArrayList<>();

    public DebrisBurst(ConfigurationSection config) {
        this.count = Configs.requireInt(config, "count");
        this.speed = Configs.requireDouble(config, "speed");
        this.durationTicks = Configs.requireInt(config, "duration");
        if (count <= 0) throw new IllegalArgumentException("DebrisBurst count must be > 0, got " + count);
        if (speed < 0) throw new IllegalArgumentException("DebrisBurst speed must be >= 0, got " + speed);
        if (durationTicks <= 0) {
            throw new IllegalArgumentException("DebrisBurst duration must be > 0, got " + durationTicks);
        }

        List<?> rawItems = config.getList("items");
        if (rawItems == null || rawItems.isEmpty()) {
            throw new IllegalStateException("DebrisBurst at '" + config.getCurrentPath() + "' requires non-empty 'items' list");
        }

        double weightSum = 0;
        for (Object obj : rawItems) {
            if (!(obj instanceof Map<?, ?> map)) {
                throw new IllegalStateException("DebrisBurst item entry must be a YAML map, got: " + obj);
            }
            ConfigurationSection entry = SpecList.fromMap(map);
            ItemStack stack;
            if (entry.contains("material")) {
                Material mat = Material.valueOf(Configs.requireString(entry, "material").toUpperCase());
                stack = ItemBuilder.from(mat).build();
            } else {
                stack = ItemBuilder.from(Material.STICK).modelData(ModelRef.parse(entry), null).build();
            }
            double weight = entry.contains("ratio") ? Configs.requireDouble(entry, "ratio") : 1.0;
            if (weight <= 0) throw new IllegalArgumentException("DebrisBurst item ratio must be > 0, got " + weight);
            weightSum += weight;
            weightedItems.put(weightSum, stack);
        }
        this.totalWeight = weightSum;
    }

    /** Spawns {@code count} weighted debris displays radiating from {@code origin}. */
    public void spawn(Location origin) {
        for (int i = 0; i < count; i++) {
            ItemStack item = randomItem();
            Vector velocity = Vector.getRandom().subtract(new Vector(0.5, 0.5, 0.5)).normalize().multiply(speed);
            active.add(new DebrisPiece(origin, item, velocity, durationTicks));
        }
    }

    /** Advances active debris displays and removes expired or wall-colliding ones. */
    public void tick() {
        active.removeIf(DebrisPiece::tick);
    }

    /** Immediately removes every active debris display. */
    public void clear() {
        for (DebrisPiece piece : active) {
            piece.remove();
        }
        active.clear();
    }

    private ItemStack randomItem() {
        double value = ThreadLocalRandom.current().nextDouble() * totalWeight;
        return weightedItems.ceilingEntry(value).getValue();
    }

    private static final class DebrisPiece {
        private final ItemDisplay display;
        private final Vector velocity;
        private int remainingTicks;

        DebrisPiece(Location origin, ItemStack item, Vector velocity, int durationTicks) {
            this.display = ItemDisplayBuilder.create(origin)
                    .item(item)
                    .brightness(15, 15)
                    .teleportDuration(1)
                    .spawn();
            this.velocity = velocity;
            this.remainingTicks = durationTicks;
        }

        boolean tick() {
            if (remainingTicks-- <= 0 || display.isDead()) {
                display.remove();
                return true;
            }
            Location next = display.getLocation().add(velocity);
            if (WallDetection.detectWall(next, WALL_CHECK_RADIUS)) {
                display.remove();
                return true;
            }
            display.teleport(AbstractDisplayBuilder.canonical(next));
            return false;
        }

        void remove() {
            display.remove();
        }
    }
}
