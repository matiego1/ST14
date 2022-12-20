package me.matiego.st14.commands;

import me.matiego.st14.AccountsManager;
import me.matiego.st14.Main;
import me.matiego.st14.utils.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AccountsCommand implements CommandHandler.Discord, CommandHandler.Minecraft {
    private final Main plugin;
    private final PluginCommand command;
    public AccountsCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = Main.getInstance().getCommand("accounts");
        if (command == null) {
            Logs.warning("The command /accounts does not exist in the plugin.yml file and cannot be registered.");
        }
    }

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("accounts", "Połącz twoje konta minecraft i Discord")
                .addOptions(
                        new OptionData(OptionType.STRING, "code", "twój kod weryfikacyjny", false)
                                .setRequiredLength(6, 6)
                );
    }

    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
        String code = event.getOption("code", OptionMapping::getAsString);
        AccountsManager manager = plugin.getAccountsManager();
        User user = event.getUser();
        event.deferReply(true).queue();
        InteractionHook hook = event.getHook();

        Utils.async(() -> {
            if (code != null) {
                if (manager.isLinked(user)) {
                    hook.sendMessage("Twoje konto już jest połączone z kontem minecraft.").queue();
                    return;
                }
                UUID uuid = manager.checkVerificationCode(code);
                if (uuid == null) {
                    hook.sendMessage("Twój kod jest niepoprawny. Aby wygenerować nowy, dołącz do serwera.").queue();
                    return;
                }
                if (manager.isLinked(uuid)) {
                    hook.sendMessage("To konto minecraft już jest połączone z jakimś kontem Discord.").queue();
                    return;
                }
                if (manager.link(uuid, user)) {
                    hook.sendMessage("Pomyślnie połączone twoje konta!").queue();
                    MessageEmbed embed = getEmbed(user);
                    if (embed == null) {
                        DiscordUtils.sendPrivateMessage(user, "Twoje konto zostało połączone z kontem minecraft! Niestety z powodu niespodziewanego błędu nie możemy dostarczyć Ci więcej informacji.");
                        return;
                    }
                    user.openPrivateChannel().queue(chn -> chn.sendMessageEmbeds(embed)
                            .addActionRow(
                                    Button.danger("unlink-accounts", "Rozłącz konta")
                                            .withEmoji(Emoji.fromUnicode("U+1F4A3"))
                            )
                            .queue(
                                    success -> {},
                                    failure -> {
                                        if (failure instanceof ErrorResponseException e && e.getErrorCode() == 50007) {
                                            Logs.warning("User " + user.getAsTag() + " doesn't allow private messages.");
                                        } else {
                                            Logs.error("An error occurred while sending a private message.", failure);
                                        }
                                    }
                            ));
                } else {
                    hook.sendMessage("Napotkano niespodziewany błąd. Spróbuj ponownie.").queue();
                }
                return;
            }

            MessageEmbed embed = getEmbed(user);
            if (embed == null) {
                hook.sendMessage("Twoje konto jest połączone z kontem minecraft! Niestety z powodu niespodziewanego błędu nie możemy dostarczyć Ci więcej informacji. Spróbuj później.").queue();
                return;
            }
            hook.sendMessageEmbeds(embed)
                    .addActionRow(
                            Button.danger("unlink-accounts", "Rozłącz konta")
                                    .withEmoji(Emoji.fromUnicode("U+1F4A3"))
                    )
                    .queue();
        });
    }

    private @Nullable MessageEmbed getEmbed(@NotNull UserSnowflake id) {
        UUID uuid = plugin.getAccountsManager().getPlayerByUser(id);
        if (uuid == null) return null;
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Twoje konto minecraft:");
        eb.setDescription("**Nick:** `" + plugin.getOfflinePlayers().getEffectiveNameById(uuid) + "\n**UUID:** `" + uuid);
        eb.setColor(Color.BLUE);
        eb.setTimestamp(Instant.now());
        eb.setThumbnail(Utils.getSkinUrl(uuid));
        return eb.build();
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteraction event) {
        if (!event.getComponentId().equals("unlink-accounts")) return;

        event.deferReply(true).queue();
        User user = event.getUser();
        AccountsManager manager = plugin.getAccountsManager();
        InteractionHook hook = event.getHook();

        Utils.async(() -> {
            event.editButton(event.getButton().asDisabled()).queue();
            if (!manager.isLinked(user)) {
                hook.sendMessage("Twoje konto nie jest jeszcze połączone z kontem minecraft! Aby je połączyć, dołącz do serwera").queue();
                return;
            }
            UUID uuid = manager.getPlayerByUser(user);
            if (uuid == null) {
                hook.sendMessage("Napotkano niespodziewany błąd. Spróbuj później.").queue();
                return;
            }
            if (manager.unlink(uuid)) {
                hook.sendMessage("Pomyślnie rozłączono twoje konta!").queue();
            } else {
                hook.sendMessage("Napotkano niespodziewany błąd. Spróbuj później.").queue();
            }
        });
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Utils.getComponentByString(Prefixes.DISCORD + "&cTej komendy może użyć tylko gracz."));
            return true;
        }

        UUID uuid = player.getUniqueId();
        AccountsManager manager = plugin.getAccountsManager();

        if (args.length == 1 && args[0].equalsIgnoreCase("unlink")) {
            Utils.async(() -> {
                if (!manager.isLinked(uuid)) {
                    player.sendMessage(Utils.getComponentByString(Prefixes.DISCORD + "Twoje konto nie jest połączone z kontem Discord. Aby je połączyć, poproś administratora."));
                    return;
                }
                if (!manager.unlink(uuid)) {
                    player.sendMessage(Utils.getComponentByString(Prefixes.DISCORD + "Napotkano niespodziewany błąd. Spróbuj później."));
                }
            });
            return true;
        }
        if (args.length != 0) return false;

        Utils.async(() -> {
            if (!manager.isLinked(uuid)) {
                player.sendMessage(Utils.getComponentByString(Prefixes.DISCORD + "Twoje konto nie jest połączone z kontem Discord. Aby je połączyć, poproś administratora."));
                return;
            }
            UserSnowflake id = manager.getUserByPlayer(uuid);
            if (id == null) {
                player.sendMessage(Utils.getComponentByString(Prefixes.DISCORD + "Napotkano niespodziewany błąd. Spróbuj później."));
                return;
            }
            JDA jda = plugin.getJda();
            if (jda == null) {
                player.sendMessage(Utils.getComponentByString(Prefixes.DISCORD + "Bot jest offline. Spróbuj później."));
                return;
            }
            jda.retrieveUserById(id.getId()).queue(user -> {
                if (user == null) {
                    player.sendMessage(Utils.getComponentByString(Prefixes.DISCORD + "Napotkano niespodziewany błąd. Spróbuj później."));
                    return;
                }
                player.sendMessage(Utils.getComponentByString(Prefixes.DISCORD + "Twoje konto jest połączone z kontem Discord.\nNick: &9" + user.getAsTag() + "&b; ID: &9" + user.getId()));
            });
        });
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("unlink");
        }
        return new ArrayList<>();
    }
}
