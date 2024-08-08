package me.matiego.st14.managers;

import me.matiego.st14.Main;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class F3BrandManager {
    public F3BrandManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;
    private final String CHANNEL = "minecraft:brand";

    public void refreshPlayerF3Brand(@NotNull Player player) {
//        if (!Bukkit.getMessenger().isOutgoingChannelRegistered(plugin, CHANNEL)) {
//            Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
//        }
//        byte[] str = plugin.getConfig().getString("f3-brand", "&2Serwer").replace("&", "§").getBytes(StandardCharsets.UTF_8);
//        ByteBuf buf = Unpooled.buffer();
//        buf.writeByte(str.length);
//        buf.writeBytes(str);
//        byte[] brand = buf.array();
//        buf.release();
//        player.sendPluginMessage(plugin, CHANNEL, brand);
    }
}
