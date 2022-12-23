package me.matiego.st14.commands;

import me.matiego.st14.Main;
import me.matiego.st14.utils.CommandHandler;
import me.matiego.st14.utils.DiscordUtils;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;

public class TellCommand implements CommandHandler.Minecraft {
    private final PluginCommand command;
    public TellCommand() {
        command = Main.getInstance().getCommand("tell");
        if (command == null) {
            Logs.warning("The command /tell does not exist in the plugin.yml file and cannot be registered.");
        }
    }

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
        reply.remove(uuid);
    }

    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 2) return false;
        StringBuilder builder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            builder.append(args[i]).append(" ");
        }
        String msg = builder.toString();
        msg = msg.substring(0, msg.length() - 1).replace("&", "");

        if (sender instanceof Player player) {
            Block block = player.getLocation().getBlock();
            msg = msg.replace("[here]", "[" + Utils.getWorldName(player.getWorld()) + ": " + block.getX() + ", " + block.getY() + ", " + block.getZ() + "]");

            if (args[0].equalsIgnoreCase("!Serwer")) {
                Logs.info("&6[&c" + player.getName() + " &6->&4 Serwer&6]:&r " + msg);
                log(msg, "Wiadomość prywatna - od " + player.getName());
                return true;
            }

            Player receiver = Bukkit.getPlayer(args[0]);
            if (receiver == null) {
                sender.sendMessage(Utils.getComponentByString("&cTen gracz nie jest online."));
                return true;
            }
            if (receiver.equals(player)) {
                putReply(player.getUniqueId(), player.getUniqueId());
                receiver.sendMessage(Utils.getComponentByString("&6[&cJa&6]:&r " + msg));
                return true;
            }

            putReply(player.getUniqueId(), receiver.getUniqueId());
            player.sendMessage(Utils.getComponentByString("&6[&cJa &6->&c " + receiver.getName() + "&6]:&r " + msg));
            receiver.sendMessage(Utils.getComponentByString("&6[&c" + player.getName() + " &6->&c Ja&6]:&r " + msg));
            log(msg, "Wiadomość prywatna - od " + player.getName() + " do " + receiver.getName());
        }

        Player receiver = Bukkit.getPlayer(args[0]);
        if (receiver == null) {
            sender.sendMessage(Utils.getComponentByString("&cTen gracz nie jest online."));
            return true;
        }
        sender.sendMessage(Utils.getComponentByString("&6[&cSerwer &6->&c " + receiver.getName() + "&6]:&r " + msg));
        receiver.sendMessage(Utils.getComponentByString("&6[&cSerwer &6->&c Ja&6]:&r " + msg));
        return true;
    }

    public void log(@NotNull String msg, @NotNull String footer) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setDescription(DiscordUtils.checkLength(msg, MessageEmbed.DESCRIPTION_MAX_LENGTH));
        eb.setFooter(footer);
        eb.setColor(Color.GRAY);
        eb.setTimestamp(Instant.now());
        Logs.discord(eb.build());
    }
}
