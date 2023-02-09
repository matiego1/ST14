package me.matiego.st14.commands.minecraft;

import me.matiego.st14.Main;
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WorldsCommand implements CommandHandler.Minecraft {
    private final Main plugin;
    private final PluginCommand command;
    public WorldsCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("worlds");
        if (command == null) {
            Logs.warning("The command /worlds does not exist in the plugin.yml file and cannot be registered.");
        }
    }
    private final String ERROR_MSG = "An error occurred while modifying values in \"st14_worlds_cmd\" table in the database.";

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

        setLastLocation(player.getUniqueId(), player.getLocation());
        Location loc = getLastLocation(player.getUniqueId(), target);

        Utils.async(() -> {
            try {
                String msg = switch (plugin.getTeleportsManager().teleport(player, loc, 5, () -> hasPermission(player, loc.getWorld())).get()) {
                    case SUCCESS -> null;
                    case MOVE -> "&dTeleportowanie anulowane, poruszyłeś się.";
                    case ALREADY_ACTIVE -> "&dProces teleportowania już został rozpoczęty.";
                    case CANCELLED -> "&dNie masz uprawnień, aby przenieść się do tego świata.";
                    case DISABLED -> "&dTeleportowanie anulowane.";
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

    private @NotNull Location getLastLocation(@NotNull UUID uuid, @NotNull World world) {
        if (plugin.getConfig().getBoolean("worlds-command." + world.getName() + ".teleport-to-spawn")) {
            return world.getSpawnLocation();
        }
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT x, y, z, yaw, pitch FROM st14_worlds_cmd WHERE uuid = ? AND world = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, world.getUID().toString());
            ResultSet result = stmt.executeQuery();
            if (!result.next()) return world.getSpawnLocation();

            return new Location(
                    world,
                    result.getDouble("x"),
                    result.getDouble("y"),
                    result.getDouble("z"),
                    result.getFloat("yaw"),
                    result.getFloat("pitch")
            );
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return world.getSpawnLocation();
    }

    private void setLastLocation(@NotNull UUID uuid, @NotNull Location loc) {
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO st14_worlds_cmd(uuid, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE x = ?, y = ?, z = ?, yaw = ?, pitch = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, loc.getWorld().getUID().toString());

            stmt.setDouble(3, loc.getX());
            stmt.setDouble(4, loc.getY());
            stmt.setDouble(5, loc.getZ());
            stmt.setDouble(6, loc.getYaw());
            stmt.setDouble(7, loc.getPitch());

            stmt.setDouble(8, loc.getX());
            stmt.setDouble(9, loc.getY());
            stmt.setDouble(10, loc.getZ());
            stmt.setDouble(11, loc.getYaw());
            stmt.setDouble(12, loc.getPitch());

            stmt.execute();
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
    }

    private @NotNull String getItemName(@Nullable ItemStack item) {
        if (item == null) return "";
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return "";
        Component name = meta.displayName();
        if (name == null) return "";
        return PlainTextComponentSerializer.plainText().serialize(name);
    }

    public static boolean createTable() {
        try (Connection conn = Main.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS st14_worlds_cmd(uuid VARCHAR(36) NOT NULL, world VARCHAR(36) NOT NULL, x DECIMAL(15, 5) NOT NULL, y DECIMAL(15, 5) NOT NULL, z DECIMAL(15, 5) NOT NULL, yaw DECIMAL(15, 5) NOT NULL, pitch DECIMAL(15, 5) NOT NULL, CONSTRAINT st14_worlds_cmd_const UNIQUE (uuid, world))")) {
            stmt.execute();
            return true;
        } catch (SQLException e) {
            Logs.error("An error occurred while creating the database table \"st14_worlds_cmd\"", e);
        }
        return false;
    }
}
