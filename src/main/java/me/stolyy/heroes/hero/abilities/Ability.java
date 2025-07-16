package me.stolyy.heroes.hero.abilities;

import me.stolyy.heroes.hero.characters.Hero;
import me.stolyy.heroes.hero.components.Energy;
import me.stolyy.heroes.hero.configs.AbilityConfig;
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

    public double damage() { return config.damage(); }
    public double knockback() { return config.knockback(); }
    public double cooldownTime() { return config.cooldown(); }
    public double duration() { return config.duration(); }
    public double energyCost() { return config.energyCost(); }

    public void clean() {}

    public AbilityConfig getConfig() { return config; }
    public Cooldown getCooldown() { return cooldown; }
}
