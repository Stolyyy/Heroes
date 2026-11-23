package me.stolyy.heroes.ability.kit.spooderman;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

public class SpooderClimb extends BaseAbility {
    private final double climbSpeed;
    private final int climbCost;

    public SpooderClimb(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.climbSpeed = config.getDouble("climb_speed", 0.4);
        this.climbCost = config.getInt("climb_cost", 2);
    }

    @Override
    public void perform(Hero hero) {
    }

    @Override
    protected void onTick(Hero hero) {
        LivingEntity entity = hero.getController().getEntity();
        boolean onGround = entity.isOnGround();

        hero.getEnergy().setCanRegen(onGround);

        if (hero.getController().isSneaking() && isNearWall(entity.getLocation())) {
            if (hero.getEnergy().tryConsume(hero.getController(), climbCost)) {
                Vector velocity = entity.getVelocity();
                velocity.setY(climbSpeed);

                entity.setVelocity(velocity);

                entity.setFallDistance(0);
            }
        }
    }

    private boolean isNearWall(Location loc) {
        Block b = loc.getBlock();
        return isSolid(b.getRelative(BlockFace.NORTH)) ||
               isSolid(b.getRelative(BlockFace.SOUTH)) ||
               isSolid(b.getRelative(BlockFace.EAST)) ||
               isSolid(b.getRelative(BlockFace.WEST));
    }
    
    private boolean isSolid(Block b) {
        return b != null && b.getType().isSolid();
    }
}