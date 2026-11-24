package me.stolyy.heroes.vfx.effect;

import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** One parsed visual sub-effect (particles or item display) dispatched by {@link VisualEffect}. */
interface VisualSubEffect {
    void play(PlayContext ctx);

    boolean canSpawnDisplay();

    @Nullable ItemDisplay spawnPersistentSingle(Location location, @Nullable String liveSkin);

    void spawnPersistentAll(Location location, @Nullable Vector direction,
                            List<ItemDisplay> sink, @Nullable String liveSkin);

    double modelBackOffset();
}
