package me.matiego.st14.utils;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.mvplugins.multiverse.core.MultiverseCore;
import org.mvplugins.multiverse.core.world.WorldManager;
import org.mvplugins.multiverse.core.world.options.RegenWorldOptions;

import java.util.concurrent.CompletableFuture;

public class MultiverseCoreUtils {
    private static boolean running = false;

    public static @NotNull CompletableFuture<String> regenWorld(@NotNull World world, @NotNull String seed) {
        synchronized (MultiverseCoreUtils.class) {
            if (running) return CompletableFuture.completedFuture("Some other world is being regenerated now");
            running = true;
        }

        CompletableFuture<String> future = regenWorld0(world, seed);

        synchronized (MultiverseCoreUtils.class) {
            running = false;
        }

        return future;
    }

    private static @NotNull CompletableFuture<String> regenWorld0(@NotNull World world, @NotNull String seed) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        if (plugin == null || !plugin.isEnabled()) {
            return CompletableFuture.completedFuture("Multiverse-Core plugin is not enabled");
        }

        CompletableFuture<String> future = new CompletableFuture<>();

        WorldManager worldManager = ((MultiverseCore) plugin).getApi().getWorldManager();
        worldManager.loadWorld(world.getName())
                .onSuccess(mvWorld -> {
                    RegenWorldOptions options = RegenWorldOptions.world(mvWorld)
                            .seed(seed)
                            .keepGameRule(true)
                            .keepWorldBorder(true)
                            .keepWorldConfig(true);
                    worldManager.regenWorld(options)
                            .onSuccess(() -> future.complete(null))
                            .onFailure(reason -> future.complete(reason.getFailureMessage().formatted()));
                })
                .onFailure(reason -> future.complete(reason.getFailureMessage().formatted()));

        return future;
    }
}
