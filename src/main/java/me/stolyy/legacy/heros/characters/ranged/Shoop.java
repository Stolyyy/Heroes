package me.stolyy.heroes.heros.characters.ranged;

import me.stolyy.heroes.heros.characters.Hero;
import me.stolyy.heroes.heros.components.UseSneak;
import me.stolyy.heroes.heros.configs.HeroConfig;
import org.bukkit.entity.Player;

public class Shoop extends Hero implements UseSneak {
    public Shoop(Player player, HeroConfig config) {
        super(player, config);
    }

    @Override
    protected void defineAbilities() {

    }

    @Override
    public void useSneak() {

    }
}
