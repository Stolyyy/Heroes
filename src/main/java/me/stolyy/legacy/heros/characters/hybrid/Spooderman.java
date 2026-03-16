package me.stolyy.heroes.heros.characters.hybrid;

import me.stolyy.heroes.heros.characters.Hero;
import me.stolyy.heroes.heros.components.UseSneak;
import me.stolyy.heroes.heros.configs.HeroConfig;
import org.bukkit.entity.Player;

public class Spooderman extends Hero implements UseSneak {
    public Spooderman(Player player, HeroConfig config) {
        super(player, config);
    }

    @Override
    public void useSneak() {

    }

    @Override
    protected void defineAbilities() {

    }
}
