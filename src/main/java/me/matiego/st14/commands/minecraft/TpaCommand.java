package me.matiego.st14.commands.minecraft;

import me.matiego.st14.Logs;
import me.matiego.st14.Prefix;
import me.matiego.st14.objects.CommandHandler;
import me.matiego.st14.managers.EconomyManager;
import me.matiego.st14.Main;
import me.matiego.st14.objects.GUI;
import me.matiego.st14.utils.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class TpaCommand implements CommandHandler.Minecraft {
    public TpaCommand(@NotNull Main plugin) {
        command = plugin.getCommand("tpa");
        this.plugin = plugin;
        if (command == null) {
            Logs.warning("The command /tpa does not exist in the plugin.yml file and cannot be registered.");
        }
    }
    private final PluginCommand command;
    private final Main plugin;
    private final HashMap<UUID, HashMap<UUID, BukkitTask>> tpa = new HashMap<>();

    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public int onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Utils.getComponentByString(Prefix.TPA + "&cTej komendy może użyć tylko gracz."));
            return -1;
        }

        if (Utils.checkIfCanNotExecuteCommandInWorld(player, "tpa", '.')) {
            sender.sendMessage(Utils.getComponentByString(Prefix.TPA + "&cNie możesz użyć tej komendy w tym świecie."));
            return 5;
        }

        if (args.length == 0) {
            Set<UUID> requests = tpa.getOrDefault(player.getUniqueId(), new HashMap<>()).keySet();
            if (requests.isEmpty()) {
                player.sendMessage(Utils.getComponentByString(Prefix.TPA + "&cNie masz żadnych aktywnych próśb o teleportację."));
                return 2;
            }
            Inventory inv = GUI.createInventory(9, Prefix.TPA + "Prośby o teleportację");
            for (UUID uuid : requests) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    inv.addItem(GUI.createPlayerSkull(p, "&6" + p.getName(), "&eKliknij, aby zaakceptować"));
                }
            }
            if (inv.isEmpty()) {
                player.sendMessage(Utils.getComponentByString(Prefix.TPA + "&cNie masz żadnych aktywnych próśb o teleportację."));
                return 2;
            }
            player.openInventory(inv);
            return 3;
        }

        if (args.length != 1) return -1;

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(Utils.getComponentByString(Prefix.TPA + "&cTen gracz nie jest online."));
            return 3;
        }

        if (target.equals(player)) {
            player.sendMessage(Utils.getComponentByString(Prefix.TPA + "&cNie możesz teleportować się do siebie."));
            return 3;
        }

        if (!target.getWorld().equals(player.getWorld())) {
            player.sendMessage(Utils.getComponentByString(Prefix.TPA + "&cNie możesz teleportować się do innych światów."));
            return 3;
        }

        HashMap<UUID, BukkitTask> requests = tpa.getOrDefault(target.getUniqueId(), new HashMap<>());
        Iterator<Map.Entry<UUID, BukkitTask>> it = requests.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, BukkitTask> e = it.next();
            if (player.getUniqueId().equals(e.getKey())) {
                player.sendMessage(Utils.getComponentByString(Prefix.TPA + "Anulowałeś prośbę o teleportację do tego gracza"));
                e.getValue().cancel();
                it.remove();
                return 3;
            }
        }

        if (requests.size() >= 9) {
            player.sendMessage(Utils.getComponentByString(Prefix.TPA + "&cTen gracz ma za dużo aktywnych próśb o teleportację."));
            return 5;
        }

        player.sendMessage(Utils.getComponentByString(Prefix.TPA + "Pomyślnie wysłano prośbę o teleportację."));
        target.sendMessage(Utils.getComponentByString(Prefix.TPA + "Gracz " + player.getName() + " chce się do ciebie przeteleportować. Użyj /tpa, aby mu na to pozwolić."));
        requests.put(player.getUniqueId(), Bukkit.getScheduler().runTaskLater(plugin, () -> {
            HashMap<UUID, BukkitTask> r = tpa.getOrDefault(target.getUniqueId(), new HashMap<>());
            r.remove(player.getUniqueId());
            if (!r.isEmpty()) tpa.put(target.getUniqueId(), r);
            if (!player.isOnline()) return;
            player.sendMessage(Utils.getComponentByString(Prefix.TPA + "Prośba o teleportację do gracza " + target.getName() + " wygasła."));
        }, 1200));
        tpa.put(target.getUniqueId(), requests);
        return 5;
    }

    @Override
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!GUI.checkInventory(event, Prefix.TPA + "Prośby o teleportację")) return;

        Player player = (Player) event.getWhoClicked();
        player.closeInventory();
        Location destination = player.getLocation();

        if (Utils.checkIfCanNotExecuteCommandInWorld(player, "tpa", '.')) {
            player.sendMessage(Utils.getComponentByString(Prefix.TPA + "&cNie możesz użyć tej komendy w tym świecie."));
            return;
        }

        Player requester = Bukkit.getPlayer(getItemName(event.getCurrentItem()));
        if (requester == null) {
            player.sendMessage(Utils.getComponentByString(Prefix.TPA + "&cTen gracz anulował prośbę o teleportację."));
            return;
        }
        HashMap<UUID, BukkitTask> requests = tpa.getOrDefault(player.getUniqueId(), new HashMap<>());
        if (!requests.containsKey(requester.getUniqueId())) {
            player.sendMessage(Utils.getComponentByString(Prefix.TPA + "&cTen gracz anulował prośbę o teleportację."));
            return;
        }
        if (!destination.getWorld().equals(requester.getWorld())) {
            player.sendMessage(Utils.getComponentByString(Prefix.TPA + "&cTen gracz anulował prośbę o teleportację."));
            requester.sendMessage(Utils.getComponentByString(Prefix.TPA + "&cNie możesz przeteleportować się do " + player.getName() + ", ponieważ jest on w innym świecie."));
            return;
        }

        requests.remove(requester.getUniqueId()).cancel();
        if (requests.isEmpty()) {
            tpa.remove(player.getUniqueId());
        } else {
            tpa.put(player.getUniqueId(), requests);
        }

        if (plugin.getTeleportsManager().isAlreadyActive(requester)) {
            player.sendMessage(Utils.getComponentByString(Prefix.TPA + "&cTen gracz nie może się do ciebie przeteleportować."));
            return;
        }

        double distance = requester.getLocation().distance(destination);
        if (distance <= plugin.getConfig().getInt("tpa.min", 0)) {
            requester.sendMessage(Utils.getComponentByString(Prefix.TPA + "&cJesteś za blisko drugiego gracza!"));
            player.sendMessage(Utils.getComponentByString(Prefix.TPA + "&cTen gracz jest za blisko ciebie."));
            return;
        }

        final double cost;
        if (plugin.getConfig().getStringList("tpa.free-worlds").contains(destination.getWorld().getName())) {
            cost = 0;
        } else {
            cost = Utils.round(plugin.getConfig().getDouble("tpa.cost") * (distance / 16), 2);
        }

        EconomyManager economy = plugin.getEconomyManager();
        if (cost != 0 && !economy.has(player, cost)) {
            requester.sendMessage(Utils.getComponentByString(Prefix.TPA + "Aby się przeteleportować potrzebujesz " + economy.format(cost) + " a masz tylko " + economy.getBalance(player) + "."));
            return;
        }

        player.sendMessage(Utils.getComponentByString(Prefix.TPA + "Ten gracz za chwilę zostanie do ciebie przeteleportowany"));
        requester.sendMessage(Utils.getComponentByString(Prefix.TPA + "Zostaniesz przeteleportowany za 5 sekund do gracza " + player.getName() + ". Nie ruszaj się!"));

        Utils.async(() -> {
            try {
                switch (plugin.getTeleportsManager().teleport(requester, destination, 5, () -> {
                    if (cost == 0) return true;
                    EconomyResponse response = economy.withdrawPlayer(requester, cost);
                    if (response.transactionSuccess()) return true;
                    player.sendMessage(Utils.getComponentByString(Prefix.TPA + "Gracz " + requester.getName() + " nie może się do ciebie przeteleportować."));
                    requester.sendMessage(Utils.getComponentByString(Prefix.TPA + "Aby się przeteleportować potrzebujesz " + economy.format(cost) + " a masz tylko " + economy.format(response.balance) + "."));
                    return false;
                }).get()) {
                    case SUCCESS -> {
                        player.sendMessage(Utils.getComponentByString(Prefix.TPA + "Gracz " + requester.getName() + " przeteleportował się do ciebie."));
                        requester.sendMessage(Utils.getComponentByString(Prefix.TPA + "Przeteleportowałeś się do gracza " + player.getName() + " za " + economy.format(cost)));
                        Logs.info("Gracz " + player.getName() + " przeteleportował do gracza " + requester.getName() + ".");
                    }
                    case PLAYER_MOVED -> {
                        player.sendMessage(Utils.getComponentByString(Prefix.TPA + "Gracz " + requester.getName() + " nie może się do ciebie przeteleportować."));
                        requester.sendMessage(Utils.getComponentByString(Prefix.TPA + "Teleportowanie anulowane, poruszyłeś się."));
                    }
                    case CANCELLED_ANTY_LOGOUT -> {
                        player.sendMessage(Utils.getComponentByString(Prefix.TPA + "Gracz " + requester.getName() + " nie może się do ciebie przeteleportować."));
                        requester.sendMessage(Utils.getComponentByString(Prefix.TPA + "Nie możesz teleportować się z aktywnym anty-logoutem."));
                    }
                    case ALREADY_ACTIVE -> {
                        player.sendMessage(Utils.getComponentByString(Prefix.TPA + "Gracz " + requester.getName() + " nie może się do ciebie przeteleportować."));
                        requester.sendMessage(Utils.getComponentByString(Prefix.TPA + "Proces teleportowania już został rozpoczęty."));
                    }
                    case PLUGIN_DISABLED -> {
                        player.sendMessage(Utils.getComponentByString(Prefix.TPA + "Gracz " + requester.getName() + " nie może się do ciebie przeteleportować."));
                        requester.sendMessage(Utils.getComponentByString(Prefix.TPA + "Teleportowanie anulowane."));
                    }
                    case CANCELLED_AFTER_COUNTDOWN -> {}
                    case FAILURE -> {
                        player.sendMessage(Utils.getComponentByString(Prefix.TPA + "Gracz " + requester.getName() + " nie może się do ciebie przeteleportować."));
                        requester.sendMessage(Utils.getComponentByString(Prefix.TPA + "Napotkano błąd teleportowaniu."));
                        if (!plugin.getEconomyManager().depositPlayer(requester, cost).transactionSuccess()) {
                            requester.sendMessage(Utils.getComponentByString(Prefix.TPA + "&c&lNapotkano błąd przy oddawaniu pieniędzy! Zgłoś się do administratora, aby je odzyskać. Przepraszamy."));
                            Logs.warning("Gracz " + requester.getName() + " (" + requester.getUniqueId() + ") stracił " + plugin.getEconomyManager().format(cost) + " ze swojego konta! Kwota musi być przywrócona ręcznie.");
                        }
                    }
                }
            } catch (Exception e) {
                player.sendMessage(Utils.getComponentByString(Prefix.TPA + "&cNapotkano niespodziewany błąd. Spróbuj ponownie."));
                Logs.error("An error occurred while teleporting player", e);
            }
        });
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private @NotNull String getItemName(@Nullable ItemStack item) {
        if (item == null) return "";
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return "";
        Component name = meta.displayName();
        if (name == null) return "";
        return PlainTextComponentSerializer.plainText().serialize(name);
    }

    public void cancel(@NotNull Player player) {
        HashMap<UUID, BukkitTask> requests = tpa.remove(player.getUniqueId());
        if (requests == null) return;
        for (Map.Entry<UUID, BukkitTask> e : requests.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) p.sendMessage(Utils.getComponentByString(Prefix.TPA + "Prośba o teleportację do gracza " + player.getName() + " wygasła."));
            e.getValue().cancel();
        }
        for (Map.Entry<UUID, HashMap<UUID, BukkitTask>> e1 : tpa.entrySet()) {
            HashMap<UUID, BukkitTask> map = e1.getValue();
            map.entrySet().removeIf(e2 -> e2.getKey().equals(player.getUniqueId()));
            if (!map.isEmpty()) {
                tpa.put(e1.getKey(), map);
            }
        }
    }
}
