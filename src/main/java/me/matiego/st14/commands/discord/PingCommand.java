package me.matiego.st14.commands.discord;

import me.matiego.st14.utils.CommandHandler;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;

public class PingCommand implements CommandHandler.Discord {
    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("ping", "Wyświetla aktualny ping bota")
                .addOptions(
                        new OptionData(OptionType.STRING, "incognito", "czy wiadomość ma być widoczna tylko dla ciebie", false)
                                .addChoice("Tak", "True")
                                .addChoice("Nie", "False")
                );
    }

    @Override
    public int onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
        long time = Utils.now();
        event.reply("Pong!")
                .setEphemeral(event.getOption("incognito", "False", OptionMapping::getAsString).equals("True"))
                .flatMap(v -> event.getHook().editOriginalFormat("Pong: %d ms", Utils.now() - time))
                .queue();
        return 0;
    }
}
