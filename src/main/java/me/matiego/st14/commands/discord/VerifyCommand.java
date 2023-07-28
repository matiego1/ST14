package me.matiego.st14.commands.discord;

import me.matiego.st14.Main;
import me.matiego.st14.objects.CommandHandler;
import me.matiego.st14.utils.DiscordUtils;
import me.matiego.st14.Logs;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.context.UserContextInteraction;
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

        guild.addRoleToMember(member, role).queue(
                success -> {
                    String welcomeMessage = String.join("\n", plugin.getConfig().getStringList("discord.welcome-message"))
                            .replace("{mention}", member.getAsMention());
                    DiscordUtils.sendPrivateMessage(member.getUser(), DiscordUtils.checkLength(welcomeMessage, Message.MAX_CONTENT_LENGTH));
                    hook.sendMessage(DiscordUtils.checkLength("Sukces!\nWysłana wiadomość powitalna:\n" + welcomeMessage, Message.MAX_CONTENT_LENGTH)).queue();

                    Logs.info(sender + " zweryfikował " + DiscordUtils.getAsTag(member) + " na serwerze Discord.");
                },
                failure -> hook.sendMessage("Napotkano niespodziewany błąd.").queue()
        );
        return 0;
    }
}
