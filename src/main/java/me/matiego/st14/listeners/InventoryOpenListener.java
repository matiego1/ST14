package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.utils.Utils;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.jetbrains.annotations.NotNull;

public class InventoryOpenListener implements Listener {
    public InventoryOpenListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(@NotNull InventoryOpenEvent event) {
        HumanEntity humanEntity = event.getPlayer();

        if (humanEntity instanceof Player player && !plugin.getNonPremiumManager().isLoggedIn(player)) {
            event.setCancelled(true);
            player.sendActionBar(Utils.getComponentByString("&cMusisz się zalogować, aby to zrobić!"));
        }
    }
}
