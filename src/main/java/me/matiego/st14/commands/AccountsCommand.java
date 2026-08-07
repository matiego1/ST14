package me.matiego.st14.commands;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.managers.AccountsManager;
import me.matiego.st14.objects.Pair;
import me.matiego.st14.objects.command.CommandHandler;
import me.matiego.st14.utils.DiscordUtils;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
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
    public AccountsCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("accounts");
        if (command == null) {
            Logs.warning("The command /accounts does not exist in the plugin.yml file and cannot be registered.");
        }
    }

    private final Main plugin;
    private final PluginCommand command;

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("accounts", "Połącz twoje konta minecraft i Discord")
                .addOptions(
                        new OptionData(OptionType.STRING, "code", "twój kod weryfikacyjny", false)
                                .setRequiredLength(6, 6)
                )
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public int onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
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
                Pair<UUID, String> pair = manager.checkVerificationCode(code.toUpperCase());
                if (pair == null) {
                    hook.sendMessage("Twój kod jest niepoprawny. Aby wygenerować nowy, dołącz do serwera.").queue();
                    return;
                }
                UUID uuid = pair.getFirst();
                if (manager.isLinked(uuid)) {
                    hook.sendMessage("To konto minecraft już jest połączone z jakimś kontem Discord.").queue();
                    return;
                }
                if (manager.link(uuid, user)) {
                    hook.sendMessage("Pomyślnie połączono twoje konta!").queue();
                    MessageEmbed embed = getEmbed(user, pair.getSecond());
                    if (embed == null) {
                        DiscordUtils.sendPrivateMessage(user, "Twoje konto zostało połączone z kontem minecraft! Niestety z powodu niespodziewanego błędu nie możemy dostarczyć Ci więcej informacji.");
                        return;
                    }
                    DiscordUtils.sendPrivateMessage(user, embed, action -> action
                            .setComponents(ActionRow.of(
                                    Button.danger("unlink-accounts", "Rozłącz konta")
                                            .withEmoji(Emoji.fromUnicode("U+1F4A3"))
                            )), result -> {
                    });
                } else {
                    hook.sendMessage("Napotkano niespodziewany błąd. Spróbuj ponownie.").queue();
                }
                return;
            }

            if (!manager.isLinked(user)) {
                hook.sendMessage("Twoje konto nie jest jeszcze połączone z kontem minecraft! Aby je połączyć, użyj komendy `/accounts` w grze.").queue();
                return;
            }
            MessageEmbed embed = getEmbed(user, "[Dołącz do gry, aby wyświetlić]");
            if (embed == null) {
                hook.sendMessage("Twoje konto jest połączone z kontem minecraft! Niestety z powodu niespodziewanego błędu nie możemy dostarczyć Ci więcej informacji. Spróbuj później.").queue();
                return;
            }
            hook.sendMessageEmbeds(embed)
                    .setComponents(ActionRow.of(
                            Button.danger("unlink-accounts", "Rozłącz konta")
                                    .withEmoji(Emoji.fromUnicode("U+1F4A3"))
                    ))
                    .queue();
        });
        return 3;
    }

    private @Nullable MessageEmbed getEmbed(@NotNull UserSnowflake id, @NotNull String playerNameFallback) {
        UUID uuid = plugin.getAccountsManager().getPlayerByUser(id);
        if (uuid == null) return null;
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Twoje konto minecraft:");
        String playerName = plugin.getOfflinePlayersManager().getNameById(uuid);
        eb.setDescription("**Nick:** `" + (playerName == null ? playerNameFallback : playerName) + "`\n**UUID:** `" + uuid + "`");
        eb.setColor(Color.BLUE);
        eb.setTimestamp(Instant.now());
        eb.setThumbnail(Utils.getSkinUrl(uuid));
        return eb.build();
    }

    @Override
    public int onButtonInteraction(@NotNull ButtonInteraction event) {
        if (!event.getComponentId().equals("unlink-accounts")) return 0;

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
        return 3;
    }

    @Override
    public int onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Utils.getComponentByString(Prefix.DISCORD + "&cTej komendy może użyć tylko gracz."));
            return 0;
        }
        if (args.length != 0) return -1;
        AccountsManager manager = plugin.getAccountsManager();
        UUID uuid = player.getUniqueId();

        Utils.async(() -> {
            List<DialogBody> body = new ArrayList<>();
            JDA jda = plugin.getJda();

            if (jda == null) {
                body.add(DialogBody.plainMessage(Utils.getComponentByString("&bBot Discord: &cOFFLINE")));
            } else {
                body.add(DialogBody.plainMessage(Utils.getComponentByString("&bBot Discord: &aONLINE")));
            }

            body.add(DialogBody.plainMessage(Utils.getComponentByString("&bZaproszenie na serwer Discord:\n&a" + plugin.getConfig().getString("discord.invite-link", "&cBRAK"))));

            boolean link;
            if (manager.isLinked(uuid)) {
                link = false;
                UserSnowflake id = manager.getUserByPlayer(uuid);
                if (id == null) {
                    body.add(DialogBody.plainMessage(Utils.getComponentByString("&bTwoje konto Discord:\n&aPołączone!\n\n&cNapotkano błąd przy wczytywaniu dodatkowych informacji.")));
                } else {
                    String user = jda == null ? "&cBRAK" : DiscordUtils.getAsTag(jda.retrieveUserById(id.getId()).complete());
                    body.add(DialogBody.plainMessage(Utils.getComponentByString("&bTwoje konto Discord:\n&aPołączone!\n\n&aNick: " + user + "\nID: " + id.getId())));
                }
            } else {
                link = true;
                body.add(DialogBody.plainMessage(Utils.getComponentByString("&bTwoje konto Discord:\n&cNie połączone :(")));
            }

            DialogBase base = DialogBase.create(
                    Utils.getComponentByString(Prefix.DISCORD + "Serwer Discord"),
                    null,
                    true,
                    true,
                    DialogBase.DialogAfterAction.CLOSE,
                    body,
                    List.of()
            );


            ActionButton linkButton = ActionButton.create(
                    Utils.getComponentByString(link ? "&aPołącz konto Discord" : "&cRozłącz konto Discord"),
                    null,
                    Utils.DIALOG_BUTTON_WIDTH,
                    DialogAction.customClick((view, audience) -> change(player), Utils.BUTTON_OPTIONS)
            );
            ActionButton close = ActionButton.create(
                    Utils.getComponentByString("Gotowe"),
                    null,
                    Utils.DIALOG_BUTTON_WIDTH,
                    null
            );

            Dialog dialog = Dialog.create(builder -> builder.empty()
                    .base(base)
                    .type(DialogType.confirmation(linkButton, close))
            );
            player.showDialog(dialog);
        });
        return 5;
    }

    private void change(@NotNull Player player) {
        AccountsManager manager = plugin.getAccountsManager();
        UUID uuid = player.getUniqueId();

        Utils.async(() -> {
            if (manager.isLinked(uuid)) {
                UserSnowflake id = manager.getUserByPlayer(uuid);
                boolean success = manager.unlink(uuid);
                player.sendMessage(Utils.getComponentByString(Prefix.DISCORD + (success ?
                        "Pomyślnie rozłączono twoje konto Discord" :
                        "Napotkano niespodziewany błąd. Spróbuj później"
                )));

                JDA jda = plugin.getJda();
                if (jda == null || id == null || !success) return;

                jda.retrieveUserById(id.getId()).queue(
                        user -> DiscordUtils.sendPrivateMessage(user, "Twoje konto zostało rozłączone z kontem minecraft!"),
                        failure -> {}
                );
            } else {
                String code = plugin.getAccountsManager().getNewVerificationCode(uuid, player.getName());
                player.sendMessage(Utils.getComponentByString(
                        Prefix.DISCORD + "=================================\n" +
                                Prefix.DISCORD + "Aby dokończyć proces łączenia kont,\n" +
                                Prefix.DISCORD + "użyj komendy &a/accounts&b\n" +
                                Prefix.DISCORD + "na Discord z kodem: &a" + code + "&b.\n" +
                                Prefix.DISCORD + "Kod jest ważny przez 5 minut.\n" +
                                Prefix.DISCORD + "================================="
                ));
            }
        });
    }
}
