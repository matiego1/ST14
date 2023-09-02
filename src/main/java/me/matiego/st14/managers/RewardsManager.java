package me.matiego.st14.managers;

import lombok.Getter;
import lombok.Setter;
import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.rewards.RewardForCounting;
import me.matiego.st14.rewards.RewardForMiniGame;
import me.matiego.st14.rewards.RewardForPlaying;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class RewardsManager {
    public RewardsManager(@NotNull Main plugin) {
        this.rewardForPlaying = new RewardForPlaying(plugin);
        this.rewardForCounting = new RewardForCounting(plugin);
        this.rewardForMiniGame = new RewardForMiniGame(plugin);
    }

    @Getter private final RewardForPlaying rewardForPlaying;
    @Getter private final RewardForCounting rewardForCounting;
    @Getter private final RewardForMiniGame rewardForMiniGame;

    public static boolean createTable() {
        try (Connection conn = Main.getInstance().getMySQLConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS st14_rewards_rfp(uuid VARCHAR(36) NOT NULL, amount INT NOT NULL, last BIGINT NOT NULL, PRIMARY KEY (uuid))")) {
                stmt.execute();
            }
            try (PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS st14_rewards_counting(uuid VARCHAR(36) NOT NULL, amount INT NOT NULL, last BIGINT NOT NULL, PRIMARY KEY (uuid))")) {
                stmt.execute();
            }
            try (PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS st14_rewards_mg(uuid VARCHAR(36) NOT NULL, amount INT NOT NULL, last BIGINT NOT NULL, PRIMARY KEY (uuid))")) {
                stmt.execute();
            }
            return true;
        } catch (SQLException e) {
            Logs.error("An error occurred while creating the database table \"st14_rewards\"", e);
        }
        return false;
    }

    public static class Data {
        public Data(double limit, long last) {
            this.limit = limit;
            this.last = last;
        }
        @Setter
        @Getter
        private double limit;
        @Setter @Getter private long last;
        public void addLast(long value) {
            last += value;
        }
    }
}
