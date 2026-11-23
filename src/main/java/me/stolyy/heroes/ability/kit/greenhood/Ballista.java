package me.stolyy.heroes.ability.kit.greenhood;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Hitscan;
import me.stolyy.heroes.listener.GreenHoodListener;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class Ballista extends Hitscan {
    private final double maxDamage;
    private final double overchargeDamageMultiplier;
    private final int maxOverchargeTicks;

    private final SoundEffect armSound;
    private final SoundEffect normalFireSound, overchargedFireSound;
    private final VisualEffect normalTrailVisual, overchargedTrailVisual;
    private final VisualEffect normalHitVisual, overchargedHitVisual;
    private final VisualEffect armVisual, armedVisual;
    private final int armedVisualIntervalTicks;
    private int armedTicks;

    private boolean armed;
    private double shotCharge;
    private int shotOverchargeTicks;

    public Ballista(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.maxDamage = this.config.getDouble("max_damage");
        this.overchargeDamageMultiplier = this.config.getDouble("overcharge_damage_multiplier");
        this.maxOverchargeTicks = this.config.getInt("max_overcharge_ticks");

        this.armSound = new SoundEffect(this.config.getConfigurationSection("visuals.arm_sound"));
        this.normalFireSound = fireSound;
        this.normalTrailVisual = trailVisual;
        this.normalHitVisual = hitVisual;
        this.overchargedFireSound = new SoundEffect(this.config.getConfigurationSection("visuals.overcharged_fire_sound"));
        this.overchargedTrailVisual = new VisualEffect(this.config.getConfigurationSection("visuals.overcharged_trail_visual"));
        this.overchargedHitVisual = new VisualEffect(this.config.getConfigurationSection("visuals.overcharged_hit_visual"));
        this.armVisual = new VisualEffect(Configs.requireSection(this.config, "visuals.arm_visual"));
        this.armedVisual = new VisualEffect(Configs.requireSection(this.config, "visuals.armed_visual"));
        this.armedVisualIntervalTicks = Configs.requireInt(this.config, "visuals.armed_visual_interval_ticks");
    }

    /** While loaded, the bolt glows on the bow so both players can read that the next shot is the Ballista. */
    @Override
    protected void onTick(Hero hero) {
        super.onTick(hero);
        if (!armed) return;
        if (armedTicks++ % armedVisualIntervalTicks == 0) {
            armedVisual.play(hero.getController().getEyeLocation(), hero.getController().getLocation().getDirection());
        }
    }

    @Override
    public void perform(Hero hero) {
        armed = true;
        armedTicks = 0;
        GreenHoodListener.disarmOthers(hero, this);
        armSound.play(hero.getController().getLocation(), hero.getController());
        armVisual.play(hero.getController().getLocation(), hero.getController().getLocation().getDirection());
        hero.getController().sendActionBar(Component.text("Ballista loaded — next shot", NamedTextColor.GOLD));
    }

    public boolean isArmed() {
        return armed;
    }

    public void disarm() {
        armed = false;
    }

    public void fire(Hero hero, Player shooter, double charge, int overchargeTicks) {
        armed = false;
        this.shotCharge = charge;
        this.shotOverchargeTicks = overchargeTicks;

        boolean overcharged = overchargeTicks > 0;
        fireSound = overcharged ? overchargedFireSound : normalFireSound;
        trailVisual = overcharged ? overchargedTrailVisual : normalTrailVisual;
        hitVisual = overcharged ? overchargedHitVisual : normalHitVisual;

        fireSingleShot(hero);
    }

    @Override
    protected double calcDamage(Hero hero, LivingEntity victim) {
        double overchargeFraction = maxOverchargeTicks > 0
                ? Math.min(1.0, shotOverchargeTicks / (double) maxOverchargeTicks) : 0;
        double factor = 1.0 + overchargeFraction * (overchargeDamageMultiplier - 1.0);
        return (minDamage + (maxDamage - minDamage) * shotCharge) * factor;
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        armed = false;
    }
}
