package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Prefix;
import me.matiego.st14.utils.Utils;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class PlayerInteractListener implements Listener {
    public PlayerInteractListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;
    @EventHandler
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        EquipmentSlot hand = event.getHand();
        if (hand != EquipmentSlot.HAND) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        if (!plugin.getBanknoteManager().isBanknote(item)) return;
        double amount = plugin.getBanknoteManager().getAmount(item);
        if (amount <= 0) return;

        Player player = event.getPlayer();
        if (!plugin.getConfig().getStringList("economy-worlds").contains(player.getWorld().getName())) return;

        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);


        if (!plugin.getEconomy().depositPlayer(player, amount).transactionSuccess()) {
            player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Napotkano niespodziewany błąd. Spróbuj później."));
            return;
        }

        Logs.info("Gracz " + player.getName() + " wpłacił banknot o wartości " + plugin.getEconomy().format(amount));

        player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Pomyślnie wpłacono pieniądze na twoje konto!"));
        player.getInventory().getItemInMainHand().setAmount(player.getInventory().getItemInMainHand().getAmount() - 1);
    }
}
