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

    /**
     * Sends a normal message to the console.
     * @param message the message to send
     */
    public static void info(@NotNull String message) {
        plugin.getLogger().info(message);
        discord(message);
    }

    /**
     * Sends a warning to the console and Discord logs channel.
     * @param message the message to send
     */
    public static void warning(@NotNull String message) {
        plugin.getLogger().warning(message);
        discord("WARNING", message, null);
    }

    /**
     * Sends a warning to the console and Discord logs channel.
     * This will also send a throwable stack trace.
     * @param message the message to send
     * @param throwable the throwable whose stack trace is to be sent to the console
     */
    public static void warning(@NotNull String message, @NotNull Throwable throwable) {
        plugin.getLogger().severe(message);

        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        for (String line : stringWriter.toString().split("\n")) plugin.getLogger().warning(line);

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(Color.YELLOW);
        eb.setDescription(DiscordUtils.checkLength(stringWriter.toString(), MessageEmbed.DESCRIPTION_MAX_LENGTH));
        discord("WARNING", message, eb.build());
    }

    /**
     * Sends a warning to the console and Discord logs channel.
     * @param message the message to send
     */
    public static void error(@NotNull String message) {
        plugin.getLogger().severe(message);
        discord("ERROR", message, null);
    }

    /**
     * Sends an error to the console and Discord logs channel.
     * This will also send a throwable stack trace.
     * @param message the message to send
     * @param throwable the throwable whose stack trace is to be sent to the console
     */
    public static void error(@NotNull String message, @NotNull Throwable throwable) {
        plugin.getLogger().severe(message);

        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        for (String line : stringWriter.toString().split("\n")) plugin.getLogger().severe(line);

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(Color.RED);
        eb.setDescription(DiscordUtils.checkLength(stringWriter.toString(), MessageEmbed.DESCRIPTION_MAX_LENGTH));
        discord("ERROR", message, eb.build());
    }

    public static void discord(@NotNull String message) {
        discord("INFO", message, null);
    }
    private static void discord(@NotNull String type, @NotNull String message, @Nullable MessageEmbed embed) {
        TextChannel chn = DiscordUtils.getConsoleChannel();
        if (chn == null) return;
        MessageCreateAction action = chn.sendMessage(DiscordUtils.checkLength("[<t:" + (Utils.now() / 1000) + ":T> " + type + "]: " + message, Message.MAX_CONTENT_LENGTH));
        if (embed != null) {
            action.setEmbeds(embed);
        }
        action.queue();
    }
}
