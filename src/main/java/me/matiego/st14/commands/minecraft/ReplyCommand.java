package me.matiego.st14.commands.minecraft;

import me.matiego.st14.Main;
import me.matiego.st14.commands.TellCommand;
import me.matiego.st14.objects.command.CommandHandler;
import me.matiego.st14.Logs;
import me.matiego.st14.utils.Utils;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplyCommand implements CommandHandler.Minecraft {
    public ReplyCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("reply");
        if (command == null) {
            Logs.warning("The command /reply does not exist in the plugin.yml file and cannot be registered.");
        }
    }
    private final Main plugin;
    private final PluginCommand command;

    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public int onCommand(@NotNull CommandSender commandSender, @NotNull String[] args) {
        if (!(commandSender instanceof Player sender)) {
            commandSender.sendMessage(Utils.getComponentByString("&cTej komendy może użyć tylko gracz"));
            return 0;
        }
        if (args.length == 0) return -1;

        Block block = sender.getLocation().getBlock();
        String msg = String.join(" ", args)
                .replace("&", "")
                .replace("[here]", "[" + Utils.getWorldName(block.getWorld()) + ": " + block.getX() + ", " + block.getY() + ", " + block.getZ() + "]");

        TellCommand manager = plugin.getTellCommand();
        Player receiver = manager.getReply(sender.getUniqueId());
        if (receiver == null) {
            sender.sendMessage(Utils.getComponentByString("&cNie pisałeś ostatnio do nikogo."));
            return 0;
        }

        manager.putReply(sender.getUniqueId(), receiver.getUniqueId());

        sender.sendMessage(Utils.getComponentByString("&6[&cJa &6->&c " + receiver.getName() + "&6]:&r " + msg));
        receiver.sendMessage(Utils.getComponentByString("&6[&c" + sender.getName() + " &6->&c Ja&6]:&r " + msg));

        manager.log(msg, sender, receiver);
        return 0;
    }
}
