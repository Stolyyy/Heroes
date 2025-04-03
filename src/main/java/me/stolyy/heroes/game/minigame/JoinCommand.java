package me.stolyy.heroes.game.minigame;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class JoinCommand extends Command {
    //make sure party sizes are good before joining
    public JoinCommand() {
        super("join");
        this.setDescription("Join a game");
        this.setUsage("/join <1v1|2v2|party>");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(getUsage());
            return true;
        }

        GameEnums.GameMode gameMode;
        try {
            gameMode = parseGameMode(args[0]);
        } catch (IllegalArgumentException e) {
            player.sendMessage("Invalid game mode. Use 1v1, 2v2, or party.");
            return true;
        }

        GameManager.join(player, gameMode);

        return true;
    }

    private GameEnums.GameMode parseGameMode(String input) {
        return switch (input.toLowerCase()) {
            case "1v1" -> GameEnums.GameMode.ONE_V_ONE;
            case "2v2" -> GameEnums.GameMode.TWO_V_TWO;
            case "party" -> GameEnums.GameMode.PARTY;
            default -> throw new IllegalArgumentException("Invalid game mode");
        };
    }
}
