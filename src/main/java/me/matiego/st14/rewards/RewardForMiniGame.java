package me.matiego.st14.rewards;

import me.matiego.st14.Main;
import me.matiego.st14.objects.rewards.Reward;
import org.jetbrains.annotations.NotNull;

public class RewardForMiniGame extends Reward {
    public RewardForMiniGame(@NotNull Main plugin) {
        super(plugin);
    }

    @Override
    protected @NotNull String getTableSuffix() {
        return "mg";
    }
}
