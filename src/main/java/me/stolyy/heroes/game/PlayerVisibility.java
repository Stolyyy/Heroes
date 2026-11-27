package me.stolyy.heroes.game;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.vfx.glow.TeamGlow;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Per-viewer player hiding and Citizens bot tab-list scoping. Keeps matches isolated so
 * spectators, combatants, and respawners only see participants permitted by their game.
 */
public final class PlayerVisibility {

    private PlayerVisibility() {}

    /** Re-evaluates bidirectional visibility for {@code player} and all game bot tab listings. */
    public static void refreshVisibility(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            applyVisibility(player, other);
            applyVisibility(other, player);
        }
        // Scope Citizens bot tab entries to viewers inside that bot's game after applyVisibility.
        for (Game game : GameManager.getActiveGames()) {
            for (Player botEntity : game.botEntities()) {
                applyListing(player, botEntity);
            }
        }
    }

    /** Re-applies the tab-listing rule for one bot against every real online player. */
    public static void refreshBotListing(Player botEntity) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!Interactions.isRealPlayer(viewer)) continue;
            applyListing(viewer, botEntity);
        }
    }

    /** Refreshes team outline packets outside the WAITING lobby phase. */
    public static void refreshGlow(Game game) {
        if (game.gameState() == GameState.WAITING) return;
        TeamGlow.refreshTeams(game);
    }

    private static void applyListing(Player viewer, Player botEntity) {
        Game viewerGame = GameManager.getPlayerGame(viewer);
        boolean sameGame = viewerGame != null
                && viewerGame == GameManager.getEntityGame(botEntity.getUniqueId());
        if (sameGame && viewer.canSee(botEntity)) {
            viewer.listPlayer(botEntity);
        } else {
            viewer.unlistPlayer(botEntity);
        }
    }

    private static void applyVisibility(Player viewer, Player target) {
        if (canSee(viewer, target)) {
            viewer.showPlayer(Heroes.getInstance(), target);
        } else {
            viewer.hidePlayer(Heroes.getInstance(), target);
            if (GameManager.getPlayerGame(viewer) == null
                    && Interactions.isRealPlayer(target)
                    && GameManager.getPlayerGame(target) != null) {
                viewer.listPlayer(target);
            } else {
                // Explicit unlist: hidePlayer is a no-op when viewer was already hiding target from the lobby.
                viewer.unlistPlayer(target);
            }
        }
    }

    private static boolean canSee(Player viewer, Player target) {
        Game viewerGame = GameManager.getPlayerGame(viewer);
        if (viewerGame != GameManager.getPlayerGame(target)) return false;
        if (viewerGame == null) return true;
        return !hiddenWhileRespawning(viewerGame, viewer, target);
    }

    private static boolean hiddenWhileRespawning(Game game, Player viewer, Player target) {
        if (!game.isRespawning(target.getUniqueId())) return false;
        if (!TeamGlow.isRespawnGlowEnabled()) return true;
        Team viewerTeam = game.playerTeam(viewer.getUniqueId());
        return viewerTeam == null || !viewerTeam.equals(game.playerTeam(target.getUniqueId()));
    }
}
