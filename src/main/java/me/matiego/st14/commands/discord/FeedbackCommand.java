package me.matiego.st14.commands.discord;

import me.matiego.st14.objects.CommandHandler.Discord;
import me.matiego.st14.utils.DiscordUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalInteraction;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.time.Instant;
import java.util.Objects;

public class FeedbackCommand implements Discord {
    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("feedback", "Zgłoś błąd, napisz propozycję lub podziel się swoją opinią").setGuildOnly(true);
    }

    @Override
    public int onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
        event.replyModal(
                Modal.create("feedback-modal", "Wyślij twoją opinię")
                        .addComponents(
                                ActionRow.of(TextInput.create("subject", "Temat", TextInputStyle.SHORT)
                                        .setRequired(true)
                                        .setPlaceholder("zgłoszenie błędu, propozycja...")
                                        .build()),
                                ActionRow.of(TextInput.create("description", "opis", TextInputStyle.PARAGRAPH)
                                        .setRequired(true)
                                        .build())
                        )
                        .build()
        ).queue();
        return 0;
    }

    @Override
    public int onModalInteraction(@NotNull ModalInteraction event) {
        if (!event.getModalId().equals("feedback-modal")) return 0;
        InteractionHook hook = event.getHook();
        String subject = Objects.requireNonNull(event.getValue("subject")).getAsString();
        String description = Objects.requireNonNull(event.getValue("description")).getAsString();

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(DiscordUtils.checkLength("Opinia - " + subject, MessageEmbed.TITLE_MAX_LENGTH));
        eb.setDescription(DiscordUtils.checkLength(description, MessageEmbed.DESCRIPTION_MAX_LENGTH));
        eb.setTimestamp(Instant.now());
        eb.setColor(Color.MAGENTA);
        eb.setFooter(DiscordUtils.getAsTag(event.getUser()));

        event.deferReply(true).queue();

        String message = "Napotkano niespodziewany błąd.\nWiadomość, którą chciałeś wysłać:\n```\n%s\n```";
        String param = DiscordUtils.checkLength(subject + "\n" + description, Message.MAX_CONTENT_LENGTH - message.length() - 5);
        String errorMessage = DiscordUtils.checkLength(message.formatted(param), Message.MAX_CONTENT_LENGTH);

        TextChannel chn = DiscordUtils.getConsoleChannel();
        if (chn == null) {
            hook.sendMessage(errorMessage).queue();
            return 5;
        }
        chn.sendMessageEmbeds(eb.build()).queue(
                success -> hook.sendMessage("Dziękujemy za twoją opinię!").queue(),
                failure -> hook.sendMessage(errorMessage).queue()
        );
        return 30;
    }
}
