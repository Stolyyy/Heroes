package me.stolyy.heroes.heros.components;

import me.stolyy.heroes.heros.abilities.Cooldown;

import java.util.HashSet;
import java.util.Set;

//set of recharging abilities
//on tick method that charges abilities and updates displays based on their cooldowns and type
//methods for on ready and not ready
public class CooldownManager implements OnTick {
    private final Set<Cooldown> cooldowns = new HashSet<>();

    public boolean add(Cooldown cooldown) {
        return cooldowns.add(cooldown);
    }

    public boolean remove(Cooldown cooldown) {
        return cooldowns.remove(cooldown);
    }

    @Override
    public void onTick() {
        for(Cooldown cooldown : cooldowns){
            cooldown.updateCooldown(0.05);
        }
    }

    @Override
    public void clean() {
        cooldowns.clear();
    }
}
