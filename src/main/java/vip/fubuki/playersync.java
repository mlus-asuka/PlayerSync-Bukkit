package vip.fubuki;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import vip.fubuki.chat.ChatSyncClient;
import vip.fubuki.config.JdbcConfig;
import vip.fubuki.sync.ChatSync;
import vip.fubuki.sync.VanillaSync;
import vip.fubuki.util.JDBCsetUp;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class playersync extends JavaPlugin {
    private FileConfiguration config;
    public static playersync instance;
    public static JdbcConfig JdbcConfig;

    public playersync() {
        instance = this;
    }
    @Override
    public void onEnable() {
        saveDefaultConfig();

        config = getConfig();

        JdbcConfig = new JdbcConfig(config);

        ChatSync.register();

        getServer().getPluginManager().registerEvents(new VanillaSync(), this);
        getServer().getPluginManager().registerEvents(new ChatSyncClient(), this);

        String dbName = JdbcConfig.DATABASE_NAME;

        // Step 1: Create the database using a connection that does not select a database.
        try {
            JDBCsetUp.executeUpdate("CREATE DATABASE IF NOT EXISTS " + dbName, 1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // Step 2: Explicitly select the database on a connection obtained without default database.
        try (Connection conn = JDBCsetUp.getConnection(false);
             Statement st = conn.createStatement()) {
            st.execute("USE " + dbName);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // Step 3: Create and alter tables using fully qualified names.
        // Create player_data table
        try {
            JDBCsetUp.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + dbName + ".`player_data` (" +
                            "`uuid` char(36) NOT NULL," +
                            "`inventory` mediumblob," +
                            "`armor` blob," +
                            "`advancements` blob," +
                            "`enderchest` mediumblob," +
                            "`effects` blob," +
                            "`left_hand` blob," +
                            "`cursors` blob," +
                            "`xp` int DEFAULT NULL," +
                            "`food_level` int DEFAULT NULL," +
                            "`score` int DEFAULT NULL," +
                            "`health` int DEFAULT NULL," +
                            "`online` tinyint(1) DEFAULT NULL," +
                            "`last_server` int DEFAULT NULL," +
                            "PRIMARY KEY (`uuid`)" +
                            ");"
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // Create server_info table
        try {
            JDBCsetUp.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + dbName + ".server_info (" +
                            "`id` INT NOT NULL," +
                            "`enable` boolean NOT NULL," +
                            "`last_update` BIGINT NOT NULL," +
                            "PRIMARY KEY (`id`)" +
                            ");"
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        long current = System.currentTimeMillis();
        try {
            JDBCsetUp.executeUpdate(
                    "INSERT INTO " + dbName + ".server_info(id,enable,last_update) " +
                            "VALUES(" + JdbcConfig.SERVER_ID + ",true," + current + ") " +
                            "ON DUPLICATE KEY UPDATE id= " + JdbcConfig.SERVER_ID + ",enable = 1," +
                            "last_update=" + current + ";"
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        try {
            JDBCsetUp.executeUpdate(
                    "UPDATE " + dbName + ".server_info SET last_update=" + System.currentTimeMillis() +
                            " WHERE id='" + JdbcConfig.SERVER_ID + "'"
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        // ----- END NEW BLOCK -----

        getLogger().info("PlayerSync is ready!");
    }

    @Override
    public void onDisable() {
        saveConfig();
    }
}
