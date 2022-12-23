package me.matiego.st14.commands;

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
import java.util.regex.Pattern;

public class SayCommand implements CommandHandler.Minecraft {
    private final PluginCommand command;
    public SayCommand() {
        command = Main.getInstance().getCommand("say");
        if (command == null) {
            Logs.warning("The command /say does not exist in the plugin.yml file and cannot be registered.");
        }
    }

    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 0) return false;

        String message = String.join(" ", args);

        if (!(sender instanceof Player)) {
            try {
                if (Pattern.compile(Main.getInstance().getConfig().getString("say-command-disallowed-regex", "[^\\s\\S]*")).matcher(message).matches()) return true;
            } catch (Exception e) {
                Logs.warning("An error occurred while matching the /say message to the regex. Is the regex valid?");
                e.printStackTrace();
            }
        }

        Bukkit.broadcast(Utils.getComponentByString("&2[&aSerwer&2]:&r " + message));

        EmbedBuilder eb = new EmbedBuilder();
        eb.setDescription(DiscordUtils.checkLength(message, MessageEmbed.DESCRIPTION_MAX_LENGTH));
        eb.setColor(Color.GREEN);
        Main.getInstance().getChatMinecraft().sendMessageEmbed(eb.build());
        return true;
    }
}
