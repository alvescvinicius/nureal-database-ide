package com.nureal.ide.modulos.iachat.infraestrutura.tool;
import com.nureal.ide.modulos.iachat.dominio.entidades.ToolRequest;
import com.nureal.ide.modulos.iachat.dominio.entidades.ToolResult;
import com.nureal.ide.modulos.iachat.dominio.contratos.Tool;

import java.util.List;
import java.util.Map;

import com.nureal.ide.modulos.iachat.dominio.contratos.IdeStateAccessor;
import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.TableInfo;

/**
 * Lista as tabelas/views do schema ja carregado no cache — sem round-trip
 * ao banco, portanto seguro e rapido de sempre disponibilizar ao modelo.
 */
public final class ListTablesTool implements Tool {

    private final IdeStateAccessor accessor;

    public ListTablesTool(IdeStateAccessor accessor) {
        this.accessor = accessor;
    }

    @Override
    public String getName() {
        return "list_tables";
    }

    @Override
    public String getDescription() {
        return "Lista as tabelas e views do schema atualmente conectado na IDE.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of("type", "object", "properties", Map.of(), "required", List.of());
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        long start = System.currentTimeMillis();
        SchemaInfo schema = accessor.cachedSchema();
        if (schema == null) {
            return ToolResult.failure("Nenhum schema carregado - conecte a um banco primeiro.");
        }
        List<String> tables = schema.tables().stream().map(TableInfo::name).toList();
        List<String> views = schema.views().stream().map(TableInfo::name).toList();

        StringBuilder content = new StringBuilder();
        content.append("Tabelas (").append(tables.size()).append("): ").append(String.join(", ", tables));
        if (!views.isEmpty()) {
            content.append("\nViews (").append(views.size()).append("): ").append(String.join(", ", views));
        }
        return ToolResult.ok(content.toString(), Map.of("tables", tables, "views", views),
                System.currentTimeMillis() - start);
    }
}
