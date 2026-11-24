package me.stolyy.heroes.ability.kit.sgt;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

public class Parry extends BaseAbility {
    private final long parryWindowMs;
    private final long successInvulnMs;
    private final long buffDurationMs;

    private final double parryDamageTakenMultiplier;
    private final double parryKnockbackTakenMultiplier;
    private final double buffDamageDealtMultiplier;
    private final double buffKnockbackDealtMultiplier;
    private final int energyRefund;
    private final Component chargedMessage;
    private final Component successTitle;
    private final Component successSubtitle;
    private final int titleFadeInTicks;
    private final int titleStayTicks;
    private final int titleFadeOutTicks;
    private final SoundEffect activationSound;
    private final SoundEffect successSound;
    private final SoundEffect missSound;

    private final Object defenseKey = new Object();
    private final Object buffKey = new Object();

    private boolean windowActive;
    private long windowEndMs;

    private boolean invulnActive;
    private long invulnEndMs;

    private boolean buffApplied;
    private long buffEndMs;

    public Parry(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.parryWindowMs = Configs.requireLong(this.config, "parry_window_ms");
        this.successInvulnMs = Configs.requireLong(this.config, "success_invuln_ms");
        this.buffDurationMs = (long) (Configs.requireDouble(this.config, "buff_duration") * 1000);
        this.parryDamageTakenMultiplier = Configs.requireDouble(this.config, "parry_damage_taken_multiplier");
        this.parryKnockbackTakenMultiplier = Configs.requireDouble(this.config, "parry_knockback_taken_multiplier");
        this.buffDamageDealtMultiplier = Configs.requireDouble(this.config, "buff_damage_dealt_multiplier");
        this.buffKnockbackDealtMultiplier = Configs.requireDouble(this.config, "buff_knockback_dealt_multiplier");
        this.energyRefund = Configs.requireInt(this.config, "energy_refund");
        this.chargedMessage = MiniMessage.miniMessage()
                .deserialize(Configs.requireString(this.config, "charged_message"));

        ConfigurationSection titleSection = Configs.requireSection(this.config, "visuals.success_title");
        this.successTitle = MiniMessage.miniMessage().deserialize(Configs.requireString(titleSection, "title"));
        this.successSubtitle = MiniMessage.miniMessage().deserialize(Configs.requireString(titleSection, "subtitle"));
        this.titleFadeInTicks = Configs.requireInt(titleSection, "fade_in_ticks");
        this.titleStayTicks = Configs.requireInt(titleSection, "stay_ticks");
        this.titleFadeOutTicks = Configs.requireInt(titleSection, "fade_out_ticks");

        this.activationSound = new SoundEffect(Configs.requireSection(this.config, "visuals.activation_sound"));
        this.successSound = new SoundEffect(Configs.requireSection(this.config, "visuals.success_sound"));
        this.missSound = new SoundEffect(Configs.requireSection(this.config, "visuals.miss_sound"));
    }

    @Override
    protected boolean canUse(Hero hero) {
        return !cooldown.isInUse();
    }

    @Override
    public void perform(Hero hero) {
        if (!windowActive && !invulnActive) {
            hero.getAttributes().addDamageTakenMultiplier(defenseKey, parryDamageTakenMultiplier);
            hero.getAttributes().addKnockbackTakenMultiplier(defenseKey, parryKnockbackTakenMultiplier);
        }
        windowActive = true;
        invulnActive = false;
        windowEndMs = System.currentTimeMillis() + parryWindowMs;
        activationSound.play(hero.getController().getLocation(), hero.getController());
    }

    @Override
    protected void onTick(Hero hero) {
        if (windowActive && System.currentTimeMillis() >= windowEndMs) {
            closeWindow(hero);
            missSound.play(hero.getController().getLocation(), hero.getController());
        }
        if (invulnActive && System.currentTimeMillis() >= invulnEndMs) {
            endInvuln(hero);
        }
        if (buffApplied && System.currentTimeMillis() >= buffEndMs) {
            clearBuff(hero);
        }
        if (buffApplied) {
            hero.getController().sendActionBar(chargedMessage);
        }
    }

    @Override
    public void onDamageTaken(Hero hero, Entity source, double amount) {
        if (windowActive) blockSuccess(hero);
    }

    @Override
    public void onKnockbackTaken(Hero hero, Entity source, Vector knockback) {
        if (windowActive) blockSuccess(hero);
    }

    @Override
    public void onAbilityUse(Hero hero, Ability ability) {
        if (ability == this) return;
        clearBuff(hero);
    }

    private void blockSuccess(Hero hero) {
        hero.getEnergy().changeEnergy(energyRefund);
        successSound.play(hero.getController().getLocation(), hero.getController());
        hero.getController().sendTitle(successTitle, successSubtitle,
                titleFadeInTicks, titleStayTicks, titleFadeOutTicks);
        applyBuff(hero);

        windowActive = false;
        invulnActive = true;
        invulnEndMs = System.currentTimeMillis() + successInvulnMs;
    }

    private void applyBuff(Hero hero) {
        buffEndMs = System.currentTimeMillis() + buffDurationMs;
        if (buffApplied) return;
        buffApplied = true;
        hero.getAttributes().addDamageDealtMultiplier(buffKey, buffDamageDealtMultiplier);
        hero.getAttributes().addKnockbackDealtMultiplier(buffKey, buffKnockbackDealtMultiplier);
    }

    private void clearBuff(Hero hero) {
        if (!buffApplied) return;
        buffApplied = false;
        hero.getAttributes().removeDamageDealtModifier(buffKey);
        hero.getAttributes().removeKnockbackDealtModifier(buffKey);
    }

    private void closeWindow(Hero hero) {
        if (!windowActive) return;
        windowActive = false;
        removeDefenseModifiers(hero);
    }

    private void endInvuln(Hero hero) {
        if (!invulnActive) return;
        invulnActive = false;
        removeDefenseModifiers(hero);
    }

    private void removeDefenseModifiers(Hero hero) {
        hero.getAttributes().removeDamageTakenModifier(defenseKey);
        hero.getAttributes().removeKnockbackTakenModifier(defenseKey);
    }

    public boolean isCharged() { return buffApplied; }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        closeWindow(hero);
        endInvuln(hero);
        clearBuff(hero);
    }

    @Override
    public void onOwnerLost(Hero hero) {
        super.onOwnerLost(hero);
        closeWindow(hero);
        endInvuln(hero);
        clearBuff(hero);
    }
}
