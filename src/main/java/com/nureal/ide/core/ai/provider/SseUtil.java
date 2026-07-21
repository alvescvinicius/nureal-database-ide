package com.nureal.ide.core.ai.provider;

import java.util.Optional;

/**
 * Parsing minimo de Server-Sent Events, compartilhado por Claude/OpenAI/Gemini/
 * OpenRouter (Ollama usa NDJSON puro, nao SSE). So extrai o payload de linhas
 * {@code data: ...} — nao e um parser de SSE de proposito geral (sem suporte a
 * {@code id:}/{@code retry:}/eventos multi-linha, que nenhum destes providers usa).
 */
final class SseUtil {

    private SseUtil() {
    }

    static Optional<String> dataPayload(String line) {
        if (line == null) {
            return Optional.empty();
        }
        String trimmed = line.strip();
        if (!trimmed.startsWith("data:")) {
            return Optional.empty();
        }
        String payload = trimmed.substring("data:".length()).strip();
        return payload.isEmpty() ? Optional.empty() : Optional.of(payload);
    }

    static boolean isDone(String payload) {
        return "[DONE]".equals(payload);
    }
}
