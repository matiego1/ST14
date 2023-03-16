package me.matiego.st14.utils;

import me.matiego.st14.Main;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;

public class Logs {
    private static final Main plugin;

    static {
        plugin = Main.getInstance();
    }

    public static void info(@NotNull String message) {
        plugin.getLogger().info(message);
        discord(message);
    }

    public static void infoWithBlock(@NotNull String message) {
        plugin.getLogger().info(message);
        discordWithBlock(message);
    }

    /**
     * Sends a warning to the console and Discord logs channel.
     * @param message the message to send
     */
    public static void warning(@NotNull String message) {
        warning(message, null);
    }

    /**
     * Sends a warning to the console and Discord logs channel.
     * This will also send a throwable stack trace.
     * @param message the message to send
     * @param throwable the throwable whose stack trace is to be sent to the console
     */
    public static void warning(@NotNull String message, @Nullable Throwable throwable) {
        plugin.getLogger().warning(message);

        MessageEmbed embed = null;
        if (throwable != null) {
            StringWriter stringWriter = new StringWriter();
            throwable.printStackTrace(new PrintWriter(stringWriter));
            for (String line : stringWriter.toString().split("\n")) plugin.getLogger().warning(line);

            EmbedBuilder eb = new EmbedBuilder();
            eb.setColor(Color.YELLOW);
            eb.setDescription(DiscordUtils.checkLength(stringWriter.toString(), MessageEmbed.DESCRIPTION_MAX_LENGTH));
            embed = eb.build();
        }

        discord("__WARNING__", message, embed);
    }

    /**
     * Sends a warning to the console and Discord logs channel.
     * @param message the message to send
     */
    public static void error(@NotNull String message) {
        error(message, null);
    }

    /**
     * Sends an error to the console and Discord logs channel.
     * This will also send a throwable stack trace.
     * @param message the message to send
     * @param throwable the throwable whose stack trace is to be sent to the console
     */
    public static void error(@NotNull String message, @Nullable Throwable throwable) {
        plugin.getLogger().severe(message);

        MessageEmbed embed = null;
        if (throwable != null) {
            StringWriter stringWriter = new StringWriter();
            throwable.printStackTrace(new PrintWriter(stringWriter));
            for (String line : stringWriter.toString().split("\n")) plugin.getLogger().severe(line);

            EmbedBuilder eb = new EmbedBuilder();
            eb.setColor(Color.RED);
            eb.setDescription(DiscordUtils.checkLength(stringWriter.toString(), MessageEmbed.DESCRIPTION_MAX_LENGTH));
            embed = eb.build();
        }

        discord("__ERROR__", message, embed);
    }

    public static void discord(@NotNull String message) {
        discord("INFO", message, null);
    }

    private static void discordWithBlock(@NotNull String message) {
        TextChannel chn = DiscordUtils.getConsoleChannel();
        if (chn == null) return;
        chn.sendMessage(DiscordUtils.checkLength("**[<t:" + (Utils.now() / 1000) + ":T> INFO]:** " + message, Message.MAX_CONTENT_LENGTH)).complete();
    }

    private static void discord(@NotNull String type, @NotNull String message, @Nullable MessageEmbed embed) {
        TextChannel chn = DiscordUtils.getConsoleChannel();
        if (chn == null) return;
        MessageCreateAction action = chn.sendMessage(DiscordUtils.checkLength("**[<t:" + (Utils.now() / 1000) + ":T> " + type + "]:** " + message, Message.MAX_CONTENT_LENGTH));
        if (embed != null) {
            action.setEmbeds(embed);
        }
        action.queue();
    }
}
