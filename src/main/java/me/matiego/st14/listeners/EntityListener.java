package me.matiego.st14.listeners;

import me.matiego.st14.AntyLogoutManager;
import me.matiego.st14.Main;
import org.bukkit.PortalType;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.jetbrains.annotations.NotNull;

public class EntityListener implements Listener {
    @EventHandler (ignoreCancelled = true)
    public void onEntityChangeBlock(@NotNull EntityChangeBlockEvent event) {
        if (event.getEntityType() == EntityType.ENDERMAN) {
            event.setCancelled(true);
        }
    }

    @EventHandler (ignoreCancelled = true)
    public void onEntityPortal(@NotNull EntityPortalEvent event) {
        if (event.getPortalType() == PortalType.ENDER && Main.getInstance().getConfig().getBoolean("block-end")) {
            event.setCancelled(true);
        }
    }

    @EventHandler (priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(@NotNull EntityExplodeEvent event) {
        AntyLogoutManager manager = Main.getInstance().getAntyLogoutManager();
        Player player = manager.getPlayerByEntity(event.getEntity());
        if (player == null) return;
        manager.cancelAntyLogout(player);
    }

    @EventHandler (priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(@NotNull EntityDeathEvent event) {
        AntyLogoutManager manager = Main.getInstance().getAntyLogoutManager();
        Player player = manager.getPlayerByEntity(event.getEntity());
        if (player == null) return;
        manager.cancelAntyLogout(player);
    }
}
