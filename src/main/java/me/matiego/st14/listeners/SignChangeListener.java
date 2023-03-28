package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.utils.Utils;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SignChangeListener implements Listener {
    public SignChangeListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSignChange(@NotNull SignChangeEvent event) {
        Sign sign = getSign(event.getBlock());
        if (sign == null) return;

        List<String> lines = event.lines().stream().map(c -> PlainTextComponentSerializer.plainText().serialize(c)).toList();
        if (lines.isEmpty()) return;
        if (!lines.get(0).equalsIgnoreCase("[map]")) return;

        StringBuilder textBuilder = new StringBuilder();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.isBlank()) {
                textBuilder.append(lines.get(i)).append("\n");
            }
        }

        String text = textBuilder.toString();
        if (text.isBlank()) return;

        if (plugin.getDynmap().addSignMarker(sign.getLocation(), text)) {
            event.getPlayer().sendMessage(Utils.getComponentByString("&aPomyślnie dodano tabliczkę do mapy."));
        }
    }

    private @Nullable Sign getSign(@NotNull Block block) {
        if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) return null;
        return block.getState() instanceof Sign sign ? sign : null;
    }
}
