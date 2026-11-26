package me.stolyy.heroes.command.general;

import me.stolyy.heroes.command.HeroesCommand;
import me.stolyy.heroes.party.PartyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.List;

public class PartyChatCommand extends HeroesCommand {

    public PartyChatCommand() {
        super("partychat", "Send a message to your party",
                "/partychat <message>", List.of("pc"));
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /partychat <message>", NamedTextColor.RED));
            return;
        }

        PartyManager.sendMessage(player, Component.text(String.join(" ", args)));
    }
}
