package me.stolyy.heroes.ability.kit.railgunner;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Hitscan;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RayOfDoom extends Hitscan {
    private final int windupTicks;
    private final long hitDelayMs;
    private final SoundEffect activationSound;
    private Vector direction;
    private Location teleportLocation;
    private Location startLocation;
    private int elapsed = 0;
    private boolean firing = false;
    private final Map<UUID, Long> hitTimestamps = new HashMap<>();

    public RayOfDoom(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.windupTicks = Configs.requireInt(this.config, "windup_ticks");
        this.hitDelayMs = Configs.requireInt(this.config, "hit_delay_ticks") * 50L;
        this.activationSound = new SoundEffect(this.config.getConfigurationSection("visuals.activation_sound"));
    }

    @Override
    public void perform(Hero hero) {
        startLocation = hero.getController().getEyeLocation();
        direction = startLocation.getDirection();
        teleportLocation = hero.getController().getLocation();
        activationSound.play(hero.getController());

        firing = true;
        elapsed = 0;
        hitTimestamps.clear();
    }

    @Override
    protected void onTick(Hero hero) {
        if (!firing) return;

        if (!getCooldownObject().isInUse()) {
            firing = false;
            elapsed = 0;
            hitTimestamps.clear();
            return;
        }

        elapsed++;

        if (elapsed >= windupTicks) {
            super.perform(hero);
            hero.getController().getEntity().teleport(teleportLocation);
        }
    }

    @Override
    protected Location getStartLocation(Hero hero) {
        return startLocation;
    }

    @Override
    protected Vector getDirection(Location location) {
        return direction;
    }

    @Override
    public void onHit(Hero attacker, LivingEntity victim, Location hitLoc) {
        long now = System.currentTimeMillis();
        Long lastHit = hitTimestamps.get(victim.getUniqueId());
        if (lastHit != null && (now - lastHit) < hitDelayMs) return;

        hitTimestamps.put(victim.getUniqueId(), now);
        super.onHit(attacker, victim, hitLoc);
    }
}
