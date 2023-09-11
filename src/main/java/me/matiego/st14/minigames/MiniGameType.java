package me.matiego.st14.minigames;

import lombok.Getter;
import me.matiego.st14.Main;
import me.matiego.st14.minigames.handlers.*;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum MiniGameType {
    TAG(null, "Berek", Material.NAME_TAG, 10 * 60),
    HIDE_AND_SEEK(null, "Chowany", Material.TALL_GRASS, 15 * 60),
    SNOWBALLS_BATTLE(SnowballsBattleMiniGame.class, "Bitwa na śnieżki", Material.SNOWBALL, 15 * 60),
    TNT_RUN(TNTRunMiniGame.class, "TNT Run", Material.TNT, 15 * 60),
    SPLEEF(SpleefMiniGame.class, "Spleef", Material.STONE_SHOVEL, 15 * 60),
    RED_GREEN(RedGreenMiniGame.class, "Czerwone-Zielone", Material.BOW, 15 * 60),
    MAZE(MazeMiniGame.class, "Labirynt", Material.BRICKS, 15 * 60),
    PVP(null, "PvP", Material.STICK, 15 * 60),
    SKYWARS(SkywarsMiniGame.class, "Skywars", Material.ENDER_EYE, 20 * 60),
    PARKOUR(ParkourMiniGame.class, "Parkour", Material.POTION, 25 * 60),
    BLOCKED_IN_COMBAT(null, "Blocked in combat", Material.STONE, 30 * 60),
    UHC(null, "UHC", Material.GOLDEN_APPLE, 30 * 60),
    MANHUNT(null, "Manhunt", Material.DIAMOND_SWORD, 30 * 60),
    DEATH_SWAP(null, "Death Swap", Material.ENDER_PEARL, 30 * 60);

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

    public boolean isMiniGameEnabled() {
        Main plugin = Main.getInstance();
        if (plugin == null) return false;

        if (handler == null) return false;

        return plugin.getConfig().getStringList("minigames.enabled").contains(name().toLowerCase());
    }

    public @Nullable MiniGame getNewHandlerInstance() {
        Main plugin = Main.getInstance();
        if (plugin == null) return null;

        if (!isMiniGameEnabled()) return null;

        if (handler == null) return null;
        try {
            return handler.getConstructor(Main.class, Integer.TYPE).newInstance(plugin, getGameTimeInSeconds());
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
