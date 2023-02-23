package me.matiego.st14.minigames;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class MiniGameException extends Exception {
    public MiniGameException() {}
    public MiniGameException(@NotNull String msg) {
        super(msg);
    }
    public MiniGameException(@NotNull Throwable err) {
        super(err);
    }
    public MiniGameException(@NotNull String msg, @NotNull Throwable err) {
        super(msg, err);
    }
}
