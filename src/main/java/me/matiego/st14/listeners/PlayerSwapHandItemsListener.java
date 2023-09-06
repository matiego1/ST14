package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.utils.Utils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.jetbrains.annotations.NotNull;

public class PlayerSwapHandItemsListener implements Listener {
    public PlayerSwapHandItemsListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(ignoreCancelled = true)
    public void onPlayerSwapHandItems(@NotNull PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getNonPremiumManager().isLoggedIn(player)) {
            event.setCancelled(true);
            player.sendActionBar(Utils.getComponentByString("&cMusisz się zalogować, aby to zrobić!"));
        }
    }
}
