package me.stolyy.heroes.ability.kit.bug.charms;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.template.Dash;
import me.stolyy.heroes.hero.Hero;

/**
 * Targets a data-driven {@link StatCharm} can modify on a hero via keyed {@code ModifierStack}s.
 */
public enum StatTarget {
    SPEED {
        @Override
        public void apply(Hero hero, Object key, Op op, double value) {
            if (op == Op.MULT) hero.getAttributes().addSpeedMultiplier(hero.getController(), key, value);
            else hero.getAttributes().addSpeedFlat(hero.getController(), key, value);
        }

        @Override
        public void remove(Hero hero, Object key) {
            hero.getAttributes().removeSpeedModifier(hero.getController(), key);
        }
    },
    STEP_HEIGHT {
        @Override
        public void apply(Hero hero, Object key, Op op, double value) {
            if (op == Op.MULT) hero.getAttributes().addStepHeightMultiplier(hero.getController(), key, value);
            else hero.getAttributes().addStepHeightFlat(hero.getController(), key, value);
        }

        @Override
        public void remove(Hero hero, Object key) {
            hero.getAttributes().removeStepHeightModifier(hero.getController(), key);
        }
    },
    WEIGHT {
        @Override
        public void apply(Hero hero, Object key, Op op, double value) {
            if (op == Op.MULT) hero.getAttributes().addWeightMultiplier(key, value);
            else hero.getAttributes().addWeightFlat(key, value);
        }

        @Override
        public void remove(Hero hero, Object key) {
            hero.getAttributes().removeWeightModifier(key);
        }
    },
    JAB_COOLDOWN {
        @Override
        public void apply(Hero hero, Object key, Op op, double value) {
            if (op == Op.MULT) hero.getCombat().addCooldownMultiplier(key, value);
            else hero.getCombat().addCooldownFlat(key, value);
        }

        @Override
        public void remove(Hero hero, Object key) {
            hero.getCombat().removeCooldownModifier(key);
        }
    },
    JAB_DAMAGE {
        @Override
        public void apply(Hero hero, Object key, Op op, double value) {
            if (op == Op.MULT) hero.getCombat().addDamageMultiplier(key, value);
            else hero.getCombat().addDamageFlat(key, value);
        }

        @Override
        public void remove(Hero hero, Object key) {
            hero.getCombat().removeDamageModifier(key);
        }
    },
    JAB_RANGE {
        @Override
        public void apply(Hero hero, Object key, Op op, double value) {
            if (op == Op.MULT) hero.getCombat().addRangeMultiplier(key, value, hero.getController());
            else hero.getCombat().addRangeFlat(key, value, hero.getController());
        }

        @Override
        public void remove(Hero hero, Object key) {
            hero.getCombat().removeRangeModifier(key, hero.getController());
        }
    },
    JAB_KNOCKBACK {
        @Override
        public void apply(Hero hero, Object key, Op op, double value) {
            if (op == Op.MULT) hero.getCombat().addKnockbackMultiplier(key, value);
            else hero.getCombat().addKnockbackFlat(key, value);
        }

        @Override
        public void remove(Hero hero, Object key) {
            hero.getCombat().removeKnockbackModifier(key);
        }
    },
    PRIMARY_COOLDOWN {
        @Override
        public void apply(Hero hero, Object key, Op op, double value) {
            Ability primary = hero.getPrimaryAbility();
            if (primary == null) return;
            if (op == Op.MULT) primary.getCooldownObject().addDurationMultiplier(key, value);
            else primary.getCooldownObject().addDurationFlat(key, value);
        }

        @Override
        public void remove(Hero hero, Object key) {
            Ability primary = hero.getPrimaryAbility();
            if (primary != null) primary.getCooldownObject().removeDurationModifier(key);
        }
    },
    PRIMARY_DAMAGE {
        @Override
        public void apply(Hero hero, Object key, Op op, double value) {
            Ability primary = hero.getPrimaryAbility();
            if (primary == null) return;
            if (op == Op.MULT) primary.addDamageMultiplier(key, value);
            else primary.addDamageFlat(key, value);
        }

        @Override
        public void remove(Hero hero, Object key) {
            Ability primary = hero.getPrimaryAbility();
            if (primary != null) primary.removeDamageModifier(key);
        }
    },
    PRIMARY_KNOCKBACK {
        @Override
        public void apply(Hero hero, Object key, Op op, double value) {
            Ability primary = hero.getPrimaryAbility();
            if (primary == null) return;
            if (op == Op.MULT) primary.addKnockbackMultiplier(key, value);
            else primary.addKnockbackFlat(key, value);
        }

        @Override
        public void remove(Hero hero, Object key) {
            Ability primary = hero.getPrimaryAbility();
            if (primary != null) primary.removeKnockbackModifier(key);
        }
    },
    PRIMARY_DASH_SPEED {
        @Override
        public void apply(Hero hero, Object key, Op op, double value) {
            if (hero.getPrimaryAbility() instanceof Dash dash) {
                if (op == Op.MULT) dash.addSpeedMultiplier(key, value);
                else dash.addSpeedFlat(key, value);
            }
        }

        @Override
        public void remove(Hero hero, Object key) {
            if (hero.getPrimaryAbility() instanceof Dash dash) {
                dash.removeSpeedModifier(key);
            }
        }
    },
    SECONDARY_DAMAGE {
        @Override
        public void apply(Hero hero, Object key, Op op, double value) {
            Ability secondary = hero.getSecondaryAbility();
            if (secondary == null) return;
            if (op == Op.MULT) secondary.addDamageMultiplier(key, value);
            else secondary.addDamageFlat(key, value);
        }

        @Override
        public void remove(Hero hero, Object key) {
            Ability secondary = hero.getSecondaryAbility();
            if (secondary != null) secondary.removeDamageModifier(key);
        }
    },
    SECONDARY_KNOCKBACK {
        @Override
        public void apply(Hero hero, Object key, Op op, double value) {
            Ability secondary = hero.getSecondaryAbility();
            if (secondary == null) return;
            if (op == Op.MULT) secondary.addKnockbackMultiplier(key, value);
            else secondary.addKnockbackFlat(key, value);
        }

        @Override
        public void remove(Hero hero, Object key) {
            Ability secondary = hero.getSecondaryAbility();
            if (secondary != null) secondary.removeKnockbackModifier(key);
        }
    },
    SECONDARY_RADIUS {
        @Override
        public void apply(Hero hero, Object key, Op op, double value) {
            Ability secondary = hero.getSecondaryAbility();
            if (secondary == null) return;
            if (op == Op.MULT) secondary.addRadiusMultiplier(key, value);
            else secondary.addRadiusFlat(key, value);
        }

        @Override
        public void remove(Hero hero, Object key) {
            Ability secondary = hero.getSecondaryAbility();
            if (secondary != null) secondary.removeRadiusModifier(key);
        }
    },
    SECONDARY_ENERGY_COST {
        @Override
        public void apply(Hero hero, Object key, Op op, double value) {
            Ability secondary = hero.getSecondaryAbility();
            if (secondary == null) return;
            if (op == Op.MULT) secondary.getCooldownObject().addEnergyCostMultiplier(key, value);
            else secondary.getCooldownObject().addEnergyCostFlat(key, value);
        }

        @Override
        public void remove(Hero hero, Object key) {
            Ability secondary = hero.getSecondaryAbility();
            if (secondary != null) secondary.getCooldownObject().removeEnergyCostModifier(key);
        }
    },
    ENERGY_REGEN_PER_SECOND {
        @Override
        public void apply(Hero hero, Object key, Op op, double value) {
            if (op == Op.MULT) hero.getEnergy().addRegenMultiplier(key, value);
            else hero.getEnergy().addRegenFlat(key, value / Heroes.TICKS_PER_SECOND);
        }

        @Override
        public void remove(Hero hero, Object key) {
            hero.getEnergy().removeRegenModifier(key);
        }
    };

    public enum Op { MULT, ADD }

    public abstract void apply(Hero hero, Object key, Op op, double value);
    public abstract void remove(Hero hero, Object key);
}
