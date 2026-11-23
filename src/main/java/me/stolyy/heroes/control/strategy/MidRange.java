package me.stolyy.heroes.control.strategy;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.physics.WallDetection;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

public class MidRange implements Strategy {
    private final ConfigurationSection config;

    public MidRange(ConfigurationSection config) {
        this.config = config;
    }

    @Override
    public double getPreferredDistance() {
        return config.getDouble("preferred_distance", 5.0);
    }

    @Override
    public boolean shouldUseAbility(Hero hero, AbilityType type, LivingEntity target, double distance) {
        double range = config.getDouble("ability_ranges." + type.name().toLowerCase(), 7.0);
        return distance <= range;
    }

    @Override
    public double getPanicDistance() {
        return config.getDouble("panic_distance", 0.0);
    }

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
        return config.getDouble("far_distance", 12.0);
    }

    @Override
    public boolean canDoubleJump() {
        return config.getBoolean("can_double_jump", true);
    }

    @Override
    public boolean performMovement(Hero hero, BotController controller, LivingEntity target) {
        Location botLoc = controller.getLocation();
        Location targetLoc = target.getLocation();
        double distance = botLoc.distance(targetLoc);

        // Face the target before every jump — DoubleJump launches along the entity's facing, and
        // Citizens navigation leaves the bot facing its last path step (see Aggressive). Chase
        // jumps are vetoed when they'd land over the void; the old above-void escape case lives
        // in the RECOVER task, which preempts FIGHT.

        // Chase jumps must respect the RING: far_distance is typically smaller than a ranged
        // bot's preferred_distance (punishgers: far 12, preferred 25), and an unfloored far
        // jump hopped the bot from its ring all the way into panic range, over and over. No
        // chase jump fires unless the target is beyond preferred + 3 (BaseBotAI.RING_SLACK) —
        // inside that, spacing is the ring/backpedal's job and the bot shoots instead.
        double horizontalDistance = Math.hypot(
                targetLoc.getX() - botLoc.getX(), targetLoc.getZ() - botLoc.getZ());
        double ringFloor = getPreferredDistance() + 3.0;
        if (horizontalDistance <= ringFloor) return false;

        // Case 1: Target is above
        double heightDiff = targetLoc.getY() - botLoc.getY();
        if (canDoubleJump() && heightDiff > 1.5
                && JumpSafety.isChaseJumpSafe(hero, controller, target)) {
            controller.lookAt(target.getEyeLocation());
            hero.getMobility().performJump(hero);
            return true;
        }

        // Case 2: Target is very far — HORIZONTAL distance only (see Aggressive: 3D read a
        // pure height gap as "far" and airborne bots juggled themselves above the target).
        if (canDoubleJump() && horizontalDistance > getFarDistance()
                && JumpSafety.isChaseJumpSafe(hero, controller, target)) {
            controller.lookAt(target.getEyeLocation());
            hero.getMobility().performJump(hero);
            return true;
        }

        return false;
    }

}
