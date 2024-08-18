package me.stolyy.heroes.Games;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.Games.GameManager;
import me.stolyy.heroes.Games.GameEnums;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class JoinCommand extends Command {
    private final Heroes plugin;
    private final GameManager gameManager;

    public JoinCommand(Heroes plugin, GameManager gameManager) {
        super("join");
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.setDescription("Join a game");
        this.setUsage("/join <1v1|2v2|party>");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length != 1) {
            player.sendMessage("Usage: /join <1v1|2v2|party>");
            return true;
        }

        GameEnums.GameMode gameMode;
        switch (args[0].toLowerCase()) {
            case "1v1":
                gameMode = GameEnums.GameMode.ONE_V_ONE;
                break;
            case "2v2":
                gameMode = GameEnums.GameMode.TWO_V_TWO;
                break;
            case "party":
                gameMode = GameEnums.GameMode.PARTY;
                break;
            default:
                player.sendMessage("Invalid game mode. Use 1v1, 2v2, or party.");
                return true;
        }

        if (gameManager.joinGame(player, gameMode)) {
            player.sendMessage("You have joined a " + args[0] + " game.");
        } else {
            player.sendMessage("Unable to join game. Check party size and game availability.");
        }

        return true;
    }
}