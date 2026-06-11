package me.matiego.st14.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class InventoryClickListener implements Listener {
    @EventHandler (priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (event.getSlotType() != InventoryType.SlotType.ARMOR) return;
        if (event.getSlot() != 39) return;
        if (event.getAction() != InventoryAction.PLACE_ALL) return;

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack item = player.getInventory().getHelmet();
        player.getInventory().setHelmet(event.getCursor());
        player.setItemOnCursor(item);
        player.updateInventory();
    }
}
