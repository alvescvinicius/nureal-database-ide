package com.nureal.ide.core.ai.provider;

import java.util.Map;

/**
 * Descricao de uma tool no formato que os providers de LLM esperam (schema
 * estilo OpenAI/Ollama: nome, descricao e um JSON Schema dos parametros).
 * Montada pelo {@code Agent} a partir de {@code core.ai.tool.Tool} — o
 * Provider nunca conhece a interface {@code Tool} nem o {@code ToolExecutor},
 * so este DTO, mantendo o pacote {@code provider} independente do pacote
 * {@code tool}.
 */
public record ToolSpec(String name, String description, Map<String, Object> parametersSchema) {

    public ToolSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name nao pode ser vazio");
        }
        description = description == null ? "" : description;
        parametersSchema = parametersSchema == null ? Map.of() : Map.copyOf(parametersSchema);
    }
}
