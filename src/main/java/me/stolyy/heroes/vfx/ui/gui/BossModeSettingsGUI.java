package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameBot;
import me.stolyy.heroes.game.GamePlayer;
import me.stolyy.heroes.game.GameState;
import me.stolyy.heroes.game.TeamColor;
import me.stolyy.heroes.game.rules.BossSetup;
import me.stolyy.heroes.util.GameModeConfigManager;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import me.stolyy.heroes.vfx.ui.prompt.PromptManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Boss-mode setup (B13.2/B13.7): click a participant head in the top row to designate
 * the boss; the numbers below tune the setup. Every change re-applies
 * BossSetup while a boss is designated, so adjustments stick immediately. The applied
 * values land on GamePlayers and stay editable through the normal player-settings GUIs.
 *
 * <p>RANDOM BOSS HERO resolves HERE, not at match start: the roll fires the moment the
 * toggle is switched on (or lazily on the next apply when the yml default is true) and is
 * written as the boss's hero override, which Game.start() consumes through
 * GamePlayer.resolveHeroName(). That keeps the rolled hero visible in this GUI and
 * re-rollable with a right-click, filters admin-only heroes (the settings-level
 * randomize-heroes path does not), and survives GameManager.changeMap, which copies both
 * bossPlayer and heroOverride.
 */
public class BossModeSettingsGUI extends GUI {
    private static final int HEAD_ROW_START = 0; // slots 0-8: participant heads
    private static final int SLOT_BOSS_HP_PER_LIFE = 9;
    private static final int SLOT_NON_BOSS_LIVES = 11;
    private static final int SLOT_NON_BOSS_HP = 13;
    private static final int SLOT_LIVES_PER_ENEMY = 15;
    private static final int SLOT_RANDOM_BOSS = 17;
    private static final int SLOT_BACK = 22;

    // Left-click cycles. Doubles can't be switched on, so these walk an array instead.
    private static final double[] LIVES_PER_ENEMY_CYCLE = {0.5, 1.0, 1.5, 2.0, 3.0};
    private static final double[] BOSS_HP_CYCLE = {100.0, 200.0, 300.0, 500.0};
    // PromptManager only parses ints, so lives-per-enemy comes in as free text and is
    // matched against this before parsing — a validation gate, not an exception path.
    private static final Pattern DECIMAL = Pattern.compile("\\d*\\.?\\d+");

    private final Game game;
    private final CustomGameGUI partyGUI;
    private final Map<Integer, UUID> headSlots = new HashMap<>();

    // Apply-time inputs, seeded from gamemodes.yml. The authoritative state after apply
    // is the GamePlayers themselves.
    private int nonBossLives = GameModeConfigManager.getBossNonBossLives();
    private double nonBossHp = GameModeConfigManager.getBossNonBossHp();
    private double livesPerEnemy = GameModeConfigManager.getBossLivesPerEnemy();
    private double bossHpPerLife = GameModeConfigManager.getBossHpPerLife();

    public BossModeSettingsGUI(Game game, Player player, CustomGameGUI partyGUI) {
        super(player, 27, "Boss Mode Settings");
        this.game = game;
        this.partyGUI = partyGUI;
    }

    @Override
    protected void populate() {
        inventoryItems.put(SLOT_BACK, ItemBuilder.from(Material.PAPER)
                .name("<white>Save & Back")
                .modelData("gui", "navigation", "back")
                .build());
    }

