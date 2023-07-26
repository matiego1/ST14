package me.matiego.st14.commands.minecraft;

import me.matiego.st14.managers.EconomyManager;
import me.matiego.st14.Main;
import me.matiego.st14.utils.CommandHandler;
import me.matiego.st14.Logs;
import me.matiego.st14.Prefix;
import me.matiego.st14.utils.Utils;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SpawnCommand implements CommandHandler.Minecraft {
    public SpawnCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("spawn");
        if (command == null) {
            Logs.warning("The command /spawn does not exist in the plugin.yml file and cannot be registered.");
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
            if (args.length != 1) return -1;

            Player player = Bukkit.getPlayer(args[0]);
            if (player == null) {
                sender.sendMessage(Utils.getComponentByString("&cTen gracz nie jest online!"));
                return 0;
            }

            sender.sendMessage(Utils.getComponentByString("&aTeleportowanie..."));
            player.teleportAsync(player.getWorld().getSpawnLocation()).thenAcceptAsync(success -> {
                if (success) {
                    sender.sendMessage(Utils.getComponentByString("&aPomyślnie przeteleportowano gracza na spawn."));
                    player.sendMessage(Utils.getComponentByString("&aZostałeś przeteleportowany na spawn!"));
                } else {
                    sender.sendMessage(Utils.getComponentByString("&aNapotkano niespodziewany błąd podczas teleportowania gracza na spawn."));
                }
            });
            return 0;
        }

        if (args.length != 0) return -1;

        if (Utils.checkIfCanNotExecuteCommandInWorld(player, "spawn", '.')) {
            player.sendMessage(Utils.getComponentByString("&cNie możesz użyć tej komendy w tym świecie."));
            return 3;
        }

        if (plugin.getTeleportsManager().isAlreadyActive(player)) {
            player.sendMessage(Utils.getComponentByString("&cProces teleportowania już został rozpoczęty."));
            return 3;
        }

        Location spawn = player.getWorld().getSpawnLocation();

        double distance = player.getLocation().distance(spawn);
        if (distance <= plugin.getConfig().getInt("spawn.min", 0)) {
            player.sendMessage(Utils.getComponentByString("&cJesteś za blisko spawnu!"));
            return 3;
        }

        final double cost;
        if (plugin.getConfig().getStringList("spawn.free-worlds").contains(player.getWorld().getName())) {
            cost = 0;
        } else {
            cost = Utils.round(plugin.getConfig().getDouble("spawn.cost") * (distance / 16), 2);
        }

        EconomyManager economy = plugin.getEconomyManager();
        if (cost != 0 && !economy.has(player, cost)) {
            player.sendMessage(Utils.getComponentByString("&aAby się przeteleportować potrzebujesz " + economy.format(cost) + " a masz tylko " + economy.getBalance(player) + "."));
            return 3;
        }

        player.sendMessage(Utils.getComponentByString("&aZa chwilę zostaniesz przeteleportowany na spawn. Nie ruszaj się!"));

        Utils.async(() -> {
            try {
                switch (plugin.getTeleportsManager().teleport(player, spawn, 5, () -> {
                    if (cost == 0) return true;
                    EconomyResponse response = economy.withdrawPlayer(player, cost);
                    if (response.transactionSuccess()) return true;
                    player.sendMessage(Utils.getComponentByString("&cAby się przeteleportować potrzebujesz " + economy.format(cost) + " a masz tylko " + economy.format(response.balance) + "."));
                    return false;
                }).get()) {
                    case SUCCESS -> {
                        player.sendMessage(Utils.getComponentByString("&aPomyślnie przeteleportowano na spawn" + " za " + economy.format(cost)));
                        Logs.info("Gracz " + player.getName() + " przeteleportował się na spawn.");
                    }
                    case PLAYER_MOVED -> player.sendMessage(Utils.getComponentByString("&cTeleportowanie anulowane, poruszyłeś się"));
                    case ALREADY_ACTIVE -> player.sendMessage(Utils.getComponentByString("&cProces teleportowania już został rozpoczęty."));
                    case PLUGIN_DISABLED -> player.sendMessage(Utils.getComponentByString("&cTeleportowanie anulowane."));
                    case CANCELLED_AFTER_COUNTDOWN -> {}
                    case CANCELLED_ANTY_LOGOUT -> player.sendMessage(Utils.getComponentByString("&cNie możesz się teleportować z aktywnym anty-logoutem."));
                    case FAILURE -> {
                        player.sendMessage(Utils.getComponentByString("&cNapotkano niespodziewany błąd."));
                        if (!economy.depositPlayer(player, cost).transactionSuccess()) {
                            player.sendMessage(Utils.getComponentByString("&c&lNapotkano błąd przy oddawaniu pieniędzy! Zgłoś się do administratora, aby je odzyskać. Przepraszamy."));
                            Logs.warning("Gracz " + player.getName() + " (" + player.getUniqueId() + ") stracił " + economy.format(cost) + " ze swojego konta! Kwota musi być przywrócona ręcznie.");
                        }
                    }
                }
            } catch (Exception e) {
                player.sendMessage(Utils.getComponentByString(Prefix.TPA + "&cNapotkano niespodziewany błąd. Spróbuj ponownie."));
                Logs.error("An error occurred while teleporting player", e);
            }
        });

        return 30;
    }
}
