package me.stolyy.heroes.ability.kit.spooderman;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CaveSpooderman extends BaseAbility {
    private final double speedMultiplier;
    private final double dotDamage;
    private final int dotIntervalTicks;
    private final int dotDurationTicks;
    private final int slowDurationTicks;
    private final int slowAmplifier;
    private final SoundEffect activationSound;

    private boolean active;
    private final List<DotInstance> dots = new ArrayList<>();

    public CaveSpooderman(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.speedMultiplier = this.config.getDouble("speed_multiplier");
        this.dotDamage = this.config.getDouble("dot_damage");
        this.dotIntervalTicks = this.config.getInt("dot_interval_ticks");
        this.dotDurationTicks = this.config.getInt("dot_duration_ticks");
        this.slowDurationTicks = this.config.getInt("slow_duration_ticks");
        this.slowAmplifier = this.config.getInt("slow_amplifier");
        this.activationSound = new SoundEffect(this.config.getConfigurationSection("visuals.activation_sound"));
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public void perform(Hero hero) {
        active = true;
        activationSound.play(hero.getController().getLocation(), hero.getController());
        hero.getAttributes().setSpeedMultiplier(hero.getController(), speedMultiplier);
    }

    @Override
    protected void onTick(Hero hero) {
        if (active && !cooldown.isInUse()) {
            deactivate(hero);
        }

        Iterator<DotInstance> it = dots.iterator();
        while (it.hasNext()) {
            if (it.next().tick(hero)) it.remove();
        }
    }

    @Override
    public void onJabHit(Hero hero, LivingEntity victim) {
        if (!active) return;
        dots.removeIf(dot -> dot.victim.getUniqueId().equals(victim.getUniqueId()));
        dots.add(new DotInstance(victim));
    }

    @Override
    public void onAbilityUse(Hero hero, Ability ability) {
        if (!active || ability.getType() != AbilityType.PRIMARY) return;
        for (Ability passive : hero.getPassiveAbilities()) {
            if (passive instanceof SpooderFriends friends) {
                friends.trySpawnBuddy(hero, hero.getController().getLocation());
                return;
            }
        }
    }

    @Override
    public void onAbilityHit(Hero hero, Ability ability, LivingEntity victim) {
        if (!active || ability != hero.getSecondaryAbility()) return;
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowDurationTicks, slowAmplifier));
    }

    private void deactivate(Hero hero) {
        active = false;
        hero.getAttributes().setSpeedMultiplier(hero.getController(), 1.0);
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        if (active) deactivate(hero);
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
