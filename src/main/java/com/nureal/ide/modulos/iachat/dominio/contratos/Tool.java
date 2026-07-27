package com.nureal.ide.modulos.iachat.dominio.contratos;
import com.nureal.ide.modulos.iachat.dominio.entidades.ToolRequest;
import com.nureal.ide.modulos.iachat.dominio.entidades.ToolResult;
import com.nureal.ide.modulos.iachat.aplicacao.ToolExecutor;

import java.util.Map;

/**
 * Uma capacidade reutilizavel da IDE que o Agent pode invocar (ver
 * {@code docs/052-Tool-Interface.md}). Implementacoes nunca conhecem o
 * Agent nem o ToolExecutor, e nunca implementam regra de negocio propria —
 * so chamam servicos ja existentes ({@code MetadataService},
 * {@code ConnectionManager} etc.).
 */
public interface Tool {

    String getName();

    String getDescription();

    /** JSON Schema (formato OpenAI/Ollama) dos parametros aceitos por {@link #execute}. */
    Map<String, Object> getParametersSchema();

    ToolResult execute(ToolRequest request);
}
