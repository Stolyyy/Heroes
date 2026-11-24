package me.stolyy.heroes.ability.kit.skullfire;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.hero.mechanics.DoubleJump;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

public class SkullfireBullseye extends BaseAbility {
    private final int hitsForReset;
    private int streak = 0;
    private int hitsThisUse = 0;
    private boolean hadUse = false;

    public SkullfireBullseye(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        hitsForReset = Configs.requireInt(this.config, "hits_for_reset");
    }

    @Override
    public void perform(Hero hero) { }

    @Override
    public void onAbilityUse(Hero hero, Ability ability){
        if (ability.getType() != AbilityType.PRIMARY) return;
        if (hadUse && hitsThisUse == 0) streak = 0;
        hitsThisUse = 0;
        hadUse = true;
    }

    @Override
    public void onAbilityHit(Hero hero, Ability ability, LivingEntity victim){
        if (ability == null || ability.getType() != AbilityType.PRIMARY) return;

        hitsThisUse++;
        streak++;

        DoubleJump mobility = hero.getMobility();
        if(mobility instanceof SkullMobility skullMobility){
            if(!hero.getController().isOnGround())
                skullMobility.addJump(hero);
        }

        if (streak >= hitsForReset) {
            resetSecondary(hero);
            streak = 0;
        }
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        streak = 0;
        hitsThisUse = 0;
        hadUse = false;
    }

    private void resetSecondary(Hero hero){
        hero.getSecondaryAbility().getCooldownObject().forceReady();
    }
}