    @Override
    protected void update() {
        for (int slot = HEAD_ROW_START; slot < HEAD_ROW_START + 9; slot++) {
            inventoryItems.remove(slot);
        }
        headSlots.clear();

        int slot = HEAD_ROW_START;
        for (GamePlayer gamePlayer : game.getGamePlayers().values()) {
            if (slot >= HEAD_ROW_START + 9) break;
            if (gamePlayer.leftGame() || gamePlayer.team() == TeamColor.SPECTATOR) continue;
            ItemStack head = createParticipantHead(gamePlayer);
            if (head == null) continue;
            if (gamePlayer.uuid().equals(game.bossPlayer())) highlightItem(head);
            inventoryItems.put(slot, head);
            headSlots.put(slot, gamePlayer.uuid());
            slot++;
        }

        inventoryItems.put(SLOT_BOSS_HP_PER_LIFE, ItemBuilder.from(Material.ENCHANTED_GOLDEN_APPLE)
                .name("<gold>Boss HP Per Life: " + (int) bossHpPerLife)
                .lore(Component.text("Starting/max HP the boss respawns", NamedTextColor.GRAY),
                      Component.text("with on every one of its lives", NamedTextColor.GRAY),
                      Component.text("Left-click: cycle", NamedTextColor.GRAY),
                      Component.text("Right-click: enter amount", NamedTextColor.GRAY))
                .build());

        inventoryItems.put(SLOT_NON_BOSS_LIVES, ItemBuilder.from(Material.PAPER)
                .name("<gold>Non-Boss Lives: " + nonBossLives)
                .lore(Component.text("Left-click: cycle", NamedTextColor.GRAY),
                      Component.text("Right-click: enter amount", NamedTextColor.GRAY))
                .build());

        inventoryItems.put(SLOT_NON_BOSS_HP, ItemBuilder.from(Material.GOLDEN_APPLE)
                .name("<gold>Non-Boss HP: " + (int) nonBossHp)
                .lore(Component.text("Left-click: cycle", NamedTextColor.GRAY),
                      Component.text("Right-click: enter amount", NamedTextColor.GRAY))
                .build());

        inventoryItems.put(SLOT_LIVES_PER_ENEMY, ItemBuilder.from(Material.TOTEM_OF_UNDYING)
                .name("<gold>Boss Lives Per Enemy: " + formatLives(livesPerEnemy))
                .lore(Component.text(bossLivesSummary(), NamedTextColor.GRAY),
                      Component.text("Decimals allowed - rounded to the nearest", NamedTextColor.GRAY),
                      Component.text("whole life, ties up, minimum 1", NamedTextColor.GRAY),
                      Component.text("Left-click: cycle", NamedTextColor.GRAY),
                      Component.text("Right-click: enter amount", NamedTextColor.GRAY))
                .build());

        // A button, not a toggle: it rolls immediately and shows you the result, exactly like
        // clicking a head does. A persistent "randomize at start" flag would have to resolve
        // during Game.start(), where nobody could see or re-roll the pick.
        inventoryItems.put(SLOT_RANDOM_BOSS, ItemBuilder.from(Material.CHORUS_FRUIT)
                .name("<green>Random Boss")
                .lore(Component.text("Picks a random participant as the boss", NamedTextColor.GRAY),
                      Component.text(bossNameSummary(), NamedTextColor.GRAY),
                      Component.text("Click: roll", NamedTextColor.GRAY))
                .build());

        fillEmptySlots();
        super.update();
    }

