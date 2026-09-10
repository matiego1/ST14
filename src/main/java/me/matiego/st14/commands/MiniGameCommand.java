package me.matiego.st14.commands;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.managers.MiniGamesManager;
import me.matiego.st14.objects.command.CommandHandler;
import me.matiego.st14.objects.minigames.MiniGame;
import me.matiego.st14.objects.minigames.MiniGameType;
import me.matiego.st14.utils.MiniGamesUtils;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class MiniGameCommand implements CommandHandler.Minecraft, CommandHandler.Discord {
    public MiniGameCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("minigame");
        if (command == null) {
            Logs.warning("The command /minigame does not exist in the plugin.yml file and cannot be registered.");
        }
    }
    private final Main plugin;
    private final PluginCommand command;
    private final HashMap<UUID, MiniGameType> chosenMiniGame = new HashMap<>();

    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public int onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        MiniGamesManager manager = plugin.getMiniGamesManager();

        if (args.length == 1) {
            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "damage" -> {
                    sender.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Już wkrótce!"));
                    return 1;
                }
                case "vote-stop" -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Tej komendy może użyć tylko gracz"));
                        return 0;
                    }

                    MiniGame miniGame = manager.getActiveMiniGame();
                    if (miniGame == null) {
                        player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Żadna minigra nie jest rozpoczęta."));
                        return 3;
                    }

                    if (miniGame.getPlayerStatus(player) != MiniGame.PlayerStatus.IN_MINI_GAME) {
                        player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Nie bierzesz udziału w toczącej się minigrze."));
                        return 3;
                    }

                    miniGame.voteToStop(player);
                    return 3;
                }
            }

            if (!hasPermissionToSubCommand(sender, subCommand)) return -1;
            switch (subCommand) {
                case "stop" -> {
                    if (manager.getActiveMiniGame() == null) {
                        sender.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Żadna minigra nie jest rozpoczęta."));
                        return 0;
                    }
                    sender.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Zatrzymywanie..."));
                    manager.stopMiniGame();
                    return 1;
                }
                case "editor" -> {
                    if (!(sender instanceof Player player)) return -1;

                    if (!MiniGamesUtils.isInAnyMiniGameWorld(player)) {
                        player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Nie możesz użyć tej komendy w tym świecie."));
                        return 3;
                    }

                    manager.setEditorMode(player, !manager.isInEditorMode(player));
                    return 5;
                }
            }
            return -1;
        }

        if (args.length != 0) return -1;

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Tej komendy może użyć tylko gracz"));
            return 0;
        }

        if (manager.isInEditorMode(player)) {
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Nie możesz użyć tej komendy, jesteś w trybie edytora."));
            return 3;
        }

        if (!MiniGamesUtils.isInAnyMiniGameWorld(player)) {
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Nie możesz użyć tej komendy w tym świecie."));
            return 3;
        }

        if (manager.getActiveMiniGame() != null) {
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Jakaś minigra jest już rozpoczęta."));
            return 3;
        }

        DialogBase base = DialogBase.create(
                Utils.getComponentByString(Prefix.MINI_GAMES + "Wybierz minigrę"),
                null,
                true,
                true,
                DialogBase.DialogAfterAction.CLOSE,
                List.of(),
                List.of()
        );

        List<ActionButton> actions = new ArrayList<>();
        for (MiniGameType type : MiniGameType.values()) {
            Component tooltip;
            if (type.isMiniGameEnabled()) {
                tooltip = Utils.getComponentByString(
                        "&aKliknij, aby rozpocząć!\n" +
                        "&eCzas minigry: &d" + Utils.parseMillisToString(type.getGameTimeInSeconds() * 1000L, false)
                );
            } else {
                tooltip = Utils.getComponentByString(
                        "&cTa minigra jest wyłączona :(\n" +
                        "&eCzas minigry: &d" + Utils.parseMillisToString(type.getGameTimeInSeconds() * 1000L, false)
                );
            }

            Component label = type.getIcon().append(Utils.getComponentByString("&f " + type.getName()));
            actions.add(ActionButton.create(
                    label,
                    tooltip,
                    120,
                    DialogAction.customClick((view, audience) -> handleMiniGameChoice(type, player), Utils.BUTTON_OPTIONS)
            ));
        }

        ActionButton exitAction = ActionButton.create(Utils.getComponentByString("Anuluj"), null, 150, null);

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(actions, exitAction, 3))
        );
        player.showDialog(dialog);
        return 5;
    }

    private boolean hasPermissionToSubCommand(@NotNull CommandSender sender, @NotNull String subCommand) {
        if (sender instanceof Player player) {
            if (player.isOp()) return true;
            if (player.hasPermission("st14.minigame.admin")) return true;
            return player.hasPermission("st14.minigame." + subCommand);
        }
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length != 1) return new ArrayList<>();
        List<String> completions = new ArrayList<>();
        if (sender instanceof Player) completions.add("vote-stop");
        if (sender instanceof Player) completions.add("damage");
        if (hasPermissionToSubCommand(sender, "stop")) completions.add("stop");
        if (hasPermissionToSubCommand(sender, "editor")) completions.add("editor");
        return completions;
    }

    private void handleMiniGameChoice(@NotNull MiniGameType type, @NotNull Player player) {
        if (!MiniGamesUtils.isInAnyMiniGameWorld(player)) {
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Nie możesz użyć tej komendy w tym świecie."));
            return;
        }

        MiniGamesManager manager = plugin.getMiniGamesManager();
        if (manager.isInEditorMode(player)) {
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Nie możesz użyć tej komendy, jesteś w trybie edytora."));
            return;
        }

        if (manager.getActiveMiniGame() != null) {
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Jakaś minigra jest już rozpoczęta."));
            return;
        }

        chosenMiniGame.put(player.getUniqueId(), type);

        List<String> maps = type.getMaps();
        if (maps.isEmpty()) {
            startMiniGame(player, null);
            return;
        }
        if (maps.size() == 1) {
            startMiniGame(player, maps.getFirst());
            return;
        }

        DialogBase base = DialogBase.create(
                Utils.getComponentByString(Prefix.MINI_GAMES + "Wybierz mapę"),
                null,
                true,
                true,
                DialogBase.DialogAfterAction.CLOSE,
                List.of(),
                List.of()
        );

        List<ActionButton> actions = new ArrayList<>();
        for (String map : maps) {
            actions.add(ActionButton.create(
                    Utils.getComponentByString("&f" + map),
                    Utils.getComponentByString("&aKliknij, aby wybrać!"),
                    120,
                    DialogAction.customClick((view, audience) -> startMiniGame(player, map), Utils.BUTTON_OPTIONS)
            ));
        }
        actions.add(ActionButton.create(
                Utils.getComponentByString("&dLosowa mapa"),
                Utils.getComponentByString("&aKliknij, aby wybrać!"),
                120,
                DialogAction.customClick((view, audience) -> startMiniGame(player, null), Utils.BUTTON_OPTIONS)
        ));

        ActionButton exitAction = ActionButton.create(Utils.getComponentByString("Anuluj"), null, 150, null);

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(actions, exitAction, 2))
        );
        player.showDialog(dialog);
    }


    private void startMiniGame(@NotNull Player player, @Nullable String mapName) {
        MiniGameType type = chosenMiniGame.remove(player.getUniqueId());
        if (type == null) {
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Napotkano niespodziewany błąd. Spróbuj ponownie."));
            return;
        }
        MiniGame miniGame = type.getNewHandlerInstance(mapName);
        if (miniGame == null) {
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Ta minigra jest wyłączona."));
            return;
        }

        Set<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(MiniGamesUtils::isInAnyMiniGameWorld)
                .filter(p -> !plugin.getMiniGamesManager().isInEditorMode(p))
                .collect(Collectors.toSet());

        if (players.size() < miniGame.getMinimumPlayersAmount()) {
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Do rozpoczęcia tej minigry potrzeba conajmniej " + miniGame.getMinimumPlayersAmount() + " graczy, a znaleziono " + players.size() + "."));
            return;
        }

        if (players.size() > miniGame.getMaximumPlayersAmount()) {
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "W tę minigrę może grać tylko " + miniGame.getMinimumPlayersAmount() + " graczy, a znaleziono " + players.size() + "."));
            return;
        }

        if (miniGame.getMapType() == MiniGame.MapType.SURVIVAL && Utils.getTps() < 17) {
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Nie możesz grać teraz w tę minigrę, bo TPS spadły poniżej 17. Spróbuj później."));
            return;
        }

        if (!plugin.getMiniGamesManager().startMiniGame(miniGame, players, player)) {
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Napotkano niespodziewany błąd przy uruchamianiu minigry."));
        }
    }

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("minigame-stop", "zatrzymaj aktywną minigrę")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public int onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
        MiniGamesManager manager = plugin.getMiniGamesManager();
        if (manager.getActiveMiniGame() == null) {
            event.reply("Żadna minigra nie jest rozpoczęta.").queue();
            return 0;
        }
        event.reply("Zatrzymywanie...").queue();
        manager.stopMiniGame();
        return 1;
    }

    public void clearChosenMiniGame(@NotNull Player player) {
        chosenMiniGame.remove(player.getUniqueId());
    }
}
