package me.matiego.st14.listeners;

import me.matiego.st14.AntyLogoutManager;
import me.matiego.st14.Main;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.jetbrains.annotations.NotNull;

public class EntityDeathListener implements Listener {
    public EntityDeathListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(@NotNull EntityDeathEvent event) {
        AntyLogoutManager manager = plugin.getAntyLogoutManager();
        Player player = manager.getPlayerByEntity(event.getEntity());
        if (player == null) return;
        manager.cancelAntyLogout(player);
    }
}
