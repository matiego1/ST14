package me.matiego.st14.commands.discord;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.objects.CommandHandler;
import me.matiego.st14.utils.DiscordUtils;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.context.UserContextInteraction;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.jetbrains.annotations.NotNull;

public class VerifyCommand implements CommandHandler.Discord {
    public VerifyCommand(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.user("verify")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
                .setGuildOnly(true);
    }

    @Override
    public int onUserContextInteraction(@NotNull UserContextInteraction event) {
        if (!event.getName().equals(getDiscordCommand().getName())) return -1;

        event.deferReply(true).queue();
        InteractionHook hook = event.getHook();
        String sender = DiscordUtils.getAsTag(event.getUser());

        Guild guild = event.getGuild();
        if (guild == null || plugin.getConfig().getLong("discord.guild-id") != guild.getIdLong()) {
            hook.sendMessage("Nie możesz użyć tej funkcji w tym serwerze.").queue();
            return 0;
        }

        Member member = event.getTargetMember();
        if (member == null) {
            hook.sendMessage("Nie możesz użyć tej funkcji w tym serwerze.").queue();
            return 0;
        }

        Role role = guild.getRoleById(plugin.getConfig().getLong("discord.role-ids.verified"));
        if (role == null) {
            Logs.warning("A verified-role-id in the config file is not correct.");
            hook.sendMessage("Napotkano błąd przy wczytywaniu roli").queue();
            return 0;
        }

        if (member.getRoles().contains(role)) {
            hook.sendMessage("Ten użytkownik już jest zweryfikowany.").queue();
            return 0;
        }

        member.getUser().openPrivateChannel().queue(chn -> chn.sendMessage("Kliknij poniższy przycisk, aby dokończyć proces weryfikacji.")
                .addActionRow(
                        Button.success("verify-account", "Akceptuję regulamin")
                )
                .queue(
                        success -> {
                            hook.sendMessage("Pomyślnie wysłano prywatną wiadomość do użytkownika. Oczekiwanie na odpowiedź...").queue();
                            Logs.info(sender + " rozpoczął proces weryfikacji nowego członka " + DiscordUtils.getAsTag(member));
                        },
                        failure -> {
                            if (failure instanceof ErrorResponseException e && e.getErrorResponse() == ErrorResponse.CANNOT_SEND_TO_USER) {
                                Logs.warning("User " + DiscordUtils.getAsTag(member) + " doesn't allow private messages.");
                                hook.sendMessage("Ten użytkownik nie zezwala na prywatne wiadomości od bota.").queue();
                            } else {
                                Logs.error("An error occurred while sending a private message.", failure);
                                hook.sendMessage("Napotkano niespodziewany błąd. Spróbuj ponownie.").queue();
                            }
                        }
                ));
        return 0;
    }

    @Override
    public int onButtonInteraction(@NotNull ButtonInteraction event) {
        if (!event.getComponentId().equals("verify-account")) return 0;

        event.deferReply(true).queue();
        InteractionHook hook = event.getHook();

        Guild guild = event.getJDA().getGuildById(plugin.getConfig().getLong("discord.guild-id"));
        if (guild == null) {
            hook.sendMessage("Napotkano niespodziewany błąd. Spróbuj ponownie.").queue();
            Logs.warning("A guild id in the config file is not correct.");
            return 0;
        }

        Utils.async(() -> {
            event.editButton(event.getButton().asDisabled()).queue();

            Member member = DiscordUtils.retrieveMember(guild, event.getUser());
            if (member == null) {
                hook.sendMessage("Wygląda na to, że już wyszedłeś z serwera.").queue();
                event.editButton(event.getButton().asDisabled()).queue();
                return;
            }

            Role role = guild.getRoleById(plugin.getConfig().getLong("discord.role-ids.verified"));
            if (role == null) {
                Logs.warning("A verified-role-id in the config file is not correct.");
                hook.sendMessage("Napotkano błąd przy wczytywaniu roli. Poproś administratora o ponowną weryfikację.").queue();
                return;
            }

            if (member.getRoles().contains(role)) {
                hook.sendMessage("Już jesteś zweryfikowany!").queue();
                return;
            }

            guild.addRoleToMember(member, role).queue(
                    success -> {
                        String welcomeMessage = String.join("\n", plugin.getConfig().getStringList("discord.welcome-message"))
                                .replace("{mention}", member.getAsMention());
                        event.getChannel().sendMessage(DiscordUtils.checkLength(welcomeMessage, Message.MAX_CONTENT_LENGTH)).queue();
                        hook.sendMessage("Sukces!").queue();

                        Logs.info(DiscordUtils.getAsTag(member) + " dokończył proces weryfikacji na serwerze Discord.");
                    },
                    failure -> {
                        hook.sendMessage("Napotkano niespodziewany błąd. Poproś administratora o ponowną weryfikację.").queue();
                        Logs.warning("Napotkano błąd przy weryfikowaniu użytkownika " + DiscordUtils.getAsTag(member) + "! Proces weryfikacji musi być rozpoczęty od nowa.");
                    }
            );
        });
        return 0;
    }
}
