package me.stolyy.heroes.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Base class for Heroes commands: enforces permission and optional player-only gating
 * once before dispatching to subclass execution and tab-completion hooks.
 */
public abstract class HeroesCommand extends Command {
    private final boolean playerOnly;

    protected HeroesCommand(String name, String description, String usage,
                            List<String> aliases, @Nullable String permission, boolean playerOnly) {
        super(name);
        this.setDescription(description);
        this.setUsage(usage);
        if (!aliases.isEmpty()) this.setAliases(aliases);
        if (permission != null) this.setPermission(permission);
        this.playerOnly = playerOnly;
    }

    protected HeroesCommand(String name, String description, String usage, List<String> aliases) {
        this(name, description, usage, aliases, null, true);
    }

    protected HeroesCommand(String name, String description, String usage) {
        this(name, description, usage, List.of(), null, true);
    }

    /** Additional op-default permission nodes beyond {@link #getPermission()} to register on startup. */
    public List<String> extraPermissions() {
        return List.of();
    }

    @Override
    public final boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        String perm = getPermission();
        if (perm != null && !sender.hasPermission(perm)) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        if (playerOnly) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
                return true;
            }
            execute(player, args);
        } else {
            execute(sender, args);
        }
        return true;
    }

    @Override
    public final @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        String perm = getPermission();
        if (perm != null && !sender.hasPermission(perm)) return List.of();
        if (playerOnly && !(sender instanceof Player)) return List.of();
        return complete(sender, args);
    }

    protected void execute(Player player, String[] args) {
        throw new UnsupportedOperationException(getName() + " must override execute(Player, String[])");
    }

    protected void execute(CommandSender sender, String[] args) {
        throw new UnsupportedOperationException(getName() + " must override execute(CommandSender, String[])");
    }

    protected List<String> complete(CommandSender sender, String[] args) {
        return List.of();
    }
}
