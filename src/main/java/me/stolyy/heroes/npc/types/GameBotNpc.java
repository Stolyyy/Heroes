package me.stolyy.heroes.npc.types;

import me.stolyy.heroes.npc.Npc;
import me.stolyy.heroes.npc.NpcService;
import me.stolyy.heroes.npc.NpcServiceProvider;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Spawns and despawns the Citizens NPC entity backing a combat bot in a game.
 * Hero, controller, and AI lifecycle are owned by {@link me.stolyy.heroes.game.Game}.
 */
public class GameBotNpc extends Npc {

    private static final String ID_PREFIX = "game_bot_";
    private static int nextBotId = 0;

    private final String botName;

    public GameBotNpc(String botName, Location location) {
        super(ID_PREFIX + nextBotId++, location);
        this.botName = botName;
    }

    @Override
    public void spawn() {
        NpcService service = NpcServiceProvider.get();
        if (!service.isAvailable()) return;

        handle = service.create(id, location);

        // Plain names <=16 chars avoid Citizens spawning a separate overhead hologram above the nametag.
        service.setDisplayName(handle, botName);
        service.setShowInTab(handle, true);
        service.setNameplateVisible(handle, true);
        service.setTurnToPlayer(handle, false);

        service.spawn(handle);

        // Combat NPCs must be damageable and in ADVENTURE mode for Hitbox.isValidTarget.
        service.setProtected(handle, false);
        LivingEntity entity = service.getBukkitEntity(handle);
        if (!(entity instanceof Player botPlayer)) {
            throw new IllegalStateException("GameBotNpc '" + id + "' spawned non-Player entity: " + entity);
        }
        botPlayer.setGameMode(GameMode.ADVENTURE);
    }

    @Override
    public void handleInteraction(Player player) {}
}
