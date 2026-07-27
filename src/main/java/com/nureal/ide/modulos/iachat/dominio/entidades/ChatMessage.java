package com.nureal.ide.modulos.iachat.dominio.entidades;

import java.util.List;

/**
 * Uma mensagem de uma conversa com o modelo. {@code role} segue o
 * vocabulario padrao dos providers de LLM: {@code system}, {@code user},
 * {@code assistant} ou {@code tool} (resultado de uma execucao de tool,
 * devolvido ao modelo no proximo turno).
 *
 * {@code toolCallId}/{@code toolCalls} so sao preenchidos quando a mensagem faz parte
 * de uma rodada de tool-calling: uma mensagem {@code tool} carrega o
 * {@code toolCallId} do {@link ToolCall} que ela responde; uma mensagem
 * {@code assistant} que pediu tools carrega a lista de {@link ToolCall}s originais.
 * Sem isso, providers que exigem a referencia explicita entre pedido e resposta de
 * tool (Claude sempre; OpenAI tambem, embora de forma mais tolerante) nao conseguem
 * reconstruir o historico corretamente ao montar o request.
 */
public record ChatMessage(String role, String content, String toolCallId, List<ToolCall> toolCalls) {

    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_TOOL = "tool";

    public ChatMessage {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role nao pode ser vazio");
        }
        if (content == null) {
            content = "";
        }
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public ChatMessage(String role, String content) {
        this(role, content, null, List.of());
    }

    public ChatMessage(String role, String content, String toolCallId) {
        this(role, content, toolCallId, List.of());
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
