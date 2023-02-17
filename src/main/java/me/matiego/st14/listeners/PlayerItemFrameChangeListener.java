package me.matiego.st14.listeners;

import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import me.matiego.st14.Main;
import me.matiego.st14.utils.FixedSizeMap;
import me.matiego.st14.utils.Utils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Rotation;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class PlayerItemFrameChangeListener implements Listener {
    public PlayerItemFrameChangeListener(@NotNull Main plugin) {
        INVISIBLE_ITEM_FRAME = new NamespacedKey(plugin, "invisible_item_frame");
    }
    private final FixedSizeMap<UUID, Long> lastItemFrameRotation = new FixedSizeMap<>(100);
    private final NamespacedKey INVISIBLE_ITEM_FRAME;

    @EventHandler(ignoreCancelled = true)
    public void onPlayerItemFrameChange(@NotNull PlayerItemFrameChangeEvent event) {
        Player player = event.getPlayer();
        ItemFrame frame = event.getItemFrame();

        switch (event.getAction()) {
            case ROTATE -> {
                long now = Utils.now();
                long last = lastItemFrameRotation.getOrDefault(player.getUniqueId(), now);
                lastItemFrameRotation.put(player.getUniqueId(), now);

                now -= last;
                if (now <= 0 || now > 5_000) {
                    event.setCancelled(true);
                    player.sendMessage(Utils.getComponentByString("&cAby obrócić ten przedmiot, kliknij ponownie!"));
                }
            }
            case PLACE -> {
                frame.setRotation(Rotation.NONE);
                if (event.getItemStack().getType() == Material.SHEARS) {
                    frame.getPersistentDataContainer().set(INVISIBLE_ITEM_FRAME, PersistentDataType.BYTE, (byte) 1);
                }
                if (frame.getPersistentDataContainer().has(INVISIBLE_ITEM_FRAME, PersistentDataType.BYTE)) {
                    frame.setVisible(false);
                }
            }
            case REMOVE -> {
                if (frame.getPersistentDataContainer().has(INVISIBLE_ITEM_FRAME, PersistentDataType.BYTE)) {
                    frame.setVisible(true);
                }
            }
        }
    }
}
