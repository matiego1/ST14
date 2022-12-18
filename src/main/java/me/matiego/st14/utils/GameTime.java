package me.matiego.st14.utils;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

public class GameTime {
    public GameTime(int normal, int afk, int incognito) {
        this.normal = normal;
        this.afk = afk;
        this.incognito = incognito;
    }

    @Getter private int normal;
    @Getter private int afk;
    @Getter private int incognito;

    public void addNormal(int time) {
        normal += time;
    }
    public void addAfk(int time) {
        afk += time;
    }
    public void addIncognito(int time) {
        incognito += time;
    }

    public enum Type {
        NORMAL,
        AFK,
        INCOGNITO
    }

    public static @NotNull GameTime add(@NotNull GameTime... times) {
        GameTime result = new GameTime(0, 0, 0);
        for (GameTime time : times) {
            result.addNormal(time.getNormal());
            result.addAfk(time.getAfk());
            result.addIncognito(time.getIncognito());
        }
        return result;
    }
}
