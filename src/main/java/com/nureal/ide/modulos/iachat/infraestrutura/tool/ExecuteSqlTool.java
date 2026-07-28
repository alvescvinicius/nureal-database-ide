package com.nureal.ide.modulos.iachat.infraestrutura.tool;
import com.nureal.ide.modulos.iachat.dominio.entidades.ToolRequest;
import com.nureal.ide.modulos.iachat.dominio.entidades.ToolResult;
import com.nureal.ide.modulos.iachat.dominio.contratos.Tool;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.nureal.ide.modulos.iachat.dominio.contratos.IdeStateAccessor;
import com.nureal.ide.modulos.conexoes.dominio.contratos.ConexaoAtivaPort;
import com.nureal.ide.core.safety.SqlRiskAnalyzer;

/**
 * Executa uma consulta SQL de leitura no banco conectado e devolve os dados
 * em forma tabular (ver {@link SqlQueryResult}), para perguntas
 * quantitativas/analiticas sobre os DADOS que {@code ListTablesTool}/
 * {@code DescribeTableTool} (metadados) nao respondem — ex.: "quais tabelas
 * tem mais de 1 milhao de registros".
 * <p>
 * Reusa o MESMO {@link SqlRiskAnalyzer} do editor: nenhum comando de risco
 * (DELETE/UPDATE sem WHERE, DROP, TRUNCATE, ALTER/CREATE/RENAME) roda por
 * aqui — a IA nunca ganha um atalho que o usuario humano nao tem no editor
 * SQL (la, esses comandos exigem confirmacao visual explicita). Linhas
 * limitadas a {@value #MAX_ROWS} (mesmo numero de {@code MainWindow#PAGE_SIZE},
 * usado pela paginacao do ResultGrid) — este pacote nao pode depender de
 * {@code ui.*} pra reusar a CONSTANTE em si, so o mesmo valor.
 */
public final class ExecuteSqlTool implements Tool {

    private static final int MAX_ROWS = 200;

    private final IdeStateAccessor accessor;

    public ExecuteSqlTool(IdeStateAccessor accessor) {
        this.accessor = accessor;
    }

    @Override
    public String getName() {
        return "execute_sql";
    }

    @Override
    public String getDescription() {
        return "Executa uma consulta SQL (normalmente SELECT) no banco conectado e devolve os dados reais em "
                + "forma de tabela. Prefira esta tool para perguntas quantitativas/analiticas sobre os DADOS "
                + "(contagens, agregacoes, filtros, comparacoes) que list_tables/describe_table (so metadados) "
                + "nao respondem. Comandos de escrita ou de definicao de estrutura (INSERT/UPDATE/DELETE/DROP/"
                + "ALTER/CREATE/TRUNCATE/RENAME) sao bloqueados por seguranca — nunca assuma que serao aceitos.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of("sql", Map.of(
                        "type", "string",
                        "description", "Instrucao SQL a executar (normalmente um SELECT).")),
                "required", List.of("sql"));
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        long start = System.currentTimeMillis();
        Object sqlArg = request.arguments().get("sql");
        String sql = sqlArg == null ? "" : String.valueOf(sqlArg).trim();
        if (sql.isEmpty()) {
            return ToolResult.failure("Parametro obrigatorio \"sql\" nao informado.");
        }

        // isWriteOrDdl ja cobre todos os verbos que SqlRiskAnalyzer#riskReason
        // marcaria como risco (delete/update/truncate/drop/alter/rename/create
        // sao subconjunto de WRITE_OR_DDL_VERBS) — checar riskReason tambem
        // aqui seria codigo morto, nunca alcancado para este tool.
        if (SqlRiskAnalyzer.isWriteOrDdl(sql)) {
            return ToolResult.failure("Operacao bloqueada: a IA so pode executar consultas de leitura (SELECT). "
                    + "Comandos de escrita (INSERT/UPDATE/DELETE/REPLACE) ou de definicao de estrutura "
                    + "(DROP/ALTER/CREATE/TRUNCATE/RENAME) devem ser rodados manualmente no editor SQL, "
                    + "onde a confirmacao aparece.");
        }

        ConexaoAtivaPort manager = accessor.connectionManager();
        if (manager == null || !manager.isConnected()) {
            return ToolResult.failure("Nenhuma conexao ativa - conecte a um banco primeiro.");
        }

        Connection conn = manager.getConnection();
        try (Statement st = conn.createStatement()) {
            // +1 so pra detectar se havia mais linhas alem do limite (ver "truncated" abaixo) —
            // nunca devolvido ao chamador.
            st.setMaxRows(MAX_ROWS + 1);
            boolean hasResultSet = st.execute(sql);
            long durationMs = System.currentTimeMillis() - start;

            if (!hasResultSet) {
                int updated = st.getUpdateCount();
                SqlQueryResult data = new SqlQueryResult(sql, List.of(), List.of(), false);
                return ToolResult.ok(updated + " linha(s) afetada(s).", data, durationMs);
            }

            try (ResultSet rs = st.getResultSet()) {
                SqlQueryResult data = readData(sql, rs);
                String content = data.rows().size() + " linha(s)"
                        + (data.truncated() ? " (limitado a " + MAX_ROWS + ")" : "") + " em " + durationMs + "ms.";
                return ToolResult.ok(content, data, durationMs);
            }
        } catch (SQLException e) {
            return ToolResult.failure("Erro ao executar SQL: " + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    /** {@code rs} so pode ser percorrido UMA vez — quem chamar nao pode reusar o mesmo ResultSet depois. */
    private static SqlQueryResult readData(String sql, ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int columnCount = md.getColumnCount();
        List<String> columns = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            columns.add(md.getColumnLabel(i));
        }
        List<List<Object>> rows = new ArrayList<>();
        while (rows.size() < MAX_ROWS && rs.next()) {
            List<Object> row = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                row.add(rs.getObject(i));
            }
            rows.add(row);
        }
        boolean truncated = rows.size() >= MAX_ROWS && rs.next();
        return new SqlQueryResult(sql, columns, rows, truncated);
    }
}
