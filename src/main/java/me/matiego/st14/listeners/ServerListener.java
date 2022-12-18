package me.matiego.st14.listeners;

import com.destroystokyo.paper.event.server.GS4QueryEvent;
import me.matiego.st14.IncognitoManager;
import me.matiego.st14.Main;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ServerListener implements Listener, PluginMessageListener {

    private final Main plugin;
    public ServerListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        if (!channel.equalsIgnoreCase("minecraft:brand")) return;
        String brand = new String(message, StandardCharsets.UTF_8).substring(1);
        List<String> allowedBrands = plugin.getConfig().getStringList("allowed-brands");
        if (allowedBrands.isEmpty()) return;
        for (String allowedBrand : allowedBrands) {
            if (brand.toLowerCase().startsWith(allowedBrand.toLowerCase())) return;
        }
        player.kick(Utils.getComponentByString(
                "&4Używasz niedozwolonego klienta minecraft!\n" +
                "&c(" + brand + ")\n\n" +
                "&7Przepraszamy"
        ));
        Logs.warning(player.getName() + " próbował grać na niedozwolonym kliencie minecraft. (" + brand + ")");
    }

    @EventHandler
    public void onGS4Query(@NotNull GS4QueryEvent event){
        GS4QueryEvent.QueryResponse response = event.getResponse();
        List<String> list = new ArrayList<>(response.getPlayers());
        Iterator<String> it = list.iterator();
        while (it.hasNext()) {
            Player player = Bukkit.getPlayer(it.next());
            if (player == null) return;
            if (plugin.getIncognitoManager().isIncognito(player.getUniqueId())) it.remove();
        }
        event.setResponse(response.toBuilder().clearPlayers().clearPlugins().currentPlayers(list.size()).players(list).build());
    }

    @EventHandler
    public void onServerListPing(@NotNull ServerListPingEvent event) {
        Iterator<Player> iterator = event.iterator();
        IncognitoManager manager = plugin.getIncognitoManager();
        while (iterator.hasNext()) {
            if (manager.isIncognito(iterator.next().getUniqueId())) iterator.remove();
        }
    }
}
