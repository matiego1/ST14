package me.matiego.st14.commands;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.managers.AccountsManager;
import me.matiego.st14.managers.IncognitoManager;
import me.matiego.st14.objects.command.CommandHandler;
import me.matiego.st14.utils.DiscordUtils;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class IncognitoCommand implements CommandHandler.Minecraft, CommandHandler.Discord, Listener {
    public IncognitoCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("incognito");
        if (command == null) {
            Logs.warning("The command /incognito does not exist in the plugin.yml file and cannot be registered.");
        }
    }

    private final Main plugin;
    private final PluginCommand command;
    private final Material BLOCK_ON = Material.LIME_WOOL;
    private final Material BLOCK_OFF = Material.RED_WOOL;

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("incognito", "Zarządzaj statusem incognito twojego konta minecraft").setContexts(InteractionContextType.GUILD);
    }

    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public int onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Utils.getComponentByString(Prefix.INCOGNITO + "&cTej komendy może użyć tylko gracz."));
            return 0;
        }
        if (args.length != 0) return -1;

        IncognitoManager manager = plugin.getIncognitoManager();

        Utils.async(() -> {
            List<DialogInput> inputs = new ArrayList<>();
            boolean incognito = manager.isIncognito(player.getUniqueId());
            inputs.add(DialogInput.bool(
                    "incognito",
                    Utils.getComponentByString("&7- &fTryb incognito"),
                    incognito,
                    "true", "false"
            ));
            inputs.add(DialogInput.bool(
                    "kicking",
                    Utils.getComponentByString("&7- &fWyrzucanie z serwera, gdy dołącza niezaufany gracz"),
                    manager.isKickingEnabled(player.getUniqueId()),
                    "true", "false"
            ));
            inputs.add(DialogInput.text(
                    "trust",
                    Utils.getComponentByString("Dodaj nowego zaufanego gracza: ")
            ).maxLength(16).build());

            DialogBase base = DialogBase.create(
                    Utils.getComponentByString(Prefix.INCOGNITO + "Status incognito"),
                    null,
                    true,
                    true,
                    DialogBase.DialogAfterAction.CLOSE,
                    List.of(DialogBody.plainMessage(Utils.getComponentByString("Nie dołączając do gry nie można zobaczyć, że gracz incognito jest online."))),
                    inputs
            );

            List<ActionButton> actions = new ArrayList<>();
            actions.add(ActionButton.create(
                    Utils.getComponentByString("&7Zaufaj nowemu graczowi!"),
                    null,
                    Utils.DIALOG_BUTTON_WIDTH,
                    DialogAction.customClick((view, audience) -> Utils.async(() -> addTrustedPlayer(player, view.getText("trust"))), Utils.BUTTON_OPTIONS)
            ));
            List<UUID> trustedPlayers = manager.getTrustedPlayers(player.getUniqueId());
            for (UUID trustedPlayer : trustedPlayers) {
                Component head = MiniMessage.miniMessage().deserialize("<head:" + trustedPlayer + ">");
                actions.add(ActionButton.create(
                        head.append(Utils.getComponentByString(" " + plugin.getOfflinePlayersManager().getEffectiveNameById(trustedPlayer))),
                        Utils.getComponentByString("&7Ufasz temu graczowi!\n\n&cKliknij, aby przestać mu ufać"),
                        Utils.DIALOG_BUTTON_WIDTH,
                        DialogAction.customClick((view, audience) -> Utils.async(() -> removeTrustedPlayer(player, trustedPlayer)), Utils.BUTTON_OPTIONS)
                ));
            }

            ActionButton exitAction = ActionButton.create(
                    Utils.getComponentByString("Gotowe"),
                    null,
                    Utils.DIALOG_BUTTON_WIDTH,
                    DialogAction.customClick((view, audience) -> Utils.async(() -> acceptChanges(player, view.getBoolean("incognito"), view.getBoolean("kicking"))), Utils.BUTTON_OPTIONS)
            );
            Dialog dialog = Dialog.create(builder -> builder.empty()
                    .base(base)
                    .type(DialogType.multiAction(actions, exitAction, 3))
            );
            Utils.sync(() -> player.showDialog(dialog));
        });
        return 5;
    }

    private void acceptChanges(@NotNull Player player, @Nullable Boolean incognito, @Nullable Boolean kicking) {
        IncognitoManager manager = plugin.getIncognitoManager();
        UUID uuid = player.getUniqueId();
        if (incognito != null) manager.setIncognito(uuid, incognito);
        if (kicking != null) manager.setKickingEnabled(uuid, kicking);
    }

    private void addTrustedPlayer(@NotNull Player player, @Nullable String name) {
        if (name == null) return;

        UUID trustedUuid = plugin.getOfflinePlayersManager().getIdByName(name);
        if (trustedUuid == null) {
            player.sendMessage(Utils.getComponentByString(Prefix.INCOGNITO + "&cTen gracz nie istnieje!"));
            return;
        }

        UUID uuid = player.getUniqueId();
        if (trustedUuid.equals(uuid)) {
            player.sendMessage(Utils.getComponentByString(Prefix.INCOGNITO + "&cTo twój nick!"));
            return;
        }

        plugin.getIncognitoManager().addTrustedPlayer(uuid, trustedUuid);
        player.sendMessage(Utils.getComponentByString(Prefix.INCOGNITO + "Pomyślnie zaufano nowemu graczowi"));
    }

    private void removeTrustedPlayer(@NotNull Player player, @NotNull UUID trustedPlayer) {
        if (plugin.getIncognitoManager().removeTrustedPlayer(player.getUniqueId(), trustedPlayer)) {
            player.sendMessage(Utils.getComponentByString(Prefix.INCOGNITO + "Przestałeś ufać temu graczowi!"));
        } else {
            player.sendMessage(Utils.getComponentByString(Prefix.INCOGNITO + "&cNapotkano niespodziewany błąd. Spróbuj później."));
        }
    }

    @Override
    public int onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
        event.deferReply(true).queue();
        InteractionHook hook = event.getHook();
        AccountsManager accounts = plugin.getAccountsManager();
        IncognitoManager manager = plugin.getIncognitoManager();
        User user = event.getUser();
        Utils.async(() -> {
            if (!accounts.isLinked(user)) {
                hook.sendMessage("Twoje konto nie jest jeszcze połączone z kontem minecraft! Aby je połączyć, użyj komendy `/accounts` w grze.").queue();
                return;
            }
            UUID uuid = accounts.getPlayerByUser(user);
            if (uuid == null) {
                hook.sendMessage("Napotkano niespodziewany błąd. Spróbuj później.").queue();
                return;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                hook.sendMessage("Jesteś online! Zmień swój status incognito komendą `/incognito` w grze.").queue();
                return;
            }
            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("__**Ustawienia**__");
            eb.setColor(Color.LIGHT_GRAY);
            eb.setDescription("**Gracz:** `" + plugin.getOfflinePlayersManager().getEffectiveNameById(uuid) + "`");
            eb.addField("**Status incognito:**", manager.isIncognito(uuid) ? "`Włączone` :green_circle:" : "`Wyłączone` :red_circle:", false);
            eb.setFooter(DiscordUtils.getName(user, event.getMember()), DiscordUtils.getAvatar(user, event.getMember()));
            eb.setTimestamp(Instant.now());
            hook.sendMessageEmbeds(eb.build()).setComponents(ActionRow.of(Button.secondary("change-inc-status", "Zmień status incognito"))).queue();
        });
        return 5;
    }

    @Override
    public int onButtonInteraction(@NotNull ButtonInteraction event) {
        if (!event.getComponentId().equals("change-inc-status")) return 0;

        event.deferReply(true).queue();
        AccountsManager accounts = plugin.getAccountsManager();
        IncognitoManager manager = plugin.getIncognitoManager();
        User user = event.getUser();
        InteractionHook hook = event.getHook();
        event.editButton(event.getButton().asDisabled()).queue();

        Utils.async(() -> {
            if (!accounts.isLinked(user)) {
                hook.sendMessage("Twoje konto nie jest jeszcze połączone z kontem minecraft! Aby je połączyć, użyj komendy `/accounts` w grze.").queue();
                return;
            }
            UUID uuid = accounts.getPlayerByUser(user);
            if (uuid == null) {
                hook.sendMessage("Napotkano niespodziewany błąd. Spróbuj później.").queue();
                return;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                hook.sendMessage("Jesteś online! Zmień swój status incognito komendą `/incognito` w grze.").queue();
                return;
            }
            manager.setIncognito(uuid, !manager.isIncognito(uuid));
            hook.sendMessage("Pomyślnie zmieniono twój status incognito! **Aktualny status incognito:** " + (manager.isIncognito(uuid) ? "`Włączone` :green_circle:" : "`Wyłączone` :red_circle:")).queue();
        });
        return 0;
    }
}
