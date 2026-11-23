package me.stolyy.heroes.game;

import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.vfx.effect.ItemDisplayBuilder;
import me.stolyy.heroes.vfx.effect.TextDisplayBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.joml.Quaternionf;

import java.util.Set;

public class SmashCrystal {

    public enum CrystalState {
        SPAWNING,
        ACTIVE
    }

    // Config fields
    private static final double width = GameConfigManager.getCrystalWidth();
    private static final double height = GameConfigManager.getCrystalHeight();
    private static final double yOffset = GameConfigManager.getCrystalYOffset();
    private static final int spawnDelayTicks = GameConfigManager.getCrystalSpawnDelayTicks();
    private static final float spinSpeed = GameConfigManager.getCrystalSpinSpeed();
    private static final double bobAmplitude = GameConfigManager.getCrystalBobAmplitude();
    private static final double bobSpeed = GameConfigManager.getCrystalBobSpeed();

    // Instance fields
    private final Location baseLocation;
    private final ItemDisplay displayEntity;
    private final TextDisplay textEntity;
    private CrystalState state;
    private int tickCounter;

    public SmashCrystal(Location location) {
        this.baseLocation = location.clone().add(0, yOffset, 0);
        this.state = CrystalState.SPAWNING;
        this.tickCounter = 0;

        // Spawn the ItemDisplay crystal model
        this.displayEntity = ItemDisplayBuilder.create(baseLocation)
                .item(new ItemStack(Material.END_CRYSTAL))
                .transformType(ItemDisplay.ItemDisplayTransform.GROUND)
                .billboard(Display.Billboard.FIXED)
                .scale(1.0f)
                .viewRange(2.0f)
                .spawn();

        // Spawn the TextDisplay as a passenger of the ItemDisplay
        if (displayEntity != null) {
            Location textLoc = baseLocation.clone().add(0, 1.5, 0);
            this.textEntity = TextDisplayBuilder.create(textLoc)
                    .text(Component.text("Spawning...", NamedTextColor.YELLOW))
                    .billboard(Display.Billboard.CENTER)
                    .viewRange(2.0f)
                    .spawn();

            if (textEntity != null) {
                displayEntity.addPassenger(textEntity);
            }
        } else {
            this.textEntity = null;
        }
    }

    /**
     * Ticks the crystal logic each game tick.
     *
     * @param players the set of players to check for pickup overlap
     * @return the player who picked up the crystal, or null if not picked up this tick
     */
    public Player tick(Set<Player> players) {
        if (displayEntity == null || displayEntity.isDead()) return null;

        tickCounter++;

        // Apply spin: rotate Y by spinSpeed degrees each tick
        float spinRad = (float) Math.toRadians(spinSpeed * tickCounter);
        org.bukkit.util.Transformation trans = displayEntity.getTransformation();
        org.joml.Quaternionf yRotation = new Quaternionf().rotateY(spinRad);
        displayEntity.setTransformation(new org.bukkit.util.Transformation(
                trans.getTranslation(),
                yRotation,
                trans.getScale(),
                trans.getRightRotation()
        ));

        // Apply bob: move entity up and down
        double bobY = baseLocation.getY() + bobAmplitude * Math.sin(tickCounter * bobSpeed);
        Location currentLoc = displayEntity.getLocation();
        currentLoc.setY(bobY);
        displayEntity.teleport(currentLoc);

        if (state == CrystalState.SPAWNING) {
            int remainingTicks = spawnDelayTicks - tickCounter;
            int remainingSeconds = (int) Math.ceil(remainingTicks / 20.0);
            if (textEntity != null && !textEntity.isDead()) {
                if (remainingSeconds > 0) {
                    textEntity.text(Component.text("Spawning in " + remainingSeconds + "s", NamedTextColor.YELLOW));
                } else {
                    textEntity.text(Component.text("Spawning...", NamedTextColor.YELLOW));
                }
            }

            if (tickCounter >= spawnDelayTicks) {
                state = CrystalState.ACTIVE;
                tickCounter = 0;
                if (textEntity != null && !textEntity.isDead()) {
                    textEntity.text(Component.text("Smash Crystal!", NamedTextColor.AQUA)
                            .decoration(TextDecoration.BOLD, true));
                }
            }
            return null;
        }

        // ACTIVE: check player overlap
        Location loc = displayEntity.getLocation();
        double halfW = width / 2.0;
        BoundingBox crystalBox = new BoundingBox(
                loc.getX() - halfW,
                loc.getY(),
                loc.getZ() - halfW,
                loc.getX() + halfW,
                loc.getY() + height,
                loc.getZ() + halfW
        );

        for (Player player : players) {
            if (crystalBox.overlaps(player.getBoundingBox())) {
                return player;
            }
        }

        return null;
    }

    /**
     * Removes the crystal entities from the world.
     */
    public void remove() {
        if (textEntity != null && !textEntity.isDead()) {
            textEntity.remove();
        }
        if (displayEntity != null && !displayEntity.isDead()) {
            displayEntity.remove();
        }
    }

    /** The crystal's anchor location (bots pathfind here while it's up). */
    public Location location() {
        return baseLocation.clone();
    }

    public CrystalState getState() {
        return state;
    }
}
