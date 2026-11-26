package me.stolyy.heroes.vfx.effect;

import me.stolyy.heroes.Heroes;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class BlockDisplayBuilder extends AbstractDisplayBuilder<BlockDisplay, BlockDisplayBuilder> {

    private BlockData blockData;

    private BlockDisplayBuilder(Location location) {
        super(location);
    }

    public static BlockDisplayBuilder create(Location location) {
        return new BlockDisplayBuilder(location);
    }

    public BlockDisplayBuilder block(BlockData blockData) {
        this.blockData = blockData;
        return this;
    }

    public BlockDisplayBuilder block(Material material) {
        if (!material.isBlock()) {
            throw new IllegalArgumentException("Material is not a block: " + material);
        }
        this.blockData = material.createBlockData();
        return this;
    }

    /**
     * Probes the floor at {@code groundProbe} (stepping down one block if air) and, when a solid
     * block is found, spawns a non-persistent BlockDisplay that rises by {@code peakHeight} and falls
     * back over {@code durationTicks}. Returns {@code null} when there is no solid floor to erupt.
     */
    public static @Nullable BlockDisplay riseAndFall(Location groundProbe, float peakHeight, int durationTicks) {
        if (durationTicks <= 0) {
            throw new IllegalArgumentException("durationTicks must be > 0, got " + durationTicks);
        }
        Block block = groundProbe.getBlock();
        if (block.getType().isAir()) {
            block = groundProbe.clone().add(0, -1, 0).getBlock();
        }
        if (block.getType().isAir() || !block.getType().isSolid()) {
            return null;
        }

        BlockDisplay display = create(block.getLocation().add(0.5, 0, 0.5))
                .block(block.getType())
                .lifetime(durationTicks + 2L)
                .brightness(15, 15)
                .spawn();

        int riseTicks = Math.max(1, durationTicks / 2);
        int fallTicks = Math.max(1, durationTicks - riseTicks);
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> {
            if (!display.isValid()) return;
            transformOver(display, riseTicks, t -> withTranslation(t, new Vector3f(0, peakHeight, 0)));
        }, 1L);
        Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> {
            if (!display.isValid()) return;
            transformOver(display, fallTicks, t -> withTranslation(t, new Vector3f(0, 0, 0)));
        }, 1L + riseTicks);
        return display;
    }

    @Override
    public BlockDisplay spawn() {
        if (blockData == null) {
            throw new IllegalStateException("BlockDisplayBuilder requires blockData before spawn()");
        }
        World world = requireWorld();
        Location spawnAt = spawnLocation();
        return (BlockDisplay) world.spawnEntity(spawnAt, EntityType.BLOCK_DISPLAY, CreatureSpawnEvent.SpawnReason.CUSTOM, entity -> {
            BlockDisplay display = (BlockDisplay) entity;
            applyBase(display);
            display.setBlock(blockData);
        });
    }
}
