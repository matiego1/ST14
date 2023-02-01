package me.matiego.st14.commands.discord;

import me.matiego.counting.utils.Utils;
import me.matiego.st14.Main;
import me.matiego.st14.utils.CommandHandler;
import me.matiego.st14.utils.Logs;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
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
        event.deferReply(true).queue();
        InteractionHook hook = event.getHook();

        Guild guild = event.getGuild();
        if (guild == null || plugin.getConfig().getLong("discord.guild-id") != guild.getIdLong()) {
            hook.sendMessage("This interaction can only be used in a guild.").queue();
            return 0;
        }

        Member member = event.getTargetMember();
        if (member == null) {
            hook.sendMessage("This interaction can only be used in a guild.").queue();
            return 0;
        }

        Role role = guild.getRoleById(plugin.getConfig().getLong("discord.role-ids.verified"));
        if (role == null) {
            Logs.warning("A verified-role-id in the config file is not correct.");
            hook.sendMessage("The `verified` role could not be loaded.").queue();
            return 0;
        }

        if (member.getRoles().contains(role)) {
            hook.sendMessage("This user has already been verified.").queue();
            return 0;
        }

        guild.addRoleToMember(member, role).queue(
                success -> {
                    Utils.sendPrivateMessage(
                            member.getUser(),
                            String.join("\n", plugin.getConfig().getStringList("discord.welcome-message"))
                                    .replace("{mention}", member.getAsMention())
                    );
                    hook.sendMessage("Success!").queue();
                },
                failure -> hook.sendMessage("An error occurred.").queue()
        );
        return 0;
    }
}
