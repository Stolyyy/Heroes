package me.stolyy.heroes.ability.kit.mrmonster;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Dash;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;

public class MonsterDash extends Dash {

    private record DashParams(double speed, int duration, double radius, double damage, double knockback) {
        static DashParams of(ConfigurationSection section) {
            return new DashParams(
                    Configs.requireDouble(section, "speed"),
                    Configs.requireInt(section, "duration"),
                    Configs.requireDouble(section, "radius"),
                    Configs.requireDouble(section, "damage"),
                    Configs.requireDouble(section, "knockback"));
        }
    }

    private final DashParams firstDash;
    private final DashParams secondDash;
    private final int delayTicks;

    private boolean sequenceActive;
    private boolean secondQueued;
    private int delayLeft = -1;

    private boolean delayArmedThisTick;

    public MonsterDash(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.firstDash = DashParams.of(Configs.requireSection(this.config, "dash1"));
        this.secondDash = DashParams.of(Configs.requireSection(this.config, "dash2"));
        this.delayTicks = Configs.requireInt(this.config, "delay_ticks");
    }

    @Override
    protected boolean canUse(Hero hero) {
        return !sequenceActive && super.canUse(hero);
    }

    @Override
    public void perform(Hero hero) {
        sequenceActive = true;
        secondQueued = !hero.getController().isSneaking();
        delayLeft = -1;
        delayArmedThisTick = false;
        applyParams(firstDash);
        super.perform(hero);
    }

    @Override
    protected void onDashEnd(Hero hero) {
        if (secondQueued) {
            delayLeft = delayTicks;
            delayArmedThisTick = true;
        } else {
            finishSequence(hero);
        }
    }

    @Override
    protected void onDashTick(Hero hero) {
        if (currentDash != null) return;
        if (delayArmedThisTick) {
            delayArmedThisTick = false;
            return;
        }
        if (delayLeft > 0) {
            delayLeft--;
            return;
        }
        if (delayLeft == 0) {
            delayLeft = -1;
            secondQueued = false;
            applyParams(secondDash);
            super.perform(hero);
        }
    }

    private void finishSequence(Hero hero) {
        sequenceActive = false;
        resolveCooldown(hero);
    }

    private void applyParams(DashParams params) {
        setBaseSpeed(params.speed());
        this.ticks = params.duration();
        setBaseRadius(params.radius());
        setBaseDamage(params.damage());
        setBaseKnockback(params.knockback());
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        sequenceActive = false;
        secondQueued = false;
        delayLeft = -1;
        delayArmedThisTick = false;
    }
}
