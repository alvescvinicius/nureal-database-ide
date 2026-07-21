package com.nureal.ide.core.ai.provider;

import java.util.Map;

/** Um pedido de chamada de tool feito pelo modelo, extraido da resposta do provider. */
public record ToolCall(String id, String name, Map<String, Object> arguments) {

    public ToolCall {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name nao pode ser vazio");
        }
        id = (id == null || id.isBlank()) ? name : id;
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
