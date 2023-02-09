package me.matiego.st14;

import lombok.Getter;
import me.matiego.st14.utils.Pair;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public class TeleportsManager {
    private final HashMap<UUID, Pair<BukkitTask, CompletableFuture<Response>>> tasks = new HashMap<>();
    private final HashMap<UUID, BlockLocation> location = new HashMap<>();

    public synchronized boolean isAlreadyActive(@NotNull Player player) {
        return tasks.get(player.getUniqueId()) != null;
    }

    public synchronized @NotNull CompletableFuture<Response> teleport(@NotNull Player player, @NotNull Location loc, int time, @NotNull Callable<Boolean> teleportWhenReady) {
        CompletableFuture<Response> future = new CompletableFuture<>();
        if (isAlreadyActive(player)) {
            future.complete(Response.ALREADY_ACTIVE);
            return future;
        }
        location.put(player.getUniqueId(), BlockLocation.parseLocation(player.getLocation()));
        tasks.put(player.getUniqueId(), new Pair<>(
                Bukkit.getScheduler().runTaskLater(
                        Main.getInstance(),
                        () -> {
                            CompletableFuture<Response> result = tasks.remove(player.getUniqueId()).getSecond();
                            try {
                                if (!teleportWhenReady.call()) {
                                    result.complete(Response.CANCELLED);
                                    return;
                                }
                            } catch (Exception e) {
                                result.complete(Response.CANCELLED);
                                return;
                            }
                            if (Main.getInstance().getAntyLogoutManager().isInAntyLogout(player)) {
                                result.complete(Response.CANCELLED);
                                return;
                            }
                            BlockLocation previousLocation = location.remove(player.getUniqueId());
                            if (previousLocation == null || !previousLocation.equals(player.getLocation())) {
                                result.complete(Response.MOVE);
                                return;
                            }
                            player.teleportAsync(loc).thenAccept(b -> result.complete(b ? Response.SUCCESS : Response.FAILURE));
                        },
                        time * 20L
                ),
                future
        ));
        return future;
    }

    public void onMove(@NotNull Player player) {
        UUID uuid = player.getUniqueId();

        BlockLocation loc = location.get(uuid);
        if (loc == null) return;
        if (loc.equals(player.getLocation())) return;

        Pair<BukkitTask, CompletableFuture<Response>> task = tasks.remove(uuid);
        if (task == null) return;
        location.remove(uuid);

        task.getFirst().cancel();
        task.getSecond().complete(Response.MOVE);
    }

    public void onPlayerQuit(@NotNull Player player) {
        UUID uuid = player.getUniqueId();

        Pair<BukkitTask, CompletableFuture<Response>> task = tasks.remove(uuid);
        if (task == null) return;
        location.remove(uuid);

        task.getFirst().cancel();
        task.getSecond().complete(Response.MOVE);
    }

    public void cancelAll() {
        Iterator<Map.Entry<UUID, Pair<BukkitTask, CompletableFuture<Response>>>> it = tasks.entrySet().iterator();
        while (it.hasNext()) {
            Pair<BukkitTask, CompletableFuture<Response>> cur = it.next().getValue();
            it.remove();
            cur.getFirst().cancel();
            cur.getSecond().complete(Response.DISABLED);
        }
        location.clear();
    }

    public enum Response {
        SUCCESS,
        ALREADY_ACTIVE,
        MOVE,
        CANCELLED,
        DISABLED,
        FAILURE
    }

    private static class BlockLocation {
        private BlockLocation(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        public static @NotNull BlockLocation parseLocation(@NotNull Location location) {
            return new BlockLocation(location.getX(), location.getY(), location.getZ());
        }

        @Getter private final double x;
        @Getter private final double y;
        @Getter private final double z;

        public boolean equals(@NotNull Location location) {
            return getX() == location.getX() && getY() == location.getY() && getZ() == location.getZ();
        }
    }
}
