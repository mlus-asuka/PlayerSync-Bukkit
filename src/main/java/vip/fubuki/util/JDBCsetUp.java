package vip.fubuki.util;

import vip.fubuki.playersync;

import java.sql.*;

public class JDBCsetUp {


    /**
     * Returns a connection to the MySQL server.
     * @param selectDatabase if true, the returned URL includes the configured database name.
     * @return a Connection object with the database explicitly selected.
     * @throws SQLException if a database access error occurs.
     */
    public static Connection getConnection(boolean selectDatabase) throws SQLException {
        String dbName = playersync.JdbcConfig.DATABASE_NAME;
        // Build the base URL
        String url = "jdbc:mysql://" + playersync.JdbcConfig.HOST + ":" + playersync.JdbcConfig.PORT;
        if (selectDatabase && dbName != null && !dbName.isEmpty()) {
            url += "/" + dbName;
        }
        url += "?useUnicode=true&characterEncoding=utf-8&useSSL=false"
                + "&serverTimezone=UTC&allowPublicKeyRetrieval=true";
        Connection conn = DriverManager.getConnection(url, playersync.JdbcConfig.USERNAME, playersync.JdbcConfig.PASSWORD);
        // Ensure that the connection uses the desired database by explicitly issuing "USE dbName"
        if (selectDatabase && dbName != null && !dbName.isEmpty()) {
            try (Statement st = conn.createStatement()) {
                st.execute("USE " + dbName);
            }
        }
        return conn;
    }

    // Default connection always includes the database.
    public static Connection getConnection() throws SQLException {
        return getConnection(true);
    }

    /**
     * Executes a query using a connection that includes the database.
     */
    public static QueryResult executeQuery(String sql) throws SQLException {
        Connection connection = getConnection();  // With database selected (and "USE" already run)
        PreparedStatement queryStatement = connection.prepareStatement(sql);
        ResultSet resultSet = queryStatement.executeQuery();
        return new QueryResult(connection, resultSet);
    }

    /**
     * Executes an update using a connection that includes the database.
     */
    public static void executeUpdate(String sql) throws SQLException {
        try (Connection connection = getConnection()) {  // With database selected
            try (PreparedStatement updateStatement = connection.prepareStatement(sql)) {
                updateStatement.executeUpdate();
            }
        }
    }

    /**
     * Executes an update using a connection that does NOT include a default database.
     * This method is used for commands like "CREATE DATABASE IF NOT EXISTS ..."
     */
    public static void executeUpdate(String sql, int dummy) throws SQLException {
        try (Connection connection = getConnection(false)) {  // Without default database
            try (PreparedStatement updateStatement = connection.prepareStatement(sql)) {
                updateStatement.executeUpdate();
            }
        }
    }

    public record QueryResult(Connection connection, ResultSet resultSet) {
    }
}
