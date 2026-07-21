package com.nureal.ide.core.ai.provider;

/**
 * Evento emitido por {@link OllamaProvider#pullModel} durante o download de
 * um modelo ({@code POST /api/pull}, NDJSON de progresso). Especifico do
 * Ollama — nao entra em {@link LLMProvider} porque "baixar um modelo" nao
 * existe como conceito em providers remotos (OpenAI/Gemini/Claude).
 */
public sealed interface PullEvent {

    String requestId();

    /** Um passo do download (ex.: {@code status="downloading sha256:..."}, com bytes baixados/total quando disponivel). */
    record Progress(String requestId, String status, long completed, long total) implements PullEvent {

        /** Percentual 0-100, ou -1 se o total ainda nao e conhecido nesse passo. */
        public int percent() {
            return total > 0 ? (int) Math.min(100, (completed * 100) / total) : -1;
        }
    }

    record Completed(String requestId) implements PullEvent {
    }

    record Failed(String requestId, ProviderException error) implements PullEvent {
    }

    record Cancelled(String requestId) implements PullEvent {
    }
}
