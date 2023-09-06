package me.matiego.st14.listeners;

import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import me.matiego.st14.Main;
import me.matiego.st14.utils.Utils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class PlayerPickupExperienceListener implements Listener {
    public PlayerPickupExperienceListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(ignoreCancelled = true)
    public void onPlayerPickupExperience(@NotNull PlayerPickupExperienceEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getNonPremiumManager().isLoggedIn(player)) {
            event.setCancelled(true);
            player.sendActionBar(Utils.getComponentByString("&cMusisz się zalogować, aby to zrobić!"));
        }
    }
}
