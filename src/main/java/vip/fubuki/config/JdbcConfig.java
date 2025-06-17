package vip.fubuki.config;


import org.bukkit.configuration.file.FileConfiguration;

import java.util.Random;


public class JdbcConfig {
    public String HOST;
    public int PORT;
    public String USERNAME;
    public String PASSWORD;
    public String DATABASE_NAME;
    public Boolean SYNC_ADVANCEMENTS;

    public Integer SERVER_ID;
    public Boolean IS_CHAT_SERVER;
    public String CHAT_SERVER_IP;
    public Integer CHAT_SERVER_PORT;



    public JdbcConfig(FileConfiguration config) {
        HOST = config.getString("host", "localhost");
        PORT = config.getInt("port", 3306);
        USERNAME = config.getString("username", "playersync");
        PASSWORD = config.getString("password", "playersync");
        DATABASE_NAME = config.getString("database_name", "playersync");
        if (config.getInt("server_id", 0) == 0) {
            SERVER_ID = new Random().nextInt(100000);
            config.set("server_id", SERVER_ID);
        }
        SERVER_ID = config.getInt("server_id", 0);
        IS_CHAT_SERVER = config.getBoolean("is_chat_server", false);
        CHAT_SERVER_IP = config.getString("chat_server_ip", "localhost");
        CHAT_SERVER_PORT = config.getInt("chat_server_port", 7900);
    }
}
