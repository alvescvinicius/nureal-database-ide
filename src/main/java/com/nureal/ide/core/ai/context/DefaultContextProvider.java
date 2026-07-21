package com.nureal.ide.core.ai.context;

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
        String sql = accessor.currentEditorSql();
        if (sql != null && sql.length() > MAX_EDITOR_SQL_CHARS) {
            sql = sql.substring(0, MAX_EDITOR_SQL_CHARS) + "\n-- (truncado)";
        }
        return new AgentContext(accessor.connectionLabel(), accessor.activeSchemaName(), sql);
    }
}
