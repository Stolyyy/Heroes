package me.stolyy.heroes.ability.kit.shoop;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Hitscan;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.AbstractDisplayBuilder;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ShoopSmash extends Hitscan {
    private final int windupTicks;
    private final long hitDelayMs;
    private final double startOffset;
    private final SoundEffect activationSound;
    private int elapsed = 0;
    private boolean firing = false;
    private final Map<UUID, Long> hitTimestamps = new HashMap<>();

    private final List<ItemDisplay> beamSegments = new ArrayList<>();
    private int visibleSegments = 0;
    private float segmentViewRange = 1.0f;

    public ShoopSmash(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.windupTicks = Configs.requireInt(this.config, "windup_ticks");
        this.hitDelayMs = Configs.requireInt(this.config, "hit_delay_ticks") * 50L;
        this.startOffset = Configs.requireDouble(this.config, "start_offset");
        this.activationSound = new SoundEffect(this.config.getConfigurationSection("visuals.activation_sound"));
    }

    @Override
    public void perform(Hero hero) {
        activationSound.play(hero.getController());
        firing = true;
        elapsed = 0;
        hitTimestamps.clear();
    }

    @Override
    protected void onTick(Hero hero) {
        if (!firing) return;

        if (!getCooldownObject().isInUse()) {
            endBeam();
            return;
        }

        elapsed++;
        if (elapsed >= windupTicks) super.perform(hero);
    }

    @Override
    protected Location getStartLocation(Hero hero) {
        Location eye = hero.getController().getEyeLocation();
        return eye.add(eye.getDirection().multiply(startOffset));
    }

    @Override
    public void onHit(Hero attacker, LivingEntity victim, Location hitLoc) {
        long now = System.currentTimeMillis();
        Long lastHit = hitTimestamps.get(victim.getUniqueId());
        if (lastHit != null && (now - lastHit) < hitDelayMs) return;

        hitTimestamps.put(victim.getUniqueId(), now);
        super.onHit(attacker, victim, hitLoc);
    }

    @Override
    protected void displayTrail(Hero hero, Location start, Vector direction, double distance) {
        Vector dir = direction.clone().normalize();
        double backOffset = trailVisual.modelBackOffset();
        int needed = (int) Math.floor(distance / trailSpacing) + 1;
        int total = Math.max(needed, beamSegments.size());

        for (int i = 0; i < total; i++) {
            Location loc = start.clone().add(dir.clone().multiply(i * trailSpacing));
            if (i < beamSegments.size()) {
                ItemDisplay segment = beamSegments.get(i);
                segment.teleport(AbstractDisplayBuilder.canonical(loc));

                segment.setInterpolationDelay(0);
                AbstractDisplayBuilder.orient(segment, dir, backOffset);
            } else {
                ItemDisplay segment = trailVisual.spawnDisplay(loc, hero.getSkin().getId());
                segment.setTeleportDuration(1);
                segment.setInterpolationDuration(1);
                AbstractDisplayBuilder.orient(segment, dir, backOffset);
                segmentViewRange = segment.getViewRange();
                beamSegments.add(segment);
            }
        }

        for (int i = Math.min(needed, visibleSegments); i < Math.max(needed, visibleSegments); i++) {
            beamSegments.get(i).setViewRange(i < needed ? segmentViewRange : 0f);
        }
        visibleSegments = needed;
    }

    private void endBeam() {
        firing = false;
        elapsed = 0;
        hitTimestamps.clear();
        for (ItemDisplay segment : beamSegments) segment.remove();
        beamSegments.clear();
        visibleSegments = 0;
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        endBeam();
    }
}
