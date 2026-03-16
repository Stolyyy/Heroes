package me.stolyy.heroes.heros.components;

import me.stolyy.heroes.heros.characters.Hero;
import me.stolyy.heroes.heros.configs.MovementConfig;
import me.stolyy.heroes.heros.controllers.PlayerController;
import me.stolyy.legacy.Particles;
import me.stolyy.heroes.utility.effects.Sounds;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Objects;

public class Movement {
    private final Hero hero;
    private final MovementConfig config;

    private int maxDoubleJumps;
    private int doubleJumpCount = 0;
    boolean canJump = true;

    private double moveSpeed, jumpStrength, gravity;

    public Movement(Hero hero, MovementConfig movementConfig) {
        this.hero = hero;
        this.config = movementConfig;

        moveSpeed = config.walkSpeed();
        jumpStrength = config.jumpHeight();
        gravity = config.gravity();

        setPlayerAttributes();

        maxDoubleJumps = config.maxDoubleJumps();
    }

    public void performDoubleJump() {
        Entity owner = hero.getController().getOwner();
        doubleJumpCount++;
        if (doubleJumpCount >= maxDoubleJumps) canJump = false;
        Particles.ring(owner.getLocation(), .75, Particle.CLOUD);
        if (owner instanceof Player player) Sounds.playSoundToPlayer(player, Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.0f);
        owner.setVelocity(owner.getLocation().getDirection()
                .multiply(config.doubleJumpMultiplier())
                .setY(config.doubleJumpYValue()));
    }

    public boolean canDoubleJump() {
        boolean canDoubleJump = canJump && doubleJumpCount < maxDoubleJumps;
        if (hero.getController() instanceof PlayerController pc)
            pc.getOwner().setAllowFlight(canDoubleJump);
        return canDoubleJump;
    }

    public void resetDoubleJumps() {
        doubleJumpCount = 0;
        canJump = true;
        if (hero.getController() instanceof PlayerController pc)
            pc.getOwner().setAllowFlight(true);
    }

    private void setAttributes() {
        setMoveSpeed(moveSpeed);
        setJumpStrength(jumpStrength);
        setGravity(gravity);
    }

    public Movement setMaxDoubleJumps(int maxDoubleJumps) {
        this.maxDoubleJumps = maxDoubleJumps;
        return this;
    }

    public Movement setMoveSpeed(double moveSpeed) {
        this.moveSpeed = moveSpeed;
        Objects.requireNonNull(hero.getPlayer().getAttribute(Attribute.MOVEMENT_SPEED)).setBaseValue(moveSpeed);
        return this;
    }

    public Movement setJumpStrength(double jumpStrength) {
        this.jumpStrength = jumpStrength;
        Objects.requireNonNull(hero.getPlayer().getAttribute(Attribute.JUMP_STRENGTH)).setBaseValue(jumpStrength);
        return this;
    }

    public Movement setGravity(double gravity) {
        this.gravity = gravity;
        Objects.requireNonNull(hero.getPlayer().getAttribute(Attribute.GRAVITY)).setBaseValue(gravity);
        return this;
    }
}
