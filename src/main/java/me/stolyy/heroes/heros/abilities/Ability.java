package me.stolyy.heroes.heros.abilities;

import me.stolyy.heroes.heros.characters.Hero;
import me.stolyy.heroes.heros.components.Energy;
import me.stolyy.heroes.heros.configs.AbilityConfig;
import org.bukkit.entity.Player;

public abstract class Ability
{
    protected final Hero hero;
    protected final Player player;

    protected final Cooldown cooldown;
    protected final Energy energy;

    protected final AbilityConfig config;

    public Ability(Hero hero, AbilityConfig config)
    {
        this.hero = hero;
        this.player = hero.getPlayer();
        this.cooldown = new Cooldown(this);
        this.energy = hero.getEnergy();

        this.config = config;
    }

    public void execute()
    {
        if(!cooldown.isReady())
        {
            cooldown.notReadyYet();
            return;
        }
        if(cooldown.isInUse())
        {
            cooldown.alreadyInUse();
            return;
        }
        if(energy.getCurrentEnergy() < energyCost())
        {
            cooldown.notEnoughEnergy();
            return;
        }
        energy.changeEnergy(-energyCost());
        cooldown.start();
        onUse();
    }

    public abstract void onUse();

    public void onHit(Player target) {}
    public void onEnd() {}

    public void clean() {}

    public double damage() { return config.damage(); }
    public double knockback() { return config.knockback(); }
    public double cooldownTime() { return config.cooldown(); }
    public double duration() { return config.duration(); }
    public double energyCost() { return config.energyCost(); }

    public AbilityConfig getConfig() { return config; }
    public Cooldown getCooldown() { return cooldown; }
}
