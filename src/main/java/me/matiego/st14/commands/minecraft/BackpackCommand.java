package me.matiego.st14.commands.minecraft;

import me.matiego.st14.Main;
import me.matiego.st14.utils.CommandHandler;
import me.matiego.st14.utils.GUI;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Utils;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class BackpackCommand implements CommandHandler.Minecraft, Listener {
    public BackpackCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("backpack");
        if (command == null) {
            Logs.warning("The command /backpack does not exist in the plugin.yml file and cannot be registered.");
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    private final PluginCommand command;
    private final Main plugin;

    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public int onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Utils.getComponentByString("&cTej komendy może użyć tylko gracz."));
            return 0;
        }

        if (!Main.getInstance().getConfig().getStringList("backpack-worlds").contains(player.getWorld().getName())) {
            sender.sendMessage(Utils.getComponentByString("&cNie możesz użyć tej komendy w tym świecie."));
            return 3;
        }

        List<ItemStack> items = plugin.getBackpackManager().loadBackpack(player.getUniqueId());
        if (items == null) {
            player.sendMessage(Utils.getComponentByString("&cNapotkano niespodziewany błąd. Spróbuj później."));
            return 3;
        }

        Inventory inv = GUI.createInventory(9, "&3Twój plecak");
        int slot = 0;
        for (ItemStack item : items) {
            inv.setItem(slot, item);

            slot++;
            if (slot >= 9) break;
        }

        player.openInventory(inv);
        return 8;
    }

    @EventHandler (ignoreCancelled = true)
    public void onInventoryCloseEvent(@NotNull InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GUI)) return;
        if (!LegacyComponentSerializer.legacyAmpersand().serialize(event.getView().title()).equals("&3Twój plecak")) return;

        Player player = (Player) event.getPlayer();
        Inventory inv = event.getInventory();
        List<ItemStack> items = Arrays.asList(inv.getContents());

        if (plugin.getBackpackManager().saveBackpack(player.getUniqueId(), items)) return;

        player.sendMessage(Utils.getComponentByString("&cNapotkano niespodziewany błąd przy zapisywaniu twojego plecaka! Zgłoś się do administratora, aby odzyskać swoje przedmioty!"));
        Logs.warning("Gracz " + player.getName() + " (" + player.getUniqueId() + ") stracił przedmioty z plecaka! Muszą być przywrócone ręcznie. Base64: `" + GUI.itemsToString(items) + "`");
    }

}
