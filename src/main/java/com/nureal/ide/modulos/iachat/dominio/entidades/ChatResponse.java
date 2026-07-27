package com.nureal.ide.modulos.iachat.dominio.entidades;

import java.util.List;

public record ChatResponse(ChatMessage message, String finishReason, ChatUsage usage, List<ToolCall> toolCalls) {

    public ChatResponse {
        finishReason = finishReason == null ? "stop" : finishReason;
        usage = usage == null ? ChatUsage.EMPTY : usage;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
