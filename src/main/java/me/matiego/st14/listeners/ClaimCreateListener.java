package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import net.crashcraft.crashclaim.api.ClaimCreateEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class ClaimCreateListener implements Listener {
    public ClaimCreateListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler
    public void onClaimCreate(@NotNull ClaimCreateEvent event) {
        plugin.getDynmap().refreshClaim(event.getClaim());
    }
}
