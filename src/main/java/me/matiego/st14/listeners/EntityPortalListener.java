package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import org.bukkit.PortalType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.jetbrains.annotations.NotNull;

public class EntityPortalListener implements Listener {
    public EntityPortalListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(ignoreCancelled = true)
    public void onEntityPortal(@NotNull EntityPortalEvent event) {
        String world = event.getFrom().getWorld().getName();
        if (event.getPortalType() == PortalType.ENDER && plugin.getConfig().getStringList("allow-end-portals").contains(world)) {
            return;
        }
        if (event.getPortalType() == PortalType.NETHER && plugin.getConfig().getStringList("allow-nether-portals").contains(world)) {
            return;
        }
        event.setCancelled(true);
    }
}
