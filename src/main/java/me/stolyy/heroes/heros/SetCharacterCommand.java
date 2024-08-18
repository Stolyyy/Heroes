package me.stolyy.heroes.heros;

import me.stolyy.heroes.Hero;
import me.stolyy.heroes.Heroes;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.Arrays;

public class SetCharacterCommand extends Command {
    private final HeroManager heroManager;

    public SetCharacterCommand(HeroManager heroManager) {
        super("setCharacter");
        this.heroManager = heroManager;
        setDescription("Set your character");
        setUsage("/setCharacter <characterName>");
        this.setAliases(Arrays.asList("hero"));
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (args.length > 0) {
                String characterName = args[0];
                Hero hero = null;

                // Instantiate the appropriate character based on the name
                if (characterName.equalsIgnoreCase("VoidCrawler")) {
                    hero = new VoidCrawler(player);
                    ((VoidCrawler) hero).lowerVoidEnergy();
                    Bukkit.getPluginManager().registerEvents((Listener) hero, Heroes.getInstance());
                } else if (characterName.equalsIgnoreCase("Bulk")) {
                    hero = new Bulk(player);
                } else if (characterName.equalsIgnoreCase("Shoop")) {
                    hero = new Shoop(player);
                    ((Shoop) hero).updateEnergy();
                    Bukkit.getPluginManager().registerEvents((Listener) hero, Heroes.getInstance());
                } else if (characterName.equalsIgnoreCase("Skullfire")) {
                    hero = new Skullfire(player);
                } else if (characterName.equalsIgnoreCase("Pug")) {
                    hero = new Pug(player);
                    ((Pug) hero).updateEnergy();
                }

                if (hero != null) {
                    heroManager.setPlayerCharacter(player, hero);
                    player.sendMessage("Character set to " + characterName);
                    return true;
                }
            }
            player.sendMessage("Invalid character name");
        }
        return false;
    }
}
