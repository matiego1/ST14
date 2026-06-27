package me.matiego.st14.commands.minecraft;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.managers.EconomyManager;
import me.matiego.st14.objects.command.CommandHandler;
import me.matiego.st14.utils.Utils;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BalanceCommand implements CommandHandler.Minecraft {
    public BalanceCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("balance");
        if (command == null) {
            Logs.warning("The command /balance does not exist in the plugin.yml file and cannot be registered.");
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
            sender.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Tej komendy może użyć tylko gracz."));
            return 0;
        }

        if (args.length != 0) {
            player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Poprawne użycie: /balance"));
            return 2;
        }

        Utils.async(() -> {
            EconomyManager manager = plugin.getEconomyManager();
            player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Saldo twojego konta: &9" + manager.format(manager.getBalance(player))));
        });

        return 5;
    }
}
