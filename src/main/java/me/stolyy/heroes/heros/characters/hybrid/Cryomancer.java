package me.stolyy.heroes.heros.characters.hybrid;

import me.stolyy.heroes.heros.characters.Hero;
import me.stolyy.heroes.heros.components.UseSneak;
import me.stolyy.heroes.heros.configs.HeroConfig;
import org.bukkit.entity.Player;

public class Cryomancer extends Hero implements UseSneak {

    public Cryomancer(Player player, HeroConfig config) {
        super(player, config);
    }

    @Override
    protected void defineAbilities() {

    }

    @Override
    public void useSneak() {

    }
}
