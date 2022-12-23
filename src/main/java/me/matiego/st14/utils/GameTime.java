package me.matiego.st14.utils;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

public class GameTime {
    public GameTime(int normal, int afk, int incognito) {
        this.normal = normal;
        this.afk = afk;
        this.incognito = incognito;
    }

    @Getter @Setter private int normal;
    @Getter @Setter private int afk;
    @Getter @Setter private int incognito;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (!(o instanceof GameTime time)) return false;
        return getNormal() == time.getNormal() &&
                getAfk() == time.getAfk() &&
                getIncognito() == time.getIncognito();
    }
}
