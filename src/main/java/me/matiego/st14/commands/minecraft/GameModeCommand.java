package me.matiego.st14.commands.minecraft;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.utils.*;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GameModeCommand implements CommandHandler.Minecraft {
    public GameModeCommand(@NotNull Main plugin) {
        command = plugin.getCommand("gamemode");
        if (command == null) {
            Logs.warning("The command /gamemode does not exist in the plugin.yml file and cannot be registered.");
        }
    }
    private final PluginCommand command;

    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public int onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Bukkit.dispatchCommand(sender, "minecraft:gamemode " + String.join(" ", args));
            return 1; //not zero to prevent a loop if it somehow happened
        }
        if (args.length != 0) return -1;
        if (Utils.checkIfCanNotExecuteCommandInWorld(player, "gamemode")) {
            sender.sendMessage(Utils.getComponentByString("&cNie możesz zmienić trybu gry w tym świecie."));
            return 3;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            change(player, GameMode.CREATIVE, "kreatywny");
            return 2;
        }
        Inventory inv = GUI.createInventory(9, "&6Ustaw tryb gry");
        inv.setItem(1, GUI.createGuiItem(Material.DIAMOND_SWORD, "&ePrzetrwania", "Kliknij, aby ustawić"));
        inv.setItem(3, GUI.createGuiItem(Material.DIAMOND_BLOCK, "&eKreatywny", "Kliknij, aby ustawić"));
        inv.setItem(5, GUI.createGuiItem(Material.GRASS_BLOCK, "&ePrzygodowy", "Kliknij, aby ustawić"));
        inv.setItem(7, GUI.createGuiItem(Material.ENDER_PEARL, "&eObserwatora", "Kliknij, aby ustawić"));
        player.openInventory(inv);
        return 3;
    }

    @Override
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!GUI.checkInventory(event, "&6Ustaw tryb gry")) return;

        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        event.getInventory().close();

        if (Utils.checkIfCanNotExecuteCommandInWorld(player, "gamemode")) {
            player.sendMessage(Utils.getComponentByString("&cNie możesz zmienić trybu gry w tym świecie."));
            return;
        }

        switch (slot) {
            case 1 -> change(player, GameMode.SURVIVAL, "przetrwania");
            case 3 -> change(player, GameMode.CREATIVE, "kreatywny");
            case 5 -> change(player, GameMode.ADVENTURE, "przygodowy");
            case 7 -> change(player, GameMode.SPECTATOR, "obserwatora");
        }
    }

    private void change(@NotNull Player player, @NotNull GameMode gameMode, @NotNull String name) {
        if (player.getGameMode() == gameMode) {
            player.sendMessage(Utils.getComponentByString("&cJuż masz ten tryb gry!"));
            return;
        }
        player.setGameMode(gameMode);

        Utils.broadcastMessage(
                player,
                Prefix.GAMEMODE,
                "&aPomyślnie zmieniono twój tryb gry na &2" + name + "&a.",
                "&aGracz &2" + player.getName() + "&a zmienił swój tryb gry na &2" + name + "&a w świecie &2" + Utils.getWorldName(player.getWorld()) + "&a.",
                "**[" + Utils.getWorldName(player.getWorld()) + "]** Gracz **" + player.getName() + "** zmienił swój tryb gry na **" + name + "**."
        );
    }
}
