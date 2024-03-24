package me.matiego.st14.objects.times;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

public class GameTime {
    public GameTime(long normal, long afk, long incognito) {
        this.normal = normal;
        this.afk = afk;
        this.incognito = incognito;
    }

    public static final GameTime EMPTY = empty();
    @Getter @Setter private long normal;
    @Getter @Setter private long afk;
    @Getter @Setter private long incognito;

    public void addNormal(long time) {
        normal += time;
    }
    public void addAfk(long time) {
        afk += time;
    }
    public void addIncognito(long time) {
        incognito += time;
    }

    public enum Type {
        NORMAL,
        AFK,
        INCOGNITO
    }

    public static @NotNull GameTime add(@NotNull GameTime... times) {
        GameTime result = GameTime.empty();
        for (GameTime time : times) {
            result.addNormal(time.getNormal());
            result.addAfk(time.getAfk());
            result.addIncognito(time.getIncognito());
        }
        return result;
    }
    
    public static @NotNull GameTime empty() {
        return new GameTime(0, 0, 0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GameTime gameTime = (GameTime) o;
        return getNormal() == gameTime.getNormal() && getAfk() == gameTime.getAfk() && getIncognito() == gameTime.getIncognito();
    }
}
