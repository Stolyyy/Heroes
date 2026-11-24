package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameBot;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.GameState;
import me.stolyy.heroes.game.TeamBalancer;
import me.stolyy.heroes.game.TeamColor;
import me.stolyy.heroes.game.map.GameMap;
import me.stolyy.heroes.game.map.MapFilter;
import me.stolyy.heroes.game.map.MapPoolSelector;
import me.stolyy.heroes.game.series.MatchSeries;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import me.stolyy.heroes.vfx.ui.GuiText;
import me.stolyy.heroes.vfx.ui.gui.settings.GameSettingsGUI;
import me.stolyy.heroes.vfx.ui.gui.settings.PlayerSettingsGUI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Custom game lobby GUI. Row 0 holds paginated spectators, add-bot, and auto-balance; one utility
 * column holds gamemode, settings, map, start, and cancel; remaining columns show teams.
 */
public class CustomGameGUI extends GUI {
    private static final long TICKER_PERIOD = 40L;
    private static final int SLOT_ARROW_OR_BOT = 7;
    private static final int SLOT_BALANCE = 8;
    private static final int PAGE0_CAP = 7;
    private static final int PAGEN_CAP = 6;

    private Game game;
    private final Random random = new Random();

    private final Map<Integer, TeamColor> headerSlots = new LinkedHashMap<>();
    private final Map<Integer, TeamColor> spotTeam = new LinkedHashMap<>();
    private final int slotGamemode, slotSettings, slotMap, slotStart, slotCancel;

    private final Map<Integer, UUID> slotOwners = new HashMap<>();
    private final Pager spectatorPager = new Pager(PAGE0_CAP, PAGEN_CAP);
    private boolean leftArrowShown;
    private boolean rightArrowShown;

    private UUID selectedUuid;
    private int cycleStep;
    private TeamBalancer.Shape balanceShape = TeamBalancer.Shape.FFA;
    private final MapPoolSelector poolSelector = new MapPoolSelector();
    private BukkitTask ticker;

    public CustomGameGUI(Game game, Player player) {
        super(player, 54, GuiText.raw("gui.custom.title"));
        this.game = game;

        int utilityColumn = GameConfigManager.getCustomGuiUtilityColumn();
        this.slotGamemode = 9 + utilityColumn;
        this.slotSettings = 18 + utilityColumn;
        this.slotMap = 27 + utilityColumn;
        this.slotStart = 36 + utilityColumn;
        this.slotCancel = 45 + utilityColumn;

        TeamColor[] playable = Arrays.stream(TeamColor.values())
                .filter(c -> c != TeamColor.SPECTATOR)
                .toArray(TeamColor[]::new);
        int t = 0;
        for (int col = 0; col <= 8 && t < playable.length; col++) {
            if (col == utilityColumn) continue;
            TeamColor color = playable[t++];
            headerSlots.put(9 + col, color);
            for (int row = 2; row <= 5; row++) {
                spotTeam.put(row * 9 + col, color);
            }
        }
    }

    @Override
    protected void populate() {
        CustomGameHotbar.remove(player);
        startTicker();
    }

    private void startTicker() {
        if (ticker != null) return;
        ticker = Bukkit.getScheduler().runTaskTimer(Heroes.getInstance(), () -> {
            if (GUIManager.getOpenGui(player) != this) {
                ticker.cancel();
                ticker = null;
                return;
            }
            cycleStep++;
            update();
        }, TICKER_PERIOD, TICKER_PERIOD);
    }

    @Override
    protected void update() {
        inventoryItems.clear();
        slotOwners.clear();

        renderSpectatorRow();
        renderBalanceButton();
        renderUtilities();
        renderTeams();

        super.update();
    }

