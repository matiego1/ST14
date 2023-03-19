package me.matiego.st14.listeners;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import me.matiego.st14.Main;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class PlayerLaunchProjectileListener implements Listener {
    public PlayerLaunchProjectileListener(@NotNull  Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerLaunchProjectile(@NotNull PlayerLaunchProjectileEvent event) {
        plugin.getEntityDamageByEntityListener().copyItemStackDamageToProjectile(event.getProjectile(), event.getItemStack());
    }
}
