package me.matiego.st14.commands.discord;

import me.matiego.st14.Main;
import me.matiego.st14.managers.AccountsManager;
import me.matiego.st14.managers.NonPremiumManager;
import me.matiego.st14.objects.command.CommandHandler;
import me.matiego.st14.utils.DiscordUtils;
import me.matiego.st14.utils.NonPremiumUtils;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class NonPremiumCommand implements CommandHandler.Discord {
    public NonPremiumCommand(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("nonpremium", "Zarządzaj swoim kontem non-premium")
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(
                        new SubcommandData("cancel", "Zakończ aktualną sesję"),
                        new SubcommandData("start", "Zacznij nową sesję")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "name", "nick, z którym dołączysz do serwera", false)
                                                .setRequiredLength(7, 16)
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
                manager.endSession(NonPremiumUtils.createNonPremiumUuid(member), "Sesja zakończona przez użytkownika");
                hook.sendMessage("Pomyślnie zakończono sesję, jeśli jakaś była aktywna.").queue();
                return 1;
            }
            case "start" -> {
                if (!(
                        DiscordUtils.hasRole(member, plugin.getConfig().getLong("discord.role-ids.verified")) &&
                        DiscordUtils.hasRole(member, plugin.getConfig().getLong("discord.role-ids.non-premium"))
                )) {
                    hook.sendMessage("Nie masz uprawnień do używania tej komendy. Jeśli chcesz grać na koncie non-premium zgłoś się do administratora.").queue();
                    return 5;
                }

                if (!member.getUser().getDiscriminator().equals("0000")) {
                    hook.sendMessage("Nadal używasz nicku z tagiem! Zmień swój nick na nowy zgodnie z nowymi zasadami na Discord.").queue();
                    return 5;
                }

                String name = plugin.getNonPremiumManager().getLastUsedName(member);
                String nameOption = event.getOption("name", OptionMapping::getAsString);
                if (nameOption != null) name = nameOption;

                if (name == null) {
                    hook.sendMessage("Podaj nick, z którym dołączysz do serwera.").queue();
                    return 2;
                }
                plugin.getNonPremiumManager().setLastUsedName(member, name);

                String joinNamePrefix = plugin.getNonPremiumManager().getJoinNamePrefix();
                if (!name.startsWith(joinNamePrefix)) {
                    hook.sendMessage("Nick musi zaczynać się od `" + joinNamePrefix + "`. Ten nick służy tylko do dołączenia na serwer, później zostanie zmieniony!").queue();
                    return 2;
                }
                if (manager.isNameUsed(name, member)) {
                    hook.sendMessage("Ktoś już używa tego nicku! Wymyśl inny.").queue();
                    return 2;
                }

                AccountsManager accountsManager = plugin.getAccountsManager();
                UUID uuid = NonPremiumUtils.createNonPremiumUuid(member);
                UUID linkedUuid = accountsManager.getPlayerByUser(member);
                if (linkedUuid == null) {
                    if (!accountsManager.link(uuid, member)) {
                        hook.sendMessage("Napotkano niespodziewany błąd przy łączeniu kont. Spróbuj później.").queue();
                        return 3;
                    }
                } else if (!uuid.equals(linkedUuid)) {
                    hook.sendMessage("Twoje konto jest połączone z kontem minecraft!").queue();
                    return 3;
                }

                if (!manager.startLogin(member, name, 60)) {
                    hook.sendMessage("Napotkano niespodziewany błąd. Spróbuj ponownie.").queue();
                    return 3;
                }

                hook.sendMessage("""
                        **Dołącz do gry używając nicku `%s`. Masz na to 1 minutę!**
                        
                        W każdej chwili możesz anulować sesję, używając komendy `/nonpremium cancel` na Discord.
                        Twój nick po dołączeniu do gry zostanie zmieniony na `%s`.
                        Wszystkie informacje o graczu zostaną przypisane do twojego konta Discord.
                        30 sekund po wyjściu z serwera twoja sesja automatycznie wygaśnie.
                        
                        **UWAGA!** Przez następną minutę **każdy** gracz z wybranym nickiem może dołączyć na serwer za ciebie! Wybierz skomplikowany nick, aby temu zapobiec.
                        """.formatted(name, NonPremiumManager.NAME_PREFIX + member.getUser().getName())
                ).setComponents(ActionRow.of(Button.danger("end-session", "Zakończ sesję"))).queue();
                return 3;
            }
        }
        return 0;
    }

    @Override
    public int onButtonInteraction(@NotNull ButtonInteraction event) {
        if (!event.getComponentId().equals("end-session")) return 0;

        plugin.getNonPremiumManager().endSession(NonPremiumUtils.createNonPremiumUuid(event.getUser()), "Sesja zakończona przez użytkownika");

        event.reply("Pomyślnie zakończono sesję, jeśli jakaś była aktywna.").setEphemeral(true).queue();
        return 0;
    }
}
