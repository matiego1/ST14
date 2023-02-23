package me.matiego.st14.commands.minecraft;

import me.matiego.st14.Main;
import me.matiego.st14.utils.CommandHandler;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Prefix;
import me.matiego.st14.utils.Utils;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SuicideCommand implements CommandHandler.Minecraft {
    public SuicideCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("suicide");
        if (command == null) {
            Logs.warning("The command /suicide does not exist in the plugin.yml file and cannot be registered.");
        }
    }
    private final Main plugin;
    private final PluginCommand command;

    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public int onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Utils.getComponentByString(Prefix.SUICIDE + "Tej komendy może użyć tylko gracz"));
            return 0;
        }

        if (Utils.checkIfCanNotExecuteCommandInWorld(player, "suicide")) {
            player.sendMessage(Utils.getComponentByString("&cNie możesz użyć tej komendy w tym świecie."));
            return 3;
        }

        plugin.getGraveCreateListener().unprotectNextGrave(player.getUniqueId());
        player.setHealth(0);

        Utils.broadcastMessage(
                player,
                Prefix.SUICIDE,
                "&cPopełniłeś samobójstwo! Twój grób nie jest zabezpieczony.",
                "Gracz " + player.getName() + " popełnił samobójstwo!",
                "Gracz **" + player.getName() + "** popełnił samobójstwo!"
        );
        return 60;
    }
}
