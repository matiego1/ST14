package me.matiego.st14.listeners;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class PlayerAdvancementCriterionGrantListener implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onPlayerAdvancementCriterionGrant(@NotNull PlayerAdvancementCriterionGrantEvent event) {
        event.setCancelled(true);
    }
}
