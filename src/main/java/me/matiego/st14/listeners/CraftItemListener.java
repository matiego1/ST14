package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

public class CraftItemListener implements Listener {
    public CraftItemListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(ignoreCancelled = true)
    public void onCraftItem(@NotNull CraftItemEvent event) {
        if (Arrays.stream(event.getInventory().getMatrix())
                .filter(Objects::nonNull)
                .anyMatch(item -> plugin.getBanknoteManager().isBanknote(item))) {
            event.setCancelled(true);
        }
    }
}
