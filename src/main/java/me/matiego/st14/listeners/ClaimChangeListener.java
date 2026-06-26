package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.utils.Utils;
import net.crashcraft.crashclaim.api.ClaimChangeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class ClaimChangeListener implements Listener {
    public ClaimChangeListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler
    public void onClaimChange(@NotNull ClaimChangeEvent event) {
        Utils.async(() -> plugin.getDynmapManager().getClaimsMarker().refreshClaim(event.getClaim()));
    }
}