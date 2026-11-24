package me.stolyy.heroes.ability.kit.skullfire;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.ability.template.ActiveAbility;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.CosmeticManager;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.hero.mechanics.Attributes;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import me.stolyy.heroes.physics.Stun;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class SkullfireBlazingDeagle extends ActiveAbility {
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
    private final SoundEffect stunSound;
    private final SoundEffect blastSound;

    public SkullfireBlazingDeagle(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.bonusAmmo = Configs.requireInt(this.config, "bonus_ammo");
        this.originalMaxAmmo = Configs.requireInt(this.config, "original_max_ammo");
        this.movementSlow = Configs.requireDouble(this.config, "movement_slow");
        this.gravitySlow = Configs.requireDouble(this.config, "gravity_slow");
        this.maxStage = Configs.requireInt(this.config, "max_stage");
        this.decayMs = (long) (Configs.requireDouble(this.config, "decay_duration") * 1000);
        this.stunDurationMs = (long) (Configs.requireDouble(this.config, "stun_duration") * 1000);
        this.blastDamage = Configs.requireDouble(this.config, "blast_damage");
        this.blastKnockback = Configs.requireDouble(this.config, "blast_knockback");
        this.ultItemSkin = Configs.requireString(this.config, "ult_item_skin");

        this.ringVisual = new VisualEffect(Configs.requireSection(this.config, "visuals.ring_visual"));
        this.ringSpacing = Configs.requireDouble(this.config, "visuals.ring_spacing");
        this.stunSound = new SoundEffect(Configs.requireSection(this.config, "visuals.stun_sound"));
        this.blastSound = new SoundEffect(Configs.requireSection(this.config, "visuals.blast_sound"));

        this.stun = new Stun(stunDurationMs, this::endStun);
    }

    @Override
    protected void onActivate(Hero hero) {
        CosmeticManager.setHeldModel(hero, CosmeticManager.defaultHeldTexture(hero), ultItemSkin);

        Ability primary = hero.getPrimaryAbility();
        if (primary != null && primary.getAmmo() != null) {
            primary.getAmmo().setMax(originalMaxAmmo + bonusAmmo);
            primary.getAmmo().setAmmo(originalMaxAmmo + bonusAmmo);
        }
    }

    @Override
    public void onAbilityHit(Hero hero, Ability ability, LivingEntity victim) {
        if (!isActive() || ability.getType() != AbilityType.PRIMARY) return;
        if (stun.isStunned(victim)) return;

        int stage = victimStages.getOrDefault(victim, 0) + 1;
        victimStages.put(victim, stage);
        lastHitTimes.put(victim, System.currentTimeMillis());

        Hero victimHero = HeroManager.getHero(victim);
        if (victimHero == null) return;

        if (stage >= maxStage) {
            triggerStun(victim);
        } else {
            applyStageEffects(victimHero, stage);
        }
    }

    private void applyStageEffects(Hero victim, int stage) {
        Attributes attributes = victim.getAttributes();
        attributes.addSpeedFlat(victim.getController(), this, -movementSlow * stage);
        attributes.addGravityMultiplier(victim.getController(), this, Math.max(0.0, 1.0 - gravitySlow * stage));
    }

    private void resetAttributes(LivingEntity entity) {
        Hero victimHero = HeroManager.getHero(entity);
        if (victimHero != null) {
            victimHero.getAttributes().removeSpeedModifier(victimHero.getController(), this);
            victimHero.getAttributes().removeGravityModifier(victimHero.getController(), this);
        }
    }

    private void triggerStun(LivingEntity victim) {
        stun.apply(victim);
        stunSound.play(victim.getLocation());
    }

    private void endStun(Hero attacker, LivingEntity victim) {
        lastHitTimes.remove(victim);
        victimStages.remove(victim);
        resetAttributes(victim);

        blastSound.play(victim.getLocation());
        hit(attacker, victim).damage(blastDamage).knockback(blastKnockback).staticKnockback().execute();
    }

    @Override
    protected void onActiveTick(Hero hero) {
        if (hero.getPrimaryAbility().getAmmo().getCurrent() == 0) {
            deactivateNow(hero);
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
            y += ringSpacing * i * Math.pow(-1, i);
            ringVisual.play(loc.clone().add(0, y, 0));
        }
    }

    @Override
    protected void onDeactivate(Hero hero) {
        CosmeticManager.setHeldModel(hero, CosmeticManager.defaultHeldTexture(hero));

        Ability primary = hero.getPrimaryAbility();
        if (primary != null && primary.getAmmo() != null) {
            primary.getAmmo().setMax(originalMaxAmmo);
            if (primary.getAmmo().getCurrent() > originalMaxAmmo) {
                primary.getAmmo().setAmmo(originalMaxAmmo);
            }
        }

        for (LivingEntity entity : victimStages.keySet()) {
            resetAttributes(entity);
        }
        victimStages.clear();
        lastHitTimes.clear();
        stun.releaseAll();
    }
}