package me.stolyy.heroes.ability.kit.bug;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.ActiveAbility;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;

public class BugVoidBeast extends ActiveAbility {
    public BugVoidBeast(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
    }

    @Override
    protected void onActivate(Hero hero) {
        hero.findAbility(BugCharms.class).ifPresent(charms -> charms.setUltimateActive(hero, true));
    }

    @Override
    protected void onDeactivate(Hero hero) {
        hero.findAbility(BugCharms.class).ifPresent(charms -> charms.setUltimateActive(hero, false));
    }
}