package com.nureal.ide.ui.ai;

import java.util.Optional;
import java.util.function.Supplier;

import com.nureal.ide.core.ai.context.IdeStateAccessor;
import com.nureal.ide.core.connection.ConexaoAtivaPort;
import com.nureal.ide.core.history.ExecutionHistoryStore;
import com.nureal.ide.core.metadata.MetadataRepository;
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

    private final Supplier<ConexaoAtivaPort> connectionManager;
    private final Supplier<MetadataRepository> metadataService;
    private final Supplier<SchemaInfo> cachedSchema;
    private final Supplier<String> activeSchemaName;
    private final Supplier<String> connectionLabel;
    private final Supplier<String> databaseProductName;
    private final Supplier<String> databaseVersion;
    private final Supplier<String> currentEditorSql;
    private final Supplier<Boolean> hasEditorSelection;
    private final Supplier<Optional<ExecutionHistoryStore.Entry>> lastExecution;

    public IdeContextAccessor(Supplier<ConexaoAtivaPort> connectionManager, Supplier<MetadataRepository> metadataService,
            Supplier<SchemaInfo> cachedSchema, Supplier<String> activeSchemaName, Supplier<String> connectionLabel,
            Supplier<String> databaseProductName, Supplier<String> databaseVersion, Supplier<String> currentEditorSql,
            Supplier<Boolean> hasEditorSelection, Supplier<Optional<ExecutionHistoryStore.Entry>> lastExecution) {
        this.connectionManager = connectionManager;
        this.metadataService = metadataService;
        this.cachedSchema = cachedSchema;
        this.activeSchemaName = activeSchemaName;
        this.connectionLabel = connectionLabel;
        this.databaseProductName = databaseProductName;
        this.databaseVersion = databaseVersion;
        this.currentEditorSql = currentEditorSql;
        this.hasEditorSelection = hasEditorSelection;
        this.lastExecution = lastExecution;
    }

    @Override
    public ConexaoAtivaPort connectionManager() {
        return connectionManager.get();
    }

    @Override
    public MetadataRepository metadataService() {
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
    public String databaseProductName() {
        return databaseProductName.get();
    }

    @Override
    public String databaseVersion() {
        return databaseVersion.get();
    }

    @Override
    public String currentEditorSql() {
        return currentEditorSql.get();
    }

    @Override
    public boolean hasEditorSelection() {
        return Boolean.TRUE.equals(hasEditorSelection.get());
    }

    @Override
    public Optional<ExecutionHistoryStore.Entry> lastExecution() {
        return lastExecution.get();
    }
}
