package me.matiego.st14.listeners;

import me.matiego.st14.utils.Utils;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.UUID;

public class PlayerBedEnterListener implements Listener {
    private final HashMap<UUID, UUID> sleepingPlayers = new HashMap<>();

    @EventHandler(ignoreCancelled = true)
    public void onPlayerBedEnter(@NotNull PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;
        Player player = event.getPlayer();
        UUID world = event.getBed().getWorld().getUID();
        if (sleepingPlayers.containsKey(world)) {
            event.setCancelled(true);
            player.sendMessage(Utils.getComponentByString("&cW tym świecie już ktoś spi"));
            return;
        }
        sleepingPlayers.put(world, player.getUniqueId());
    }

    public synchronized void clearSleepingInWorld(@NotNull World world) {
        sleepingPlayers.remove(world.getUID());
    }
}
