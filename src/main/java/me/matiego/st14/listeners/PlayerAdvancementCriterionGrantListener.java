package me.matiego.st14.listeners;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import me.matiego.st14.Main;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class PlayerAdvancementCriterionGrantListener implements Listener {
    public PlayerAdvancementCriterionGrantListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(ignoreCancelled = true)
    public void onPlayerAdvancementCriterionGrant(@NotNull PlayerAdvancementCriterionGrantEvent event) {
        if (plugin.getConfig().getStringList("allow-advancements").contains(event.getPlayer().getWorld().getName())) return;
        event.setCancelled(true);
    }
}
