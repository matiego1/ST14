package me.matiego.st14.commands.minecraft;

import me.matiego.st14.Main;
import me.matiego.st14.MiniGamesManager;
import me.matiego.st14.minigames.MiniGame;
import me.matiego.st14.minigames.MiniGameType;
import me.matiego.st14.minigames.MiniGamesUtils;
import me.matiego.st14.utils.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class MiniGameCommand implements CommandHandler.Minecraft {
    public MiniGameCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("minigame");
        if (command == null) {
            Logs.warning("The command /minigame does not exist in the plugin.yml file and cannot be registered.");
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
        MiniGamesManager manager = plugin.getMiniGamesManager();

        if (args.length == 1) {
            String subCommand = args[0].toLowerCase();
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
                    return 10;
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
                        (type.isMiniGameEnabled() ? "" : "&4Ta gra jest wyłączona")
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
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!GUI.checkInventory(event, Prefix.MINI_GAMES + "Wybierz minigrę")) return;
        event.getInventory().close();

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

        Component displayName = item.getItemMeta().displayName();
        if (displayName == null) return;
        MiniGameType type = MiniGameType.getMiniGameTypeByName(PlainTextComponentSerializer.plainText().serialize(displayName));
        if (type == null) return;

        MiniGame miniGame = type.getNewHandlerInstance();
        if (miniGame == null) {
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Ta gra jest wyłączona."));
            return;
        }

        Set<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(MiniGamesUtils::isInAnyMiniGameWorld)
                .filter(p -> !manager.isInEditorMode(p))
                .collect(Collectors.toSet());

        if (players.size() < miniGame.getMinimumPlayersAmount()) {
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Do rozpoczęcia tej minigry potrzeba conajmniej " + miniGame.getMinimumPlayersAmount() + " graczy, a znaleziono " + players.size() + "."));
            return;
        }

        if (players.size() > miniGame.getMaximumPlayersAmount()) {
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "W tą minigrę może grać tylko " + miniGame.getMinimumPlayersAmount() + " graczy, a znaleziono " + players.size() + "."));
            return;
        }

        if (!manager.startMiniGame(miniGame, players, player)) {
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Napotkano niespodziewany błąd."));
        }
    }
}
