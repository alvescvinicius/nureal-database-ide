package com.nureal.ide.core.ai.provider;

import java.util.Map;

/**
 * Um pedido de chamada de tool feito pelo modelo, extraido da resposta do
 * provider. {@code metadata} e um espaco opaco (mesma ideia de
 * {@code ToolResult#metadata}) pro provider que EMITIU esta {@link ToolCall}
 * guardar dado proprio do seu protocolo que precise ser devolvido
 * verbatim numa rodada futura (ex.: o {@code thoughtSignature} que o Gemini
 * exige de volta em cada parte {@code functionCall}) — Agent e
 * {@code ToolExecutor} nunca leem isso, so {@code arguments()}; so a
 * {@link ConversationSession} do MESMO provider que preencheu o campo sabe o
 * que fazer com ele.
 */
public record ToolCall(String id, String name, Map<String, Object> arguments, Map<String, Object> metadata) {

    public ToolCall {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name nao pode ser vazio");
        }
        id = (id == null || id.isBlank()) ? name : id;
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public ToolCall(String id, String name, Map<String, Object> arguments) {
        this(id, name, arguments, Map.of());
    }
}
