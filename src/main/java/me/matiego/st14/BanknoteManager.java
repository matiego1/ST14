package me.matiego.st14;

import me.matiego.st14.utils.GUI;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class BanknoteManager {
    public BanknoteManager(@NotNull Main plugin) {
        this.plugin = plugin;
        key = new NamespacedKey(plugin, "banknote");
    }
    private final Main plugin;
    private final NamespacedKey key;
    private final String NAME = "&9Banknot";
    private final String[] LORES = {"&bKliknij PPM, aby wpłacić", "&bWartość: &9{amount}"};

    public @NotNull ItemStack createBanknote(double amount) {
        ItemStack item = GUI.createGuiItem(
                Material.PAPER,
                NAME,
                Arrays.stream(LORES.clone())
                        .map(lore -> lore.replace("{amount}", plugin.getEconomy().format(amount)))
                        .toArray(String[]::new)
        );
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, amount);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isBanknote(@NotNull ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer().has(key);
    }

    public double getAmount(@NotNull ItemStack item) {
        if (!isBanknote(item)) return 0;
        try {
            Double amount = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
            return amount == null ? 0 : amount;
        } catch (Exception ignored) {}
        return 0;
    }
}
