package me.stolyy.heroes;

import me.stolyy.heroes.heros.*;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
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

                // Initialize character based on arguments
                if (characterName.equalsIgnoreCase("VoidCrawler")) {
                    hero = new VoidCrawler(player);
                    player.playSound(player.getLocation(), "duskcrawler.select", SoundCategory.MASTER, 2.0f, 1.0f);
                    ((VoidCrawler) hero).updateAttackDamage();
                    Bukkit.getPluginManager().registerEvents((Listener) hero, Heroes.getInstance());
                } else if (characterName.equalsIgnoreCase("Bulk")) {
                    //hero = new Bulk(player);
                } else if (characterName.equalsIgnoreCase("Shoop")) {
                    hero = new Shoop(player);
                    player.playSound(player.getLocation(), "shoopdawhoop.select", SoundCategory.MASTER, 2.0f, 1.0f);
                    Bukkit.getPluginManager().registerEvents((Listener) hero, Heroes.getInstance());
                } else if (characterName.equalsIgnoreCase("Skullfire")) {
                    hero = new Skullfire(player);
                    player.playSound(player.getLocation(), "skullfire.select", SoundCategory.MASTER, 2.0f, 1.0f);
                } else if (characterName.equalsIgnoreCase("Pug")) {
                    hero = new Pug(player);
                    player.playSound(player.getLocation(), Sound.ENTITY_WOLF_HOWL, 2.0f, 1.0f);
                }

                if (hero != null) {
                    heroManager.setHero(player, hero);
                    player.sendMessage("Character set to " + characterName);
                    return true;
                }
            }
            player.sendMessage("Invalid character name");
        }
        return false;
    }
}
