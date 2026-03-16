package me.stolyy.heroes.heros.characters.melee;


import me.stolyy.heroes.heros.characters.Hero;
import me.stolyy.heroes.heros.components.UseSneak;
import me.stolyy.heroes.heros.configs.HeroConfig;
import org.bukkit.entity.Player;

public class Marauder extends Hero implements UseSneak {
    public Marauder(Player player, HeroConfig config) {
        super(player, config);
    }

    @Override
    protected void defineAbilities() {

    }

    @Override
    public void useSneak() {

    }
}
