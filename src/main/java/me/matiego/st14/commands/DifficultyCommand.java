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
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

public class DifficultyCommand implements CommandHandler.Minecraft, CommandHandler.Discord {
    public DifficultyCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("difficulty");
        if (command == null) {
            Logs.warning("The command /difficulty does not exist in the plugin.yml file and cannot be registered.");
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
        if (args.length != 0) return -1;

        if (Utils.checkIfCanNotExecuteCommandInWorld(player, "difficulty")) {
            sender.sendMessage(Utils.getComponentByString("&cNie możesz zmienić poziomu trudności w tym świecie."));
            return 3;
        }

        Difficulty difficulty = player.getWorld().getDifficulty();
        List<SingleOptionDialogInput.OptionEntry> options = List.of(
                SingleOptionDialogInput.OptionEntry.create("easy", Utils.getComponentByString("&aŁatwy"), difficulty == Difficulty.EASY),
                SingleOptionDialogInput.OptionEntry.create("normal", Utils.getComponentByString("&eNormalny"), difficulty == Difficulty.NORMAL),
                SingleOptionDialogInput.OptionEntry.create("hard", Utils.getComponentByString("&cTrudny"), difficulty == Difficulty.HARD)
        );

        DialogBase base = DialogBase.create(
                Utils.getComponentByString("&lZmień poziom trudności"),
                null,
                true,
                true,
                DialogBase.DialogAfterAction.CLOSE,
                List.of(DialogBody.plainMessage(Utils.getComponentByString("Aktualny poziom trudności: " + getDifficultyNameFormatted(difficulty)))),
                List.of(DialogInput.singleOption("difficulty", Utils.getComponentByString("Nowy poziom trudności"), options).build())
        );

        ActionButton yes = ActionButton.create(
                Utils.getComponentByString("&aOK"),
                null,
                Utils.DIALOG_BUTTON_WIDTH,
                DialogAction.customClick((view, audience) -> {
                    switch (view.getText("difficulty")) {
                        case "easy" -> change(player, Difficulty.EASY);
                        case "normal" -> change(player, Difficulty.NORMAL);
                        case "hard" -> change(player, Difficulty.HARD);
                        case null, default -> {}
                    }
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

    private void change(@NotNull Player player, @NotNull Difficulty difficulty) {
        World world = player.getWorld();
        if (world.getDifficulty() == difficulty) {
            player.sendMessage(Utils.getComponentByString("&cW tym świecie już obowiązuje ten poziom trudności"));
            return;
        }
        world.setDifficulty(difficulty);

        plugin.getCommandManager().putCooldown("difficulty", player.getUniqueId(), 30);

        String name = getDifficultyName(difficulty);
        Utils.broadcastMessage(
                player,
                Prefix.DIFFICULTY,
                "&aPomyślnie zmieniono poziom trudności na &2" + name + "&a.",
                "&aGracz &2" + player.getName() + "&a zmienił poziom trudności na &2" + name + "&a.",
                "**[" + Utils.getWorldName(world) + "]** Gracz **" + player.getName() + "** zmienił poziom trudności na **" + name + "**."
        );
    }

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("difficulty", "Sprawdź poziom trudności w wybranym świecie")
                .addOptions(
                        new OptionData(OptionType.STRING, "swiat", "nazwa świata, w którym chcesz sprawdzić poziom trudności", true, true),
                        new OptionData(OptionType.STRING, "incognito", "czy wiadomość ma być widoczna tylko dla ciebie", false)
                                .addChoice("Tak", "True")
                                .addChoice("Nie", "False")
                )
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public int onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
        boolean ephemeral = event.getOption("incognito", "False", OptionMapping::getAsString).equals("True");

        String worldName = event.getOption("swiat", OptionMapping::getAsString);
        if (worldName == null) {
            event.reply("Nie istnieje świat o takiej nazwie.").setEphemeral(ephemeral).queue();
            return 3;
        }

        World world = null;
        for (World candidate : Bukkit.getWorlds()) {
            if (worldName.equals(Utils.getWorldName(candidate))) {
                world = candidate;
                break;
            }
        }
        if (world == null) {
            event.reply("Nie istnieje świat o takiej nazwie.").setEphemeral(ephemeral).queue();
            return 3;
        }

        String difficulty = getDifficultyName(world.getDifficulty());
        event.reply("W świecie **" + Utils.getWorldName(world) + "** obowiązuje **" + difficulty + "** poziom trudności.").setEphemeral(ephemeral).queue();

        return 5;
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteraction event) {
        if (!event.getName().equals(getDiscordCommand().getName())) return;
        if (!event.getFocusedOption().getName().equals("swiat")) return;
        event.replyChoices(Bukkit.getWorlds().stream()
                .map(Utils::getWorldName)
                .filter(name -> name.toLowerCase().startsWith(event.getFocusedOption().getValue().toLowerCase()))
                .map(name -> new Command.Choice(name, name))
                .collect(Collectors.toList())
        ).queue();
    }

    private @NotNull String getDifficultyName(@NotNull Difficulty difficulty) {
        return getDifficultyNameFormatted(difficulty).substring(2).toLowerCase();
    }

    private @NotNull String getDifficultyNameFormatted(@NotNull Difficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> "&fPokojowy";
            case EASY -> "&aŁatwy";
            case NORMAL -> "&eNormalny";
            case HARD -> "&cTrudny";
        };
    }
}
