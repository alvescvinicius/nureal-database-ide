package com.nureal.ide.core.ai.provider;

import java.util.List;

/**
 * Pedido de chat enviado ao {@link LLMProvider}. {@code stream} nao e um
 * campo aqui de proposito: quem decide se a resposta vem em streaming ou
 * nao e o metodo do provider chamado ({@link LLMProvider#chat} vs
 * {@link LLMProvider#stream}), nao um flag dentro do request — evita os
 * dois poderem discordar entre si.
 */
public record ChatRequest(String model, List<ChatMessage> messages, Double temperature, List<ToolSpec> tools) {

    public ChatRequest {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model nao pode ser vazio");
        }
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages nao pode ser vazio");
        }
        messages = List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
    }

    public ChatRequest(String model, List<ChatMessage> messages, Double temperature) {
        this(model, messages, temperature, List.of());
    }
}
