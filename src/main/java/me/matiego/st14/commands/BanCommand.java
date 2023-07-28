package me.matiego.st14.commands;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.managers.BansManager;
import me.matiego.st14.objects.Ban;
import me.matiego.st14.objects.CommandHandler;
import me.matiego.st14.utils.Utils;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class BanCommand implements CommandHandler.Minecraft {
    public BanCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("ban");
        if (command == null) {
            Logs.warning("The command /coordinates does not exist in the plugin.yml file and cannot be registered.");
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
        if (args.length == 0) return -1;

        UUID uuid = plugin.getOfflinePlayersManager().getIdByName(args[0]);
        if (uuid == null) {
            sender.sendMessage(Utils.getComponentByString("&cZły nick!"));
            return 0;
        }

        BansManager manager = plugin.getBansManager();

        switch (args[0]) {
            case "get" -> {
                if (args.length != 1) return -1;
                if (!manager.isBanned(uuid)) {
                    sender.sendMessage(Utils.getComponentByString("&cTen gracz nie jest zbanowany."));
                    return 0;
                }
                Ban ban = manager.getBan(uuid);
                if (ban == null) {
                    sender.sendMessage(Utils.getComponentByString("&cTen gracz jest zbanowany, ale nie udało się wczytać więcej informacji."));
                    return 0;
                }
                sender.sendMessage(Utils.getComponentByString("&aPozostały czas: &2" + Utils.parseMillisToString(ban.getExpiration() - Utils.now(), true) + "&a; Powód: &2" + ban.getReason()));
                return 0;
            }
            case "pardon" -> {
                if (args.length != 1) return -1;
                if (!manager.isBanned(uuid)) {
                    sender.sendMessage(Utils.getComponentByString("&cTen gracz nie jest zbanowany."));
                    return 0;
                }
                if (manager.setBan(new Ban(uuid))) {
                    sender.sendMessage(Utils.getComponentByString("&aPomyślnie odbanowano gracza."));
                } else {
                    sender.sendMessage(Utils.getComponentByString("&cNapotkano błąd przy odbanowywaniu gracza."));
                }
                return 0;
            }
            case "set" -> {
                if (args.length < 3) return -1;
                if (manager.isBanned(uuid)) {
                    sender.sendMessage(Utils.getComponentByString("&cTen gracz już jest zbanowany."));
                    return 0;
                }

                long time;
                try {
                    time = Utils.parseStringToMillis(args[1]);
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(Utils.getComponentByString("&cZły czas."));
                    return 0;
                }

                StringBuilder reason = new StringBuilder();
                for (int i = 2; i < args.length; i++) {
                    reason.append(args[i]).append(" ");
                }
                reason.deleteCharAt(reason.length() - 1);

                if (manager.setBan(new Ban(uuid, reason.toString(), Utils.now() + time))) {
                    sender.sendMessage(Utils.getComponentByString("&aPomyślnie zbanowano tego gracza."));
                } else {
                    sender.sendMessage(Utils.getComponentByString("&cNapotkano błąd przy banowaniu gracza."));
                }
                return 0;
            }
            default -> {
                return -1;
            }
        }
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return switch (args.length) {
            case 1 -> plugin.getOfflinePlayersManager().getNames();
            case 2 -> Arrays.asList("30s", "30m", "1h", "1h30m", "1d");
            default -> new ArrayList<>();
        };
    }
}
