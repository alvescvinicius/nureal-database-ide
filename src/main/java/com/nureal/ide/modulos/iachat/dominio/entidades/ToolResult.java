package com.nureal.ide.modulos.iachat.dominio.entidades;
import com.nureal.ide.modulos.iachat.dominio.contratos.Tool;

import java.util.Map;

/** Resultado de uma execucao de {@link Tool} (ver {@code docs/055-ToolResult.md}). */
public record ToolResult(boolean success, String content, Object structuredData, String error,
                          long durationMs, Map<String, Object> metadata) {

    public ToolResult {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static ToolResult ok(String content, Object structuredData, long durationMs) {
        return new ToolResult(true, content, structuredData, null, durationMs, Map.of());
    }

    public static ToolResult failure(String error) {
        return new ToolResult(false, null, null, error, 0, Map.of());
    }

    public static ToolResult failure(String error, long durationMs) {
        return new ToolResult(false, null, null, error, durationMs, Map.of());
    }
}
