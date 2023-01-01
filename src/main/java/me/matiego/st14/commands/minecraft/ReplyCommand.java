package me.matiego.st14.commands.minecraft;

import me.matiego.st14.Main;
import me.matiego.st14.utils.CommandHandler;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Utils;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplyCommand implements CommandHandler.Minecraft {
    private final PluginCommand command;
    public ReplyCommand() {
        command = Main.getInstance().getCommand("reply");
        if (command == null) {
            Logs.warning("The command /reply does not exist in the plugin.yml file and cannot be registered.");
        }
    }
    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public int onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Utils.getComponentByString("&cTej komendy może użyć tylko gracz"));
            return 0;
        }
        if (args.length == 0) return -1;

        Block block = player.getLocation().getBlock();
        String msg = String.join(" ", args)
                .replace("&", "")
                .replace("[here]", "[" + Utils.getWorldName(player.getWorld()) + ": " + block.getX() + ", " + block.getY() + ", " + block.getZ() + "]");

        TellCommand manager = Main.getInstance().getTellCommand();
        Player receiver = manager.getReply(player.getUniqueId());
        if (receiver == null) {
            player.sendMessage(Utils.getComponentByString("&cNie pisałeś ostatnio do nikogo."));
            return 0;
        }

        manager.putReply(player.getUniqueId(), receiver.getUniqueId());
        player.sendMessage(Utils.getComponentByString("&6[&cJa &6->&c " + receiver.getName() + "&6]:&r " + msg));
        receiver.sendMessage(Utils.getComponentByString("&6[&c" + player.getName() + " &6->&c Ja&6]:&r " + msg));
        manager.log(msg, "Wiadomość prywatna - od " + player.getName() + " do " + receiver.getName());
        return 0;
    }
}
