package me.stolyy.heroes.ability.kit.skullfire;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.CosmeticManager;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.hero.mechanics.Attributes;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import me.stolyy.heroes.physics.Stun;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

import java.util.*;

public class SkullfireBlazingDeagle extends BaseAbility {
    private final Map<LivingEntity, Integer> victimStages = new HashMap<>();
    private final Map<LivingEntity, Long> lastHitTimes = new HashMap<>();
    private final Stun stun;

    private final int bonusAmmo;
    private final int originalMaxAmmo;
    private final double movementSlow, gravitySlow;
    private final int maxStage;
    private final long decayMs;
    private final long stunDurationMs;
    private final double blastDamage;
    private final double blastKnockback;
    private final String ultItemSkin;

    private final VisualEffect ringVisual;
    private final double ringSpacing;
    private final SoundEffect activationSound;
    private final SoundEffect stunSound;
    private final SoundEffect blastSound;

    private boolean isActive = false;

    public SkullfireBlazingDeagle(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.bonusAmmo = config.getInt("bonus_ammo");
        this.originalMaxAmmo = config.getInt("original_max_ammo");
        this.movementSlow = config.getDouble("movement_slow");
        this.gravitySlow = config.getDouble("gravity_slow");
        this.maxStage = config.getInt("max_stage");
        this.decayMs = (long) (config.getDouble("decay_duration") * 1000);
        this.stunDurationMs = (long) (config.getDouble("stun_duration") * 1000);
        this.blastDamage = config.getDouble("blast_damage");
        this.blastKnockback = config.getDouble("blast_knockback");
        this.ultItemSkin = Configs.requireString(this.config, "ult_item_skin");

        this.ringVisual = new VisualEffect(this.config.getConfigurationSection("visuals.ring_visual"));
        this.ringSpacing = this.config.getDouble("visuals.ring_spacing");
        this.stunSound = new SoundEffect(this.config.getConfigurationSection("visuals.stun_sound"));
        this.blastSound = new SoundEffect(this.config.getConfigurationSection("visuals.blast_sound"));
        this.activationSound = new SoundEffect(this.config.getConfigurationSection("visuals.activation_sound"));

        this.stun = new Stun(stunDurationMs, this::endStun);
    }

    @Override
    public void perform(Hero hero) {
        isActive = true;
        activationSound.play(hero.getController());

        CosmeticManager.setHeldModel(hero, CosmeticManager.defaultHeldTexture(hero), ultItemSkin);

        Ability primary = hero.getPrimaryAbility();
        if (primary != null && primary.getAmmo() != null) {
            primary.getAmmo().setMax(originalMaxAmmo + bonusAmmo);
            primary.getAmmo().setAmmo(originalMaxAmmo + bonusAmmo);
        }
    }

    @Override
    public void onAbilityHit(Hero hero, Ability ability, LivingEntity victim) {
        if (!isActive || ability.getType() != AbilityType.PRIMARY) return;
        if (stun.isStunned(victim)) return;

        int stage = victimStages.getOrDefault(victim, 0) + 1;
        victimStages.put(victim, stage);
        lastHitTimes.put(victim, System.currentTimeMillis());

        Hero victimHero = HeroManager.getHero(victim);
        if (victimHero == null) return;

        if (stage >= maxStage) {
            triggerStun(hero, victim);
        } else {
            applyStageEffects(victimHero, stage);
        }
    }

    private void applyStageEffects(Hero victim, int stage) {
        Attributes attributes = victim.getAttributes();
        double baseSpeed = attributes.getBaseMovementSpeed();
        double baseGravity = attributes.getBaseGravity();

        attributes.setMovementSpeed(victim.getController(), baseSpeed - movementSlow * stage);
        attributes.setGravity(victim.getController(), baseGravity * Math.max(0.0, 1.0 - gravitySlow * stage));
    }

    private void resetAttributes(LivingEntity entity) {
        Hero victimHero = HeroManager.getHero(entity);
        if (victimHero != null) {
            victimHero.getAttributes().resetMovementSpeed(victimHero.getController());
            victimHero.getAttributes().resetGravity(victimHero.getController());
        }
    }

    private void triggerStun(Hero attacker, LivingEntity victim) {
        stun.apply(attacker, victim);
        stunSound.play(victim.getLocation());
    }

    private void endStun(Hero attacker, LivingEntity victim) {
        lastHitTimes.remove(victim);
        victimStages.remove(victim);

        Hero victimHero = HeroManager.getHero(victim);
        if (victimHero != null) {
            victimHero.getAttributes().resetMovementSpeed(victimHero.getController());
            victimHero.getAttributes().resetGravity(victimHero.getController());
        }

        blastSound.play(victim.getLocation());
        Interactions.on(attacker.getController().getEntity(), victim).abilityName(getName()).damage(blastDamage).knockback(blastKnockback).staticKnockback().execute();
    }

    @Override
    protected void onTick(Hero hero) {
        if (!isActive) return;

        if (hero.getPrimaryAbility().getAmmo().getCurrent() == 0) getCooldownObject().reset();

        if (!getCooldownObject().isInUse()) {
            onExpire(hero, null);
            return;
        }

        stun.tick(hero);

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<LivingEntity, Long>> it = lastHitTimes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Long> entry = it.next();
            LivingEntity e = entry.getKey();

            if (stun.isStunned(e)) continue;

            if (now - entry.getValue() > decayMs) {
                it.remove();
                victimStages.remove(e);
                resetAttributes(e);
            } else {
                displayRings(e);
            }
        }
    }

    private void displayRings(LivingEntity entity) {
        Location loc = entity.getBoundingBox().getCenter().toLocation(entity.getWorld());
        int stage = victimStages.getOrDefault(entity, 0);
        double y = stage % 2 == 0 ? ringSpacing / 2 : 0;

        for (int i = 0; i < stage; i++) {
            y += ringSpacing * i * Math.pow(-1,i);
            ringVisual.play(loc.clone().add(0, y, 0));
        }
    }

    @Override
    public void onExpire(Hero hero, Location location) {
        if (!isActive) return;
        isActive = false;

        CosmeticManager.setHeldModel(hero, CosmeticManager.defaultHeldTexture(hero));

        Ability primary = hero.getPrimaryAbility();
        if (primary != null && primary.getAmmo() != null) {
            primary.getAmmo().setMax(originalMaxAmmo);
            if (primary.getAmmo().getCurrent() > originalMaxAmmo) {
                primary.getAmmo().setAmmo(originalMaxAmmo);
            }
        }

        for (LivingEntity entity : victimStages.keySet()) {
            Hero victimHero = HeroManager.getHero(entity);
            if (victimHero != null) {
                victimHero.getAttributes().resetMovementSpeed(victimHero.getController());
                victimHero.getAttributes().resetGravity(victimHero.getController());
            }
        }
        victimStages.clear();
        lastHitTimes.clear();
        stun.releaseAll();
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        onExpire(hero, null);
    }
}