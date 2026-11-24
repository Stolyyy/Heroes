package me.stolyy.heroes.ability.kit.bug;

import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Cooldown;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.ability.template.CompositeAbility;
import me.stolyy.heroes.ability.template.Cone;
import me.stolyy.heroes.ability.template.Dash;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.ability.template.Shockwave;
import me.stolyy.heroes.physics.WallDetection;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BugSpell extends CompositeAbility {
    private final Cone upSpell;
    private final Projectile neutralSpell;
    private final DownSpell downSpell;

    private final double upPitchMax;
    private final double downPitchMin;

    public BugSpell(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.upPitchMax = Configs.requireDouble(this.config, "up_pitch_max");
        this.downPitchMin = Configs.requireDouble(this.config, "down_pitch_min");
        this.upSpell = addChild(new Cone(Configs.requireSection(this.config, "up"), skin, getType()) {});
        this.neutralSpell = addChild(new Projectile(Configs.requireSection(this.config, "neutral"), skin, getType()) {});
        this.downSpell = addChild(new DownSpell(Configs.requireSection(this.config, "down"), skin, getType()));
    }

    private BaseAbility aimedVariant(Hero hero) {
        float pitch = hero.getController().getLocation().getPitch();
        if (pitch < upPitchMax) return upSpell;
        if (pitch > downPitchMin) return downSpell;
        return neutralSpell;
    }

    @Override
    protected boolean canUse(Hero hero) {
        return aimedVariant(hero).canExecute(hero);
    }

    @Override
    public void perform(Hero hero) {
        BaseAbility variant = aimedVariant(hero);
        variant.getCooldownObject().start();
        variant.perform(hero);
    }

    @Override
    public Cooldown getDisplayCooldown(Hero hero) {
        Cooldown root = getCooldownObject();
        Cooldown variant = aimedVariant(hero).getCooldownObject();
        return variant.getTimeRemaining() > root.getTimeRemaining() ? variant : root;
    }

    private static class DownSpell extends Dash {
        private final Shockwave landingShockwave;
        private final Map<UUID, Long> sharedHitHistory = new HashMap<>();
        private boolean airborne = false;
        private boolean landed = false;

        public DownSpell(ConfigurationSection config, HeroSkin skin, AbilityType type) {
            super(config, skin, type);
            this.landingShockwave = new Shockwave(Configs.requireSection(this.config, "shockwave"), skin, type);
        }

        @Override
        protected Map<UUID, Long> sharedHitHistory() {
            return sharedHitHistory;
        }

        @Override
        public void perform(Hero hero) {
            sharedHitHistory.clear();
            airborne = false;
            landed = false;
            super.perform(hero);
        }

        @Override
        protected boolean shouldEndDash(Hero hero) {
            LivingEntity entity = hero.getController().getEntity();
            if (entity == null) return true;
            boolean grounded = WallDetection.isGrounded(entity);
            if (airborne && grounded) {
                landed = true;
                return true;
            }
            if (!grounded) airborne = true;
            return false;
        }

        @Override
        protected void onDashEnd(Hero hero) {
            if (landed) {
                landed = false;
                hero.getController().setVelocity(new Vector(0, 0, 0));
                landingShockwave.perform(hero, sharedHitHistory);
            }
            airborne = false;
        }

        @Override
        public void onDeath(Hero hero) {
            super.onDeath(hero);
            airborne = false;
            landed = false;
        }

        @Override public void addDamageMultiplier(Object key, double mult) {
            super.addDamageMultiplier(key, mult);
            landingShockwave.addDamageMultiplier(key, mult);
        }
        @Override public void addDamageFlat(Object key, double delta) {
            super.addDamageFlat(key, delta);
            landingShockwave.addDamageFlat(key, delta);
        }
        @Override public void removeDamageModifier(Object key) {
            super.removeDamageModifier(key);
            landingShockwave.removeDamageModifier(key);
        }
        @Override public void addKnockbackMultiplier(Object key, double mult) {
            super.addKnockbackMultiplier(key, mult);
            landingShockwave.addKnockbackMultiplier(key, mult);
        }
        @Override public void addKnockbackFlat(Object key, double delta) {
            super.addKnockbackFlat(key, delta);
            landingShockwave.addKnockbackFlat(key, delta);
        }
        @Override public void removeKnockbackModifier(Object key) {
            super.removeKnockbackModifier(key);
            landingShockwave.removeKnockbackModifier(key);
        }
        @Override public void addRadiusMultiplier(Object key, double mult) {
            super.addRadiusMultiplier(key, mult);
            landingShockwave.addRadiusMultiplier(key, mult);
        }
        @Override public void addRadiusFlat(Object key, double delta) {
            super.addRadiusFlat(key, delta);
            landingShockwave.addRadiusFlat(key, delta);
        }
        @Override public void removeRadiusModifier(Object key) {
            super.removeRadiusModifier(key);
            landingShockwave.removeRadiusModifier(key);
        }
    }
}
