package me.stolyy.heroes.utility.commands;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.minigame.GameManager;
import me.stolyy.heroes.heros.Hero;
import me.stolyy.heroes.heros.HeroManager;
import me.stolyy.heroes.heros.characters.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SetHeroCommand extends Command {

    public SetHeroCommand() {
        super("setCharacter");
        setDescription("Set your character");
        setUsage("/setCharacter <characterName>");
        this.setAliases(List.of("hero"));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, String[] args) {
        if (sender instanceof Player player) {
            if (args.length > 0) {
                String characterName = args[0];
                Hero hero = null;

                if(GameManager.isPlayerInGame(player)){
                    player.sendMessage(Component.text("You cannot change characters while in a game!", NamedTextColor.RED));
                    return false;
                }

                // Initialize character based on arguments
                if (characterName.equalsIgnoreCase("VoidCrawler") || characterName.equalsIgnoreCase("Void") || characterName.equalsIgnoreCase("Void_Crawler")) {
                    hero = new VoidCrawler(player);
                    player.playSound(player.getLocation(), "duskcrawler.select", SoundCategory.MASTER, 2.0f, 1.0f);
                    ((VoidCrawler) hero).updateAttackDamage();
                    //Bukkit.getPluginManager().registerEvents((Listener) hero, Heroes.getInstance());
                } else if (characterName.equalsIgnoreCase("Bulk")) {
                    player.sendMessage(Component.text("Hero not yet supported", NamedTextColor.RED));
                    //hero = new Bulk(player);
                } else if (characterName.equalsIgnoreCase("Bug")) {
                    hero = new Bug(player);
                    player.playSound(player.getLocation(), Sound.ENTITY_PARROT_IMITATE_SILVERFISH, 2.0f, 1.0f);
                } else if (characterName.equalsIgnoreCase("Spooderman")) {
                    player.sendMessage(Component.text("Hero not yet supported", NamedTextColor.RED));
                    //hero = new Spooderman(player);
                } else if (characterName.equalsIgnoreCase("Punishgers")) {
                    player.sendMessage(Component.text("Hero not yet supported", NamedTextColor.RED));
                    //hero = new Punishgers(player);
                } else if (characterName.equalsIgnoreCase("Blud")) {
                    player.sendMessage(Component.text("Hero not yet supported", NamedTextColor.RED));
                    //hero = new Blud(player);
                } else if (characterName.equalsIgnoreCase("Shoop")) {
                    hero = new Shoop(player);
                    player.playSound(player.getLocation(), "shoopdawhoop.select", SoundCategory.MASTER, 2.0f, 1.0f);
                    Bukkit.getPluginManager().registerEvents((Listener) hero, Heroes.getInstance());
                } else if (characterName.equalsIgnoreCase("Skullfire") || characterName.equalsIgnoreCase("Skull")) {
                    hero = new Skullfire(player);
                    player.playSound(player.getLocation(), "skullfire.select", SoundCategory.MASTER, 2.0f, 1.0f);
                } else if (characterName.equalsIgnoreCase("Pug")) {
                    hero = new Pug(player);
                    player.playSound(player.getLocation(), Sound.ENTITY_WOLF_BIG_GROWL, 2.0f, 1.0f);
                }

                if (hero != null) {
                    HeroManager.setHero(player, hero);
                    player.sendMessage("Character set to " + characterName);
                    return true;
                }
            }
            player.sendMessage("Invalid character name");
        }
        return false;
    }
}