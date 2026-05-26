package me.matiego.st14.objects.minigames;

import lombok.Getter;
import lombok.Setter;
import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.minigames.*;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public enum MiniGameType {
    ELYTRA(ElytraMiniGame.class, "Wyścigi elytrą", Material.ELYTRA, 10),
    TAG(TagMiniGame.class, "Berek", Material.NAME_TAG, 10),
    DEATH_HUNT(null, "Death Hunt", Material.WATER_BUCKET, 10),

    HIDE_AND_SEEK(null, "Chowany", Material.TALL_GRASS, 15),
    SNOWBALLS_BATTLE(SnowballsBattleMiniGame.class, "Bitwa na śnieżki", Material.SNOWBALL, 15),
    TNT_RUN(TNTRunMiniGame.class, "TNT run", Material.TNT, 15),
    SPLEEF(SpleefMiniGame.class, "Spleef", Material.STONE_SHOVEL, 15),
    RED_GREEN(RedGreenMiniGame.class, "Czerwone-Zielone", Material.SPECTRAL_ARROW, 15),
    MAZE(MazeMiniGame.class, "Labirynt", Material.BRICKS, 15),
    PVP(PvPMiniGame.class, "PvP", Material.WOODEN_SWORD, 15),
    SUMO(SumoMiniGame.class, "Sumo", Material.PANDA_SPAWN_EGG, 15),
    RANDOM_ITEMS(RandomItemsMiniGame.class, "Losowe itemy", Material.BEDROCK, 15),

    ITEM_RACE(null, "Item race", Material.SHULKER_BOX, 20),
    SKYWARS(SkywarsMiniGame.class, "Skywars", Material.ENDER_EYE, 20),
    PARKOUR(ParkourMiniGame.class, "Parkour", Material.POTION, 20),

    BLOCKED_IN_COMBAT(null, "Blocked in combat", Material.STONE, 30),
    UHC(UHCMiniHame.class, "UHC", Material.GOLDEN_APPLE, 30),
    MANHUNT(null, "Manhunt", Material.DIAMOND_SWORD, 30),
    DEATH_SWAP(null, "Death Swap", Material.ENDER_PEARL, 30);


    private final Class<? extends MiniGame> handler;
    @Getter private final String name;
    @Getter private final Material guiMaterial;
    @Getter private final int gameTimeInSeconds;
    @Getter @Setter private String previousMapName;

    MiniGameType(@Nullable Class<? extends MiniGame> handler, @NotNull String name, @NotNull Material guiMaterial, int gameTimeInMinutes) {
        this.handler = handler;
        this.name = name;
        this.guiMaterial = guiMaterial;
        this.gameTimeInSeconds = gameTimeInMinutes * 60;
    }

    public boolean isMiniGameEnabled() {
        Main plugin = Main.getInstance();
        if (plugin == null) return false;

        if (handler == null) return false;

        return plugin.getConfig().getStringList("minigames.enabled").contains(name().toLowerCase());
    }

    public @NotNull List<String> getMaps() {
        Main plugin = Main.getInstance();
        if (plugin == null) return new ArrayList<>();
        return MiniGame.getMaps(plugin, getConfigPath());
    }

    public @Nullable MiniGame getNewHandlerInstance(@Nullable String mapName) {
        Main plugin = Main.getInstance();
        if (plugin == null) return null;

        if (!isMiniGameEnabled()) return null;

        if (handler == null) return null;
        try {
            return handler.getConstructor(Main.class, MiniGameType.class, String.class).newInstance(plugin, this, mapName);
        } catch (Exception e) {
            Logs.error("Failed to get a constructor of a minigame class", e);
        }
        return null;
    }

    public @NotNull String getConfigPath() {
        return "minigames." + name().toLowerCase().replace("_", "-") + ".";
    }

    public static @Nullable MiniGameType getMiniGameTypeByName(@NotNull String name) {
        for (MiniGameType type : values()) {
            if (type.getName().equals(name)) return type;
        }
        return null;
    }
}
