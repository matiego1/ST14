package me.matiego.st14.utils;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

public enum Prefixes{
    AFK("&5[&dAFK&5]&d ", "AFK"),
    INCOGNITO("&8[&7INC&8]&f ", ""),
    TIME("&6[&eCZAS&6]&e ", ""),
    PREMIUM("&6[&dPREMIUM&6]&d ", "Premium"),
    DISCORD("&1[&9DC&1]&b ", "");

    @Getter private final String minecraft;
    @Getter private final String discord;
    Prefixes(@NotNull String minecraft, @NotNull String discord) {
        this.minecraft = minecraft;
        this.discord = discord;
    }

    @Override
    public String toString() {
        return getMinecraft();
    }
}
