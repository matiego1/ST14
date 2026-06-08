package me.matiego.st14.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.jetbrains.annotations.NotNull;

public class PrepareAnvilListener implements Listener {
    @EventHandler
    public void onPrepareAnvil(@NotNull PrepareAnvilEvent event) {
        //noinspection UnstableApiUsage
        event.getView().setMaximumRepairCost(9999);
    }
}
