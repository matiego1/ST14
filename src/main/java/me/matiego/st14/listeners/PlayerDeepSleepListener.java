package me.matiego.st14.listeners;

import io.papermc.paper.event.player.PlayerDeepSleepEvent;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.managers.EconomyManager;
import me.matiego.st14.utils.Utils;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PlayerDeepSleepListener implements Listener {
    public PlayerDeepSleepListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;
    private final HashMap<UUID, UUID> sleepingPlayers = new HashMap<>();

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDeepSleep(@NotNull PlayerDeepSleepEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        double amount = Math.max(0, plugin.getConfig().getDouble("bed-enter.cost", 5));
        if (amount == 0 || !plugin.getConfig().getStringList("bed-enter.worlds").contains(world.getName())) {
            sleepingPlayers.put(player.getUniqueId(), world.getUID());
            return;
        }

        EconomyManager economy = plugin.getEconomyManager();
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        if (response.transactionSuccess()) {
            player.sendMessage(Utils.getComponentByString("&aPobrano " + economy.format(amount) + " za położenie się spać."));
            sleepingPlayers.put(player.getUniqueId(), world.getUID());
        } else {
            event.setCancelled(true);
            try {
                player.wakeup(true);
            } catch (IllegalStateException ignored) {}
            player.sendMessage(Utils.getComponentByString("&cAby położyć się spać potrzebujesz " + economy.format(amount) + ", a masz tylko " + economy.format(response.balance) + "."));
        }
    }

    public synchronized void clearPlayerDataAndBroadcastMessage(@NotNull Player playerLeavingBed, @NotNull World world, boolean shouldBroadcastMessage) {
        if (!shouldBroadcastMessage) {
            sleepingPlayers.remove(playerLeavingBed.getUniqueId());
            return;
        }

        Iterator<Map.Entry<UUID, UUID>> it = sleepingPlayers.entrySet().iterator();
        List<Player> players = new ArrayList<>();

        while (it.hasNext()) {
            Map.Entry<UUID, UUID> e = it.next();
            if (e.getValue().equals(world.getUID())) {
                it.remove();
                Player player = Bukkit.getPlayer(e.getKey());
                if (player != null) {
                    players.add(player);
                }
            }
        }

        if (players.isEmpty()) return;

        if (players.size() == 1) {
            Player player = players.get(0);

            Utils.broadcastMessage(
                    player,
                    Prefix.SLEEPING_THROUGH_NIGHT,
                    "&6[" + Utils.getWorldName(world) + "] &eGracz &6" + player.getName() + "&e poszedł spać. Słodkich snów!",
                    "&6[" + Utils.getWorldName(world) + "] &eGracz &6" + player.getName() + "&e poszedł spać. Słodkich snów!",
                    "**[" + Utils.getWorldName(world) + "]** Gracz **" + player.getName() + "** poszedł spać. Słodkich snów!"
            );
            return;
        }

        String mcNames = String.join(", ", players.stream().map(p -> "&6" + p.getName() + "&e").toList());
        String dcNames = String.join(", ", players.stream().map(p -> "**" + p.getName() + "**").toList());

        Bukkit.broadcast(Utils.getComponentByString("&6[" + Utils.getWorldName(world) + "] &eGracze " + mcNames + " poszli spać. Słodkich snów!"));

        for (Player player : players) {
            if (plugin.getIncognitoManager().isIncognito(player.getUniqueId())) return;
        }

        plugin.getChatMinecraftManager().sendMessage("**[" + Utils.getWorldName(world) + "]** Gracze " + dcNames + " poszli spać. Słodkich snów!", Prefix.SLEEPING_THROUGH_NIGHT.getDiscord());
    }
}
