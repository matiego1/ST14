package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.jetbrains.annotations.NotNull;

public class PlayerBedEnterListener implements Listener {
    public PlayerBedEnterListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(ignoreCancelled = true)
    public void onPlayerBedEnter(@NotNull PlayerBedEnterEvent event) {
        //noinspection deprecation
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;

        Player player = event.getPlayer();

        double amount = plugin.getConfig().getDouble("bed-enter.cost", 5);
        if (amount <= 0) return;
        if (!plugin.getConfig().getStringList("bed-enter.worlds").contains(player.getWorld().getName())) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> event.getPlayer().sendActionBar(Utils.getComponentByString("&aZa przespaną noc zostanie pobrane " + plugin.getEconomyManager().format(amount))), 3);
    }
}
