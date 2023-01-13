package me.matiego.st14.commands.discord;

import me.matiego.st14.utils.CommandHandler.Discord;
import me.matiego.st14.utils.DiscordUtils;
import me.matiego.st14.utils.Logs;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
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
        return Commands.slash("feedback", "Zgłoś błąd, napisz propozycję lub podziel się swoją opinią");
    }

    @Override
    public int onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
        event.replyModal(
                Modal.create("feedback-modal", "Wyślij twoją opinię")
                        .addActionRows(
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
        String subject = Objects.requireNonNull(event.getValue("subject")).getAsString();
        String description = Objects.requireNonNull(event.getValue("description")).getAsString();

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(DiscordUtils.checkLength("Opinia - " + subject, MessageEmbed.TITLE_MAX_LENGTH));
        eb.setDescription(DiscordUtils.checkLength(description, MessageEmbed.DESCRIPTION_MAX_LENGTH));
        eb.setTimestamp(Instant.now());
        eb.setColor(Color.MAGENTA);
        eb.setFooter(event.getUser().getAsTag());

        event.deferReply(true).queue();
        Logs.discord(eb.build()).thenAcceptAsync(b -> event.getHook().sendMessage(b ? "Dziękujemy za twoją opinię!" : "Napotkano niespodziewany błąd.").queue());
        return 15;
    }
}
