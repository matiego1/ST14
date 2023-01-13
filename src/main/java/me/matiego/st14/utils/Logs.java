package me.matiego.st14.utils;

import me.matiego.st14.Main;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

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
    }

    /**
     * Sends a warning to the console and Discord logs channel.
     * @param message the message to send
     */
    public static void warning(@NotNull String message) {
        plugin.getLogger().warning(message);

        EmbedBuilder eb = new EmbedBuilder();
        eb.setDescription(DiscordUtils.checkLength(message, MessageEmbed.DESCRIPTION_MAX_LENGTH));
        eb.setColor(Color.YELLOW);
        eb.setTimestamp(Instant.now());
        eb.setFooter("Warning");
        discord(eb.build());
    }

    /**
     * Sends a warning to the console and Discord logs channel.
     * @param message the message to send
     */
    public static void error(@NotNull String message) {
        plugin.getLogger().severe(message);

        EmbedBuilder eb = new EmbedBuilder();
        eb.setDescription(DiscordUtils.checkLength(message, MessageEmbed.DESCRIPTION_MAX_LENGTH));
        eb.setColor(Color.RED);
        eb.setTimestamp(Instant.now());
        eb.setFooter("Error");
        discord(eb.build());
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
        eb.setDescription(DiscordUtils.checkLength(message, MessageEmbed.DESCRIPTION_MAX_LENGTH));
        eb.addField("**Stack trace:**", DiscordUtils.checkLength(stringWriter.toString(), MessageEmbed.VALUE_MAX_LENGTH), false);
        eb.setColor(Color.RED);
        eb.setTimestamp(Instant.now());
        eb.setFooter("Error");
        discord(eb.build());
    }

    public static @NotNull CompletableFuture<Boolean> discord(@NotNull MessageEmbed embed) {
        TextChannel chn = DiscordUtils.getConsoleChannel();
        if (chn == null) return CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        chn.sendMessageEmbeds(embed).queue(msg -> future.complete(true), failure -> future.complete(false));
        return future;
    }

    public static @NotNull CompletableFuture<Boolean> discord(@NotNull String message) {
        TextChannel chn = DiscordUtils.getConsoleChannel();
        if (chn == null) return CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        chn.sendMessage(message).queue(msg -> future.complete(true), failure -> future.complete(false));
        return future;
    }
}
