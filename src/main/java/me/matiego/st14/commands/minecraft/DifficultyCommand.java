package me.matiego.st14.commands.minecraft;

import me.matiego.st14.Main;
import me.matiego.st14.utils.*;
import org.bukkit.Difficulty;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DifficultyCommand implements CommandHandler.Minecraft {
    private final PluginCommand command;
    public DifficultyCommand() {
        command = Main.getInstance().getCommand("difficulty");
        if (command == null) {
            Logs.warning("The command /difficulty does not exist in the plugin.yml file and cannot be registered.");
        }
    }
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
        if (args.length != 0) return -1;
        if (!Main.getInstance().getConfig().getStringList("difficulty-worlds").contains(player.getWorld().getName())) {
            sender.sendMessage(Utils.getComponentByString("&cNie możesz zmienić poziomu trudności w tym świecie."));
            return 3;
        }
        Inventory inv = GUI.createInventory(9, "&6Ustaw poziom trudności");
        inv.setItem(2, GUI.createGuiItem(Material.LIME_WOOL, "&aŁatwy", "Kliknij, aby ustawić"));
        inv.setItem(4, GUI.createGuiItem(Material.YELLOW_WOOL, "&eNormalny", "Kliknij, aby ustawić"));
        inv.setItem(6, GUI.createGuiItem(Material.RED_WOOL, "&cTrudny", "Kliknij, aby ustawić"));
        player.openInventory(inv);
        return 3;
    }

    @Override
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!GUI.checkInventory(event, "&6Ustaw poziom trudności")) return;

        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        event.getInventory().close();
        switch (slot) {
            case 2 -> change(player, Difficulty.EASY, "łatwy");
            case 4 -> change(player, Difficulty.NORMAL, "normalny");
            case 6 -> change(player, Difficulty.HARD, "trudny");
        }
    }

    private void change(@NotNull Player player, @NotNull Difficulty difficulty, @NotNull String name) {
        World world = player.getWorld();
        if (world.getDifficulty() == difficulty) {
            player.sendMessage(Utils.getComponentByString("&cW tym świecie już obowiązuje ten poziom trudności"));
            return;
        }
        world.setDifficulty(difficulty);

        Utils.broadcastMessage(
                player,
                Prefix.DIFFICULTY,
                "&aPomyślnie zmieniono poziom trudności na &2" + name + "&a.",
                "&aGracz &2" + player.getName() + "&a zmienił poziom trudności na &2" + name + "&a.",
                "**[" + Utils.getWorldName(world) + "]** Gracz **" + player.getName() + "** zmienił poziom trudności na **" + name + "**."
        );
    }
}
