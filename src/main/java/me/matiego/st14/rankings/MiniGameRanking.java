package me.matiego.st14.rankings;

import me.matiego.st14.objects.Ranking;
import org.jetbrains.annotations.NotNull;

public class MiniGameRanking extends Ranking {
    @Override
    protected @NotNull String getTableName() {
        return "st14_minigames";
    }

    @Override
    protected @NotNull String getColumnName() {
        return "wins";
    }

    @Override
    public @NotNull String formatScore(long score) {
        return String.valueOf(score);
    }

}
