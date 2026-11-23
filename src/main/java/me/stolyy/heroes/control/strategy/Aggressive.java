package me.stolyy.heroes.control.strategy;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.physics.WallDetection;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

public class Aggressive implements Strategy {
    private final ConfigurationSection config;

    public Aggressive(ConfigurationSection config) {
        this.config = config;
    }

    @Override
    public double getPreferredDistance() {
        return config.getDouble("preferred_distance", 2.0);
    }

    @Override
    public boolean shouldUseAbility(Hero hero, AbilityType type, LivingEntity target, double distance) {
        double range = config.getDouble("ability_ranges." + type.name().toLowerCase(), 5.0);
        return distance <= range;
    }

    // Aggressive never panics — getPanicDistance() stays at the interface's 0 default.

    @Override
    public double getUltMinTargetHpPercent() {
        return config.getDouble("ult_min_target_hp_percent", 0.0);
    }

    @Override
    public double getUltMinSelfHpPercent() {
        return config.getDouble("ult_min_self_hp_percent", 0.0);
    }

    @Override
    public double getFarDistance() {
        return config.getDouble("far_distance", 15.0);
    }

    @Override
    public boolean canDoubleJump() {
        return config.getBoolean("can_double_jump", true);
    }

    @Override
    public boolean performMovement(Hero hero, BotController controller, LivingEntity target) {
        if(!canDoubleJump()) { return false;}

        Location botLoc = controller.getLocation();
        Location targetLoc = target.getLocation();
        double distance = botLoc.distance(targetLoc);

        // Double jump velocity goes wherever the entity FACES (DoubleJump.applyVelocity uses
        // getDirection), and Citizens navigation leaves the bot facing its last path step — jumping
        // blind is how bots hurl themselves off the map. Face the target first, and VETO chase
        // jumps whose landing is over the void (JumpSafety): a preserved double jump is recovery
        // fuel. (The old "Case 1: above void" escape jump lives in the RECOVER task now, which
        // preempts FIGHT — this method never runs over the void.)

        // Case 1: Target is above and unreachable by normal jump
        double heightDiff = targetLoc.getY() - botLoc.getY();
        if (heightDiff > 1.5 && JumpSafety.isChaseJumpSafe(hero, controller, target)) {
            controller.lookAt(target.getEyeLocation());
            hero.getMobility().performJump(hero);
            return true;
        }

        // Case 2: Target is very far — HORIZONTAL distance only. 3D distance read a pure
        // height gap as "far", so an airborne bot directly above its target (cakemonster)
        // kept re-jumping on top of them and juggled itself ever higher. Floored at the
        // ring (preferred + 3) like MidRange — a no-op for melee (preferred ~2.5), but it
        // keeps any Aggressive hero with a real standoff distance honest.
        double horizontalDistance = Math.hypot(
                targetLoc.getX() - botLoc.getX(), targetLoc.getZ() - botLoc.getZ());
        if (horizontalDistance > Math.max(getFarDistance(), getPreferredDistance() + 3.0)
                && JumpSafety.isChaseJumpSafe(hero, controller, target)) {
            controller.lookAt(target.getEyeLocation());
            hero.getMobility().performJump(hero);
            return true;
        }

        return false;
    }

}
