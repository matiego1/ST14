package me.matiego.st14.minigames;

import lombok.Getter;
import me.matiego.st14.Main;
import me.matiego.st14.minigames.handlers.SnowballsBattleMiniGame;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum MiniGameType {
    SNOWBALLS_BATTLE(SnowballsBattleMiniGame.class, "Bitwa na śnieżki", Material.SNOWBALL, 15 * 60);

    private final Class<? extends MiniGame> handler;
    @Getter private final String name;
    @Getter private final Material guiMaterial;
    @Getter private final int gameTimeInSeconds;

    MiniGameType(@Nullable Class<? extends MiniGame> handler, @NotNull String name, @NotNull Material guiMaterial, int gameTimeInSeconds) {
        this.handler = handler;
        this.name = name;
        this.guiMaterial = guiMaterial;
        this.gameTimeInSeconds = gameTimeInSeconds;
    }

    public @Nullable MiniGame getNewHandlerInstance() {
        Main plugin = Main.getInstance();
        if (plugin == null) return null;

        if (!plugin.getConfig().getStringList("minigames.enabled").contains(name().toLowerCase())) return null;

        if (handler == null) return null;
        try {
            return handler.getConstructor(Main.class, Integer.class).newInstance(plugin, getGameTimeInSeconds());
        } catch (Exception ignored) {}
        return null;
    }

    public static @Nullable MiniGameType getMiniGameTypeByName(@NotNull String name) {
        for (MiniGameType type : values()) {
            if (type.getName().equals(name)) return type;
        }
        return null;
    }
}
