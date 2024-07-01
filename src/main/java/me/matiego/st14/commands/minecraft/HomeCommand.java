package me.matiego.st14.commands.minecraft;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.managers.EconomyManager;
import me.matiego.st14.managers.HomeManager;
import me.matiego.st14.objects.command.CommandHandler;
import me.matiego.st14.objects.GUI;
import me.matiego.st14.utils.Utils;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class HomeCommand implements CommandHandler.Minecraft {
    public HomeCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("home");
        if (command == null) {
            Logs.warning("The command /home does not exist in the plugin.yml file and cannot be registered.");
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
        if (Utils.checkIfCanNotExecuteCommandInWorld(player, "home", '.')) {
            player.sendMessage(Utils.getComponentByString(Prefix.HOME + "&cNie możesz użyć tej komendy w tym świecie."));
            return 3;
        }

        Inventory inv = GUI.createInventory(9, Prefix.HOME + "Zarządzaj domem");

        HomeManager manager = plugin.getHomeManager();
        UUID uuid = player.getUniqueId();

        Utils.async(() -> {
            if (manager.isHomeLocationSet(uuid)) {
                inv.setItem(2, GUI.createGuiItem(Material.ENDER_PEARL, "&6Teleportuj do domu", "&aKliknij, aby przeteleportować się do domu"));
                inv.setItem(4, GUI.createGuiItem(Material.RED_BED, "&6Twój dom", parseLocationToString(manager.getHomeLocation(uuid))));
                inv.setItem(6, GUI.createGuiItem(Material.BARRIER, "&6Usuń swój dom", "&aKliknij, aby usunąć swój dom"));
            } else {
                inv.setItem(4, GUI.createGuiItem(Material.RED_BED, "&6Nie ustawiłeś jeszcze domu!", "&aKliknij, aby ustawić swój dom", "&aw miejscu, w którym stoisz."));
            }
        });
        player.openInventory(inv);
        return 15;
    }

    private @NotNull String[] parseLocationToString(@Nullable Location location) {
        if (location == null) {
            return new String[]{"&cNapotkano błąd przy wczytywaniu położenia"};
        }
        return new String[]{
                "&6X: &a" + Utils.round(location.getX(), 2) + "&6 Y: &a" + Utils.round(location.getY(), 2) + "&6 Z: &a" + Utils.round(location.getZ(), 2),
                "&6Świat: &a" + Utils.getWorldName(location.getWorld())
        };
    }

    @Override
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!GUI.checkInventory(event, Prefix.HOME + "Zarządzaj domem")) return;

        Player player = (Player) event.getWhoClicked();
        HomeManager manager = plugin.getHomeManager();
        UUID uuid = player.getUniqueId();
        int slot = event.getSlot();

        if (Utils.checkIfCanNotExecuteCommandInWorld(player, "home", '.')) {
            event.getInventory().close();
            player.sendMessage(Utils.getComponentByString(Prefix.HOME + "&cNie możesz użyć tej komendy w tym świecie."));
            return;
        }

        switch (slot) {
            case 2 -> Utils.async(() -> {
                Location location = manager.getHomeLocation(uuid);
                closeInventory(event);
                if (location == null) {
                    player.sendMessage(Utils.getComponentByString(Prefix.HOME + "&cNapotkano niespodziewany błąd. Spróbuj ponownie."));
                    return;
                }
                teleportPlayer(player, location);
            });
            case 4 -> Utils.async(() -> {
                if (getItemName(event.getCurrentItem()).equals("Twój dom")) return;
                closeInventory(event);

                double creation = Math.max(0, Utils.round(plugin.getConfig().getDouble("home.creation"), 2));
                EconomyManager economy = plugin.getEconomyManager();
                if (creation != 0) {
                    if (!economy.withdrawPlayer(player, creation).transactionSuccess()) {
                        player.sendMessage(Utils.getComponentByString(Prefix.HOME + "&cUstawienie domu kosztuje " + economy.format(creation) + ", a masz tylko " + economy.format(economy.getBalance(player)) + "."));
                        return;
                    }
                }

                if (manager.setHomeLocation(uuid, player.getLocation())) {
                    player.sendMessage(Utils.getComponentByString(Prefix.HOME + "Pomyślnie ustawiono twój dom za " + economy.format(creation) + "."));
                    Logs.info("Gracz " + player.getName() + " ustawił swój dom. (`" + player.getLocation() + "`)");
                } else {
                    player.sendMessage(Utils.getComponentByString(Prefix.HOME + "&cNapotkano niespodziewany błąd. Spróbuj ponownie."));
                    if (creation != 0 && !economy.depositPlayer(player, creation).transactionSuccess()) {
                        player.sendMessage(Utils.getComponentByString(Prefix.HOME + "&c&lNapotkano niespodziewany błąd przy zwracaniu pobranych pieniędzy. Zgłoś się do administratora, aby je odzyskać. Przepraszamy."));
                        Logs.warning("Gracz " + player.getName() + " (" + player.getUniqueId() + ") stracił " + economy.format(creation) + " ze swojego konta! Kwota musi być przywrócona ręcznie.");
                    }
                }
            });
            case 6 -> Utils.async(() -> {
                closeInventory(event);
                if (manager.removeHome(uuid)) {
                    player.sendMessage(Utils.getComponentByString(Prefix.HOME + "Pomyślnie usunięto twój dom."));
                    Logs.info("Gracz " + player.getName() + " usunął swój dom.");
                } else {
                    player.sendMessage(Utils.getComponentByString(Prefix.HOME + "&cNapotkano niespodziewany błąd. Spróbuj ponownie."));
                }
            });
        }
    }

    private void closeInventory(@NotNull InventoryClickEvent event) {
        Utils.sync(() -> event.getInventory().close());
    }

    private void teleportPlayer(@NotNull Player player, @NotNull Location location) {
        if (plugin.getTeleportsManager().isAlreadyActive(player)) {
            player.sendMessage(Utils.getComponentByString(Prefix.HOME + "&cProces teleportowania już jest aktywny!"));
            return;
        }

        double distance;
        try {
            distance = player.getLocation().distance(location);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Utils.getComponentByString("&cTwój dom jest w innym świecie!"));
            return;
        }

        if (distance <= plugin.getConfig().getInt("home.min", 0)) {
            player.sendMessage(Utils.getComponentByString("&cJesteś za blisko twojego domu!"));
            return;
        }

        final double cost = Utils.round(plugin.getConfig().getDouble("home.cost") * (distance / 16), 2);

        EconomyManager economy = plugin.getEconomyManager();
        if (cost != 0 && !economy.has(player, cost)) {
            player.sendMessage(Utils.getComponentByString(Prefix.HOME + "Aby się przeteleportować potrzebujesz " + economy.format(cost) + " a masz tylko " + economy.getBalance(player) + "."));
            return;
        }

        player.sendMessage(Utils.getComponentByString(Prefix.HOME + "Za 5 sekund zostaniesz przeteleportowany do swojego domu. Nie ruszaj się!"));

        Utils.async(() -> {
            try {
                switch (plugin.getTeleportsManager().teleport(player, location, 5, () -> {
                    if (cost == 0) return true;
                    EconomyResponse response = economy.withdrawPlayer(player, cost);
                    if (response.transactionSuccess()) return true;
                    player.sendMessage(Utils.getComponentByString(Prefix.HOME + "Aby się przeteleportować potrzebujesz " + economy.format(cost) + " a masz tylko " + economy.format(response.balance) + "."));
                    return false;
                }).get()) {
                    case SUCCESS -> {
                        player.sendMessage(Utils.getComponentByString(Prefix.HOME + "Przeteleportowałeś się do swojego domu za " + economy.format(cost)));
                        Logs.info("Gracz " + player.getName() + " przeteleportował do swojego domu.");
                    }
                    case PLAYER_MOVED -> player.sendMessage(Utils.getComponentByString(Prefix.HOME + "Teleportowanie anulowane, poruszyłeś się."));
                    case CANCELLED_ANTY_LOGOUT -> player.sendMessage(Utils.getComponentByString(Prefix.HOME + "Nie możesz teleportować się z aktywnym anty-logout'em."));
                    case ALREADY_ACTIVE -> player.sendMessage(Utils.getComponentByString(Prefix.HOME + "Proces teleportowania już został rozpoczęty."));
                    case PLUGIN_DISABLED -> player.sendMessage(Utils.getComponentByString(Prefix.HOME + "Teleportowanie anulowane."));
                    case CANCELLED_AFTER_COUNTDOWN -> {}
                    case FAILURE -> {
                        player.sendMessage(Utils.getComponentByString(Prefix.HOME + "Napotkano błąd teleportowaniu."));
                        if (!plugin.getEconomyManager().depositPlayer(player, cost).transactionSuccess()) {
                            player.sendMessage(Utils.getComponentByString(Prefix.HOME + "&c&lNapotkano błąd przy oddawaniu pieniędzy! Zgłoś się do administratora, aby je odzyskać. Przepraszamy."));
                            Logs.warning("Gracz " + player.getName() + " (" + player.getUniqueId() + ") stracił " + plugin.getEconomyManager().format(cost) + " ze swojego konta! Kwota musi być przywrócona ręcznie.");
                        }
                    }
                }
            } catch (Exception e) {
                player.sendMessage(Utils.getComponentByString(Prefix.HOME + "&cNapotkano niespodziewany błąd. Spróbuj ponownie."));
                Logs.error("An error occurred while teleporting player", e);
            }
        });
    }

    private @NotNull String getItemName(@Nullable ItemStack item) {
        if (item == null) return "";
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return "";
        Component name = meta.displayName();
        if (name == null) return "";
        return Utils.getPlainTextByComponent(name);
    }
}
