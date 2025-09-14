package me.stolyy.heroes.heros.abilities;

import me.stolyy.heroes.heros.components.CooldownManager;
import me.stolyy.heroes.heros.components.Display;
import me.stolyy.heroes.heros.components.Energy;
import me.stolyy.heroes.heros.configs.AbilityEnums;
import me.stolyy.heroes.utility.effects.CustomItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public class Cooldown {
    private final Ability ability;
    private final CooldownManager cooldownManager;
    private final Energy energy;
    private final Player player;

    private final AbilityEnums.CooldownType cooldownType;
    private final AbilityEnums.CooldownDisplay cooldownDisplay;
    private final AbilityEnums.CooldownReadyDisplay cooldownReadyDisplay;

    private boolean ready, inUse;
    private double timeUntilReady;

    public Cooldown(Ability ability) {
        this.ability = ability;
        this.cooldownManager = ability.hero.getCooldownManager();
        this.energy = ability.hero.getEnergy();

        cooldownType = ability.getConfig().cooldownType();
        cooldownDisplay = ability.getConfig().cooldownDisplay();
        cooldownReadyDisplay = ability.getConfig().cooldownReadyDisplay();

        this.player = ability.player;

        start();
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isInUse() {
        return inUse;
    }

    public void start() {
        ready = false;
        timeUntilReady = ability.cooldownTime();

        if(ability.duration() != 0) {
            inUse = true;
        }

        if(cooldownType == AbilityEnums.CooldownType.TIME
                || cooldownType == AbilityEnums.CooldownType.TIME_AND_ENERGY
                || cooldownType == AbilityEnums.CooldownType.ENERGY)
            cooldownManager.add(this);
    }

    public void addCharge() {
        if(cooldownType != AbilityEnums.CooldownType.CHARGE || ready || inUse) {
            return;
        }


        timeUntilReady -= 1;
        if(timeUntilReady <= 0) {
            setReady();
        }
        displayCooldown();
    }

    public void updateCooldown(double deltaSeconds) {
        if (ready && !inUse) return;

        timeUntilReady = Math.max(0, timeUntilReady - deltaSeconds);

        if(inUse && ability.cooldownTime() - timeUntilReady  >= ability.duration())
            inUse = false;
        if (timeUntilReady <= 0 && ability.energyCost() <= energy.getCurrentEnergy())
            setReady();

        displayCooldown();
    }

    public void setReady() {
        cooldownManager.remove(this);
        ready = true;
        timeUntilReady = 0;
        inUse = false;
        if(ability.getConfig().abilityType() == AbilityEnums.AbilityType.PRIMARY) {
            int maxAmmo = Math.min(1, ability.hero.getAmmo().maxAmmo());
            CustomItems.setStack(player, 0, maxAmmo);
        }
    }

    private void displayCooldown() {
        if(ready) {
            displayReady();
            return;
        }

        double percentComplete = timeUntilReady / ability.cooldownTime();
        if(ability.energyCost() != 0) {
            double percentCompleteEnergy = energy.getCurrentEnergy() / ability.energyCost();
            if(cooldownType == AbilityEnums.CooldownType.ENERGY) percentComplete = percentCompleteEnergy;
            else if(cooldownType == AbilityEnums.CooldownType.TIME_AND_ENERGY) percentComplete = Math.min(percentComplete, percentCompleteEnergy);
        }
        percentComplete = Math.max(0, Math.min(1, percentComplete));


        switch (cooldownDisplay) {
            case ACTION_BAR -> {
                Display display = ability.hero.getDisplay();
                Component progressBar = display.progressBar(percentComplete, 10);
                display.sendActionBar(Component.text(ability.getConfig().name() + ": ", NamedTextColor.WHITE).append(progressBar));
            }
            case ITEM_DAMAGE -> {
                int slot;
                if(ability.getConfig().abilityType() == AbilityEnums.AbilityType.PRIMARY) slot = 0;
                else if (ability.getConfig().abilityType() == AbilityEnums.AbilityType.SECONDARY) slot = 1;
                else slot = 2;
                CustomItems.setDamage(player, slot, 1 - percentComplete);
            }
            case NONE -> {}
        }
    }

    private void displayReady(){
        Display display = ability.hero.getDisplay();
        switch(cooldownReadyDisplay) {
            case CHAT -> player.sendMessage(Component.text(ability.getConfig().name() + " is ready!", NamedTextColor.GREEN));
            case ACTION_BAR -> display.sendActionBar(Component.text(ability.getConfig().name() + " is ready!", NamedTextColor.GREEN));
            case TITLE -> display.sendTitle(Component.text(ability.getConfig().name() + " is ready!", NamedTextColor.GREEN), Component.empty());
            case NONE -> {}
        }
    }

    public void notReadyYet() {
        player.sendMessage(Component.text(ability.getConfig().name() + " is not ready! ", NamedTextColor.RED)
                .append(Component.text((int) timeUntilReady, NamedTextColor.WHITE))
                .append(Component.text(" seconds remaining.", NamedTextColor.YELLOW)));
    }

    public void alreadyInUse() {
        player.sendMessage(Component.text(ability.getConfig().name() + " is already in use!", NamedTextColor.RED)
                .append(Component.text(" Please wait ", NamedTextColor.YELLOW))
                .append(Component.text((int) ability.cooldownTime() - timeUntilReady, NamedTextColor.WHITE))
                .append(Component.text(" more seconds", NamedTextColor.YELLOW)));
    }

    public void notEnoughEnergy() {
        player.sendMessage(Component.text("Not enough energy to use " + ability.getConfig().name() + "!", NamedTextColor.RED)
                        .append(Component.text((int) (ability.energyCost() - energy.getCurrentEnergy()) , NamedTextColor.WHITE))
                        .append(Component.text(" more needed." , NamedTextColor.YELLOW)));
    }
}
