package com.nureal.ide.modulos.conexoes.infraestrutura;
import com.nureal.ide.modulos.conexoes.dominio.contratos.ConexaoAtivaPort;
import com.nureal.ide.modulos.conexoes.dominio.entidades.ConnectionProfile;

import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Abre e mantem a(s) conexao(oes) atuais via um pool HikariCP: uma conexao
 * "principal" (ver {@link #getConnection()}) reutilizada pelos consumidores
 * de longa duracao que ja existiam antes do pool (grade de resultados,
 * caches de metadados, inspetor de FK etc. — nunca fecham a conexao que
 * pegam, entao continuam vendo o mesmo objeto sempre), e conexoes avulsas
 * emprestadas do pool (ver {@link #borrowConnection()}) para quem executa
 * uma instrucao e fecha a conexao ao terminar — e o que permite dois
 * terminais da MESMA aba de conexao rodarem SQL ao mesmo tempo sem esperar
 * um pelo outro, coisa impossivel com uma unica {@link Connection}
 * compartilhada (JDBC nao e seguro para uso concorrente na mesma conexao).
 */
public class ConnectionManager implements ConexaoAtivaPort {

    private static final int MAX_POOL_SIZE = 10;

    private final DatabaseDialect dialect;
    private HikariDataSource dataSource;
    private Connection primaryConnection;
    private ConnectionProfile profile;

    public ConnectionManager(DatabaseDialect dialect) {
        this.dialect = dialect;
    }

    /** Abre um pool novo, fechando o anterior se existir. */
    @Override
    public synchronized void open(ConnectionProfile profile) throws SQLException {
        close();
        this.profile = profile;
        String url = dialect.buildJdbcUrl(profile);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(profile.user());
        config.setPassword(profile.password());
        config.setDriverClassName(dialect.driverClassName());
        config.setMaximumPoolSize(MAX_POOL_SIZE);
        config.setMinimumIdle(1);
        config.setPoolName("nureal-" + profile.name());
        try {
            this.dataSource = new HikariDataSource(config);
            this.primaryConnection = dataSource.getConnection();
            SessionInitializer.initialize(this.primaryConnection, dialect);
        } catch (RuntimeException | SQLException ex) {
            close();
            throw (ex instanceof SQLException se) ? se : new SQLException(ex.getMessage(), ex);
        }
    }

    /**
     * Conexao "principal", de vida longa, para os consumidores que
     * historicamente seguram e reusam a mesma {@link Connection} sem
     * fecha-la (ver javadoc da classe). Nao usar para executar SQL de
     * terminal em paralelo com outros terminais — para isso, ver
     * {@link #borrowConnection()}.
     */
    @Override
    public synchronized Connection getConnection() {
        return primaryConnection;
    }

    /**
     * Empresta uma conexao DEDICADA do pool desta aba de conexao — o
     * chamador e responsavel por fecha-la (idealmente via
     * try-with-resources) assim que terminar, para devolve-la ao pool.
     * Cada chamada roda {@link SessionInitializer} na conexao emprestada,
     * ja que e uma conexao FISICA propria (nao a mesma da conexao
     * principal), entao precisa da mesma configuracao de sessao.
     */
    @Override
    public synchronized Connection borrowConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Nenhuma conexao aberta para emprestar.");
        }
        Connection c = dataSource.getConnection();
        try {
            SessionInitializer.initialize(c, dialect);
        } catch (SQLException ex) {
            c.close();
            throw ex;
        }
        return c;
    }

    @Override
    public synchronized boolean isConnected() {
        try {
            return primaryConnection != null && !primaryConnection.isClosed();
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
        if (primaryConnection != null) {
            try {
                primaryConnection.close();
            } catch (SQLException ignored) {
                // nada a fazer ao fechar
            }
            primaryConnection = null;
        }
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
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
