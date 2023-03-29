package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Prefix;
import me.matiego.st14.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
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
        if (depositBanknote(event)) return;
        editSign(event);
    }

    private boolean depositBanknote(@NotNull PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return false;

        EquipmentSlot hand = event.getHand();
        if (hand != EquipmentSlot.HAND) return false;

        ItemStack item = event.getItem();
        if (item == null) return false;

        if (!plugin.getBanknoteManager().isBanknote(item)) return false;
        double amount = plugin.getBanknoteManager().getAmount(item);
        if (amount <= 0) return false;

        Player player = event.getPlayer();
        if (!plugin.getConfig().getStringList("economy-worlds").contains(player.getWorld().getName())) return false;

        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);

        if (!plugin.getEconomy().depositPlayer(player, amount).transactionSuccess()) {
            player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Napotkano niespodziewany błąd. Spróbuj później."));
            return true;
        }

        Logs.info("Gracz " + player.getName() + " wpłacił banknot o wartości " + plugin.getEconomy().format(amount));

        player.sendMessage(Utils.getComponentByString(Prefix.ECONOMY + "Pomyślnie wpłacono pieniądze na twoje konto!"));
        player.getInventory().getItemInMainHand().setAmount(player.getInventory().getItemInMainHand().getAmount() - 1);
        return true;
    }

    private void editSign(@NotNull PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Material material = block.getType();
        if (!material.name().contains("SIGN")) return;

        Block placedAgainst;
        BlockData data = block.getBlockData();
        if (data instanceof Directional directional) {
            placedAgainst = block.getRelative(directional.getFacing().getOppositeFace());
        } else {
            placedAgainst = block.getRelative(BlockFace.DOWN);
        }

        EquipmentSlot hand = event.getHand();
        BlockPlaceEvent placeEvent = new BlockPlaceEvent(block, block.getState(), placedAgainst, new ItemStack(material), player, true, hand == null ? EquipmentSlot.HAND : hand);
        Bukkit.getPluginManager().callEvent(placeEvent);

        if (placeEvent.isCancelled()) return;

        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);

        Sign sign = (Sign) block.getState();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            sign.setEditable(true);
            sign.update();
            Bukkit.getScheduler().runTaskLater(plugin, () -> player.openSign(sign), 1);
        }, 1);
    }

}
