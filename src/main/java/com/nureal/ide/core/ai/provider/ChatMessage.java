package com.nureal.ide.core.ai.provider;

/**
 * Uma mensagem de uma conversa com o modelo. {@code role} segue o
 * vocabulario padrao dos providers de LLM: {@code system}, {@code user},
 * {@code assistant} ou {@code tool} (resultado de uma execucao de tool,
 * devolvido ao modelo no proximo turno).
 */
public record ChatMessage(String role, String content) {

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
    }
}
