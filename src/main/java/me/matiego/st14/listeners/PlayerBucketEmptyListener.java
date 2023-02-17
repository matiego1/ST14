package me.matiego.st14.listeners;

import lombok.NonNull;
import me.matiego.st14.utils.Utils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketEmptyEvent;

public class PlayerBucketEmptyListener implements Listener {
    @EventHandler
    public void onPlayerBucketEmpty(@NonNull PlayerBucketEmptyEvent event) {
        if (Utils.doesBlockContactPortalBlock(event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Utils.getComponentByString("&cNie blokuj portali do netheru."));
        }
    }
}
