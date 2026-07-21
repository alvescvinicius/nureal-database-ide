package com.nureal.ide.core.ai.context;

/**
 * Retrato somente-leitura do estado atual da IDE, montado a cada mensagem do
 * usuario (ver {@code DefaultAgent}) e usado tanto pelo {@code PromptComposer}
 * (monta o system prompt) quanto pela resolucao do Database Specialist
 * (via {@link ConnectionContext#databaseProductName()}).
 */
public record AgentContext(ConnectionContext connection, MetadataContext metadata, EditorContext editor,
                            ExecutionContext execution) {

    public static final AgentContext EMPTY = new AgentContext(
            ConnectionContext.EMPTY, MetadataContext.EMPTY, EditorContext.EMPTY, ExecutionContext.EMPTY);

    public AgentContext {
        connection = connection == null ? ConnectionContext.EMPTY : connection;
        metadata = metadata == null ? MetadataContext.EMPTY : metadata;
        editor = editor == null ? EditorContext.EMPTY : editor;
        execution = execution == null ? ExecutionContext.EMPTY : execution;
    }
}
