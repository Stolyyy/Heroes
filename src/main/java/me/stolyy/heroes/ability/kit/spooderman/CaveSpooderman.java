package me.stolyy.heroes.ability.kit.spooderman;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.ability.template.ActiveAbility;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

public class CaveSpooderman extends ActiveAbility {
    private final double speedMultiplier;
    private final double dotDamage;
    private final int dotIntervalTicks;
    private final int dotDurationTicks;
    private final int slowDurationTicks;
    private final int slowAmplifier;

    private final List<DotInstance> dots = new ArrayList<>();

    public CaveSpooderman(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.speedMultiplier = Configs.requireDouble(this.config, "speed_multiplier");
        this.dotDamage = Configs.requireDouble(this.config, "dot_damage");
        this.dotIntervalTicks = Configs.requireInt(this.config, "dot_interval_ticks");
        this.dotDurationTicks = Configs.requireInt(this.config, "dot_duration_ticks");
        this.slowDurationTicks = Configs.requireInt(this.config, "slow_duration_ticks");
        this.slowAmplifier = Configs.requireInt(this.config, "slow_amplifier");
    }

    @Override
    protected void onActivate(Hero hero) {
        hero.getAttributes().addSpeedMultiplier(hero.getController(), this, speedMultiplier);
    }

    @Override
    protected void onAlwaysTick(Hero hero) {
        for (DotInstance dot : new ArrayList<>(dots)) {
            if (dot.tick(hero)) dots.remove(dot);
        }
    }

    @Override
    public void onJabHit(Hero hero, LivingEntity victim) {
        if (!isActive()) return;
        dots.removeIf(dot -> dot.victim.getUniqueId().equals(victim.getUniqueId()));
        dots.add(new DotInstance(victim));
    }

    @Override
    public void onAbilityUse(Hero hero, Ability ability) {
        if (!isActive() || ability.getType() != AbilityType.PRIMARY) return;
        hero.findAbility(SpooderFriends.class)
                .ifPresent(friends -> friends.trySpawnBuddy(hero, hero.getController().getLocation()));
    }

    @Override
    public void onAbilityHit(Hero hero, Ability ability, LivingEntity victim) {
        if (!isActive() || ability != hero.getSecondaryAbility()) return;
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowDurationTicks, slowAmplifier));
    }

    @Override
    protected void onDeactivate(Hero hero) {
        hero.getAttributes().removeSpeedModifier(hero.getController(), this);
    }

    @Override
    protected void onActiveDeath(Hero hero) {
        dots.clear();
    }

    @Override
    protected void onActiveOwnerLost(Hero hero) {
        dots.clear();
    }

    private class DotInstance {
        private final LivingEntity victim;
        private int remainingTicks = dotDurationTicks;
        private int ticksUntilPulse = dotIntervalTicks;

        DotInstance(LivingEntity victim) {
            this.victim = victim;
        }

        boolean tick(Hero hero) {
            if (victim.isDead() || !victim.isValid()) return true;
            remainingTicks--;
            ticksUntilPulse--;
            if (ticksUntilPulse <= 0) {
                ticksUntilPulse = dotIntervalTicks;
                Interactions.on(hero.getController().getEntity(), victim)
                        .abilityName(CaveSpooderman.this.getName())
                        .damage(dotDamage)
                        .execute();
            }
            return remainingTicks <= 0;
        }
    }
}
