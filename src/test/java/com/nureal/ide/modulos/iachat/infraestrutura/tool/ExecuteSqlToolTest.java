package com.nureal.ide.modulos.iachat.infraestrutura.tool;
import com.nureal.ide.modulos.iachat.dominio.entidades.ToolRequest;
import com.nureal.ide.modulos.iachat.dominio.entidades.ToolResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

import com.nureal.ide.modulos.iachat.dominio.entidades.AgentContext;
import com.nureal.ide.modulos.iachat.dominio.contratos.IdeStateAccessor;
import com.nureal.ide.modulos.conexoes.infraestrutura.ConnectionManager;
import com.nureal.ide.modulos.historico.infraestrutura.ExecutionHistoryStore;
import com.nureal.ide.modulos.metadados.infraestrutura.MetadataService;
import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaInfo;

/**
 * {@code Connection}/{@code Statement}/{@code ResultSet} sao fakes via
 * {@link Proxy} (sem Mockito no projeto, ver {@code pom.xml}) — so os
 * metodos que {@link ExecuteSqlTool} realmente chama sao implementados;
 * qualquer outro lanca {@link UnsupportedOperationException}, o que tambem
 * serve de guarda-corpo: se a tool passar a chamar algo novo, o teste falha
 * alto e claro em vez de devolver dado incoerente.
 */
class ExecuteSqlToolTest {

    @Test
    void executaSelectComSucesso() {
        ResultSet rs = fakeResultSet(List.of("nome", "total"), List.of(
                List.of("tab1", 100L),
                List.of("tab2", 50L)));
        Statement statement = fakeStatement(true, rs, 0);
        Connection connection = fakeConnection(statement);
        ExecuteSqlTool tool = new ExecuteSqlTool(fakeAccessor(fakeManager(connection, true)));

        ToolResult result = tool.execute(new ToolRequest("execute_sql",
                Map.of("sql", "SELECT nome, total FROM x"), "conv-1", "req-1", AgentContext.EMPTY));

        assertTrue(result.success());
        SqlQueryResult data = assertInstanceOf(SqlQueryResult.class, result.structuredData());
        assertEquals(List.of("nome", "total"), data.columns());
        assertEquals(2, data.rows().size());
        assertEquals(List.of("tab1", 100L), data.rows().get(0));
        assertFalse(data.truncated());
    }

    @Test
    void bloqueiaComandoDeEscritaOuDdlSemTocarNaConexao() {
        // O manager falha se qualquer metodo for chamado - prova que o bloqueio
        // acontece ANTES de qualquer tentativa de usar a conexao. Cobre tanto
        // um verbo "arriscado" no sentido de SqlRiskAnalyzer#riskReason
        // (DELETE sem WHERE) quanto um que riskReason NUNCA marcou (INSERT) —
        // isWriteOrDdl bloqueia os dois igualmente, sem diferenciar.
        for (String sql : List.of("DELETE FROM usuarios", "INSERT INTO usuarios (nome) VALUES ('x')")) {
            ConnectionManager manager = new ConnectionManager(null) {
                @Override
                public synchronized Connection getConnection() {
                    throw new AssertionError("nao deveria pedir a conexao pra \"" + sql + "\"");
                }

                @Override
                public synchronized boolean isConnected() {
                    throw new AssertionError("nao deveria checar a conexao pra \"" + sql + "\"");
                }
            };
            ExecuteSqlTool tool = new ExecuteSqlTool(fakeAccessor(manager));

            ToolResult result = tool.execute(new ToolRequest("execute_sql",
                    Map.of("sql", sql), "conv-1", "req-1", AgentContext.EMPTY));

            assertFalse(result.success());
            assertTrue(result.error().contains("leitura"));
        }
    }

    @Test
    void devolveFalhaSemConexaoAtiva() {
        ConnectionManager manager = new ConnectionManager(null) {
            @Override
            public synchronized boolean isConnected() {
                return false;
            }
        };
        ExecuteSqlTool tool = new ExecuteSqlTool(fakeAccessor(manager));

        ToolResult result = tool.execute(new ToolRequest("execute_sql",
                Map.of("sql", "SELECT 1"), "conv-1", "req-1", AgentContext.EMPTY));

        assertFalse(result.success());
        assertTrue(result.error().contains("conexao"));
    }

    @Test
    void devolveFalhaQuandoOBancoRecusaOSql() {
        Statement statement = (Statement) proxy(Statement.class, (name, args) -> {
            if ("execute".equals(name)) {
                throw new RuntimeException(new SQLException("Unknown column 'x' in 'field list'"));
            }
            return null;
        });
        Connection connection = fakeConnection(statement);
        ExecuteSqlTool tool = new ExecuteSqlTool(fakeAccessor(fakeManager(connection, true)));

        ToolResult result = tool.execute(new ToolRequest("execute_sql",
                Map.of("sql", "SELECT x FROM y"), "conv-1", "req-1", AgentContext.EMPTY));

        assertFalse(result.success());
        assertTrue(result.error().contains("Unknown column"));
    }

