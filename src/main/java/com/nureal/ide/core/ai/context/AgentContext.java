package com.nureal.ide.core.ai.context;

/**
 * Retrato somente-leitura do estado atual da IDE, usado para montar o
 * system prompt do Agent (ver {@code docs/043-AgentContext.md}).
 * {@code connectionLabel} nunca inclui senha (ex.: "host:3306/schema (user)").
 */
public record AgentContext(String connectionLabel, String activeSchema, String currentEditorSql) {

    public static final AgentContext EMPTY = new AgentContext(null, null, null);
}
