package me.stolyy.heroes.ability.kit.shoop;

import me.stolyy.heroes.ability.template.Hitscan;
import me.stolyy.heroes.game.PracticeManager;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.BeamDisplay;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

public class ShoopSmash extends Hitscan {
    private final double startOffset;
    private final SoundEffect activationSound;
    private final BeamDisplay beam;

    public ShoopSmash(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.startOffset = Configs.requireDouble(this.config, "start_offset");
        this.activationSound = new SoundEffect(Configs.requireSection(this.config, "visuals.activation_sound"));
        this.beam = new BeamDisplay(trailVisual, trailSpacing);
    }

    @Override
    protected void onWindupStart(Hero hero) {
        activationSound.play(hero.getController());
    }

    @Override
    protected void onBurstEnd(Hero hero) {
        beam.clear();
    }

    @Override
    protected void fireSingleShot(Hero hero) {
        if (!PracticeManager.isWithinPracticeBounds(hero, getStartLocation(hero))) {
            beam.clear();
            return;
        }
        super.fireSingleShot(hero);
    }

    @Override
    protected Location getStartLocation(Hero hero) {
        Location eye = hero.getController().getEyeLocation();
        return eye.add(eye.getDirection().multiply(startOffset));
    }

    @Override
    protected void displayTrail(Hero hero, Location start, Vector direction, double distance) {
        beam.update(start, direction, distance, hero.getSkin().getId());
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        beam.clear();
    }

    @Override
    public void onOwnerLost(Hero hero) {
        super.onOwnerLost(hero);
        beam.clear();
    }
}
