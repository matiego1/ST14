package me.matiego.st14.objects;

import me.matiego.st14.utils.Base64Utils;
import me.matiego.st14.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GUI implements InventoryHolder {
    protected GUI(int slots, @NotNull String title) {
        inventory = Bukkit.createInventory(this, slots, Utils.getComponentByString(title));
    }

    private final Inventory inventory;

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public static @NotNull Inventory createInventory(int slots, @NotNull String title) {
        return new GUI(slots, title).getInventory();
    }

    public static @NotNull ItemStack createGuiItem(@NotNull Material material, @NotNull String name, String @NotNull ... lores) {
        Component[] componentLores = new Component[lores.length];
        for (int i = 0; i < lores.length; i++) {
            componentLores[i] = Utils.getComponentByString(lores[i]).decoration(TextDecoration.ITALIC, false);
        }
        
        ItemStack item = new ItemStack(material);

        ItemMeta meta = item.getItemMeta();
        meta.displayName(Utils.getComponentByString(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(Arrays.asList(componentLores));
        item.setItemMeta(meta);

        item.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ITEM_SPECIFICS, ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_DYE, ItemFlag.HIDE_PLACED_ON);
        return item;
    }

    public static @NotNull ItemStack createPlayerSkull(@NotNull OfflinePlayer player, @NotNull String name, String @NotNull ... lores) {
        ItemStack item = createGuiItem(Material.PLAYER_HEAD, name, lores);

        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        item.setItemMeta(meta);

        return item;
    }

    public static boolean checkInventory(@NotNull InventoryClickEvent event, @NotNull String title) {
        if (event.isCancelled()) return false;
        if (!(event.getView().getTopInventory().getHolder() instanceof GUI)) return false;
        if (!LegacyComponentSerializer.legacyAmpersand().serialize(event.getView().title()).equals(title)) return false;
        event.setCancelled(true);
        if (event.getView().getBottomInventory().equals(event.getClickedInventory())) return false;
        return event.getCurrentItem() != null && !event.getCurrentItem().getType().isAir();
    }

    public static @NotNull String itemsToString(@NotNull List<ItemStack> items) {
        List<String> result = new ArrayList<>();
        for (ItemStack item : items) {
            String base64 = Base64Utils.objectToBase64(item != null ? item : new ItemStack(Material.AIR));
            if (base64 != null) {
                result.add(base64);
            }
        }
        return String.join("|", result);
    }

    public static @NotNull List<ItemStack> stringToItems(@NotNull String string) {
        String[] items = string.split("\\|");
        if (items.length == 0 || items[0].isBlank()) return new ArrayList<>();

        List<ItemStack> result = new ArrayList<>();
        for (String item : items) {
            Object object = Base64Utils.base64ToObject(item);
            if (object instanceof ItemStack itemStack) {
                result.add(itemStack);
            }
        }
        return result;
    }

}
