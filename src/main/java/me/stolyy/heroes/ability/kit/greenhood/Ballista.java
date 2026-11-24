package me.stolyy.heroes.ability.kit.greenhood;

import me.stolyy.heroes.ability.template.Hitscan;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.util.Vector;

public class Ballista extends Hitscan implements ArmedShot {
    private final double maxDamage;
    private final double overchargeDamageMultiplier;
    private final int maxOverchargeTicks;

    private final SoundEffect armSound;
    private final SoundEffect overchargedFireSound;
    private final VisualEffect overchargedTrailVisual;
    private final VisualEffect overchargedHitVisual;
    private final VisualEffect armVisual, armedVisual;
    private final int armedVisualIntervalTicks;
    private int armedTicks;

    private double shotCharge;
    private int shotOverchargeTicks;
    private boolean armed;

    public Ballista(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.maxDamage = Configs.requireDouble(this.config, "max_damage");
        this.overchargeDamageMultiplier = Configs.requireDouble(this.config, "overcharge_damage_multiplier");
        this.maxOverchargeTicks = Configs.requireInt(this.config, "max_overcharge_ticks");

        this.armSound = new SoundEffect(Configs.requireSection(this.config, "visuals.arm_sound"));
        this.overchargedFireSound = new SoundEffect(Configs.requireSection(this.config, "visuals.overcharged_fire_sound"));
        this.overchargedTrailVisual = new VisualEffect(Configs.requireSection(this.config, "visuals.overcharged_trail_visual"));
        this.overchargedHitVisual = new VisualEffect(Configs.requireSection(this.config, "visuals.overcharged_hit_visual"));
        this.armVisual = new VisualEffect(Configs.requireSection(this.config, "visuals.arm_visual"));
        this.armedVisual = new VisualEffect(Configs.requireSection(this.config, "visuals.armed_visual"));
        this.armedVisualIntervalTicks = Configs.requireInt(this.config, "visuals.armed_visual_interval_ticks");
    }

    /** While loaded, the bolt glows on the bow so both players can read that the next shot is the Ballista. */
    @Override
    protected void onRangedTick(Hero hero) {
        if (!isArmed()) return;
        if (armedTicks++ % armedVisualIntervalTicks == 0) {
            armedVisual.play(hero.getController().getEyeLocation(), hero.getController().getLocation().getDirection());
        }
    }

    @Override
    public boolean isArmed() {
        return armed;
    }

    @Override
    public void disarm() {
        armed = false;
    }

    @Override
    public void perform(Hero hero) {
        armed = true;
        armedTicks = 0;
        ArmedShot.disarmOthers(hero, this);
        armSound.play(hero.getController().getLocation(), hero.getController());
        armVisual.play(hero.getController().getLocation(), hero.getController().getLocation().getDirection());
        hero.getController().sendActionBar(Component.text("Ballista loaded — next shot", NamedTextColor.GOLD));
    }

    @Override
    public boolean fireFromBow(Hero hero, GreenHoodBow bow, AbstractArrow arrow, Vector aim, Vector carried,
                               double charge, int overchargeTicks, double force) {
        armed = false;
        this.shotCharge = charge;
        this.shotOverchargeTicks = overchargeTicks;
        fireSingleShot(hero);
        return false;
    }

    @Override
    protected ShotParams shotParamsFor(Hero hero) {
        double dmg = GreenHoodBow.overchargeDamage(
                minDamage, maxDamage, shotCharge, shotOverchargeTicks, maxOverchargeTicks, overchargeDamageMultiplier);
        boolean overcharged = shotOverchargeTicks > 0;
        return new ShotParams(
                dmg,
                knockback,
                0.0,
                overcharged ? overchargedFireSound : fireSound,
                overcharged ? overchargedTrailVisual : trailVisual,
                overcharged ? overchargedHitVisual : hitVisual
        );
    }

    @Override
    public void onDeath(Hero hero) {
        disarm();
        super.onDeath(hero);
    }
}