    private void renderSpectatorRow() {
        List<UUID> spectators = TeamBalancer.orderedMembers(game, game.getTeams().get(TeamColor.SPECTATOR));
        List<UUID> visible = spectatorPager.slice(spectators);

        leftArrowShown = spectatorPager.hasPrev();
        if (leftArrowShown) {
            inventoryItems.put(0, ItemBuilder.from(Material.PAPER)
                    .name(GuiText.component("gui.nav.prev-page"))
                    .modelData("gui", "general", "prev")
                    .build());
        }

        int slot = leftArrowShown ? 1 : 0;
        for (UUID uuid : visible) {
            inventoryItems.put(slot, headFor(uuid));
            slotOwners.put(slot, uuid);
            slot++;
        }

        rightArrowShown = spectatorPager.hasNext(spectators.size());
        if (rightArrowShown) {
            inventoryItems.put(SLOT_ARROW_OR_BOT, ItemBuilder.from(Material.PAPER)
                    .name(GuiText.component("gui.nav.next-page"))
                    .lore(GuiText.lore("gui.custom.next-page-lore", "count", spectatorPager.remainingAfter(spectators.size())))
                    .modelData("gui", "general", "next")
                    .build());
        } else {
            inventoryItems.put(SLOT_ARROW_OR_BOT, ItemBuilder.from(Material.PAPER)
                    .name(GuiText.component("gui.custom.add-bot-name"))
                    .lore(GuiText.lore("gui.custom.add-bot-lore"))
                    .modelData("gui", "customgame", "add-bot")
                    .build());
        }
    }

    private void renderBalanceButton() {
        int n = TeamBalancer.participants(game).size();
        String shapeLabel = GuiText.raw("gui.custom.balance-shapes." + balanceShape.configKey());
        inventoryItems.put(SLOT_BALANCE, ItemBuilder.from(Material.PAPER)
                .name(GuiText.component("gui.custom.balance-name", "shape", shapeLabel))
                .lore(GuiText.lore("gui.custom.balance-lore", "preview", balancePreview(n)))
                .modelData("gui", "customgame", "balance")
                .build());
    }

    private void renderUtilities() {
        inventoryItems.put(slotGamemode, ItemBuilder.from(Material.COMPASS)
                .name(GuiText.component("gui.custom.gamemode-name", "mode", game.gameModePreset().displayName()))
                .lore(GuiText.lore("gui.custom.gamemode-lore"))
                .modelData("gui", "customgame", "gamemode")
                .build());
        inventoryItems.put(slotSettings, ItemBuilder.from(Material.PAPER)
                .name(GuiText.component("gui.custom.settings-name"))
                .modelData("gui", "customgame", "settings")
                .build());

        GameMap current = game.settings() != null ? game.settings().map() : null;
        String mapLabel = current != null ? current.name() : GuiText.raw("gui.custom.map-none");
        inventoryItems.put(slotMap, ItemBuilder.from(Material.OAK_SIGN)
                .name(GuiText.component("gui.custom.map-name", "map", mapLabel))
                .lore(GuiText.lore("gui.custom.map-lore", "pool", poolLabel()))
                .modelData("gui", "settings", "map")
                .build());

        inventoryItems.put(slotStart, ItemBuilder.from(Material.PAPER)
                .name(GuiText.component("gui.custom.start-name"))
                .modelData("gui", "customgame", "start")
                .build());
        inventoryItems.put(slotCancel, ItemBuilder.from(Material.PAPER)
                .name(GuiText.component("gui.custom.cancel-name"))
                .modelData("gui", "general", "close")
                .build());
    }

    private void renderTeams() {
        for (Map.Entry<Integer, TeamColor> header : headerSlots.entrySet()) {
            TeamColor color = header.getValue();
            List<UUID> members = TeamBalancer.orderedMembers(game, game.getTeams().get(color));

            ItemBuilder headerItem = ItemBuilder.from(color.wool())
                    .name(GuiText.component("gui.custom.team-header-name", "team", color.displayName()))
                    .modelData("gui", "team", color.name().toLowerCase());
            List<Component> headerLore = new ArrayList<>();
            if (members.size() > 4) {
                headerLore.add(GuiText.component("gui.custom.team-rotating-lore", "count", members.size()));
            }
            if (selectedUuid != null) {
                headerLore.add(GuiText.component("gui.custom.team-move-lore"));
            }
            if (!headerLore.isEmpty()) {
                headerItem.lore(headerLore);
            }
            inventoryItems.put(header.getKey(), headerItem.build());

            List<Integer> slots = new ArrayList<>();
            for (Map.Entry<Integer, TeamColor> e : spotTeam.entrySet()) {
                if (e.getValue() == color) slots.add(e.getKey());
            }
            Collections.sort(slots);

            if (members.size() <= slots.size()) {
                int i = 0;
                for (; i < members.size(); i++) {
                    UUID uuid = members.get(i);
                    inventoryItems.put(slots.get(i), headFor(uuid));
                    slotOwners.put(slots.get(i), uuid);
                }
                for (; i < slots.size(); i++) {
                    inventoryItems.put(slots.get(i), createEmptySlot(color));
                }
            } else {
                int offset = cycleStep % members.size();
                for (int i = 0; i < slots.size(); i++) {
                    UUID uuid = members.get((offset + i) % members.size());
                    inventoryItems.put(slots.get(i), headFor(uuid));
                    slotOwners.put(slots.get(i), uuid);
                }
            }
        }
    }

