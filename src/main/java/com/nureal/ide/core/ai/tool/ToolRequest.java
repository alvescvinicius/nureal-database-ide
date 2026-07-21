package com.nureal.ide.core.ai.tool;

import java.util.Map;

import com.nureal.ide.core.ai.context.AgentContext;

/** Pedido de execucao de uma {@link Tool} (ver {@code docs/054-ToolRequest.md}). */
public record ToolRequest(String toolName, Map<String, Object> arguments, String conversationId,
                           String requestId, AgentContext context) {

    public ToolRequest {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName nao pode ser vazio");
        }
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
