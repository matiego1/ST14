package me.matiego.st14.commands.minecraft;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.objects.command.CommandHandler;
import me.matiego.st14.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HelpCommand implements CommandHandler.Minecraft {
    public HelpCommand(@NotNull Main plugin) {
        command = plugin.getCommand("help");
        if (command == null) {
            Logs.warning("The command /help does not exist in the plugin.yml file and cannot be registered.");
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
            Bukkit.dispatchCommand(sender, "bukkit:help");
            return 1; //not zero to prevent a loop if it somehow happened
        }

        Component link = Utils.getComponentByString("&2mapa.st14.pl/tutorial")
                .clickEvent(ClickEvent.openUrl("http://mapa.st14.pl/tutorial"));

        player.sendMessage(Utils.getComponentByString("&aNa stronie ").append(link).append(Utils.getComponentByString("&a znajdziesz opis wszystkich funkcji i komend na tym serwerze.")));
        return 1;
    }
}
