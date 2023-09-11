package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.managers.EconomyManager;
import me.matiego.st14.utils.Utils;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.UUID;

public class PlayerBedEnterListener implements Listener {
    public PlayerBedEnterListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;
    private final HashMap<UUID, UUID> sleepingPlayers = new HashMap<>();

    @EventHandler(ignoreCancelled = true)
    public void onPlayerBedEnter(@NotNull PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;

        Player player = event.getPlayer();
        UUID world = event.getBed().getWorld().getUID();
        if (sleepingPlayers.containsKey(world)) {
            event.setCancelled(true);
            player.sendMessage(Utils.getComponentByString("&cW tym świecie już ktoś spi"));
            return;
        }
        sleepingPlayers.put(world, player.getUniqueId());

        double amount = Math.max(0, plugin.getConfig().getDouble("bed-enter-cost", 5));
        if (amount == 0) return;

        EconomyManager economy = plugin.getEconomyManager();
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        if (response.transactionSuccess()) {
            player.sendMessage(Utils.getComponentByString("&aPobrano " + economy.format(amount) + " za położenie się spać."));
        } else {
            player.sendMessage(Utils.getComponentByString("&cAby położyć się spać potrzebujesz " + economy.format(amount) + ", a masz tylko " + economy.format(response.balance) + "."));
        }
    }

    public synchronized void clearSleepingInWorld(@NotNull World world) {
        sleepingPlayers.remove(world.getUID());
    }
}
