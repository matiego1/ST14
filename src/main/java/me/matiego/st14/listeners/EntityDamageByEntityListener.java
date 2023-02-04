package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.jetbrains.annotations.NotNull;

public class EntityDamageByEntityListener implements Listener {
    public EntityDamageByEntityListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(@NotNull EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Entity damager = event.getDamager();

        plugin.getAntyLogoutManager().putAntyLogout(player, damager);

        EntityType type = damager.getType();
        if (type != EntityType.SNOWBALL && type != EntityType.EGG) return;
        event.setDamage(plugin.getConfig().getDouble("projectile-damage", event.getDamage()));
    }
}
