package me.matiego.st14.commands;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.managers.MiniGamesManager;
import me.matiego.st14.objects.command.CommandHandler;
import me.matiego.st14.objects.GUI;
import me.matiego.st14.objects.minigames.MiniGame;
import me.matiego.st14.objects.minigames.MiniGameType;
import me.matiego.st14.utils.MiniGamesUtils;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
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
                        player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "&cŻadna minigra nie jest rozpoczęta."));
                        return 3;
                    }

                    if (miniGame.getPlayerStatus(player) != MiniGame.PlayerStatus.IN_MINI_GAME) {
                        player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "&cNie bierzesz udziału w toczącej się minigrze."));
                        return 3;
                    }

                    miniGame.voteToStop(player);
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
                    return 7;
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
            return 10;
        }

        Inventory inv = GUI.createInventory(18, Prefix.MINI_GAMES + "Wybierz minigrę");
        for (MiniGameType type : MiniGameType.values()) {
            String[] lores;
            if (type.isMiniGameEnabled()) {
                lores = new String[] {
                        "&eKliknij, aby rozpocząć!",
                        "&eCzas minigry: &d" + Utils.parseMillisToString(type.getGameTimeInSeconds() * 1000L, false)
                };
            } else {
                lores = new String[] {
                        "&eKliknij, aby rozpocząć!",
                        "&eCzas minigry: &d" + Utils.parseMillisToString(type.getGameTimeInSeconds() * 1000L, false),
                        "",
                        (type.isMiniGameEnabled() ? "" : "&4Ta minigra jest wyłączona")
                };
            }
            inv.addItem(GUI.createGuiItem(type.getGuiMaterial(), "&9" + type.getName(), lores));
        }
        if (inv.isEmpty()) {
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Żadna gra nie została jeszcze zaimplementowana."));
            return 60;
        }

        player.openInventory(inv);
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

    @Override
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(GUI.checkInventory(event, Prefix.MINI_GAMES + "Wybierz minigrę") || GUI.checkInventory(event, Prefix.MINI_GAMES + "Wybierz mapę"))) return;
        event.getInventory().close();

        String title = LegacyComponentSerializer.legacyAmpersand().serialize(event.getView().title());
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        Objects.requireNonNull(item); //already checked in GUI#checkInventory()

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

        Component displayNameComponent = item.getItemMeta().displayName();
        if (displayNameComponent == null) return;
        String displayName = Utils.getPlainTextByComponent(displayNameComponent);

        if (title.equals(Prefix.MINI_GAMES + "Wybierz minigrę")) {
            MiniGameType type = MiniGameType.getMiniGameTypeByName(displayName);
            if (type == null) {
                player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Napotkano niespodziewany błąd. Spróbuj ponownie."));
                return;
            }
            chosenMiniGame.put(player.getUniqueId(), type);

            List<String> maps = type.getMaps();
            if (maps.isEmpty()) {
                startMiniGame(player, null);
                return;
            }
            if (maps.size() == 1) {
                startMiniGame(player, maps.get(0));
                return;
            }

            Inventory inv = GUI.createInventory(Math.min(54, ((maps.size() / 9) + 1) * 9), Prefix.MINI_GAMES + "Wybierz mapę");
            for (String map : maps.subList(0, Math.min(maps.size(), 53))) {
                inv.addItem(GUI.createGuiItem(Material.PAPER, "&9" + map, "&eKliknij, aby rozpocząć minigrę na tej mapie!"));
            }
            inv.setItem(inv.getSize() - 1, GUI.createGuiItem(Material.ARROW, "&9Wybierz losową mapę!"));

            player.openInventory(inv);
            return;
        }

        if (item.getType() == Material.ARROW) displayName = null;
        startMiniGame(player, displayName);
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

        if (!plugin.getMiniGamesManager().startMiniGame(miniGame, players, player)) {
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Napotkano niespodziewany błąd przy uruchamianiu minigry."));
        }
    }

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("minigame-stop", "zatrzymaj aktywną minigrę")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .setGuildOnly(true);
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
