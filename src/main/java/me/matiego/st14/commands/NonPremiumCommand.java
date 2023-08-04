package me.matiego.st14.commands;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.managers.NonPremiumManager;
import me.matiego.st14.objects.CommandHandler;
import me.matiego.st14.utils.DiscordUtils;
import me.matiego.st14.utils.NonPremiumUtils;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class NonPremiumCommand implements CommandHandler.Minecraft, CommandHandler.Discord {
    public NonPremiumCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("nonpremium");
        if (command == null) {
            Logs.warning("The command /nonpremium does not exist in the plugin.yml file and cannot be registered.");
        }
    }

    private final Main plugin;
    private final PluginCommand command;

    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public int onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Utils.getComponentByString("&cTej komendy może użyć tylko gracz."));
            return 0;
        }

        if (args.length != 1) return -1;

        UUID uuid = player.getUniqueId();
        if (!NonPremiumUtils.isNonPremiumUuid(uuid)) {
            player.sendMessage(Utils.getComponentByString("&cTej komendy może użyć tylko gracz non-premium."));
            return 3;
        }

        NonPremiumManager manager = plugin.getNonPremiumManager();
        if (manager.isLoggedIn(player)) {
            player.sendMessage(Utils.getComponentByString("&cJuż jesteś zalogowany!"));
            return 3;
        }

        Utils.async(() -> {
            if (manager.checkPassword(uuid, args[0])) {
                manager.logIn(player);
                player.sendMessage(Utils.getComponentByString("&aPomyślnie zalogowano! Miłej gry :)"));
                sendMessageToUser(uuid, "Gracz non-premium powiązany z twoim kontem Discord pomyślnie zalogował się do gry.");
            } else {
                player.sendMessage(Utils.getComponentByString("&cZłe hasło!"));
                sendMessageToUser(uuid, "Zarejestrowano nieudaną próbę zalogowania się gracza non-premium powiązanego z twoim kontem Discord.");
            }
        });
        return 1;
    }

    private void sendMessageToUser(@NotNull UUID uuid, @NotNull String message) {
        JDA jda = plugin.getJda();
        if (jda == null) return;
        jda.retrieveUserById(NonPremiumUtils.getIdByNonPremiumUuid(uuid)).queue(user -> DiscordUtils.sendPrivateMessage(user, message));
    }

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("non-premium", "Zarządzaj swoim kontem non-premium")
                .setGuildOnly(true)
                .addSubcommands(
                        new SubcommandData("cancel", "Zakończ aktualną sesję"),
                        new SubcommandData("start", "Zacznij nową sesję")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "name", "Twój nick, z którym dołączysz do serwera", true)
                                                .setRequiredLength(5, 36),
                                        new OptionData(OptionType.STRING, "haslo", "Hasło do zalogowania się w grze", true)
                                                .setMinLength(8)
                                )
                );
    }

    @Override
    public int onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
        event.deferReply(true).queue();
        InteractionHook hook = event.getHook();

        Member member = event.getMember();
        if (member == null) {
            hook.sendMessage("Tej komendy możesz użyć tylko na serwerze Discord!").queue();
            return 3;
        }

        hook.sendMessage("Możliwość gry na kontach non-premium już wkrótce!").queue();
        return 10;
    }
}
