package me.stolyy.heroes.hero.abilities;

import me.stolyy.heroes.hero.characters.Hero;
import me.stolyy.heroes.hero.components.Energy;
import me.stolyy.heroes.hero.config.AbilityConfig;
import me.stolyy.heroes.hero.data.AbilityData;
import org.bukkit.entity.Player;

public abstract class Ability
{
    protected final Hero hero;
    protected final Player player;
    protected final Energy energy;
    protected final AbilityConfig config;
    protected final AbilityData data;

    public Ability(Hero hero, AbilityData data)
    {
        this.hero = hero;
        this.energy = hero.getComponent(Energy.class);
        this.player = hero.getPlayer();
        this.data = data;
    }

    public void execute()
    {
        if(!cooldowns.isReady(this))
        {
            cooldowns.notReadyYet(this);
            return;
        }
        if(energy.getCurrentEnergy() < energyCost())
        {
            energy.notEnoughEnergy(this);
            return;
        }
        energy.changeEnergy(-energyCost());
        cooldowns.start(this);
        onUse();
    }

    public abstract void onUse();

    public void onHit(Player target) {}
    public void onEnd() {}

    public double damage() { return data.getDamage(); }
    public double knockback() { return data.getKnockback(); }
    public double cooldown() { return data.getCooldown(); }
    public double energyCost() { return 0; }

    public void clean() {}

    public AbilityData getData() { return data; }
}
