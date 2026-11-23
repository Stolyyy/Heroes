package me.stolyy.heroes.util;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.ContextCalculator;
import net.luckperms.api.context.ContextConsumer;
import net.luckperms.api.context.ContextSet;
import net.luckperms.api.context.ImmutableContextSet;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * LuckPerms context: {@code heroes:template-world=true} while the player stands in a map template
 * world ({@code templates/...}). The mapper group's WorldEdit/FAWE wildcards are granted under
 * this context — build tools confined to map editing without enumerating template worlds, and new
 * maps are covered automatically.
 *
 * <p>Registration is guarded on LuckPerms being present; without it this class never loads the
 * LP API classes (the call site checks the plugin first), so LuckPerms stays a soft dependency.
 */
public final class TemplateWorldContext implements ContextCalculator<Player> {

    private static final String KEY = "heroes:template-world";

    private TemplateWorldContext() {}

    /** Call from onEnable. No-op when LuckPerms isn't installed. */
    public static void register() {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) return;
        LuckPerms luckPerms = LuckPermsProvider.get();
        luckPerms.getContextManager().registerCalculator(new TemplateWorldContext());
        Bukkit.getLogger().info("[Heroes] Registered LuckPerms context " + KEY);
    }

    @Override
    public void calculate(@NotNull Player target, @NotNull ContextConsumer consumer) {
        consumer.accept(KEY, String.valueOf(target.getWorld().getName().startsWith("templates/")));
    }

    @Override
    public @NotNull ContextSet estimatePotentialContexts() {
        return ImmutableContextSet.builder().add(KEY, "true").add(KEY, "false").build();
    }
}
