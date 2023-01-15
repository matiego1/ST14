package me.matiego.st14.commands.minecraft;

import me.matiego.st14.Economy;
import me.matiego.st14.Main;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TpaCommand implements CommandHandler.Minecraft {
    private final PluginCommand command;
    private final Main plugin;
    public TpaCommand(@NotNull Main plugin) {
        command = plugin.getCommand("tpa");
        this.plugin = plugin;
        if (command == null) {
            Logs.warning("The command /tpa does not exist in the plugin.yml file and cannot be registered.");
        }
    }

    private final HashMap<UUID, HashMap<UUID, BukkitTask>> tpa = new HashMap<>();

    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public int onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Utils.getComponentByString(Prefixes.TPA + "&cTej komendy może użyć tylko gracz."));
            return -1;
        }
        if (args.length == 0) {
            Set<UUID> requests = tpa.getOrDefault(player.getUniqueId(), new HashMap<>()).keySet();
            if (requests.isEmpty()) {
                player.sendMessage(Utils.getComponentByString(Prefixes.TPA + "&cNie masz żadnych aktywnych próśb o teleportację."));
                return 2;
            }
            Inventory inv = GUI.createInventory(9, Prefixes.TPA + "Prośby o teleportację");
            for (UUID uuid : requests) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    inv.addItem(GUI.createPlayerSkull(p, "&6" + p.getName(), "&eKliknij, aby zaakceptować"));
                }
            }
            if (inv.isEmpty()) {
                player.sendMessage(Utils.getComponentByString(Prefixes.TPA + "&cNie masz żadnych aktywnych próśb o teleportację."));
                return 2;
            }
            player.openInventory(inv);
            return 3;
        }
        if (args.length != 1) return -1;
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(Utils.getComponentByString(Prefixes.TPA + "&cTen gracz nie jest online."));
            return 3;
        }
        if (target.equals(player)) {
            player.sendMessage(Utils.getComponentByString(Prefixes.TPA + "&cNie możesz teleportować się do siebie."));
            return 3;
        }
        if (!target.getWorld().equals(player.getWorld())) {
            player.sendMessage(Utils.getComponentByString(Prefixes.TPA + "&cNie możesz teleportować się do innych światów."));
            return 3;
        }
        if (!plugin.getConfig().getStringList("tpa.worlds").contains(player.getWorld().getName())) {
            sender.sendMessage(Utils.getComponentByString(Prefixes.TPA + "&cNie możesz użyć tej komendy w tym świecie."));
            return 5;
        }
        HashMap<UUID, BukkitTask> requests = tpa.getOrDefault(target.getUniqueId(), new HashMap<>());
        Iterator<Map.Entry<UUID, BukkitTask>> it = requests.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, BukkitTask> e = it.next();
            if (player.getUniqueId().equals(e.getKey())) {
                player.sendMessage(Utils.getComponentByString(Prefixes.TPA + "Anulowałeś prośbę o teleportację do tego gracza"));
                e.getValue().cancel();
                it.remove();
                return 3;
            }
        }
        if (requests.size() >= 9) {
            player.sendMessage(Utils.getComponentByString(Prefixes.TPA + "&cTen gracz ma za dużo aktywnych próśb o teleportację."));
            return 5;
        }
        player.sendMessage(Utils.getComponentByString(Prefixes.TPA + "Pomyślnie wysłano prośbę o teleportację."));
        target.sendMessage(Utils.getComponentByString(Prefixes.TPA + "Gracz " + player.getName() + " chce się do ciebie przeteleportować. Użyj /tpa, aby mu na to pozwolić."));
        requests.put(player.getUniqueId(), Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            HashMap<UUID, BukkitTask> r = tpa.getOrDefault(target.getUniqueId(), new HashMap<>());
            r.remove(player.getUniqueId());
            if (!r.isEmpty()) tpa.put(target.getUniqueId(), r);
            if (!player.isOnline()) return;
            player.sendMessage(Utils.getComponentByString(Prefixes.TPA + "Prośba o teleportację do gracza " + target.getName() + " wygasła."));
        }, 1200));
        tpa.put(target.getUniqueId(), requests);
        return 5;
    }

    @Override
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!GUI.checkInventory(event, Prefixes.TPA + "Prośby o teleportację")) return;

        Player player = (Player) event.getWhoClicked();
        player.closeInventory();
        Location destination = player.getLocation();

        Player target = Bukkit.getPlayer(getItemName(event.getCurrentItem()));
        if (target == null) {
            player.sendMessage(Utils.getComponentByString(Prefixes.TPA + "&cTen gracz anulował prośbę o teleportację."));
            return;
        }
        HashMap<UUID, BukkitTask> requests = tpa.getOrDefault(player.getUniqueId(), new HashMap<>());
        if (!requests.containsKey(target.getUniqueId())) {
            player.sendMessage(Utils.getComponentByString(Prefixes.TPA + "&cTen gracz anulował prośbę o teleportację."));
            return;
        }
        if (!destination.getWorld().equals(target.getWorld())) {
            player.sendMessage(Utils.getComponentByString(Prefixes.TPA + "&cTen gracz anulował prośbę o teleportację."));
            target.sendMessage(Utils.getComponentByString(Prefixes.TPA + "&cNie możesz przeteleportować się do " + player.getName() + ", ponieważ jest on w innym świecie."));
            return;
        }

        requests.remove(target.getUniqueId()).cancel();
        if (requests.isEmpty()) {
            tpa.remove(player.getUniqueId());
        } else {
            tpa.put(player.getUniqueId(), requests);
        }

        if (plugin.getTeleportsManager().isAlreadyActive(target)) {
            player.sendMessage(Utils.getComponentByString(Prefixes.TPA + "&cTen gracz nie może się do ciebie przeteleportować."));
            return;
        }

        player.sendMessage(Utils.getComponentByString(Prefixes.TPA + "Ten gracz za chwilę zostanie do ciebie przeteleportowany"));
        target.sendMessage(Utils.getComponentByString(Prefixes.TPA + "Zostaniesz przeteleportowany za 5 sekund do gracza " + player.getName() + ". Nie ruszaj się!"));

        double distance = target.getLocation().distance(destination);
        final double cost;
        if (plugin.getConfig().getStringList("tpa.free-worlds").contains(destination.getWorld().getName())) {
            cost = 0;
        } else {
            cost = Utils.round(plugin.getConfig().getDouble("tpa.cost") * (distance / 16), 2);
        }

        Utils.async(() -> {
            try {
                switch (plugin.getTeleportsManager().teleport(target, destination, 5, () -> {
                    if (cost == 0) return true;
                    Economy economy = plugin.getEconomy();
                    EconomyResponse response = economy.withdrawPlayer(target, cost);
                    if (!response.transactionSuccess()) {
                        player.sendMessage(Utils.getComponentByString(Prefixes.TPA + "Gracz " + target.getName() + " nie może się do ciebie przeteleportować."));
                        target.sendMessage(Utils.getComponentByString(Prefixes.TPA + "Aby się przeteleportować potrzebujesz " + economy.format(cost) + " a masz tylko " + economy.format(response.balance)));
                        return false;
                    }
                    return true;
                }).get(6, TimeUnit.SECONDS)) {
                    case SUCCESS -> {
                        player.sendMessage(Utils.getComponentByString(Prefixes.TPA + "Gracz " + target.getName() + " przeteleportował się do ciebie."));
                        target.sendMessage(Utils.getComponentByString(Prefixes.TPA + "Przeteleportowałeś się do gracza " + player.getName() + "."));
                    }
                    case MOVE -> {
                        player.sendMessage(Utils.getComponentByString(Prefixes.TPA + "Gracz " + target.getName() + " nie może się do ciebie przeteleportować."));
                        target.sendMessage(Utils.getComponentByString(Prefixes.TPA + "Teleportowanie anulowane, poruszyłeś się."));
                    }
                    case ALREADY_ACTIVE -> {
                        player.sendMessage(Utils.getComponentByString(Prefixes.TPA + "Gracz " + target.getName() + " nie może się do ciebie przeteleportować."));
                        target.sendMessage(Utils.getComponentByString(Prefixes.TPA + "Proces teleportowania już został rozpoczęty."));
                    }
                    case CANCELLED -> {}
                    case FAILURE -> {
                        player.sendMessage(Utils.getComponentByString(Prefixes.TPA + "Gracz " + target.getName() + " nie może się do ciebie przeteleportować."));
                        target.sendMessage(Utils.getComponentByString(Prefixes.TPA + "Napotkano błąd teleportowaniu."));
                        if (!plugin.getEconomy().depositPlayer(target, cost).transactionSuccess()) {
                            target.sendMessage(Utils.getComponentByString(Prefixes.TPA + "&c&lNapotkano błąd przy oddawaniu pieniędzy! Zgłoś się do administratora, aby je odzyskać. Przepraszamy."));
                            Logs.warning("Gracz " + target.getName() + " (" + target.getUniqueId() + ") stracił " + plugin.getEconomy().format(cost) + " ze swojego konta! Kwota musi być przywrócona ręcznie.");
                        }
                    }
                }
            } catch (Exception e) {
                player.sendMessage(Utils.getComponentByString(Prefixes.TPA + "&cNapotkano niespodziewany błąd. Spróbuj ponownie."));
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
            if (p != null) p.sendMessage(Utils.getComponentByString(Prefixes.TPA + "Prośba o teleportację do gracza " + player.getName() + " wygasła."));
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
