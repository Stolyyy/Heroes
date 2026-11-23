package me.stolyy.heroes.ability.kit.voidcrawler;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.physics.Hitbox;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class VoidShadowFury extends BaseAbility {
    private static final int DARKNESS_DURATION_TICKS = 60;

    private final double radius;
    private final double speedBuff;

    private final int blinkPeriodTicks;
    private final int blinkOffTicks;
    private final SoundEffect activationSound;
    private boolean wasActive = false;
    private int blinkTick = 0;

    private final Set<UUID> darkened = new HashSet<>();

    public VoidShadowFury(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.radius = this.config.getDouble("radius");
        this.speedBuff = this.config.getDouble("speed_buff"); // Base is 0.2
        this.blinkPeriodTicks = Configs.requireInt(this.config, "blink_period_ticks");
        this.blinkOffTicks = Configs.requireInt(this.config, "blink_off_ticks");
        this.activationSound = new SoundEffect(this.config.getConfigurationSection("visuals.activation_sound"));
    }

    @Override
    public void perform(Hero hero) {
        activationSound.play(hero.getController());
        hero.getEnergy().setCanRegen(false);
        hero.getEnergy().setEnergy(hero.getEnergy().getMaxEnergy());
        hero.getAttributes().setMovementSpeed(hero.getController(), speedBuff);
        blinkTick = 0;
        wasActive = true;
    }

    @Override
    protected void onTick(Hero hero) {
        boolean isActive = getCooldownObject().isInUse();

        if (isActive) {
            boolean blinkOff = (blinkTick % blinkPeriodTicks) >= blinkPeriodTicks - blinkOffTicks;
            blinkTick++;
            if (blinkOff) {
                clearDarkness();
            } else {
                Hitbox.sphere(hero.getController().getLocation(), radius)
                        .exclude(hero.getController().getEntity())
                        .excludeTeammates(hero.getController().getEntity())
                        .onHit((victim) -> onHit(hero, victim))
                        .check();
            }
        }

        if (wasActive && !isActive) {
            onExpire(hero, null);
        }
    }

    public void onHit(Hero hero, LivingEntity victim) {
        if (GameManager.canTarget(hero.getController().getEntity(), victim)) {
            victim.addPotionEffect(new PotionEffect(
                    PotionEffectType.DARKNESS, DARKNESS_DURATION_TICKS, 1, false, false, false));
            darkened.add(victim.getUniqueId());
        }
    }

    @Override
    public void onExpire(Hero hero, Location location) {
        cleanupBuff(hero);
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        if (wasActive) {
            cleanupBuff(hero);
        }
    }

    @Override
    public void onOwnerLost(Hero hero) {
        clearDarkness();
    }

    private void cleanupBuff(Hero hero) {
        clearDarkness();
        hero.getEnergy().setCanRegen(true);
        hero.getAttributes().setMovementSpeed(hero.getController(), hero.getBaseStats().attributes().movementSpeed());
        wasActive = false;
    }

    private void clearDarkness() {
        for (UUID id : darkened) {
            if (Bukkit.getEntity(id) instanceof LivingEntity living) {
                living.removePotionEffect(PotionEffectType.DARKNESS);
            }
        }
        darkened.clear();
    }
}