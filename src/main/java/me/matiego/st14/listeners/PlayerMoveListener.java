package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.utils.Utils;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.UUID;

public class PlayerMoveListener implements Listener {
    public PlayerMoveListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;
    private final HashMap<UUID, BossBar> positionBossBars = new HashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        Player player = event.getPlayer();
        plugin.getAfkManager().move(player);
        if (event.hasChangedBlock()) {
            if (player.isGliding()) {
                BossBar bar = positionBossBars.get(player.getUniqueId());
                if (bar == null) {
                    bar = BossBar.bossBar(Utils.getComponentByString(""), 1, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
                    player.showBossBar(bar);
                    positionBossBars.put(player.getUniqueId(), bar);
                }
                Block block = player.getLocation().getBlock();
                final BossBar finalBar = bar;
                Utils.async(() -> finalBar.name(Utils.getComponentByString("&6X: &e" + block.getX() + " &6Y: &e" + block.getY() + " &6Z: &e" + block.getZ())));
            } else {
                BossBar bar = positionBossBars.remove(player.getUniqueId());
                if (bar != null) {
                    player.hideBossBar(bar);
                }
            }
        }
    }

    public void removeBossBarForPlayer(@NotNull Player player) {
        positionBossBars.remove(player.getUniqueId());
    }
}
