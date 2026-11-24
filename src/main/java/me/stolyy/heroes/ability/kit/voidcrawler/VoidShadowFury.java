package me.stolyy.heroes.ability.kit.voidcrawler;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.ActiveAbility;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.physics.Hitbox;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class VoidShadowFury extends ActiveAbility {
    private static final int DARKNESS_DURATION_TICKS = 60;

    private final double speedBuff;
    private final int blinkPeriodTicks;
    private final int blinkOffTicks;
    private int blinkTick = 0;

    private final Set<UUID> darkened = new HashSet<>();

    public VoidShadowFury(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.speedBuff = Configs.requireDouble(this.config, "speed_buff"); // Base is 0.2
        this.blinkPeriodTicks = Configs.requireInt(this.config, "blink_period_ticks");
        this.blinkOffTicks = Configs.requireInt(this.config, "blink_off_ticks");
    }

    @Override
    protected void onActivate(Hero hero) {
        hero.getEnergy().blockRegen(this);
        hero.getEnergy().setEnergy(hero.getEnergy().getMaxEnergy());
        hero.getAttributes().addSpeedFlat(hero.getController(), this,
                speedBuff - hero.getAttributes().getBaseMovementSpeed());
        blinkTick = 0;
    }

    @Override
    protected void onActiveTick(Hero hero) {
        boolean blinkOff = (blinkTick % blinkPeriodTicks) >= blinkPeriodTicks - blinkOffTicks;
        blinkTick++;
        if (blinkOff) {
            clearDarkness();
        } else {
            Hitbox.sphere(hero.getController().getLocation(), radius)
                    .exclude(hero.getController().getEntity())
                    .excludeTeammates(hero.getController().getEntity())
                    .onHit(victim -> applyDarkness(hero, victim))
                    .check();
        }
    }

    private void applyDarkness(Hero hero, LivingEntity victim) {
        if (GameManager.canTarget(hero.getController().getEntity(), victim)) {
            victim.addPotionEffect(new PotionEffect(
                    PotionEffectType.DARKNESS, DARKNESS_DURATION_TICKS, 1, false, false, false));
            darkened.add(victim.getUniqueId());
        }
    }

    @Override
    protected void onDeactivate(Hero hero) {
        clearDarkness();
        hero.getEnergy().unblockRegen(this);
        hero.getAttributes().removeSpeedModifier(hero.getController(), this);
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