package me.stolyy.heroes.heros.characters.ranged;

import me.stolyy.heroes.heros.characters.Hero;
import me.stolyy.heroes.heros.components.UseDrop;
import me.stolyy.heroes.heros.components.UseSneak;
import me.stolyy.heroes.heros.configs.HeroConfig;
import org.bukkit.entity.Player;

public class Tinman extends Hero implements UseSneak, UseDrop {
    public Tinman(Player player, HeroConfig config) {
        super(player, config);
    }

    @Override
    public void useDrop() {

    }

    @Override
    public void useSneak() {

    }

    @Override
    protected void defineAbilities() {

    }
}
