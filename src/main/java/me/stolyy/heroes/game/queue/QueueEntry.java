package me.stolyy.heroes.game.queue;

import me.stolyy.heroes.game.GameMode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record QueueEntry(
    UUID playerId,
    GameMode mode,
    long joinTime,
    @Nullable UUID partyId
) {
    public int getElapsedSeconds() {
        return (int) ((System.currentTimeMillis() - joinTime) / 1000);
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(playerId);
    }
}
