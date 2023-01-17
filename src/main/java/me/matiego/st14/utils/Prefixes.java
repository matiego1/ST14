package me.matiego.st14.utils;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

public enum Prefixes{
    AFK("&5[&dAFK&5]&d ", "AFK"),
    INCOGNITO("&8[&7INC&8]&f ", ""),
    TIME("&6[&eCzas&6]&e ", ""),
    PREMIUM("&6[&dPremium&6]&d ", "Premium"),
    WORLDS("&1[&3Światy&1]&3 ", "Światy"),
    TPA("&2[&6TPA&2]&e ", "TPA"),
    ECONOMY("&9[&b$&9]&b ", "**[$]** "),
    DISCORD("&1[&9DC&1]&b ", ""),
    SUICIDE("&4[&cSamobójstwa&4]&c ", "Samobójstwa");

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