    private ItemStack headFor(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        ItemStack head = p != null
                ? createPlayerHead(p)
                : createBotHead(game.getGameBots().get(uuid));
        if (uuid.equals(selectedUuid)) highlightItem(head);
        return head;
    }

    private ItemStack createEmptySlot(TeamColor color) {
        return ItemBuilder.from(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                .name(GuiText.component("gui.custom.empty-slot").color(color.textColor()))
                .modelData("gui", "team", color.name().toLowerCase() + "-empty")
                .build();
    }

    private ItemStack createBotHead(GameBot bot) {
        return botHead(bot, GuiText.lore("gui.custom.bot-head-lore", "hero", bot.getHeroName()));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;

        if (slot == 0 && leftArrowShown) {
            spectatorPager.prev();
            playClick();
            update();
            return;
        }
        if (slot == SLOT_ARROW_OR_BOT) {
            if (rightArrowShown) {
                spectatorPager.next(TeamBalancer.orderedMembers(game, game.getTeams().get(TeamColor.SPECTATOR)).size());
                playClick();
                update();
            } else {
                handleAddBot();
            }
            return;
        }
        if (slot == SLOT_BALANCE) {
            if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
                balanceShape = balanceShape.next();
                playClick();
                update();
            } else {
                applyBalance();
            }
            return;
        }
        if (slot == slotGamemode) {
            GUIManager.open(player, new GameModeGUI(game, player, this));
            return;
        }
        if (slot == slotSettings) {
            GUIManager.open(player, new GameSettingsGUI(game, player, this));
            return;
        }
        if (slot == slotMap) {
            handleMapClick(event.getClick());
            return;
        }
        if (slot == slotStart) {
            startGame();
            return;
        }
        if (slot == slotCancel) {
            cancelGame();
            return;
        }

        TeamColor headerColor = headerSlots.get(slot);
        if (headerColor != null) {
            if (selectedUuid != null) {
                game.changeParticipantTeam(selectedUuid, headerColor);
                selectedUuid = null;
                playSuccess();
                update();
            } else {
                GUIManager.open(player, new PlayerSettingsGUI(game.getTeams().get(headerColor), player, this));
            }
            return;
        }

        handleMemberSlotClick(slot, event.getClick());
    }

    /** Shared by the Start button and the close-to-type hotbar item. */
    void startGame() {
        if (!game.canStart()) {
            player.sendMessage(GuiText.component("gui.custom.errors.cannot-start"));
            return;
        }
        if (game.gameModePreset().isSeries()) {
            if (!game.getGameBots().isEmpty()) {
                player.sendMessage(GuiText.component("gui.custom.errors.series-no-bots"));
                return;
            }
            MatchSeries.begin(game);
        } else {
            game.start();
        }
        CustomGameHotbar.remove(player);
        GUIManager.close(player);
    }

    /** Shared by the Cancel button and the close-to-type hotbar item. */
    void cancelGame() {
        // Close before teleporting to lobby so InventoryCloseEvent does not re-give the hotbar mid-teardown.
        CustomGameHotbar.remove(player);
        GUIManager.close(player);
        GameManager.leaveToLobby(player);
    }

    private void handleMemberSlotClick(int slot, ClickType clickType) {
        UUID uuid = slotOwners.get(slot);

        if (clickType == ClickType.RIGHT && uuid != null) {
            GameBot bot = game.getGameBots().get(uuid);
            if (bot != null) {
                GUIManager.open(player, new BotSettingsGUI(game, bot, uuid, player, this));
            } else {
                GUIManager.open(player, new PlayerSettingsGUI(game, uuid, player, this));
            }
            return;
        }

        if (uuid != null) {
            if (uuid.equals(selectedUuid)) {
                selectedUuid = null;
                SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_BASS).pitch(1.0f).play(player);
            } else if (selectedUuid == null) {
                selectedUuid = uuid;
                playConfirm();
            } else {
                TeamColor a = game.playerColor(selectedUuid);
                TeamColor b = game.playerColor(uuid);
                if (a != b) {
                    game.changeParticipantTeam(selectedUuid, b);
                    game.changeParticipantTeam(uuid, a);
                }
                selectedUuid = null;
                playSuccess();
            }
            update();
            return;
        }

