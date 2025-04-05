package me.stolyy.heroes.heros.characters;

import me.stolyy.heroes.heros.*;
import me.stolyy.heroes.heros.abilities.*;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.Set;

public class Bug extends HeroEnergy implements Dash, Projectile {
    public static int CHARM_NOTCHES = 9;

    private Set<Charms> charms;
    private double primaryLength;
    private Ability dive;
    private Ability spirit;
    private Ability shriek;
    private double soulPerHit;
    private double soulsPerCast;

    public Bug(Player player){
        super(player);
    }

    @Override
    public void usePrimaryAbility() {

    }

    @Override
    public void onDashHit(Player target, Location location, AbilityType abilityType) {

    }

    @Override
    public void useSecondaryAbility() {

    }

    @Override
    public void onProjectileHit(Player target, Location location, AbilityType abilityType) {

    }

    @Override
    public void onProjectileHitWall(Location location, AbilityType abilityType) {

    }

    @Override
    public void useUltimateAbility() {

    }

    @Override
    public void passiveAbility1() {

    }

    @Override
    public void passiveAbility2() {

    }

    @Override
    protected void stats() {
        primary = new Ability(AbilityType.PRIMARY, true, 5, 1.5, 2);

        registerCharms();
    }

    private void registerCharms(){
        if(charms.contains(Charms.DASHMASTER)) primaryLength += 3;
        if(charms.contains(Charms.GRUBSONG)) ;
        if(charms.contains(Charms.HEAVY_BLOW)) {
            primary.kb *= 1.2;
            dive.kb *= 1.3;
            spirit.kb *= 1.4;
            shriek.kb *= 1.15;
        }
        if(charms.contains(Charms.KINGSOUL)) ;
        if(charms.contains(Charms.MARK_OF_PRIDE)) ;
        if(charms.contains(Charms.NAIL_MASTER)) ;
        if(charms.contains(Charms.QUICK_SLASH)) ;
        if(charms.contains(Charms.SHAMAN_STONE)) ;
        if(charms.contains(Charms.SHARP_SHADOW)) primary.dmg += 2;
        if(charms.contains(Charms.SOUL_EATER)) soulPerHit *= 1.5;
        if(charms.contains(Charms.SOUL_TWISTER)) soulsPerCast *= 0.75;
        if(charms.contains(Charms.SPRINTMASTER)) player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.115);
        if(charms.contains(Charms.STEADY_BODY)) weight += 2;
        if(charms.contains(Charms.STRENGTH)) ;
        if(charms.contains(Charms.WEAVERSONG)) ;
    }
}
