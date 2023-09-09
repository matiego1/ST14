package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.objects.GUI;
import me.matiego.st14.Logs;
import me.matiego.st14.utils.Utils;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class InventoryCloseListener implements Listener {
    public InventoryCloseListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        plugin.getIncognitoCommand().onInventoryClose(event.getPlayer().getUniqueId());

        //save backpack
        if (!(event.getView().getTopInventory().getHolder() instanceof GUI)) return;
        if (!LegacyComponentSerializer.legacyAmpersand().serialize(event.getView().title()).equals("&3Twój plecak")) return;

        Player player = (Player) event.getPlayer();
        Inventory inv = event.getInventory();
        List<ItemStack> items = Arrays.asList(inv.getContents());

        Utils.async(() -> {
            if (plugin.getBackpackManager().saveBackpack(player.getUniqueId(), items)) return;

            Logs.warning("Gracz " + player.getName() + " (" + player.getUniqueId() + ") stracił przedmioty z plecaka! Muszą być przywrócone ręcznie. Base64: `" + GUI.itemsToString(items) + "`");
            player.sendMessage(Utils.getComponentByString("&cNapotkano niespodziewany błąd przy zapisywaniu twojego plecaka! Zgłoś się do administratora, aby odzyskać swoje przedmioty!"));
        });
    }
}
