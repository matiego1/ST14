package me.matiego.st14.listeners;

import me.matiego.st14.Logs;
import me.matiego.st14.utils.Utils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.jetbrains.annotations.NotNull;

public class PlayerResourcePackStatusListener implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onPlayerResourcePackStatus(@NotNull PlayerResourcePackStatusEvent event) {
        PlayerResourcePackStatusEvent.Status status = event.getStatus();
        Player player = event.getPlayer();
        switch (status) {
            case DECLINED -> {
                player.sendMessage(Utils.getComponentByString("&6Pobierz resource-pack! Dzięki temu łatwiej Ci będzie rozpoznać wszystkie nowe moby."));
                Logs.warning("Gracz " + player.getName() + " nie zezwolił na resource-pack.");
            }
            case FAILED_DOWNLOAD -> Logs.warning("Graczowi " + player.getName() + " nie udało się pobrać resource-packa! Czy link podany w pliku server.properties jest poprawny?");
        }
    }
}
