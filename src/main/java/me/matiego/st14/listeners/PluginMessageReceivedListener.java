package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.Logs;
import me.matiego.st14.utils.Utils;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class PluginMessageReceivedListener implements PluginMessageListener {
    public PluginMessageReceivedListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        if (!channel.equalsIgnoreCase("minecraft:brand")) return;
        String brand = new String(message, StandardCharsets.UTF_8).substring(1);

        List<String> disallowedBrands = plugin.getConfig().getStringList("brands.disallowed");
        for (String disallowedBrand : disallowedBrands) {
            if (brand.toLowerCase().startsWith(disallowedBrand.toLowerCase())) {
                player.kick(Utils.getComponentByString(
                        "&cUżywasz niedozwolonego klienta minecraft!\n" +
                                "&c(" + brand + ")\n\n" +
                                "&7Przepraszamy"
                ));
                Logs.warning(player.getName() + " próbował grać na niedozwolonym kliencie minecraft. (" + brand + ")");
            }
        }

        List<String> allowedBrands = plugin.getConfig().getStringList("brands.allowed");
        if (allowedBrands.isEmpty()) return;
        for (String allowedBrand : allowedBrands) {
            if (brand.toLowerCase().startsWith(allowedBrand.toLowerCase())) return;
        }
        Logs.warning(player.getName() + " gra na zmodyfikowanym kliencie minecraft: " + brand);
    }
}
