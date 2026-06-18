package me.matiego.st14.listeners;

import io.papermc.paper.event.player.PlayerOpenSignEvent;
import me.matiego.st14.utils.Utils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.UUID;

public class PlayerOpenSignListener implements Listener {
    private final HashMap<UUID, Long> lastClick = Utils.createLimitedSizeMap(100);

    @EventHandler (ignoreCancelled = true)
    public void onPlayerOpenSign(@NotNull PlayerOpenSignEvent event) {
        if (event.getCause() != PlayerOpenSignEvent.Cause.INTERACT) return;
        Player player = event.getPlayer();

        long now = Utils.now();
        long last = lastClick.getOrDefault(player.getUniqueId(), now);
        lastClick.put(player.getUniqueId(), now);

        now -= last;
        if (now <= 0 || now > 5_000) {
            event.setCancelled(true);
            player.sendActionBar(Utils.getComponentByString("&cKliknij ponownie, aby edytować tę tabliczkę"));
        }
    }
}
