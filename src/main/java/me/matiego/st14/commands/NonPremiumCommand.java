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
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
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
        if (manager.isLoggedIn(uuid)) {
            player.sendMessage(Utils.getComponentByString("&cJuż jesteś zalogowany!"));
            return 3;
        }

        if (manager.checkVerificationCode(player, args[0])) {
            manager.logIn(player);
            player.sendMessage(Utils.getComponentByString("&aPomyślnie zalogowano! Miłej gry :)"));
            sendMessageToUser(uuid, "Gracz non-premium powiązany z twoim kontem Discord pomyślnie zalogował się do gry.");
        } else {
            player.sendMessage(Utils.getComponentByString("&cZły kod! Pamiętaj, że kod jest ważny tylko 5 minut od wygenerowania."));
            sendMessageToUser(uuid, "Zarejestrowano nieudaną próbę zalogowania się gracza non-premium powiązanego z twoim kontem Discord.");
        }
        return 1;
    }

    private void sendMessageToUser(@NotNull UUID uuid, @NotNull String message) {
        JDA jda = plugin.getJda();
        if (jda == null) return;

        jda.retrieveUserById(NonPremiumUtils.getIdByNonPremiumUuid(uuid)).queue(
                user -> user.openPrivateChannel().queue(
                        privateChannel -> privateChannel.sendMessage(message)
                                .addActionRow(Button.danger("end-session", "Zakończ sesję non-premium"))
                                .queue(success -> {}, failure -> {})
                )
        );
    }

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("nonpremium", "Zarządzaj swoim kontem non-premium")
                .setGuildOnly(true)
                .addSubcommands(
                        new SubcommandData("cancel", "Zakończ aktualną sesję"),
                        new SubcommandData("start", "Zacznij nową sesję")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "name", "nick, z którym dołączysz do serwera", true)
                                                .setRequiredLength(10, 36)
                                )
                );
    }

    @Override
    public int onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
        event.deferReply(true).queue();
        InteractionHook hook = event.getHook();

        Member member = event.getMember();
        if (member == null) return 0;

        NonPremiumManager manager = plugin.getNonPremiumManager();

        switch (String.valueOf(event.getSubcommandName())) {
            case "cancel" -> {
                manager.endSession(NonPremiumUtils.createNonPremiumUuid(event.getUser()), "Sesja zakończona przez użytkownika");
                hook.sendMessage("Pomyślnie zakończono sesję, jeśli jakaś była aktywna.").queue();
                return 1;
            }
            case "start" -> {
                if (!DiscordUtils.hasRole(member, plugin.getConfig().getLong("discord.role-ids.non-premium"))) {
                    hook.sendMessage("Nie masz uprawnień do używania tej komendy. Jeśli chcesz grać na koncie non-premium zgłoś się do administratora.").queue();
                    return 5;
                }

                //noinspection deprecation
                if (!member.getUser().getDiscriminator().equals("0000")) {
                    hook.sendMessage("Nadal używasz nicku z tagiem! Zmień swój nick na nowy zgodnie z nowymi zasadami na Discord.").queue();
                    return 5;
                }

                String name = event.getOption("name", OptionMapping::getAsString);
                if (name == null) return 0;

                if (!manager.isNameUnique(name)) {
                    hook.sendMessage("Ktoś już używa tego nicku! Wymyśl inny.").queue();
                    return 3;
                }

                String code = manager.startLogin(member, name);
                if (code == null) {
                    hook.sendMessage("Napotkano niespodziewany błąd. Spróbuj ponownie.").queue();
                    return 3;
                }

                hook.sendMessage(
                        "**Dołącz do gry używając nicku `" + name + "`.**\n" +
                                "**Następnie zaloguj się używając komendy `/nonpremium " + code + "`.**\n" +
                                "(Na dołączenie do gry masz 5 minut)\n" +
                                "\n" +
                                "W każdej chwili możesz anulować sesję używając komendy `/nonpremium cancel` na Discord.\n" +
                                "\n" +
                                "Twój nick w grze zostanie zmieniony na `+" + member.getUser().getName() + "`. Wszystkie informacje o graczu zostaną przypisane do twojego konta Discord."
                ).queue();
                return 3;
            }
        }
        return 0;
    }

    @Override
    public int onButtonInteraction(@NotNull ButtonInteraction event) {
        if (!event.getComponentId().equals("end-session")) return 0;

        plugin.getNonPremiumManager().endSession(NonPremiumUtils.createNonPremiumUuid(event.getUser()), "Sesja zakończona przez użytkownika");

        event.reply("Pomyślnie zakończono sesję, jeśli jakaś była aktywna.").queue();
        return 0;
    }
}
