package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.utils.Utils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.NotNull;

public class PlayerPortalListener implements Listener {
    public PlayerPortalListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(ignoreCancelled = true)
    public void onPlayerPortal(@NotNull PlayerPortalEvent event) {
        String world = event.getFrom().getWorld().getName();
        Player player = event.getPlayer();
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL && plugin.getConfig().getStringList("allow-end-portals").contains(world)) {
            return;
        }
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL && plugin.getConfig().getStringList("allow-nether-portals").contains(world)) {
            return;
        }
        event.setCancelled(true);
        player.sendActionBar(Utils.getComponentByString("&cNie możesz skorzystać z tego portalu."));
    }
}
