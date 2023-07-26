package me.matiego.st14.commands.minecraft;

import me.matiego.st14.Main;
import me.matiego.st14.utils.CommandHandler;
import me.matiego.st14.Logs;
import me.matiego.st14.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class TellCommand implements CommandHandler.Minecraft {
    public TellCommand(@NotNull Main plugin) {
        command = plugin.getCommand("tell");
        if (command == null) {
            Logs.warning("The command /tell does not exist in the plugin.yml file and cannot be registered.");
        }
    }
    private final PluginCommand command;

    private final HashMap<UUID, UUID> reply = new HashMap<>();

    public synchronized @Nullable Player getReply(@NotNull UUID uuid) {
        Player result = Bukkit.getPlayer(reply.get(uuid));
        if (result == null) reply.remove(uuid);
        return result;
    }

    public synchronized void putReply(@NotNull UUID u1, @NotNull UUID u2) {
        reply.put(u1, u2);
        reply.put(u2, u1);
    }
    public synchronized void removeReply(@NotNull UUID uuid) {
        UUID u = reply.remove(uuid);
        if (u != null) {
            reply.remove(u);
        }
    }

    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public int onCommand(@NotNull CommandSender commandSender, @NotNull String[] args) {
        if (args.length < 2) return -1;

        StringBuilder builder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            builder.append(args[i]).append(" ");
        }
        String msg = builder.toString();
        msg = msg.substring(0, msg.length() - 1).replace("&", "");

        if (commandSender instanceof Player sender) {
            Block block = sender.getLocation().getBlock();
            msg = msg.replace("[here]", "[" + Utils.getWorldName(block.getWorld()) + ": " + block.getX() + ", " + block.getY() + ", " + block.getZ() + "]");

            if (args[0].equalsIgnoreCase("[admin]")) {
                removeReply(sender.getUniqueId());

                log(msg, sender, null);
                sender.sendMessage(Utils.getComponentByString("&6[&cJa &6->&4 [admin]&6]:&r " + msg));
                return 0;
            }

            Player receiver = Bukkit.getPlayer(args[0]);
            if (receiver == null) {
                sender.sendMessage(Utils.getComponentByString("&cTen gracz nie jest online."));
                return 0;
            }

            putReply(sender.getUniqueId(), receiver.getUniqueId());

            if (receiver.equals(sender)) {
                receiver.sendMessage(Utils.getComponentByString("&6[&cJa&6]:&r " + msg));
                return 0;
            }

            log(msg, sender, receiver);

            sender.sendMessage(Utils.getComponentByString("&6[&cJa &6->&c " + receiver.getName() + "&6]:&r " + msg));
            receiver.sendMessage(Utils.getComponentByString("&6[&c" + sender.getName() + " &6->&c Ja&6]:&r " + msg));

            return 0;
        }

        Player receiver = Bukkit.getPlayer(args[0]);
        if (receiver == null) {
            commandSender.sendMessage(Utils.getComponentByString("&cTen gracz nie jest online."));
            return 0;
        }

        removeReply(receiver.getUniqueId());

        log(msg, null, receiver);
        receiver.sendMessage(Utils.getComponentByString("&6[&4[admin] &6->&c Ja&6]:&r " + msg));
        return 0;
    }

    public void log(@NotNull String msg, @Nullable Player sender, @Nullable Player receiver) {
        String senderName = sender == null ? "[admin]" : sender.getName();
        String receiverName = receiver == null ? "[admin]" : receiver.getName();
        Logs.info("[" + senderName + " -> " + receiverName +"]: " + msg);
    }


    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
            names.add("[admin]");
            return names;
        }
        return new ArrayList<>();
    }
}
