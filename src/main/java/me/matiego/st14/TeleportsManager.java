package me.matiego.st14;

import me.matiego.st14.utils.Pair;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public class TeleportsManager implements Listener {
    HashMap<UUID, Pair<BukkitTask, CompletableFuture<Response>>> tasks = new HashMap<>();

    public synchronized boolean isAlreadyActive(@NotNull Player player) {
        return tasks.get(player.getUniqueId()) != null;
    }

    public synchronized @NotNull CompletableFuture<Response> teleport(@NotNull Player player, @NotNull Location loc, int time) {
        return teleport(player, loc, time, () -> true);
    }
    public synchronized @NotNull CompletableFuture<Response> teleport(@NotNull Player player, @NotNull Location loc, int time, @NotNull Callable<Boolean> teleportWhenReady) {

        CompletableFuture<Response> future = new CompletableFuture<>();
        if (tasks.get(player.getUniqueId()) != null) {
            future.complete(Response.ALREADY_ACTIVE);
            return future;
        }
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
                            player.teleportAsync(loc).thenAccept(b -> result.complete(b ? Response.SUCCESS : Response.FAILURE));
                        },
                        time * 20L
                ),
                future
        ));
        return future;
    }

    @EventHandler
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        Pair<BukkitTask, CompletableFuture<Response>> task = tasks.remove(event.getPlayer().getUniqueId());
        if (task == null) return;
        task.getFirst().cancel();
        task.getSecond().complete(Response.MOVE);
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Pair<BukkitTask, CompletableFuture<Response>> task = tasks.remove(event.getPlayer().getUniqueId());
        if (task == null) return;
        task.getFirst().cancel();
        task.getSecond().complete(Response.MOVE);
    }

    public void cancelAll() {
        for (Pair<BukkitTask, CompletableFuture<Response>> task : tasks.values()) {
            task.getFirst().cancel();
            task.getSecond().complete(Response.FAILURE);
        }
    }

    public enum Response {
        SUCCESS,
        ALREADY_ACTIVE,
        MOVE,
        CANCELLED,
        FAILURE
    }
}
