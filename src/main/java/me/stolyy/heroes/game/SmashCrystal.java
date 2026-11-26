package me.stolyy.heroes.game;

import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.vfx.effect.AbstractDisplayBuilder;
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

import java.util.Set;

public class SmashCrystal {

    public enum CrystalState {
        SPAWNING,
        ACTIVE
    }

    // Config fields (read per instance so /balance reload takes effect and class init doesn't depend on load order)
    private final double width;
    private final double height;
    private final double yOffset;
    private final int spawnDelayTicks;
    private final float spinSpeed;
    private final double bobAmplitude;
    private final double bobSpeed;

    private final Location baseLocation;
    private final ItemDisplay displayEntity;
    private final TextDisplay textEntity;
    private CrystalState state;
    private int tickCounter;

    public SmashCrystal(Location location) {
        this.width = GameConfigManager.getCrystalWidth();
        this.height = GameConfigManager.getCrystalHeight();
        this.yOffset = GameConfigManager.getCrystalYOffset();
        this.spawnDelayTicks = GameConfigManager.getCrystalSpawnDelayTicks();
        this.spinSpeed = GameConfigManager.getCrystalSpinSpeed();
        this.bobAmplitude = GameConfigManager.getCrystalBobAmplitude();
        this.bobSpeed = GameConfigManager.getCrystalBobSpeed();
        this.baseLocation = location.clone().add(0, yOffset, 0);
        this.state = CrystalState.SPAWNING;
        this.tickCounter = 0;

        this.displayEntity = ItemDisplayBuilder.create(baseLocation)
                .item(new ItemStack(Material.END_CRYSTAL))
                .transformType(ItemDisplay.ItemDisplayTransform.GROUND)
                .billboard(Display.Billboard.FIXED)
                .scale(1.0f)
                .viewRange(2.0f)
                .spawn();

        Location textLoc = baseLocation.clone().add(0, 1.5, 0);
        this.textEntity = TextDisplayBuilder.create(textLoc)
                .text(Component.text("Spawning...", NamedTextColor.YELLOW))
                .billboard(Display.Billboard.CENTER)
                .viewRange(2.0f)
                .spawn();
        displayEntity.addPassenger(textEntity);
    }

    /**
     * Ticks the crystal logic each game tick.
     *
     * @param players the set of players to check for pickup overlap
     * @return the player who picked up the crystal, or null if not picked up this tick
     */
    public Player tick(Set<Player> players) {
        if (displayEntity.isDead()) return null;

        tickCounter++;

        float spinRad = (float) Math.toRadians(spinSpeed * tickCounter);
        AbstractDisplayBuilder.spin(displayEntity, spinRad);

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
}
