package me.matiego.st14.commands.minecraft;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.objects.CommandHandler;
import me.matiego.st14.objects.GUI;
import me.matiego.st14.objects.Pair;
import me.matiego.st14.utils.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class WorldsCommand implements CommandHandler.Minecraft {
    public WorldsCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("worlds");
        if (command == null) {
            Logs.warning("The command /worlds does not exist in the plugin.yml file and cannot be registered.");
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
            sender.sendMessage(Utils.getComponentByString(Prefix.WORLDS + "&dTej komendy może użyć tylko gracz."));
            return 0;
        }

        World world = player.getWorld();
        World.Environment env = world.getEnvironment();
        if (env == World.Environment.NETHER || env == World.Environment.THE_END) {
            player.sendMessage(Utils.getComponentByString(Prefix.WORLDS + "&dNie możesz się teleportować z netheru i endu"));
            return 5;
        }

        List<Pair<World, Material>> worlds = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            Material m = null;
            try {
                m = Material.valueOf(plugin.getConfig().getString("worlds-command." + w.getName() + ".material"));
            } catch (Exception ignored) {}
            if (m == null) continue;
            worlds.add(new Pair<>(w, m));
        }

        if (worlds.isEmpty()) {
            player.sendMessage(Utils.getComponentByString(Prefix.WORLDS + "&dNie znaleziono światów do których możesz się przenieść."));
            return 5;
        }
        worlds = worlds.subList(0, Math.min(worlds.size(), 54));

        int slots = worlds.size() / 9 * 9 == worlds.size() ? worlds.size() : (worlds.size() / 9 + 1) * 9;
        Inventory inv = GUI.createInventory(slots, Prefix.WORLDS + "Wybierz świat");
        for (Pair<World, Material> pair : worlds) {
            inv.addItem(GUI.createGuiItem(pair.getSecond(), "&d" + Utils.getWorldName(pair.getFirst()), "Kliknij, aby wybrać"));
        }

        player.openInventory(inv);
        return 10;
    }

    @Override
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!GUI.checkInventory(event, Prefix.WORLDS + "Wybierz świat")) return;

        Player player = (Player) event.getWhoClicked();
        String name = getItemName(event.getCurrentItem());
        event.getInventory().close();

        World target = null;
        for (World world : Bukkit.getWorlds()) {
            if (Utils.getWorldName(world).equals(name)) {
                target = world;
                break;
            }
        }
        if (target == null) {
            player.sendMessage(Utils.getComponentByString(Prefix.WORLDS + "&dNapotkano niespodziewany błąd. Spróbuj ponownie."));
            return;
        }
        if (target.equals(player.getWorld())) {
            player.sendMessage(Utils.getComponentByString(Prefix.WORLDS + "&dJuż jesteś w tym świecie."));
            return;
        }
        if (!hasPermission(player, target)) {
            player.sendMessage(Utils.getComponentByString(Prefix.WORLDS + "&dNie masz uprawnień, aby przenieść się do tego świata."));
            return;
        }

        if (plugin.getTeleportsManager().isAlreadyActive(player)) {
            player.sendMessage(Utils.getComponentByString(Prefix.WORLDS + "&dProces teleportowania już został rozpoczęty"));
            return;
        }

        player.sendMessage(Utils.getComponentByString(Prefix.WORLDS + "Zostaniesz przeteleportowany za 5 sekund. Nie ruszaj się!"));

        plugin.getWorldsLastLocationManager().setLastLocation(player.getUniqueId(), player.getLocation());
        Location loc = plugin.getWorldsLastLocationManager().getLastLocation(player.getUniqueId(), target);

        Utils.async(() -> {
            try {
                String msg = switch (plugin.getTeleportsManager().teleport(player, loc, 5, () -> hasPermission(player, loc.getWorld())).get()) {
                    case SUCCESS -> null;
                    case PLAYER_MOVED -> "&dTeleportowanie anulowane, poruszyłeś się.";
                    case ALREADY_ACTIVE -> "&dProces teleportowania już został rozpoczęty.";
                    case CANCELLED_AFTER_COUNTDOWN -> "&dNie masz uprawnień, aby przenieść się do tego świata.";
                    case PLUGIN_DISABLED -> "&dTeleportowanie anulowane.";
                    case CANCELLED_ANTY_LOGOUT -> "&dNie możesz się teleportować z aktywnym anty-logout'em.";
                    case FAILURE -> "&dNapotkano niespodziewany błąd. Spróbuj ponownie.";
                };
                if (msg == null) {
                    Utils.broadcastMessage(
                            player,
                            Prefix.WORLDS,
                            "Przeteleportowano pomyślnie.",
                            "Gracz &1" + player.getName() + "&3 przeszedł do świata &1" + Utils.getWorldName(loc.getWorld()) + "&3!",
                            "Gracz **" + player.getName() + "** przeszedł do świata **" + Utils.getWorldName(loc.getWorld()) + "**!"
                    );
                    return;
                }
                player.sendMessage(Utils.getComponentByString(msg));
            } catch (Exception e) {
                player.sendMessage(Utils.getComponentByString(Prefix.WORLDS + "&dNapotkano niespodziewany błąd! Spróbuj ponownie."));
            }
        });
    }

    private boolean hasPermission(@NotNull Player player, @NotNull World world) {
        if (player.isOp()) return true;
        if (player.hasPermission("st14.worlds." + world.getName())) return true;
        return !plugin.getConfig().getBoolean("worlds-command." + world.getName() + ".private");
    }

    private @NotNull String getItemName(@Nullable ItemStack item) {
        if (item == null) return "";
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return "";
        Component name = meta.displayName();
        if (name == null) return "";
        return PlainTextComponentSerializer.plainText().serialize(name);
    }
}
