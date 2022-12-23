package me.matiego.st14.commands;

import me.matiego.st14.Main;
import me.matiego.st14.utils.CommandHandler;
import me.matiego.st14.utils.GUI;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Utils;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GameModeCommand implements CommandHandler.Minecraft {
    private final PluginCommand command;
    public GameModeCommand() {
        command = Main.getInstance().getCommand("gamemode");
        if (command == null) {
            Logs.warning("The command /gamemode does not exist in the plugin.yml file and cannot be registered.");
        }
    }
    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Utils.getComponentByString("&cTej komendy może użyć tylko gracz."));
            return true;
        }
        if (args.length != 0) return false;
        if (!Main.getInstance().getConfig().getStringList("gamemode-worlds").contains(player.getWorld().getName())) {
            sender.sendMessage(Utils.getComponentByString("&cNie możesz zmienić trybu gry w tym świecie."));
            return true;
        }
        Inventory inv = GUI.createInventory(9, "&6Ustaw tryb gry");
        inv.setItem(1, GUI.createGuiItem(Material.DIAMOND_SWORD, "&ePrzetrwania", "Kliknij, aby ustawić"));
        inv.setItem(3, GUI.createGuiItem(Material.DIAMOND_BLOCK, "&eKreatywny", "Kliknij, aby ustawić"));
        inv.setItem(5, GUI.createGuiItem(Material.GRASS_BLOCK, "&ePrzygodowy", "Kliknij, aby ustawić"));
        inv.setItem(7, GUI.createGuiItem(Material.ENDER_PEARL, "&eObserwatora", "Kliknij, aby ustawić"));
        player.openInventory(inv);
        return true;
    }

    @Override
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!GUI.checkInventory(event, "&6Ustaw poziom trudności")) return;

        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
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
        player.sendMessage(Utils.getComponentByString("&aPomyślnie zmieniono twój tryb gry na &2" + name + "&a."));
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(player))
                .filter(p -> p.getWorld().equals(player.getWorld()))
                .forEach(p -> p.sendMessage(Utils.getComponentByString("&aGracz &2" + player.getName() + "&a zmienił swój tryb gry na &2" + name + "&a.")));
        if (!Main.getInstance().getIncognitoManager().isIncognito(player.getUniqueId())) {
            Main.getInstance().getChatMinecraft().sendMessage("Tryb gry", "[" + Utils.getWorldName(player.getWorld()) + "] Gracz **" + player + "** zmienił swój tryb gry na **" + name + "**.");
        }
    }
}
