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
import me.stolyy.heroes.hero.mechanics.StatusEffects;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class SkullfireBlazingDeagle extends ActiveAbility {
    private final Map<LivingEntity, Integer> victimStages = new HashMap<>();
    private final Map<LivingEntity, Long> lastHitTimes = new HashMap<>();
    private final Set<LivingEntity> pendingStuns = new HashSet<>();
    private final StatusEffects.Spec status;

    private final int bonusAmmo;
    private final int originalMaxAmmo;
    private final double movementSlow, gravitySlow;
    private final int maxStage;
    private final long decayMs;
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
        this.status = StatusEffects.Spec.fromConfig(this.config);
        this.blastDamage = Configs.requireDouble(this.config, "blast_damage");
        this.blastKnockback = Configs.requireDouble(this.config, "blast_knockback");
        this.ultItemSkin = Configs.requireString(this.config, "ult_item_skin");

        this.ringVisual = new VisualEffect(Configs.requireSection(this.config, "visuals.ring_visual"));
        this.ringSpacing = Configs.requireDouble(this.config, "visuals.ring_spacing");
        this.stunSound = new SoundEffect(Configs.requireSection(this.config, "visuals.stun_sound"));
        this.blastSound = new SoundEffect(Configs.requireSection(this.config, "visuals.blast_sound"));
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
        if (StatusEffects.hasFrom(victim, this)) return;

        int stage = victimStages.getOrDefault(victim, 0) + 1;
        if (stage >= maxStage) {
            // Reset stage counters and slow modifiers up front so an early knockback break
            // never leaves stale slow modifiers or buildup stacks behind.
            clearVictimStage(victim);
            pendingStuns.add(victim);
            return;
        }

        victimStages.put(victim, stage);
        lastHitTimes.put(victim, System.currentTimeMillis());

        Hero victimHero = HeroManager.getHero(victim);
        if (victimHero != null) {
            applyStageEffects(victimHero, stage);
        }
    }

    /**
     * Applies the deferred final-stage stun after the triggering Deagle shot's {@code execute()}
     * finishes, so the shot's own knockback does not immediately break the stun.
     */
    void onPrimaryHitResolved(Hero hero, LivingEntity victim) {
        if (!pendingStuns.remove(victim)) return;
        if (!isActive()) return;
        if (StatusEffects.applyTo(victim, status, this, hero, () -> blast(hero, victim))) {
            stunSound.play(victim.getLocation());
        }
    }

    private void applyStageEffects(Hero victim, int stage) {
        Attributes attributes = victim.getAttributes();
        attributes.addSpeedFlat(victim.getController(), this, -movementSlow * stage);
        attributes.addGravityMultiplier(victim.getController(), this, Math.max(0.0, 1.0 - gravitySlow * stage));
    }

    private void clearVictimStage(LivingEntity victim) {
        lastHitTimes.remove(victim);
        victimStages.remove(victim);
        resetAttributes(victim);
    }

    private void resetAttributes(LivingEntity entity) {
        Hero victimHero = HeroManager.getHero(entity);
        if (victimHero != null) {
            victimHero.getAttributes().removeSpeedModifier(victimHero.getController(), this);
            victimHero.getAttributes().removeGravityModifier(victimHero.getController(), this);
        }
    }

    private void blast(Hero attacker, LivingEntity victim) {
        blastSound.play(victim.getLocation());
        hit(attacker, victim).damage(blastDamage).knockback(blastKnockback).staticKnockback().execute();
    }

    @Override
    protected void onActiveTick(Hero hero) {
        if (hero.getPrimaryAbility().getAmmo().getCurrent() == 0) {
            deactivateNow(hero);
            return;
        }

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<LivingEntity, Long>> it = lastHitTimes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Long> entry = it.next();
            LivingEntity e = entry.getKey();

            if (StatusEffects.hasFrom(e, this)) continue;

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
        pendingStuns.clear();
        StatusEffects.removeAllFrom(this);
    }
}