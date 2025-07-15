package me.stolyy.heroes.heros.characters;

import me.stolyy.heroes.heros.HeroEnergy;
import me.stolyy.heroes.hero.config.HeroType;
import me.stolyy.heroes.heros.abilities.Ability;
import me.stolyy.heroes.hero.config.AbilityType;
import org.bukkit.entity.Player;

public class BloonSlayer extends HeroEnergy {

    public BloonSlayer(Player player){
        super(player);
    }

    @Override
    public void usePrimaryAbility() {

    }

    @Override
    public void useSecondaryAbility() {

    }

    @Override
    public void useUltimateAbility() {

    }

    @Override
    protected void stats() {
        setHeroType(HeroType.HYBRID);
        setWeight(2.5);

        primary = new Ability(AbilityType.PRIMARY,  6, 2.5, 2);
        secondary = new Ability(AbilityType.SECONDARY, 10, 5, 5.5);
        ultimate = new Ability(AbilityType.ULTIMATE, 0,0,90, 10);
    }
}
