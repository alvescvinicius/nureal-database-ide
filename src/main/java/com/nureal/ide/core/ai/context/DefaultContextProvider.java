package com.nureal.ide.core.ai.context;

import java.util.ArrayList;
import java.util.List;

import com.nureal.ide.core.history.ExecutionHistoryStore;
import com.nureal.ide.core.metadata.model.SchemaInfo;
import com.nureal.ide.core.metadata.model.TableInfo;

/** Implementacao unica de {@link ContextProvider} do MVP: le de um {@link IdeStateAccessor}. */
public final class DefaultContextProvider implements ContextProvider {

    /** Limite de caracteres do SQL do editor incluido no prompt, para nao estourar o contexto do modelo. */
    private static final int MAX_EDITOR_SQL_CHARS = 4000;

    private final IdeStateAccessor accessor;

    public DefaultContextProvider(IdeStateAccessor accessor) {
        this.accessor = accessor;
    }

    @Override
    public AgentContext collect() {
        return new AgentContext(collectConnection(), collectMetadata(), collectEditor(), collectExecution());
    }

    private ConnectionContext collectConnection() {
        return new ConnectionContext(accessor.connectionLabel(), accessor.databaseProductName(),
                accessor.databaseVersion(), accessor.activeSchemaName());
    }

    private MetadataContext collectMetadata() {
        SchemaInfo schema = accessor.cachedSchema();
        if (schema == null) {
            return MetadataContext.EMPTY;
        }
        List<String> tableNames = new ArrayList<>();
        for (TableInfo t : schema.tables()) {
            tableNames.add(t.name());
        }
        return new MetadataContext(schema.tables().size(), schema.views().size(), tableNames);
    }

    private EditorContext collectEditor() {
        String sql = accessor.currentEditorSql();
        if (sql != null && sql.length() > MAX_EDITOR_SQL_CHARS) {
            sql = sql.substring(0, MAX_EDITOR_SQL_CHARS) + "\n-- (truncado)";
        }
        return new EditorContext(sql, accessor.hasEditorSelection());
    }

    private ExecutionContext collectExecution() {
        return accessor.lastExecution()
                .map(e -> new ExecutionContext(e.sql(), e.success(), e.success() ? null : e.resultSummary(),
                        e.durationMs()))
                .orElse(ExecutionContext.EMPTY);
    }
}
