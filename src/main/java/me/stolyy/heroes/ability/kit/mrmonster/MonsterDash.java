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

    public MonsterDash(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.firstDash = DashParams.of(Configs.requireSection(this.config, "dash1"));
        this.secondDash = DashParams.of(Configs.requireSection(this.config, "dash2"));
        this.delayTicks = Configs.requireInt(this.config, "delay_ticks");
    }

    @Override
    public boolean canExecute(Hero hero) {
        if (sequenceActive) return false;
        return super.canExecute(hero);
    }

    @Override
    protected void startCooldown(Hero hero) {
    }

    @Override
    public void perform(Hero hero) {
        sequenceActive = true;
        secondQueued = !hero.getController().isSneaking();
        delayLeft = -1;
        applyParams(firstDash);
        super.perform(hero);
    }

    @Override
    protected void onTick(Hero hero) {
        if (currentDash != null) {
            if (currentDash.tick()) {
                currentDash = null;
                if (secondQueued) {
                    delayLeft = delayTicks;
                } else {
                    finishSequence();
                }
            }
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

    private void finishSequence() {
        sequenceActive = false;
        getCooldownObject().start();
    }

    private void applyParams(DashParams params) {
        this.speed = params.speed();
        this.ticks = params.duration();
        this.radius = params.radius();
        this.damage = params.damage();
        this.knockback = params.knockback();
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        if (sequenceActive) finishSequence();
        secondQueued = false;
        delayLeft = -1;
    }
}
