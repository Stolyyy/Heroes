package me.stolyy.heroes.ability.kit.spooderman;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.physics.WallDetection;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

public class SpooderClimb extends BaseAbility {
    private final double climbSpeed;
    private final int climbCost;

    public SpooderClimb(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.climbSpeed = Configs.requireDouble(this.config, "climb_speed");
        this.climbCost = Configs.requireInt(this.config, "climb_cost");
    }

    @Override
    public void perform(Hero hero) {
    }

    @Override
    protected void onTick(Hero hero) {
        if (hero.getController() == null || hero.getController().getEntity() == null) return;
        if (climbCost > 0) {
            if (hero.getController().isOnGround()) {
                hero.getEnergy().unblockRegen(this);
            } else {
                hero.getEnergy().blockRegen(this);
            }
        }

        if (hero.getController().isSneaking()
                && WallDetection.isAdjacentToSolidWall(hero.getController().getLocation())) {
            if (climbCost <= 0 || hero.getEnergy().tryConsume(hero.getController(), climbCost)) {
                Vector velocity = hero.getController().getVelocity();
                velocity.setY(climbSpeed);
                hero.getController().setVelocity(velocity);
                hero.getController().setFallDistance(0);
            }
        }
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        hero.getEnergy().unblockRegen(this);
    }
}