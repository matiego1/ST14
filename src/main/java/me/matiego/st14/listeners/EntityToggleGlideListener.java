package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.utils.Utils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.jetbrains.annotations.NotNull;

public class EntityToggleGlideListener implements Listener {
    public EntityToggleGlideListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityToggleGlide(@NotNull EntityToggleGlideEvent event) {
        if (!event.isGliding()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        if (!plugin.getNonPremiumManager().isLoggedIn(player)) {
            event.setCancelled(true);
            player.sendActionBar(Utils.getComponentByString("&cMusisz się zalogować, aby to zrobić!"));
            return;
        }

        if (!plugin.getConfig().getStringList("block-elytra.worlds").contains(player.getWorld().getName())) return;
        if (Utils.getTps() > plugin.getConfig().getDouble("block-elytra.below-tps")) return;
        player.sendActionBar(Utils.getComponentByString("&cNie możesz teraz latać!"));
        event.setCancelled(true);
    }
}
