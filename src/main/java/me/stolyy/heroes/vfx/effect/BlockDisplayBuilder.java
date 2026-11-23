package me.stolyy.heroes.vfx.effect;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;

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
        if (material.isBlock()) {
            this.blockData = material.createBlockData();
        }
        return this;
    }

    @Override
    public BlockDisplay spawn() {
        if (location.getWorld() == null) return null;
        Location spawnAt = spawnLocation();
        return (BlockDisplay) spawnAt.getWorld().spawnEntity(spawnAt, EntityType.BLOCK_DISPLAY, CreatureSpawnEvent.SpawnReason.CUSTOM, entity -> {
            BlockDisplay display = (BlockDisplay) entity;
            applyBase(display);
            if (blockData != null) {
                display.setBlock(blockData);
            }
        });
    }
}
