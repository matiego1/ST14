package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.minigames.MiniGamesUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.jetbrains.annotations.NotNull;

public class FoodLevelChangeListener implements Listener {
    public FoodLevelChangeListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(ignoreCancelled = true)
    public void onFoodLevelChange(@NotNull FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!MiniGamesUtils.isInAnyMiniGameWorld(player)) return;
        if (plugin.getMiniGamesManager().getActiveMiniGame() != null) return;
        event.setFoodLevel(20);
    }
}
