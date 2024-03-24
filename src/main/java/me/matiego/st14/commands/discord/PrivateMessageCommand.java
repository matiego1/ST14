package me.matiego.st14.commands.discord;

import me.matiego.st14.objects.CommandHandler;
import me.matiego.st14.utils.DiscordUtils;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;

public class PrivateMessageCommand implements CommandHandler.Discord {
    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("send-private-message", "Wyślij prywatną wiadomość do użytkownika na Discord")
                .addOptions(
                        new OptionData(OptionType.USER, "uzytkownik", "docelowy użytkownik", true),
                        new OptionData(OptionType.STRING, "wiadomosc", "wiadomość do wysłania", true)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .setGuildOnly(true);
    }

    @Override
    public int onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
        User user = event.getOption("uzytkownik", OptionMapping::getAsUser);
        if (user == null) {
            event.reply("Zły użytkownik!").setEphemeral(true).queue();
            return 0;
        }

        String message = DiscordUtils.checkLength(event.getOption("wiadomosc", "", OptionMapping::getAsString), Message.MAX_CONTENT_LENGTH - 100);
        DiscordUtils.sendPrivateMessage(user, message, action -> {}, result -> {
            switch (result) {
                case SUCCESS -> event.reply("Pomyślnie wysłano prywatną wiadomość do użytkownika " + DiscordUtils.getAsTag(user) + " o treści:\n" + message).queue();
                case CANNOT_SEND_TO_USER -> event.reply("Ten użytkownik nie zezwala na prywatne wiadomości od bota!").queue();
                case FAILURE -> event.reply("Napotkano niespodziewany błąd. Spróbuj ponownie!").queue();
            }
        });
        return 1;
    }
}