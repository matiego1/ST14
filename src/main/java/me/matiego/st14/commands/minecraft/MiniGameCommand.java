package me.matiego.st14.commands.minecraft;

import me.matiego.st14.Main;
import me.matiego.st14.MiniGameManager;
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

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

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
        MiniGameManager manager = plugin.getMiniGameManager();

        if (args.length == 1) {
            if (sender instanceof Player player && !player.hasPermission("st14.minigame.admin") && !player.isOp()) return -1;
            if (args[0].equalsIgnoreCase("stop")) {
                if (manager.getActiveMiniGame() == null) {
                    sender.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Żadna minigra nie jest rozpoczęta."));
                    return 0;
                }
                sender.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Zatrzymywanie..."));
                manager.stopMiniGame();
                return 1;
            }
            return -1;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Tej komendy może użyć tylko gracz"));
            return 0;
        }

        if (args.length != 0) return -1;

        if (!MiniGamesUtils.isInMinigameWorldOrLobby(player)) {
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Nie możesz użyć tej komendy w tym świecie."));
            return 3;
        }

        if (manager.getActiveMiniGame() != null) {
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Jakaś minigra jest już rozpoczęta."));
            return 10;
        }

        Inventory inv = GUI.createInventory(18, Prefix.MINI_GAMES + "Wybierz minigrę");
        for (MiniGameType type : MiniGameType.values()) {
            inv.addItem(GUI.createGuiItem(
                    type.getGuiMaterial(),
                    "&9" + type.getName(),
                    "&eKliknij, aby rozpocząć!",
                    "&eMaksymalny czas gry: &d" + Utils.parseMillisToString(type.getGameTimeInSeconds() * 1000L, false),
                    "",
                    (type.isMiniGameEnabled() ? "" : "&4Ta gra jest wyłączona")
            ));
        }
        if (inv.isEmpty()) {
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Żadna gra nie została jeszcze zaimplementowana."));
            return 60;
        }

        player.openInventory(inv);
        return 3;
    }

    @Override
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!GUI.checkInventory(event, Prefix.MINI_GAMES + "Wybierz minigrę")) return;
        event.getInventory().close();

        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        Objects.requireNonNull(item); //already checked in GUI#checkInventory()

        if (!MiniGamesUtils.isInMinigameWorldOrLobby(player)) {
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Nie możesz użyć tej komendy w tym świecie."));
            return;
        }

        MiniGameManager manager = plugin.getMiniGameManager();
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

        Set<Player> players = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (MiniGamesUtils.isInMinigameWorldOrLobby(p)) {
                players.add(p);
            }
        }

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
