package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import net.crashcraft.crashclaim.api.ClaimDeleteEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class ClaimDeleteListener implements Listener {
    public ClaimDeleteListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler
    public void onClaimDelete(@NotNull ClaimDeleteEvent event) {
        plugin.getDynmap().deleteClaim(event.getClaim());
    }
}
