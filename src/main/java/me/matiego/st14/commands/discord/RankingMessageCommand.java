package me.matiego.st14.commands.discord;

import me.matiego.st14.Main;
import me.matiego.st14.managers.RankingsManager;
import me.matiego.st14.objects.command.CommandHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.*;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.stream.Collectors;

public class RankingMessageCommand implements CommandHandler.Discord {
    public RankingMessageCommand(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("ranking-message", "Wyślij wiadomość z rankingiem graczy")
                .addOptions(
                        new OptionData(OptionType.STRING, "typ", "typ rankingu", true)
                                .addChoices(
                                        Arrays.stream(RankingsManager.Type.values())
                                                .map(Enum::toString)
                                                .map(String::toLowerCase)
                                                .map(v -> new Command.Choice(v, v))
                                                .collect(Collectors.toList())
                                )
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .setGuildOnly(true);
    }

    @Override
    public int onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
        event.deferReply(true).queue();
        InteractionHook hook = event.getHook();

        RankingsManager.Type type = RankingsManager.Type.getByName(event.getOption("typ", "null", OptionMapping::getAsString));
        if (type == null) {
            hook.sendMessage("Ten ranking nie istnieje.").queue();
            return 0;
        }

        MessageChannelUnion channel = event.getChannel();
        if (channel.getType() != ChannelType.TEXT) {
            hook.sendMessage("Na tym kanale nie można wysłać wiadomości z rankingiem.").queue();
            return 0;
        }

        EmbedBuilder eb = plugin.getRankingsManager().getEmbed(type, 50);
        if (eb == null) {
            eb = new EmbedBuilder();
            eb.setDescription("Wczytywanie rankingu " + type.getRankingName() + "...");
        }
        channel.sendMessageEmbeds(eb.build()).queue(
                message -> {
                    if (plugin.getRankingsManager().addRankingMessage(type, message.getIdLong(), channel.getIdLong())) {
                        hook.sendMessage("Pomyślnie wysłano wiadomość z rankingiem.").queue();
                        return;
                    }
                    message.delete().queue(
                            success -> hook.sendMessage("Napotkano niespodziewany błąd. Spróbuj ponownie.").queue(),
                            failure -> hook.sendMessage("Napotkano niespodziewany błąd. Wiadomość z rankingiem została wysłana, jednak nie będzie aktualizowana. Spróbuj ponownie.").queue()
                    );
                },
                failure -> hook.sendMessage("Napotkano niespodziewany błąd. Spróbuj ponownie.").queue()
        );
        return 3;
    }
}
