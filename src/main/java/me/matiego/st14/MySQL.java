package me.matiego.st14;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;

public class MySQL {

    private final HikariDataSource ds;

    /**
     * Initials the database.
     * @param url a jdbc url
     * @param user a user
     * @param password a password
     * @throws SQLException thrown if connection has failed.
     */
    public MySQL(@NotNull String url, @NotNull String user, @NotNull String password) throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setPoolName("Counting-Connection-Pool");

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2058");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        config.addDataSourceProperty("allowLoadLocalInfile", "true");

        ds = new HikariDataSource(config);
        getConnection(); //test connection
    }

    /**
     * Closes the database connection.
     */
    public void close() {
        ds.close();
    }

    /**
     * Returns a database connection.
     * @return the database connection.
     * @throws SQLException thrown if connection has failed.
     */
    public @NotNull Connection getConnection() throws SQLException {
        return ds.getConnection();
    }

    public boolean createTables() {
        if (!Economy.createTable()) return false;
        if (!OfflinePlayers.createTable()) return false;
        if (!IncognitoManager.createTable()) return false;
        if (!TimeManager.createTable()) return false;
        if (!PremiumManager.createTable()) return false;
        if (!WorldsLastLocation.createTable()) return false;
        if (!BackpackManager.createTable()) return false;
        if (!RewardsManager.createTable()) return false;
        return AccountsManager.createTable();
    }
}
