package me.stolyy.heroes.hero.mechanics;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroStats;
import me.stolyy.heroes.control.Controller;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.util.Vector;

/**
 * Base multi-jump and mobility mechanic attached to each {@link Hero}, extended by custom
 * kit mobility types (glides, directional launches, dark jumps).
 */
public class DoubleJump {
    protected int maxJumps;
    protected int jumpsUsed;
    protected double xStrength;
    protected double yStrength;

    // Effects
    protected SoundEffect sound;
    protected VisualEffect effect;

    private long rootedUntil;

    public DoubleJump(HeroStats.JumpStats stats) {
        if (stats != null) {
            apply(stats);
        }
    }

    public void apply(HeroStats.JumpStats stats) {
        this.maxJumps = stats.maxDoubleJumps();
        this.jumpsUsed = 0;
        this.xStrength = stats.xStrength();
        this.yStrength = stats.yStrength();

        this.sound = stats.jumpSound();
        this.effect = stats.jumpEffect();
    }

    public boolean performJump(Hero hero) {
        // Frozen at the respawn point means frozen — same gate as usePrimary/useSecondary.
        // Variants that override performJump (SkullMobility, Botglide) carry their own copy.
        if (hero.isRespawning()) return false;
        if (isRooted()) return false;
        if (!canJump(hero)) return false;

        applyVelocity(hero);
        playEffects(hero);
        payCost(hero);
        hero.triggerDoubleJump();

        return true;
    }

    public boolean canJump(Hero hero) {
        return jumpsUsed < maxJumps;
    }

    /** Root: no double jumps for {@code ms}. Time-based, so it needs no cleanup and death resets nothing extra. */
    public void root(long ms) {
        rootedUntil = Math.max(rootedUntil, System.currentTimeMillis() + ms);
    }

    public boolean isRooted() {
        return System.currentTimeMillis() < rootedUntil;
    }

    protected void applyVelocity(Hero hero) {
        Controller controller = hero.getController();
        Vector direction = controller.getLocation().getDirection().multiply(xStrength);
        direction.setY(yStrength);
        controller.setVelocity(direction);
    }

    protected void playEffects(Hero hero) {
        Controller controller = hero.getController();
        if (sound != null) sound.play(controller.getLocation(), controller);
        if (effect != null) effect.play(controller.getLocation());
    }

    protected void payCost(Hero hero) {
        jumpsUsed++;
    }

    public void onLand(Hero hero) {
        resetJumps();
        hero.getController().setAllowFlight(true);
    }

    public void resetJumps() {
        this.jumpsUsed = 0;
    }

    // Launch parameters, exposed for the bot jump-safety projection (reach = x * airtime).
    public double getXStrength() { return xStrength; }
    public double getYStrength() { return yStrength; }

    // Exposed for DashSafety's recovery allowance: a dash that would land over the void is
    // still permitted when a double jump remains to get back onto stage with.
    public int getRemainingJumps() { return maxJumps - jumpsUsed; }

    public void tick(Hero hero) { }

    /** Called while the hero is eliminated/dormant so independent sub-projectiles (e.g. Defecake) keep resolving. */
    public void tickDormant(Hero hero) { }

    public void onDeath(Hero hero) { }

    public void onOwnerLost(Hero hero) { }
}
