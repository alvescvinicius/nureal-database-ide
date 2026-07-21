package com.nureal.ide.core.ai.tool;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import com.nureal.ide.core.ai.context.IdeStateAccessor;
import com.nureal.ide.core.connection.ConnectionManager;
import com.nureal.ide.core.metadata.model.ColumnDetail;
import com.nureal.ide.core.metadata.model.ForeignKeyInfo;
import com.nureal.ide.core.metadata.model.IndexInfo;
import com.nureal.ide.core.metadata.model.TableDetails;

/**
 * Descreve colunas, indices e chaves estrangeiras de uma tabela, reusando
 * {@code MetadataService#loadTableDetails} (o mesmo caminho que o
 * "Alterar tabela..." da UI ja usa) — nenhuma logica nova de metadados.
 */
public final class DescribeTableTool implements Tool {

    private final IdeStateAccessor accessor;

    public DescribeTableTool(IdeStateAccessor accessor) {
        this.accessor = accessor;
    }

    @Override
    public String getName() {
        return "describe_table";
    }

    @Override
    public String getDescription() {
        return "Mostra colunas, indices e chaves estrangeiras de uma tabela do schema conectado.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of("table", Map.of(
                        "type", "string",
                        "description", "Nome da tabela a descrever")),
                "required", List.of("table"));
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        long start = System.currentTimeMillis();
        Object tableArg = request.arguments().get("table");
        String table = tableArg == null ? "" : String.valueOf(tableArg).trim();
        if (table.isEmpty()) {
            return ToolResult.failure("Parametro obrigatorio \"table\" nao informado.");
        }

        ConnectionManager manager = accessor.connectionManager();
        if (manager == null || !manager.isConnected()) {
            return ToolResult.failure("Nenhuma conexao ativa - conecte a um banco primeiro.");
        }

        try {
            TableDetails details = accessor.metadataService()
                    .loadTableDetails(manager.getConnection(), accessor.activeSchemaName(), table);
            return ToolResult.ok(format(table, details), details, System.currentTimeMillis() - start);
        } catch (SQLException e) {
            return ToolResult.failure("Erro ao consultar a tabela \"" + table + "\": " + e.getMessage(),
                    System.currentTimeMillis() - start);
        }
    }

    private String format(String table, TableDetails details) {
        StringBuilder sb = new StringBuilder("Tabela ").append(table).append(":\n");
        for (ColumnDetail c : details.columns()) {
            sb.append("- ").append(c.name()).append(' ').append(c.type());
            if (!c.nullable()) {
                sb.append(" NOT NULL");
            }
            if (c.key() != null && !c.key().isBlank()) {
                sb.append(" [").append(c.key()).append(']');
            }
            if (c.extra() != null && !c.extra().isBlank()) {
                sb.append(' ').append(c.extra());
            }
            sb.append('\n');
        }
        if (!details.indexes().isEmpty()) {
            sb.append("Indices:\n");
            for (IndexInfo idx : details.indexes()) {
                sb.append("- ").append(idx.name()).append(idx.unique() ? " (unico)" : "")
                        .append(" (").append(String.join(", ", idx.columns())).append(")\n");
            }
        }
        if (!details.foreignKeys().isEmpty()) {
            sb.append("Chaves estrangeiras:\n");
            for (ForeignKeyInfo fk : details.foreignKeys()) {
                sb.append("- ").append(fk.name()).append(": (").append(String.join(", ", fk.columns()))
                        .append(") -> ").append(fk.referencedTable()).append('(')
                        .append(String.join(", ", fk.referencedColumns())).append(")\n");
            }
        }
        return sb.toString();
    }
}
