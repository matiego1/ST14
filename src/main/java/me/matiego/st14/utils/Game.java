package me.matiego.st14.utils;

import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import javax.annotation.Nonnull;
import java.util.Set;

public interface Game {
    @NotNull World getWorld();
    void onPlayerJoin(@NotNull Player player);
    void onPlayerQuit(@NotNull Player player);
    void onPlayerDeath(@NotNull Player player);
    boolean isInGame(@NotNull Player player);
    @Range(from = 2, to = Integer.MAX_VALUE) int getMinimumPlayersAmount();
    @Range(from = 2, to = Integer.MAX_VALUE) int getMaximumPlayersAmount();
    boolean isStarted();
    void startGame(@NotNull Set<Player> players, @NotNull Player sender);
    void stopGame(@Nonnull CommandSender sender);
}
