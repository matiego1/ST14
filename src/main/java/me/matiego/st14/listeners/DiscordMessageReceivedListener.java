package me.matiego.st14.listeners;

import me.matiego.st14.utils.DiscordUtils;
import me.matiego.st14.Logs;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.time.Instant;

public class DiscordMessageReceivedListener extends ListenerAdapter {
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        Message msg = event.getMessage();
        if (msg.isFromGuild()) return;
        User user = msg.getAuthor();
        if (user.isBot()) return;

        String content = msg.getContentDisplay();
        TextChannel console = DiscordUtils.getConsoleChannel();
        if (console == null) {
            Logs.warning("An attempt to get Discord console channel has failed. Is provided id correct?");
            return;
        }

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Private message from user");
        eb.setDescription(DiscordUtils.checkLength(content, MessageEmbed.DESCRIPTION_MAX_LENGTH));
        eb.setFooter("Received at", DiscordUtils.getBotIcon());
        eb.setTimestamp(Instant.now());
        eb.setAuthor(DiscordUtils.getAsTag(user), null, user.getEffectiveAvatarUrl());
        eb.setColor(Color.GREEN);
        Utils.async(() -> {
            MessageCreateAction messageAction = console.sendMessageEmbeds(eb.build());
            for (Message.Attachment attachment : msg.getAttachments()) {
                try {
                    messageAction = messageAction.addFiles(FileUpload.fromData(DiscordUtils.convertAttachmentToInputStream(attachment), attachment.getFileName() + "." + attachment.getFileExtension()));
                } catch (Exception ignored) {}
            }
            messageAction.queue();
        });
    }
}
