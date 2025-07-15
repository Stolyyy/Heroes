package me.stolyy.heroes.hero.components;

import me.stolyy.heroes.hero.characters.Hero;
import me.stolyy.heroes.hero.abilities.Ability;
import me.stolyy.heroes.hero.data.HeroData.EnergyData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Energy {
    private final Hero hero;

    private final double maxEnergy;
    private double currentEnergy, regenRate; // regen rate in energy per second
    private boolean canChangeEnergy;

    public Energy(Hero hero, EnergyData data) {
        this.hero = hero;

        maxEnergy = data.getMax();
        regenRate = data.getRegenerationRate();
        currentEnergy = 0;
        canChangeEnergy = true;
    }

    public void onTick(){
        if(canChangeEnergy) {
            changeEnergy(regenRate / 20);
        }
        hero.getDisplay().setXPBar(currentEnergy, currentEnergy / maxEnergy);
    }

    public void notEnoughEnergy(Ability ability){
        hero.getPlayer().sendMessage(
                Component.text("Not enough energy to use " +
                        ability.getData().getName() + "!", NamedTextColor.RED)
                        .append(Component.text((int) (ability.energyCost() - currentEnergy) , NamedTextColor.WHITE))
                        .append(Component.text(" more needed." , NamedTextColor.RED)));
    }

    public void changeEnergy(double change) {
        currentEnergy = Math.min(Math.max(currentEnergy + change, 0), maxEnergy);
    }

    public void allowEnergyChanges(){
        canChangeEnergy = true;
    }

    public void disallowEnergyChanges(){
        canChangeEnergy = false;
    }

    public void setEnergyPerSecond(double regenRate){
        this.regenRate = regenRate;
    }

    public double getCurrentEnergy(){
        return currentEnergy;
    }

    public double getMaxEnergy(){
        return maxEnergy;
    }
}
