package me.stolyy.heroes.vfx.effect;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;

public class ItemDisplayBuilder extends AbstractDisplayBuilder<ItemDisplay, ItemDisplayBuilder> {

    private ItemStack item;
    private ItemDisplay.ItemDisplayTransform transformType = ItemDisplay.ItemDisplayTransform.FIXED;

    private ItemDisplayBuilder(Location location) {
        super(location);
    }

    public static ItemDisplayBuilder create(Location location) {
        return new ItemDisplayBuilder(location);
    }

    public ItemDisplayBuilder item(ItemStack item) {
        this.item = item;
        return this;
    }

    public ItemDisplayBuilder item(ItemBuilder itemBuilder) {
        this.item = itemBuilder.build();
        return this;
    }

    public ItemDisplayBuilder modelData(String hero, String texture, String skin) {
        this.item = ItemBuilder.from(org.bukkit.Material.STONE)
                .modelData(hero, texture, skin)
                .build();
        return this;
    }

    /**
     * Sets the transform type (e.g., HEAD, GROUND, FIXED, THIRDPERSON_RIGHT).
     */
    public ItemDisplayBuilder transformType(ItemDisplay.ItemDisplayTransform transform) {
        this.transformType = transform;
        return this;
    }

    @Override
    public ItemDisplay spawn() {
        if (location.getWorld() == null) return null;
        Location spawnAt = spawnLocation();
        return (ItemDisplay) spawnAt.getWorld().spawnEntity(spawnAt, EntityType.ITEM_DISPLAY, CreatureSpawnEvent.SpawnReason.CUSTOM, entity -> {
            ItemDisplay display = (ItemDisplay) entity;
            applyBase(display);
            if (item != null) {
                display.setItemStack(item);
            }
            display.setItemDisplayTransform(transformType);
        });
    }
}
