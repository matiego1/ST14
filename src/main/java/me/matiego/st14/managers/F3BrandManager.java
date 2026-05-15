package me.matiego.st14.managers;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.injector.netty.WirePacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.SneakyThrows;
import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

// Based on: https://github.com/LoreSchaeffer/CustomF3Brand/blob/main/src/main/java/it/multicoredev/cf3b/bukkit/BrandUpdater.java
public class F3BrandManager {
    @SneakyThrows
    public F3BrandManager(@NotNull Main plugin) {
        this.plugin = plugin;

        this.manager = ProtocolLibrary.getProtocolManager();
        this.packetDataSerializer = Class.forName("net.minecraft.network.PacketDataSerializer");
    }

    private final Main plugin;
    private final ProtocolManager manager;
    private final Class<?> packetDataSerializer;

    public void sendF3Brand(@NotNull Player player) {
        ByteBuf buf = getPacketDataSerializer();
        if (buf == null) return;

        if (!writeString(buf, "minecraft:brand")) return;
        if (!writeString(buf, plugin.getConfig().getString("brands.server-brand", "&aSerwer ST14&r").replace("&", "§"))) return;

        byte[] data = new byte[buf.readableBytes()];
        for (int i = 0; i < data.length; i++) data[i] = buf.getByte(i);

        try {
            WirePacket customPacket = new WirePacket(PacketType.Play.Server.CUSTOM_PAYLOAD, data);
            manager.sendWirePacket(player, customPacket);
        } catch (Exception ignored) {}
    }

    private @Nullable ByteBuf getPacketDataSerializer() {
        try {
            Constructor<?> constructor = packetDataSerializer.getConstructor(ByteBuf.class);
            return (ByteBuf) constructor.newInstance(Unpooled.buffer());
        } catch (Exception e) {
            Logs.error("Failed to send the f3 server brand", e);
        }
        return null;
    }

    private boolean writeString(@NotNull Object buf, @NotNull String data) {
        try {
            Method writeString = packetDataSerializer.getDeclaredMethod("a", String.class);
            writeString.invoke(buf, data);
            return true;
        } catch (Exception e) {
            Logs.error("Failed to send the f3 server brand", e);
        }
        return false;
    }
}
