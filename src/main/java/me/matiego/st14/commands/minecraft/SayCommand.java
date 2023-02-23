package me.matiego.st14.commands.minecraft;

import me.matiego.st14.Main;
import me.matiego.st14.utils.CommandHandler;
import me.matiego.st14.utils.DiscordUtils;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class SayCommand implements CommandHandler.Minecraft {
    private final PluginCommand command;
    private final Main plugin;
    public SayCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("say");
        if (command == null) {
            Logs.warning("The command /say does not exist in the plugin.yml file and cannot be registered.");
        }
    }

    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public int onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 0) return -1;

        String message = String.join(" ", args);

        if (!(sender instanceof Player)) {
            try {
                if (message.matches(plugin.getConfig().getString("say-command.disallowed-regex", "[^\\s\\S]*"))) return 0;
                if (message.matches(plugin.getConfig().getString("say-command.stop-regex", "[^\\s\\S]*"))
                        || message.matches(plugin.getConfig().getString("say-command.restart-regex", "[^\\s\\S]*"))) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "st14:stop");
                    return 0;
                }
            } catch (Exception e) {
                Logs.warning("An error occurred while matching the /say message to the regex. Is the regex valid?", e);
            }
        }

        Bukkit.broadcast(Utils.getComponentByString("&2[&aSerwer&2]:&r " + message));

        Logs.discord("**[Serwer]:**" + message);

        EmbedBuilder eb = new EmbedBuilder();
        eb.setDescription(DiscordUtils.checkLength(message, MessageEmbed.DESCRIPTION_MAX_LENGTH));
        eb.setColor(Color.GREEN);
        plugin.getChatMinecraft().sendMessageEmbed(eb.build());
        return 0;
    }
}
