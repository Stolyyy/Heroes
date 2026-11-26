package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.GameState;
import me.stolyy.heroes.game.Team;
import me.stolyy.heroes.game.TeamColor;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.ui.GuiText;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Active match browser for joining spectators; cycles team heads across multi-player matches. */
public class SpectateGUI extends GUI {
    private static final int MAX_CONTENT_SLOTS = 45;
    private static final int SLOT_CLOSE = 49;
    private static final long CYCLE_PERIOD_TICKS = 40L;

    private final Map<Integer, Game> slotToGame = new HashMap<>();
    private final Map<Integer, List<Player>> gamePlayers = new HashMap<>();
    private final Map<Integer, Integer> currentPlayerIndex = new HashMap<>();
    private BukkitTask cycleTask;

    public SpectateGUI(Player player) {
        super(player, 54, GuiText.raw("gui.spectate.title"));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == SLOT_CLOSE) {
            cleanup();
            GUIManager.close(player);
            return;
        }

        Game game = slotToGame.get(slot);
        if (game == null) return;

        List<Player> players = gamePlayers.get(slot);
        if (players == null || players.isEmpty()) return;

        cleanup();
        GUIManager.close(player);
        GameManager.spectateGame(player, players.getFirst());
    }

    @Override
    protected void populate() {
        slotToGame.clear();
        gamePlayers.clear();
        currentPlayerIndex.clear();

        int slot = 0;
        for (Game game : GameManager.getActiveGames()) {
            if (game.gameState() != GameState.IN_PROGRESS && game.gameState() != GameState.STARTING) continue;
            if (slot >= MAX_CONTENT_SLOTS) break;

            List<Player> players = new ArrayList<>(game.onlinePlayers(false));
            if (players.isEmpty()) continue;

            slotToGame.put(slot, game);
            gamePlayers.put(slot, players);
            currentPlayerIndex.put(slot, 0);

            inventoryItems.put(slot, createGameItem(game, players.getFirst()));
            slot++;
        }

        inventoryItems.put(SLOT_CLOSE, closeItem());
        startCycleTask();
    }

    private ItemStack createGameItem(Game game, Player displayPlayer) {
        String starting = game.gameState() == GameState.STARTING
                ? GuiText.raw("gui.spectate.starting-suffix")
                : "";

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        for (TeamColor teamColor : TeamColor.values()) {
            if (teamColor == TeamColor.SPECTATOR) continue;
            Team team = game.getTeams().get(teamColor);
            if (team == null || team.onlinePlayers().isEmpty()) continue;

            lore.add(GuiText.component("gui.spectate.team-header", "team", teamColor.displayName())
                    .color(teamColor.textColor()));
            for (Player p : team.onlinePlayers()) {
                int elo = PlayerStatsManager.getStats(p).getElo(game.gameMode());
                lore.add(GuiText.component("gui.spectate.player-entry",
                        "player", p.getName(), "elo", elo));
            }
        }

        Team spectators = game.getTeams().get(TeamColor.SPECTATOR);
        if (spectators != null && !spectators.onlinePlayers().isEmpty()) {
            lore.add(Component.empty());
            lore.add(GuiText.component("gui.spectate.spectators-header"));
            for (Player p : spectators.onlinePlayers()) {
                lore.add(GuiText.component("gui.spectate.spectator-entry", "player", p.getName()));
            }
        }

        lore.add(Component.empty());
        lore.add(GuiText.component("gui.spectate.click-lore"));

        ItemStack head = ItemBuilder.from(Material.PLAYER_HEAD)
                .name(GuiText.component("gui.spectate.item-name",
                        "mode", game.gameMode().displayName(),
                        "starting", starting))
                .lore(lore)
                .build();
        head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(displayPlayer));
        return head;
    }

    private void startCycleTask() {
        if (cycleTask != null) return;
        cycleTask = Bukkit.getScheduler().runTaskTimer(Heroes.getInstance(), () -> {
            if (GUIManager.getOpenGui(player) != this) {
                cleanup();
                return;
            }
            boolean updated = false;
            for (int slot : gamePlayers.keySet()) {
                List<Player> players = gamePlayers.get(slot);
                if (players.size() <= 1) continue;

                int index = (currentPlayerIndex.get(slot) + 1) % players.size();
                currentPlayerIndex.put(slot, index);

                Game game = slotToGame.get(slot);
                if (game != null) {
                    inventoryItems.put(slot, createGameItem(game, players.get(index)));
                    updated = true;
                }
            }
            if (updated) {
                update();
            }
        }, CYCLE_PERIOD_TICKS, CYCLE_PERIOD_TICKS);
    }

    @Override
    public void cleanup() {
        if (cycleTask != null) {
            cycleTask.cancel();
            cycleTask = null;
        }
    }
}
