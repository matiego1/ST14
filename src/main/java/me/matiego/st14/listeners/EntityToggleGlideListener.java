package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.utils.Utils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.jetbrains.annotations.NotNull;

public class EntityToggleGlideListener implements Listener {
    public EntityToggleGlideListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(ignoreCancelled = true)
    public void onEntityToggleGlide(@NotNull EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.getConfig().getStringList("elytra.worlds").contains(player.getWorld().getName())) return;

        if (event.isGliding() && Utils.getTps() < plugin.getConfig().getDouble("elytra.block-below-tps")) {
            player.sendActionBar(Utils.getComponentByString("&cNie możesz teraz latać!"));
            event.setCancelled(true);
            return;
        }

        if (event.isGliding()) {
            plugin.getElytraExhaustionManager().flightBeginning(player);
        } else {
            plugin.getElytraExhaustionManager().flightEnd(player);
        }
    }
}