    @Test
    void exigeParametroSqlNaoVazio() {
        ExecuteSqlTool tool = new ExecuteSqlTool(fakeAccessor(fakeManager(null, true)));

        ToolResult result = tool.execute(
                new ToolRequest("execute_sql", Map.of(), "conv-1", "req-1", AgentContext.EMPTY));

        assertFalse(result.success());
        assertNull(result.structuredData());
    }

    // ---------- Fakes ----------

    private static ConnectionManager fakeManager(Connection connection, boolean connected) {
        return new ConnectionManager(null) {
            @Override
            public synchronized Connection getConnection() {
                return connection;
            }

            @Override
            public synchronized boolean isConnected() {
                return connected;
            }
        };
    }

    private static IdeStateAccessor fakeAccessor(ConnectionManager manager) {
        return new IdeStateAccessor() {
            @Override
            public ConnectionManager connectionManager() {
                return manager;
            }

            @Override
            public MetadataService metadataService() {
                return null;
            }

            @Override
            public SchemaInfo cachedSchema() {
                return null;
            }

            @Override
            public String activeSchemaName() {
                return null;
            }

            @Override
            public String connectionLabel() {
                return null;
            }

            @Override
            public String databaseProductName() {
                return null;
            }

            @Override
            public String databaseVersion() {
                return null;
            }

            @Override
            public String currentEditorSql() {
                return null;
            }

            @Override
            public boolean hasEditorSelection() {
                return false;
            }

            @Override
            public Optional<ExecutionHistoryStore.Entry> lastExecution() {
                return Optional.empty();
            }
        };
    }

    private static Connection fakeConnection(Statement statement) {
        return (Connection) proxy(Connection.class, (name, args) -> {
            if ("createStatement".equals(name)) {
                return statement;
            }
            throw new UnsupportedOperationException(name);
        });
    }

    private static Statement fakeStatement(boolean hasResultSet, ResultSet resultSet, int updateCount) {
        return (Statement) proxy(Statement.class, (name, args) -> {
            switch (name) {
                case "setMaxRows" -> {
                    return null;
                }
                case "execute" -> {
                    return hasResultSet;
                }
                case "getResultSet" -> {
                    return resultSet;
                }
                case "getUpdateCount" -> {
                    return updateCount;
                }
                default -> throw new UnsupportedOperationException(name);
            }
        });
    }

    /** Linhas ja prontas (mesma ordem das colunas); {@code next()} avanca por elas, {@code getObject} le a atual. */
    private static ResultSet fakeResultSet(List<String> columns, List<List<Object>> rows) {
        ResultSetMetaData metaData = (ResultSetMetaData) proxy(ResultSetMetaData.class, (name, args) -> {
            if ("getColumnCount".equals(name)) {
                return columns.size();
            }
            if ("getColumnLabel".equals(name)) {
                return columns.get((int) args[0] - 1);
            }
            throw new UnsupportedOperationException(name);
        });
        int[] cursor = { -1 };
        return (ResultSet) proxy(ResultSet.class, (name, args) -> {
            switch (name) {
                case "getMetaData" -> {
                    return metaData;
                }
                case "next" -> {
                    cursor[0]++;
                    return cursor[0] < rows.size();
                }
                case "getObject" -> {
                    return rows.get(cursor[0]).get((int) args[0] - 1);
                }
                default -> throw new UnsupportedOperationException(name);
            }
        });
    }

    /**
     * {@code impl} so pode lancar excecoes NAO verificadas (limitacao de
     * {@link BiFunction}) — pra simular uma {@link SQLException} vinda do
     * banco, embrulhe numa {@code RuntimeException(SQLException)}: este
     * metodo desembrulha e relanca a causa de verdade, ja que
     * {@code InvocationHandler#invoke} pode declarar {@code throws Throwable}.
     */
    private static Object proxy(Class<?> iface, BiFunction<String, Object[], Object> impl) {
        return Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] { iface }, (p, method, args) -> {
            try {
                return switch (method.getName()) {
                    case "equals" -> p == args[0];
                    case "hashCode" -> System.identityHashCode(p);
                    case "toString" -> iface.getSimpleName() + "$Fake";
                    case "close" -> null;
                    default -> impl.apply(method.getName(), args);
                };
            } catch (RuntimeException e) {
                if (e.getCause() instanceof SQLException sqlException) {
                    throw sqlException;
                }
                throw e;
            }
        });
    }
}
