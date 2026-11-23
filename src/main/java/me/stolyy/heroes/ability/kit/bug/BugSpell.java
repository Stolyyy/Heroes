package me.stolyy.heroes.ability.kit.bug;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Cooldown;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.ability.template.Cone;
import me.stolyy.heroes.ability.template.Dash;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.ability.template.Shockwave;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BugSpell extends BaseAbility {
    private final UpSpell upSpell;
    private final NeutralSpell neutralSpell;
    private final DownSpell downSpell;

    private final double upPitchMax;
    private final double downPitchMin;

    public BugSpell(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.upPitchMax = Configs.requireDouble(this.config, "up_pitch_max");
        this.downPitchMin = Configs.requireDouble(this.config, "down_pitch_min");
        this.upSpell = new UpSpell(typedSubsection(config, "up"), skin);
        this.neutralSpell = new NeutralSpell(typedSubsection(config, "neutral"), skin);
        this.downSpell = new DownSpell(typedSubsection(config, "down"), skin);
    }

    private ConfigurationSection typedSubsection(ConfigurationSection parent, String key) {
        ConfigurationSection section = parent.getConfigurationSection(key);
        section.set("type", getType().name());
        return section;
    }


    private BaseAbility aimedVariant(Hero hero) {
        float pitch = hero.getController().getLocation().getPitch();
        if (pitch < upPitchMax) return upSpell;
        if (pitch > downPitchMin) return downSpell;
        return neutralSpell;
    }


    @Override
    public boolean canExecute(Hero hero) {
        if (!super.canExecute(hero)) return false;
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

    @Override
    public List<Cooldown> getAllCooldowns() {
        return List.of(getCooldownObject(), upSpell.getCooldownObject(),
                neutralSpell.getCooldownObject(), downSpell.getCooldownObject());
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        upSpell.onDeath(hero);
        neutralSpell.onDeath(hero);
        downSpell.onDeath(hero);
    }

    @Override
    public void onOwnerLost(Hero hero) {
        upSpell.onOwnerLost(hero);
        neutralSpell.onOwnerLost(hero);
        downSpell.onOwnerLost(hero);
    }

    @Override
    protected void onTick(Hero hero) {
        upSpell.tick(hero);
        neutralSpell.tick(hero);
        downSpell.tick(hero);
    }

    @Override public void setDamage(double damage) {
        upSpell.setDamage(damage);
        neutralSpell.setDamage(damage);
        downSpell.setDamage(damage);
    }
    @Override public double getDamage() { return neutralSpell.getDamage(); }

    @Override public void setKnockback(double knockback) {
        upSpell.setKnockback(knockback);
        neutralSpell.setKnockback(knockback);
        downSpell.setKnockback(knockback);
    }
    @Override public double getKnockback() { return neutralSpell.getKnockback(); }

    @Override public void setRadius(double radius) {
        upSpell.setRadius(radius);
        downSpell.setRadius(radius);
    }
    @Override public double getRadius() { return upSpell.getRadius(); }

    private static class UpSpell extends Cone {
        public UpSpell(ConfigurationSection config, HeroSkin skin) { super(config, skin); }
        @Override public boolean execute(Hero hero) { return false; }
    }

    private static class NeutralSpell extends Projectile {
        public NeutralSpell(ConfigurationSection config, HeroSkin skin) { super(config, skin); }
        @Override public boolean execute(Hero hero) { return false; }
    }

    private class DownSpell extends Dash {
        private final Shockwave landingShockwave;
        private final Set<UUID> sharedHitHistory = new HashSet<>();
        private boolean dashing = false;

        public DownSpell(ConfigurationSection config, HeroSkin skin) {
            super(config, skin);
            this.landingShockwave = new Shockwave(config.getConfigurationSection("shockwave"), skin) {
                @Override public boolean execute(Hero hero) { return false; }
                @Override protected void onHit(Hero hero, LivingEntity victim, Location center) {
                    if (sharedHitHistory.contains(victim.getUniqueId())) return;
                    sharedHitHistory.add(victim.getUniqueId());
                    super.onHit(hero, victim, center);
                }
            };
        }

        @Override
        public void perform(Hero hero) {
            sharedHitHistory.clear();
            dashing = true;
            super.perform(hero);
        }

        @Override
        protected void onHit(Hero hero, LivingEntity victim, Vector dashDir) {
            if (sharedHitHistory.contains(victim.getUniqueId())) return;
            sharedHitHistory.add(victim.getUniqueId());
            super.onHit(hero, victim, dashDir);
        }

        @Override
        public void onDeath(Hero hero) {
            super.onDeath(hero);
            dashing = false;
        }

        private boolean airborne = false;

        @Override
        protected void onTick(Hero hero) {
            if (!dashing) return;

            LivingEntity entity = hero.getController().getEntity();
            if (entity != null && airborne && grounded(entity)) {
                dashing = false;
                airborne = false;
                currentDash.cleanup();
                currentDash = null;
                hero.getController().setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                landingShockwave.perform(hero);
                return;
            }
            if (entity != null && !grounded(entity)) airborne = true;

            super.onTick(hero);
            if (currentDash == null) {
                dashing = false;
                airborne = false;
            }
        }

        private boolean grounded(LivingEntity entity) {
            if (entity.isOnGround()) return true;
            if (entity.getVelocity().getY() > 0.1) return false;
            org.bukkit.util.BoundingBox box = entity.getBoundingBox();
            org.bukkit.World world = entity.getWorld();
            int y = (int) Math.floor(box.getMinY() - 0.06);
            if (box.getMinY() - y > 1.06) return false;
            double inset = 0.02;
            double[][] corners = {
                    {box.getMinX() + inset, box.getMinZ() + inset},
                    {box.getMaxX() - inset, box.getMinZ() + inset},
                    {box.getMinX() + inset, box.getMaxZ() - inset},
                    {box.getMaxX() - inset, box.getMaxZ() - inset}};
            for (double[] corner : corners) {
                if (world.getBlockAt((int) Math.floor(corner[0]), y, (int) Math.floor(corner[1]))
                        .getType().isSolid()) {
                    return true;
                }
            }
            return false;
        }

        @Override public void setDamage(double damage) {
            this.damage = damage;
            landingShockwave.setDamage(damage);
        }
        @Override public void setKnockback(double knockback) {
            this.knockback = knockback;
            landingShockwave.setKnockback(knockback);
        }
        @Override public void setRadius(double radius) {
            this.radius = radius;
            landingShockwave.setRadius(radius);
        }
        @Override public boolean execute(Hero hero) { return false; }
    }
}
