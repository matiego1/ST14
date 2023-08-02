package me.matiego.st14;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

public enum Prefix {
    AFK("&5[&dAFK&5]&d ", "AFK"),
    INCOGNITO("&8[&7INC&8]&f ", ""),
    TIME("&6[&eCzas&6]&e ", ""),
    PREMIUM("&6[&bPremium&6]&b ", "Premium"),
    WORLDS("&1[&3Światy&1]&3 ", "Światy"),
    TPA("&2[&6TPA&2]&e ", "TPA"),
    ECONOMY("&9[&b$&9]&b ", "**[$]** "),
    DISCORD("&1[&9DC&1]&b ", ""),
    SUICIDE("&4[&cSamobójstwa&4]&c ", "Samobójstwa"),
    DIFFICULTY("", "Poziom trudności"),
    GAMEMODE("", "Tryb gry"),
    ANTY_LOGOUT("&4[&cANTY-LOGOUT&4]&c ", "Anty-logout"),
    SLEEPING_THROUGH_NIGHT("", "Przesypianie nocy"),
    MINI_GAMES("&d[&9MG&d]&e ", "Minigry"),
    AUTO_MOD("[AutoMod]", ""),
    HOME("&6[&aDom&6]&a ", "");

    @Getter private final String minecraft;
    @Getter private final String discord;
    Prefix(@NotNull String minecraft, @NotNull String discord) {
        this.minecraft = minecraft;
        this.discord = discord;
    }

    @Override
    public String toString() {
        return getMinecraft();
    }
}
