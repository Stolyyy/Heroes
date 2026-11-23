package me.stolyy.heroes.ability.kit.bulk;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Shockwave;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;

public class BulkSlam extends Shockwave {
    private boolean doubleJumped = false;

    public BulkSlam(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
    }

    @Override
    public void onDoubleJump(Hero hero) {
        doubleJumped = true;
    }

    @Override
    public void onLand(Hero hero){
        if (!doubleJumped) return;
        doubleJumped = false;
        if(hero.getSmashAbility().getCooldownObject().isInUse()) return;
        execute(hero);
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        doubleJumped = false;
    }
}
