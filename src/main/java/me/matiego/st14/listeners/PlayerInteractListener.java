package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.utils.Prefix;
import me.matiego.st14.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PlayerInteractListener implements Listener {
    public PlayerInteractListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;
    @EventHandler(ignoreCancelled = true)
    public void onPlayerUse(@NotNull PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR) return;

        EquipmentSlot hand = event.getHand();
        if (hand != EquipmentSlot.HAND) return;

        ItemStack item = event.getItem();
        if (item == null) return;
        if (!getItemName(item).equals("&9Banknot")) return;
        List<String> lores = getItemLore(item);
        if (lores.size() != 3) return;
        if (!lores.get(0).equals("&bKliknij PPM, trzymając w ręku,")) return;
        if (!lores.get(1).equals("&baby wpłacić")) return;
        if (!lores.get(2).startsWith("&bWartość: &9")) return;

        Player player = event.getPlayer();
        if (!Main.getInstance().getConfig().getStringList("economy-worlds").contains(player.getWorld().getName())) return;

        event.setCancelled(true);

        double amount;
        try {
            amount = Double.parseDouble(lores.get(2).replaceFirst(Pattern.quote("&bWartość: &9"), "").replaceFirst(Pattern.quote("$"), ""));
        }catch(Exception e) {
            return;
        }

        EconomyResponse response = plugin.getEconomy().depositPlayer(player, amount);
        if (!response.transactionSuccess()) {
            player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Napotkano niespodziewany błąd. Spróbuj później."));
            return;
        }

        player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Pomyślnie wpłacono pieniądze na twoje konto!"));
        player.getInventory().getItemInMainHand().setAmount(player.getInventory().getItemInMainHand().getAmount() - 1);
    }

    private @NotNull String getItemName(@NotNull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return "";
        Component name = meta.displayName();
        if (name == null) return "";
        return LegacyComponentSerializer.legacyAmpersand().serialize(name);
    }

    private @NotNull List<String> getItemLore(@NotNull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return new ArrayList<>();
        List<Component> lores = meta.lore();
        if (lores == null) return new ArrayList<>();
        return lores.stream().map(lore -> LegacyComponentSerializer.legacyAmpersand().serialize(lore)).collect(Collectors.toList());
    }
}
