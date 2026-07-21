package com.nureal.ide.ui.ai;

import java.util.function.Supplier;

import com.nureal.ide.core.ai.context.IdeStateAccessor;
import com.nureal.ide.core.connection.ConnectionManager;
import com.nureal.ide.core.metadata.MetadataService;
import com.nureal.ide.core.metadata.model.SchemaInfo;

/**
 * Unica implementacao de {@link IdeStateAccessor}, montada pelo
 * {@code MainWindow} com referencias a metodos privados dele (ex.:
 * {@code this::connectionManager}) — o pacote {@code core.ai} nunca ve
 * {@code MainWindow} nem Swing, so esta interface. Recebe {@link Supplier}s
 * em vez de uma referencia direta ao {@code MainWindow} para nao precisar
 * expor nenhum getter novo como package/public so para isto.
 */
public final class IdeContextAccessor implements IdeStateAccessor {

    private final Supplier<ConnectionManager> connectionManager;
    private final Supplier<MetadataService> metadataService;
    private final Supplier<SchemaInfo> cachedSchema;
    private final Supplier<String> activeSchemaName;
    private final Supplier<String> connectionLabel;
    private final Supplier<String> currentEditorSql;

    public IdeContextAccessor(Supplier<ConnectionManager> connectionManager, Supplier<MetadataService> metadataService,
            Supplier<SchemaInfo> cachedSchema, Supplier<String> activeSchemaName, Supplier<String> connectionLabel,
            Supplier<String> currentEditorSql) {
        this.connectionManager = connectionManager;
        this.metadataService = metadataService;
        this.cachedSchema = cachedSchema;
        this.activeSchemaName = activeSchemaName;
        this.connectionLabel = connectionLabel;
        this.currentEditorSql = currentEditorSql;
    }

    @Override
    public ConnectionManager connectionManager() {
        return connectionManager.get();
    }

    @Override
    public MetadataService metadataService() {
        return metadataService.get();
    }

    @Override
    public SchemaInfo cachedSchema() {
        return cachedSchema.get();
    }

    @Override
    public String activeSchemaName() {
        return activeSchemaName.get();
    }

    @Override
    public String connectionLabel() {
        return connectionLabel.get();
    }

    @Override
    public String currentEditorSql() {
        return currentEditorSql.get();
    }
}
