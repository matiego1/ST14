package me.matiego.st14.listeners;

import io.papermc.paper.event.player.PlayerServerFullCheckEvent;
import me.matiego.st14.Main;
import me.matiego.st14.managers.PremiumManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class PlayerServerFullCheckListener implements Listener {
    public PlayerServerFullCheckListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;

    @EventHandler (priority = EventPriority.HIGHEST)
    public void onPlayerServerFullCheck(@NotNull PlayerServerFullCheckEvent event) {
        if (event.isAllowed()) return;

        UUID uuid = event.getPlayerProfile().getId();
        if (uuid == null) return;

        PremiumManager manager = plugin.getPremiumManager();
        if (manager.isPremium(uuid) && manager.makeSpaceForPlayer(uuid)) {
            event.allow(true);
        }
    }
}
