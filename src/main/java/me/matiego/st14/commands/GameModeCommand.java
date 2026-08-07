package me.matiego.st14.commands;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.objects.command.CommandHandler;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.*;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GameModeCommand implements CommandHandler.Minecraft, CommandHandler.Discord {
    public GameModeCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("gamemode");
        if (command == null) {
            Logs.warning("The command /gamemode does not exist in the plugin.yml file and cannot be registered.");
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
        if (!(sender instanceof Player player) || (player.isOp() && args.length > 0)) {
            Bukkit.dispatchCommand(sender, "minecraft:gamemode " + String.join(" ", args));
            return 1; //non-zero to prevent loop if it somehow happened
        }
        if (args.length != 0) return -1;

        if (Utils.checkIfCanNotExecuteCommandInWorld(player, "gamemode")) {
            sender.sendMessage(Utils.getComponentByString("&cNie możesz zmienić trybu gry w tym świecie."));
            return 3;
        }

        GameMode currentGameMode = player.getGameMode();
        List<SingleOptionDialogInput.OptionEntry> options = new ArrayList<>();
        for (GameMode gameMode : GameMode.values()) {
            options.add(SingleOptionDialogInput.OptionEntry.create(
                    gameMode.name(),
                    Utils.getComponentByString(getGameModeNameFormatted(gameMode)),
                    currentGameMode == gameMode
            ));
        }

        DialogBase base = DialogBase.create(
                Utils.getComponentByString("&lZmień swój tryb gry"),
                null,
                true,
                true,
                DialogBase.DialogAfterAction.CLOSE,
                List.of(DialogBody.plainMessage(Utils.getComponentByString("Aktualny tryb gry: " + getGameModeNameFormatted(currentGameMode)))),
                List.of(DialogInput.singleOption("gamemode", Utils.getComponentByString("Nowy tryb gry"), options).build())
        );

        ActionButton yes = ActionButton.create(
                Utils.getComponentByString("&aOK"),
                null,
                Utils.DIALOG_BUTTON_WIDTH,
                DialogAction.customClick((view, audience) -> {
                    GameMode gameMode;
                    try {
                        gameMode = GameMode.valueOf(view.getText("gamemode"));
                    } catch (IllegalArgumentException e) {
                        return;
                    }

                    change(player, gameMode);
                }, Utils.BUTTON_OPTIONS)
        );
        ActionButton no = ActionButton.create(
                Utils.getComponentByString("&cAnuluj"),
                null,
                Utils.DIALOG_BUTTON_WIDTH,
                null
        );

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.confirmation(yes, no))
        );
        player.showDialog(dialog);

        return 3;
    }

    private void change(@NotNull Player player, @NotNull GameMode gameMode) {
        if (Utils.checkIfCanNotExecuteCommandInWorld(player, "gamemode")) {
            player.sendMessage(Utils.getComponentByString("&cNie możesz zmienić trybu gry w tym świecie."));
            return;
        }

        if (player.getGameMode() == gameMode) {
            player.sendMessage(Utils.getComponentByString("&cJuż masz ten tryb gry!"));
            return;
        }
        player.setGameMode(gameMode);

        String name = getGameModeName(gameMode);
        Utils.broadcastMessage(
                player,
                Prefix.GAMEMODE,
                "&aPomyślnie zmieniono twój tryb gry na &2" + name + "&a.",
                "&aGracz &2" + player.getName() + "&a zmienił swój tryb gry na &2" + name + "&a w świecie &2" + Utils.getWorldName(player.getWorld()) + "&a.",
                "**[" + Utils.getWorldName(player.getWorld()) + "]** Gracz **" + player.getName() + "** zmienił swój tryb gry na **" + name + "**."
        );
    }

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("gamemode", "Sprawdź tryb gry gracza")
                .addOptions(
                        new OptionData(OptionType.STRING, "gracz", "nick gracza, którego tryb gry chcesz sprawdzić", true, true),
                        new OptionData(OptionType.STRING, "incognito", "czy wiadomość ma być widoczna tylko dla ciebie", false)
                                .addChoice("Tak", "True")
                                .addChoice("Nie", "False")
                )
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public int onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
        boolean ephemeral = event.getOption("incognito", "False", OptionMapping::getAsString).equals("True");

        String playerName = event.getOption("gracz", OptionMapping::getAsString);
        if (playerName == null) return 10;

        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null || plugin.getIncognitoManager().isIncognito(player.getUniqueId())) {
            event.reply("Ten gracz nie jest online").setEphemeral(ephemeral).queue();
            return 3;
        }

        String gameMode = getGameModeName(player.getGameMode());
        event.reply("Tryb gry gracza **" + player.getName() + "** to **" + gameMode + "**.").setEphemeral(ephemeral).queue();

        return 5;
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteraction event) {
        if (!event.getName().equals(getDiscordCommand().getName())) return;
        if (!event.getFocusedOption().getName().equals("gracz")) return;
        event.replyChoices(Bukkit.getOnlinePlayers().stream()
                .filter(p -> !plugin.getIncognitoManager().isIncognito(p.getUniqueId()))
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(event.getFocusedOption().getValue().toLowerCase()))
                .map(name -> new Command.Choice(name, name))
                .collect(Collectors.toList())
        ).queue();
    }

    private @NotNull String getGameModeName(@NotNull GameMode gameMode) {
        return getGameModeNameFormatted(gameMode).substring(2).toLowerCase();
    }

    private @NotNull String getGameModeNameFormatted(@NotNull GameMode gameMode) {
        return switch (gameMode) {
            case SPECTATOR -> "&bObserwatora";
            case CREATIVE -> "&6Kreatywny";
            case SURVIVAL -> "&aPrzetrwania";
            case ADVENTURE -> "&dPrzygodowy";
        };
    }
}