        TeamColor target = spotTeam.get(slot);
        if (target == null && slot < PAGE0_CAP) target = TeamColor.SPECTATOR;
        if (target != null && selectedUuid != null) {
            game.changeParticipantTeam(selectedUuid, target);
            selectedUuid = null;
            playSuccess();
            update();
        }
    }

    private String balancePreview(int n) {
        if (n == 0) return GuiText.raw("gui.custom.balance-preview.empty");
        return switch (balanceShape) {
            case FFA -> n <= 8
                    ? GuiText.raw("gui.custom.balance-preview.ffa-all", "total", n)
                    : GuiText.raw("gui.custom.balance-preview.ffa-overflow", Map.of(
                            "total", String.valueOf(n),
                            "teams", "8",
                            "spectators", String.valueOf(n - 8)));
            case TWO_TEAMS -> GuiText.raw("gui.custom.balance-preview.two-teams", Map.of(
                    "total", String.valueOf(n),
                    "left", String.valueOf(n - n / 2),
                    "right", String.valueOf(n / 2)));
            case TEAMS_OF_TWO -> {
                String shape = TeamBalancer.teamSizesOfTwo(n).stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining("v"));
                yield n > 16
                        ? GuiText.raw("gui.custom.balance-preview.teams-of-two-overflow", Map.of(
                                "total", String.valueOf(n),
                                "shape", shape,
                                "spectators", String.valueOf(n - 16)))
                        : GuiText.raw("gui.custom.balance-preview.teams-of-two",
                                "total", n, "shape", shape);
            }
        };
    }

    private void applyBalance() {
        if (!TeamBalancer.apply(game, balanceShape)) {
            SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_BASS).volume(0.5f).pitch(1.0f).play(player);
            return;
        }
        selectedUuid = null;
        spectatorPager.reset();
        SoundBuilder.of(Sound.ENTITY_PLAYER_LEVELUP).pitch(1.2f).play(player);
        update();
    }

    private void handleMapClick(ClickType click) {
        switch (click) {
            case SHIFT_RIGHT -> {
                poolSelector.cycle();
                playClick();
                update();
            }
            case RIGHT -> rerollMap();
            default -> GUIManager.open(player, new GameMapGUI(game, player, this));
        }
    }

    private String poolLabel() {
        MapFilter explicit = poolSelector.filter();
        if (explicit != null) {
            return GuiText.raw("gui.custom.pool." + explicit.configKey());
        }
        String autoKey = GuiText.raw("gui.custom.pool." + poolSelector.autoPool(game).configKey());
        return GuiText.raw("gui.custom.pool-auto", "pool", autoKey);
    }

    private void rerollMap() {
        GameMap next = poolSelector.reroll(game, random);
        if (next == null) {
            player.sendMessage(GuiText.component("gui.custom.errors.no-other-maps"));
            return;
        }
        game = GameManager.changeMap(game, next);
        player.sendMessage(GuiText.component("gui.custom.map-changed", "map", next.name()));
        playSuccess();
        update();
    }

    private void handleAddBot() {
        switch (game.addRandomBot()) {
            case LIMIT_REACHED -> player.sendMessage(GuiText.component("gui.custom.errors.bot-limit"));
            case NO_NPC_SERVICE -> player.sendMessage(GuiText.component("gui.custom.errors.no-npc-service"));
            case FAILED -> player.sendMessage(GuiText.component("gui.custom.errors.bot-add-failed"));
            case ADDED -> {
                SoundBuilder.of(Sound.ENTITY_PLAYER_LEVELUP).pitch(1.2f).play(player);
                update();
            }
        }
    }

    public void setGame(Game newGame) {
        this.game = newGame;
    }

    public Game getGame() {
        return game;
    }

    @Override
    public void cleanup() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        if (game.gameState() == GameState.WAITING && GameManager.getPlayerGame(player) == game) {
            CustomGameHotbar.give(player, this);
        }
    }
}
