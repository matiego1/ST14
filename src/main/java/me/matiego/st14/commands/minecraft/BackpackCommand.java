package me.matiego.st14.commands.minecraft;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.objects.CommandHandler;
import me.matiego.st14.objects.GUI;
import me.matiego.st14.utils.Utils;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BackpackCommand implements CommandHandler.Minecraft, Listener {
    public BackpackCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("backpack");
        if (command == null) {
            Logs.warning("The command /backpack does not exist in the plugin.yml file and cannot be registered.");
        }
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

        if (Utils.checkIfCanNotExecuteCommandInWorld(player, "backpack")) {
            sender.sendMessage(Utils.getComponentByString("&cNie możesz użyć tej komendy w tym świecie."));
            return 3;
        }

        Utils.async(() -> {
            List<ItemStack> items = plugin.getBackpackManager().loadBackpack(player.getUniqueId());
            if (items == null) {
                player.sendMessage(Utils.getComponentByString("&cNapotkano niespodziewany błąd. Spróbuj później."));
                return;
            }

            Inventory inv = GUI.createInventory(9, "&3Twój plecak");
            int slot = 0;
            for (ItemStack item : items) {
                inv.setItem(slot, item);

                slot++;
                if (slot >= 9) break;
            }

            Utils.sync(() -> player.openInventory(inv));
        });
        return 8;
    }
}
