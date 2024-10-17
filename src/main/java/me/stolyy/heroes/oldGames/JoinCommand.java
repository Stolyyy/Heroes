package me.stolyy.heroes.oldGames;

import me.stolyy.heroes.Game.GameEnums.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class JoinCommand extends Command {
    private final GameManager gameManager;

    public JoinCommand(GameManager gameManager) {
        super("join");
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
            player.sendMessage(getUsage());
            return true;
        }

        GameMode gameMode;
        try {
            gameMode = parseGameMode(args[0]);
        } catch (IllegalArgumentException e) {
            player.sendMessage("Invalid game mode. Use 1v1, 2v2, or party.");
            return true;
        }

        if (gameManager.joinGame(player, gameMode)) {
            player.sendMessage("You have joined a " + gameMode + " game.");
        } else {
            player.sendMessage("Unable to join game. Check party size and game availability.");
        }

        return true;
    }

    private GameMode parseGameMode(String input) {
        switch (input.toLowerCase()) {
            case "1v1":
                return GameMode.ONE_V_ONE;
            case "2v2":
                return GameMode.TWO_V_TWO;
            case "party":
                return GameMode.PARTY;
            default:
                throw new IllegalArgumentException("Invalid game mode");
        }
    }
}