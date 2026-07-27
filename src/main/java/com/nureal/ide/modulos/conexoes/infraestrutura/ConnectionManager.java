package com.nureal.ide.modulos.conexoes.infraestrutura;
import com.nureal.ide.modulos.conexoes.dominio.contratos.ConexaoAtivaPort;
import com.nureal.ide.modulos.conexoes.dominio.entidades.ConnectionProfile;

import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Abre e mantem a conexao atual.
 *
 * Protótipo: usa DriverManager (uma conexao). A evolucao natural e trocar
 * o corpo de open()/getConnection() por um pool (HikariCP) sem alterar quem
 * chama.
 */
public class ConnectionManager implements ConexaoAtivaPort {

    private final DatabaseDialect dialect;
    private Connection connection;
    private ConnectionProfile profile;

    public ConnectionManager(DatabaseDialect dialect) {
        this.dialect = dialect;
    }

    /** Abre uma conexao nova, fechando a anterior se existir. */
    @Override
    public synchronized void open(ConnectionProfile profile) throws SQLException {
        close();
        this.profile = profile;
        String url = dialect.buildJdbcUrl(profile);
        this.connection = DriverManager.getConnection(
                url,
                profile.user(),
                profile.password());
        try {
            SessionInitializer.initialize(this.connection, dialect);
        } catch (SQLException ex) {
            this.connection.close();
            this.connection = null;
            throw ex;
        }
    }

    @Override
    public synchronized Connection getConnection() {
        return connection;
    }

    @Override
    public synchronized boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public DatabaseDialect dialect() {
        return dialect;
    }

    @Override
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

    /**
     * Testa se e possivel abrir uma conexao com o perfil informado, sem
     * afetar a conexao atual desta instancia (nem de nenhuma outra): abre
     * uma conexao propria, so para validar, e fecha em seguida. Usado pelo
     * botao "Testar conexao" do formulario de Nova/Editar conexao (ver
     * com.nureal.ide.ui.ConnectionEditDialog), para o usuario conferir
     * host/porta/usuario/senha ANTES de salvar, sem precisar fechar o
     * formulario e conectar de verdade primeiro.
     * <p>
     * Timeout curto (5s) para nao travar a UI esperando um host
     * inalcancavel por muito tempo. Lanca {@link SQLException} com a causa
     * original em caso de falha (host errado, credencial invalida, porta
     * fechada, etc.) — o chamador decide como mostrar a mensagem.
     */
    public static void testConnection(DatabaseDialect dialect, ConnectionProfile profile) throws SQLException {
        String url = dialect.buildJdbcUrl(profile);
        DriverManager.setLoginTimeout(5);
        try (Connection test = DriverManager.getConnection(url, profile.user(), profile.password())) {
            // Conexao aberta com sucesso: nada mais a verificar. O
            // try-with-resources ja fecha antes de devolver.
        }
    }
}
