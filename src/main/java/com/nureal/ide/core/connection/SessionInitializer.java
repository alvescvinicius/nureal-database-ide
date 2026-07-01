package com.nureal.ide.core.connection;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SessionInitializer {

    private SessionInitializer() {
    }

    public static void initialize(Connection connection) throws SQLException {

        if (connection == null || connection.isClosed()) {
            throw new SQLException("Connection is null or closed.");
        }

        List<String> commands = buildCommands(connection);

        if (commands.isEmpty()) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            for (String command : commands) {
                statement.execute(command);
            }
        }
    }

    private static List<String> buildCommands(Connection connection) throws SQLException {

        String databaseProduct = connection.getMetaData()
                .getDatabaseProductName()
                .toLowerCase(Locale.ROOT);

        List<String> commands = new ArrayList<>();

        switch (databaseProduct) {

            case "mysql":
                configureMySql(commands);
                break;

            /*
             * case "oracle":
             * configureOracle(commands);
             * break;
             * 
             * case "postgresql":
             * configurePostgreSql(commands);
             * break;
             * 
             * case "microsoft sql server":
             * configureSqlServer(commands);
             * break;
             */

            default:
                break;
        }

        return commands;
    }

    private static void configureMySql(List<String> commands) {

        /*
         * Futuras configurações específicas do MySQL.
         *
         * Exemplo:
         *
         * commands.add("SET NAMES utf8mb4");
         * commands.add("SET SESSION sql_mode='STRICT_TRANS_TABLES'");
         */
    }

    /*
     * private static void configureOracle(List<String> commands) {
     * commands.add("ALTER SESSION SET TIME_ZONE = LOCAL");
     * }
     * 
     * private static void configurePostgreSql(List<String> commands) {
     * commands.add("SET TIME ZONE LOCAL");
     * }
     * 
     * private static void configureSqlServer(List<String> commands) {
     * // Sem comandos atualmente.
     * }
     */

}