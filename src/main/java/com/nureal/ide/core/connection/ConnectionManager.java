package com.nureal.ide.core.connection;

import com.nureal.ide.core.dialect.DatabaseDialect;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Abre e mantem a conexao atual.
 *
 * Protótipo: usa DriverManager (uma conexao). A evolucao natural e trocar
 * o corpo de open()/getConnection() por um pool (HikariCP) sem alterar quem chama.
 */
public class ConnectionManager implements AutoCloseable {

    private final DatabaseDialect dialect;
    private Connection connection;
    private ConnectionProfile profile;

    public ConnectionManager(DatabaseDialect dialect) {
        this.dialect = dialect;
    }

    /** Abre uma conexao nova, fechando a anterior se existir. */
    public synchronized void open(ConnectionProfile profile) throws SQLException {
        close();
        this.profile = profile;
        String url = dialect.buildJdbcUrl(profile);
        this.connection = DriverManager.getConnection(url, profile.user(), profile.password());
    }

    public synchronized Connection getConnection() {
        return connection;
    }

    public synchronized boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public DatabaseDialect dialect() {
        return dialect;
    }

    public ConnectionProfile profile() {
        return profile;
    }

    @Override
    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // nada a fazer ao fechar
            }
            connection = null;
        }
    }
}
