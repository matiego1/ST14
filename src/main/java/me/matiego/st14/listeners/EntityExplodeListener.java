package me.matiego.st14.listeners;

import me.matiego.st14.AntyLogoutManager;
import me.matiego.st14.Main;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.jetbrains.annotations.NotNull;

public class EntityExplodeListener implements Listener {
    public EntityExplodeListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(@NotNull EntityExplodeEvent event) {
        AntyLogoutManager manager = plugin.getAntyLogoutManager();
        Player player = manager.getPlayerByEntity(event.getEntity());
        if (player == null) return;
        manager.cancelAntyLogout(player);
    }
}
