package com.nureal.ide.modulos.conexoes.infraestrutura;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;

/**
 * Roda, uma unica vez logo apos a conexao ser aberta, as instrucoes de
 * configuracao de sessao que o {@link DatabaseDialect} da conexao declarar
 * (ver {@link DatabaseDialect#sessionInitStatements()}) — nunca decide por
 * conta propria o que rodar para cada banco: isso e responsabilidade do
 * dialeto, o unico ponto de extensao multi-banco do projeto (ver
 * {@code .specs/04-modulo-dialeto-e-metadados.md}).
 */
public final class SessionInitializer {

    private SessionInitializer() {
    }

    public static void initialize(Connection connection, DatabaseDialect dialect) throws SQLException {

        if (connection == null || connection.isClosed()) {
            throw new SQLException("Connection is null or closed.");
        }

        List<String> commands = dialect.sessionInitStatements();

        if (commands.isEmpty()) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            for (String command : commands) {
                statement.execute(command);
            }
        }
    }

}
