package me.matiego.st14.managers;

import me.matiego.st14.Main;
import me.matiego.st14.objects.GUI;
import me.matiego.st14.Logs;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.UUID;

public class BanknoteManager {
    public BanknoteManager(@NotNull Main plugin) {
        this.plugin = plugin;
        amountKey = new NamespacedKey(plugin, "banknote_amount");
        idKey = new NamespacedKey(plugin, "banknote_id");
    }
    private final Main plugin;
    private final String ERROR_MSG = "An error occurred while modifying values in \"st14_banknote\" table in the database.";
    private final NamespacedKey amountKey;
    private final NamespacedKey idKey;
    private final String NAME = "&9Banknot";
    private final String[] LORES = {"&bKliknij PPM, aby wpłacić", "&bWartość: &9{amount}"};

    public @Nullable ItemStack createBanknote(double amount) {
        UUID id = createBanknoteId(amount);
        if (id == null) return null;
        ItemStack item = GUI.createGuiItem(
                Material.PAPER,
                NAME,
                Arrays.stream(LORES.clone())
                        .map(lore -> lore.replace("{amount}", plugin.getEconomyManager().format(amount)))
                        .toArray(String[]::new)
        );
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(amountKey, PersistentDataType.DOUBLE, amount);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id.toString());
        item.setItemMeta(meta);
        return item;
    }

    public boolean isBanknote(@NotNull ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer().has(idKey);
    }

    public double getAmount(@NotNull ItemStack item) {
        if (!isBanknote(item)) return 0;
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        try {
            String id = data.get(idKey, PersistentDataType.STRING);
            if (id == null) return 0;
            Double amount = data.get(amountKey, PersistentDataType.DOUBLE);
            if (amount == null) return 0;
            if (!checkBanknoteId(UUID.fromString(id), amount)) return 0;
            return amount;
        } catch (Exception ignored) {}
        return 0;
    }

    private @Nullable UUID createBanknoteId(double amount) {
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO st14_banknote(id, amount, date) VALUES (?, ?, ?)")) {
            UUID id = UUID.randomUUID();
            stmt.setString(1, id.toString());
            stmt.setDouble(2, amount);
            stmt.setString(3, DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").format(LocalDateTime.now()));
            if (stmt.executeUpdate() > 0) return id;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return null;
    }

    private boolean checkBanknoteId(@NotNull UUID id, double amount) {
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT amount FROM st14_banknote WHERE id = ?")) {
            stmt.setString(1, id.toString());
            ResultSet result = stmt.executeQuery();
            if (!result.next()) return false;
            return result.getDouble("amount") == amount;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return false;
    }

    public static boolean createTable() {
        try (Connection conn = Main.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS st14_banknote(id VARCHAR(36) NOT NULL, amount DECIMAL(12, 2) NOT NULL, date VARCHAR(19), PRIMARY KEY (id))")) {
            stmt.execute();
            return true;
        } catch (SQLException e) {
            Logs.error("An error occurred while creating the database table \"st14_banknote\"", e);
        }
        return false;
    }
}
