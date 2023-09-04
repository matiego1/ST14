package me.matiego.st14.rankings;

import me.matiego.st14.Main;
import me.matiego.st14.objects.Ranking;
import org.jetbrains.annotations.NotNull;

public class EconomyRanking extends Ranking {
    @Override
    protected @NotNull String getTableName() {
        return "st14_economy";
    }

    @Override
    protected @NotNull String getColumnName() {
        return "money";
    }

    @Override
    public @NotNull String formatScore(long score) {
        return Main.getInstance().getEconomyManager().format(score);
    }
}