    private ItemStack createParticipantHead(GamePlayer gamePlayer) {
        boolean isBoss = gamePlayer.uuid().equals(game.bossPlayer());
        List<Component> lore = List.of(
                Component.text(isBoss ? "THE BOSS" : "Click to make boss",
                                isBoss ? NamedTextColor.RED : NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));

        Player target = Bukkit.getPlayer(gamePlayer.uuid());
        if (target != null) {
            ItemStack head = createPlayerHead(target);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.lore(lore);
            head.setItemMeta(meta);
            return head;
        }
        GameBot bot = gamePlayer.bot();
        if (bot == null) return null; // offline player without a bot ref — skip
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        meta.displayName(Component.text("[BOT] " + bot.getName(), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (game.gameState() != GameState.WAITING) {
            player.closeInventory();
            return;
        }
        int slot = event.getSlot();

        UUID clicked = headSlots.get(slot);
        if (clicked != null) {
            designateBoss(clicked);
            return;
        }

        switch (slot) {
            case SLOT_BOSS_HP_PER_LIFE -> handleBossHpPerLife(event.getClick());
            case SLOT_NON_BOSS_LIVES -> handleNonBossLives(event.getClick());
            case SLOT_NON_BOSS_HP -> handleNonBossHp(event.getClick());
            case SLOT_LIVES_PER_ENEMY -> handleLivesPerEnemy(event.getClick());
            case SLOT_RANDOM_BOSS -> designateBoss(BossSetup.rollBossPlayer(game));
            case SLOT_BACK -> {
                reapply();
                GUIManager.open(player, partyGUI);
            }
        }
    }

    private void handleNonBossLives(ClickType click) {
        if (click == ClickType.RIGHT) {
            PromptManager.promptInt(player, "Enter the non-boss lives (1-20):", 1, 20,
                    value -> {
                        nonBossLives = value;
                        applyAndReopen();
                    },
                    () -> GUIManager.open(player, this));
            return;
        }
        nonBossLives = switch (nonBossLives) {
            case 1 -> 2;
            case 2 -> 3;
            case 3 -> 4;
            default -> 1;
        };
        applyAndRefresh();
    }

    private void handleNonBossHp(ClickType click) {
        if (click == ClickType.RIGHT) {
            PromptManager.promptInt(player, "Enter the non-boss HP (10-500):", 10, 500,
                    value -> {
                        nonBossHp = value;
                        applyAndReopen();
                    },
                    () -> GUIManager.open(player, this));
            return;
        }
        nonBossHp = switch ((int) nonBossHp) {
            case 50 -> 100;
            case 100 -> 150;
            case 150 -> 200;
            default -> 50;
        };
        applyAndRefresh();
    }

    private void handleBossHpPerLife(ClickType click) {
        if (click == ClickType.RIGHT) {
            PromptManager.promptInt(player, "Enter the boss HP per life (10-1000):", 10, 1000,
                    value -> {
                        bossHpPerLife = value;
                        applyAndReopen();
                    },
                    () -> GUIManager.open(player, this));
            return;
        }
        bossHpPerLife = nextInCycle(BOSS_HP_CYCLE, bossHpPerLife);
        applyAndRefresh();
    }

    private void handleLivesPerEnemy(ClickType click) {
        if (click == ClickType.RIGHT) {
            promptLivesPerEnemy();
            return;
        }
        livesPerEnemy = nextInCycle(LIVES_PER_ENEMY_CYCLE, livesPerEnemy);
        applyAndRefresh();
    }

    /**
     * Decimal entry. PromptManager only parses ints, so this takes free text and validates
     * it before parsing; unparseable input re-prompts, matching promptInt's retry behaviour.
     */
    private void promptLivesPerEnemy() {
        PromptManager.promptText(player, "Enter the boss lives per enemy (0.1-10, decimals allowed):",
                input -> {
                    if (!DECIMAL.matcher(input).matches()) {
                        player.sendMessage(Component.text("'" + input
                                + "' is not a number - try again, or type 'cancel'.", NamedTextColor.RED));
                        promptLivesPerEnemy();
                        return;
                    }
                    livesPerEnemy = Math.max(0.1, Math.min(10.0, Double.parseDouble(input)));
                    applyAndReopen();
                },
                () -> GUIManager.open(player, this));
    }

    /**
     * Makes a participant the boss — the shared path for both a head click and a random roll.
     * Teams first: BossSetup counts enemies by team, so boss-vs-all must be arranged before the
     * numbers are applied.
     */
    private void designateBoss(UUID bossUuid) {
        game.setBossPlayer(bossUuid);
        assignBossTeams(bossUuid);
        reapply();
        SoundBuilder.of(Sound.ENTITY_ENDER_DRAGON_GROWL).volume(0.5f).pitch(1.5f).play(player);
        update();
    }

    /** Who is currently the boss, for the random-boss button's lore. */
    private String bossNameSummary() {
        if (game.bossPlayer() == null) return "No boss picked yet";
        Player target = Bukkit.getPlayer(game.bossPlayer());
        if (target != null) return "Current: " + target.getName();
        GameBot bot = game.getGamePlayer(game.bossPlayer()).bot();
        return "Current: " + (bot == null ? "unknown" : "[BOT] " + bot.getName());
    }

    /** Next value in a left-click cycle, wrapping. Doubles can't be switched on. */
    private static double nextInCycle(double[] cycle, double current) {
        for (double step : cycle) {
            if (step > current + 1e-9) return step;
        }
        return cycle[0];
    }

    /** 1.0 renders as "1", 1.5 as "1.5". */
    public static String formatLives(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private String bossLivesSummary() {
        if (game.bossPlayer() == null) return "Pick a boss to see the total";
        int enemies = BossSetup.enemyCount(game, game.bossPlayer());
        return "Boss gets " + BossSetup.bossLives(enemies, livesPerEnemy)
                + " lives vs " + enemies + " enemies";
    }

    private void applyAndRefresh() {
        reapply();
        SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.0f).play(player);
        update();
    }

    private void applyAndReopen() {
        reapply();
        SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.2f).play(player);
        GUIManager.open(player, this);
    }

    /**
     * Boss-vs-all: the boss alone on RED, every other participant (players and bots) on
     * BLUE. The custom-game GUI only renders 3 heads per team — more than 3 on BLUE is
     * legal, the extras just aren't shown there.
     */
    private void assignBossTeams(UUID bossUuid) {
        for (GamePlayer gamePlayer : game.getGamePlayers().values()) {
            if (gamePlayer.leftGame() || gamePlayer.team() == TeamColor.SPECTATOR) continue;
            TeamColor targetTeam = gamePlayer.uuid().equals(bossUuid) ? TeamColor.RED : TeamColor.BLUE;
            Player participant = Bukkit.getPlayer(gamePlayer.uuid());
            if (participant != null) game.changeTeam(participant, targetTeam);
            else if (gamePlayer.bot() != null) game.changeBotTeam(gamePlayer.uuid(), targetTeam);
        }
    }

    private void reapply() {
        if (game.bossPlayer() == null) return;
        BossSetup.apply(game, game.bossPlayer(), nonBossLives, nonBossHp, livesPerEnemy,
                bossHpPerLife, GameModeConfigManager.getBossUltimatesEnabled());
    }

    @Override
    protected GUI getParentGUI() {
        return partyGUI;
    }
}
