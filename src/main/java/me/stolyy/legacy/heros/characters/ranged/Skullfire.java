package me.stolyy.heroes.heros.characters.ranged;

import me.stolyy.heroes.heros.characters.Hero;
import me.stolyy.heroes.heros.components.UseDrop;
import me.stolyy.heroes.heros.configs.HeroConfig;
import org.bukkit.entity.Player;

public class Skullfire extends Hero implements UseDrop {
    public Skullfire(Player player, HeroConfig config) {
        super(player, config);
    }

    @Override
    public void useDrop() {

    }

    @Override
    protected void defineAbilities() {

    }
}
