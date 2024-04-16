package me.matiego.st14.utils;

import com.onarandombox.MultiverseCore.MultiverseCore;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class MultiverseCoreUtils {
    private static boolean running = false;

    public static void regenWorld(@NotNull World world, @NotNull String seed) throws IllegalStateException {
        synchronized (MultiverseCoreUtils.class) {
            if (running) throw new IllegalStateException("Some world is already being regenerated");
            running = true;
        }

        Plugin plugin = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        if (plugin == null || !plugin.isEnabled()) {
            throw new IllegalStateException("Multiverse-Core plugin is not enabled");
        }

        if (!((MultiverseCore) plugin).getCore().getMVWorldManager().regenWorld(world.getName(), true, false, seed, true)) {
            throw new IllegalStateException("The world failed to regenerate");
        }

        synchronized (MultiverseCoreUtils.class) {
            running = false;
        }
    }
}
