package com.nureal.ide.core.ai.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.nureal.ide.core.log.AppLogger;

/**
 * Localiza, executa e captura excecoes de {@link Tool}s (ver
 * {@code docs/053-ToolExecutor.md}). O Agent so conversa com esta classe,
 * nunca diretamente com uma {@link Tool}.
 */
public final class ToolExecutor {

    private final Map<String, Tool> byName;

    public ToolExecutor(List<Tool> tools) {
        Map<String, Tool> index = new LinkedHashMap<>();
        for (Tool tool : tools) {
            index.put(tool.getName(), tool);
        }
        this.byName = index;
    }

    public List<Tool> tools() {
        return List.copyOf(byName.values());
    }

    public ToolResult execute(ToolRequest request) {
        Tool tool = byName.get(request.toolName());
        if (tool == null) {
            return ToolResult.failure("Tool desconhecida: \"" + request.toolName() + "\".");
        }
        long start = System.currentTimeMillis();
        try {
            return tool.execute(request);
        } catch (Exception e) {
            AppLogger.warning("Falha ao executar tool de IA \"" + request.toolName() + "\"", e);
            return ToolResult.failure("Erro ao executar \"" + request.toolName() + "\": " + e.getMessage(),
                    System.currentTimeMillis() - start);
        }
    }
}
