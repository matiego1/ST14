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
import java.util.concurrent.TimeUnit;

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
            sender.sendMessage(Utils.getComponentByString(Prefixes.WORLDS + "&dTej komendy może użyć tylko gracz."));
            return 0;
        }

        World world = player.getWorld();
        World.Environment env = world.getEnvironment();
        if (env == World.Environment.NETHER || env == World.Environment.THE_END) {
            player.sendMessage(Utils.getComponentByString(Prefixes.WORLDS + "&dNie możesz się teleportować z netheru i endu"));
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
            player.sendMessage(Utils.getComponentByString(Prefixes.WORLDS + "&dNie znaleziono światów do których możesz się przenieść."));
            return 5;
        }
        worlds = worlds.subList(0, Math.min(worlds.size(), 54));

        int slots = worlds.size() / 9 * 9 == worlds.size() ? worlds.size() : (worlds.size() / 9 + 1) * 9;
        Inventory inv = GUI.createInventory(slots, Prefixes.WORLDS + "Wybierz świat");
        for (Pair<World, Material> pair : worlds) {
            inv.addItem(GUI.createGuiItem(pair.getSecond(), "&d" + Utils.getWorldName(pair.getFirst()), "Kliknij, aby wybrać"));
        }

        player.openInventory(inv);
        return 10;
    }

    @Override
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!GUI.checkInventory(event, Prefixes.WORLDS + "Wybierz świat")) return;

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
            player.sendMessage(Utils.getComponentByString(Prefixes.WORLDS + "&dNapotkano niespodziewany błąd. Spróbuj ponownie."));
            return;
        }
        if (target.equals(player.getWorld())) {
            player.sendMessage(Utils.getComponentByString(Prefixes.WORLDS + "&dJuż jesteś w tym świecie."));
            return;
        }
        if (plugin.getConfig().getBoolean("worlds-command." + target.getName() + ".private") && !player.hasPermission("st14.worlds." + target.getName())) {
            player.sendMessage(Utils.getComponentByString(Prefixes.WORLDS + "&dNie masz uprawnień, aby przenieść się do tego świata."));
            return;
        }

        if (plugin.getTeleportsManager().isAlreadyActive(player)) {
            player.sendMessage(Utils.getComponentByString(Prefixes.WORLDS + "&dProces teleportowania już został rozpoczęty"));
            return;
        }

        player.sendMessage(Utils.getComponentByString(Prefixes.WORLDS + "Zostaniesz przeteleportowany za 5 sekund. Nie ruszaj się!"));

        setLastLocation(player.getUniqueId(), player.getLocation());
        Location loc = getLastLocation(player.getUniqueId(), target);
        Location finalLoc = loc == null ? target.getSpawnLocation() : loc;
        Utils.async(() -> {
            try {
                player.sendMessage(Utils.getComponentByString(Prefixes.WORLDS +
                        switch (plugin.getTeleportsManager().teleport(player, finalLoc, 5, () -> {
                            String world = finalLoc.getWorld().getName();
                            return !plugin.getConfig().getBoolean("worlds-command." + world + ".private") || player.hasPermission("st14.worlds." + world);
                        }).get(6, TimeUnit.SECONDS)) {
                            case SUCCESS -> broadcastMessage(
                                    player,
                                    "Gracz &1" + player.getName() + "&3 przeszedł do świata &1" + Utils.getWorldName(finalLoc.getWorld()) + "&3!",
                                    "Gracz **" + player.getName() + "** przeszedł do świata **" + Utils.getWorldName(finalLoc.getWorld()) + "**!"
                            );
                            case MOVE -> "&dTeleportowanie anulowane, poruszyłeś się.";
                            case ALREADY_ACTIVE -> "&dProces teleportowania już został rozpoczęty.";
                            case CANCELLED -> "&dNie masz uprawnień, aby przenieść się do tego świata.";
                            case FAILURE -> "&dNapotkano niespodziewany błąd. Spróbuj ponownie.";
                        }
                ));
            } catch (Exception e) {
                player.sendMessage(Utils.getComponentByString(Prefixes.WORLDS + "&dNapotkano niespodziewany błąd! Spróbuj ponownie."));
            }
        });
    }

    private @NotNull String broadcastMessage(@NotNull Player player, @NotNull String others, @NotNull String discord) {
        Utils.async(() -> {
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.equals(player))
                    .forEach(p -> p.sendMessage(Utils.getComponentByString(Prefixes.WORLDS + others)));
            Bukkit.getConsoleSender().sendMessage(Utils.getComponentByString(Prefixes.WORLDS + others));

            if (plugin.getIncognitoManager().isIncognito(player.getUniqueId())) return;
            plugin.getChatMinecraft().sendMessage(discord, Prefixes.WORLDS.getDiscord());
        });
        return "Przeteleportowano pomyślnie.";
    }

    private @Nullable Location getLastLocation(@NotNull UUID uuid, @NotNull World world) {
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT x, y, z, yaw, pitch FROM st14_worlds_cmd WHERE uuid = ? AND world = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, world.getUID().toString());
            ResultSet result = stmt.executeQuery();
            if (!result.next()) return null;

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
        return null;
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
