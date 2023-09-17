package me.matiego.st14.listeners;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.utils.Utils;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

public class PluginMessageReceivedListener implements PluginMessageListener {
    public PluginMessageReceivedListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        if (!channel.equalsIgnoreCase("minecraft:brand")) return;
        String brand = new String(message, StandardCharsets.UTF_8).substring(1).toLowerCase();

        if (plugin.getConfig().getStringList("brands.allowed").stream()
                .map(String::toLowerCase)
                .anyMatch(b -> b.equals(brand.toLowerCase()))) return;

        if (plugin.getConfig().getStringList("brands.bypass").contains(player.getUniqueId().toString())) return;

        player.kick(Utils.getComponentByString(
                "&cAby grać na tym serwerze musisz używać optifine!\n" +
                "&cMożesz go pobrać tutaj: optifine.net"
        ));
        Logs.warning(player.getName() + " próbował grać na niedozwolonym kliencie minecraft. (" + brand + ")");
    }
}
