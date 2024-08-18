package me.stolyy.heroes.heros;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class UltTimer extends BukkitRunnable {
    private Player player;
    private int time;
    private int timePassed;

    public  UltTimer(Player player, int time) {
        this.player = player;
        this.time = time;
        this.timePassed = time;
    }

    @Override
    public void run() {
        String title;
        if (timePassed > 0.7 * time) {
            title = ChatColor.DARK_GREEN + String.valueOf(timePassed);
        } else if (timePassed > 0.3 * time) {
            title = ChatColor.YELLOW + String.valueOf(timePassed);
        } else {
            title = ChatColor.RED + String.valueOf(timePassed);
        }
        player.sendTitle(title, "", 5, 25, 10);
        timePassed -=1;

        if (timePassed == 0) {
            this.cancel();
        }
    }
}
