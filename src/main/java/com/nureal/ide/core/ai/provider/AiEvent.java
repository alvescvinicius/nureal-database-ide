package com.nureal.ide.core.ai.provider;

/**
 * Evento emitido por {@link LLMProvider#stream} durante uma resposta em
 * streaming. Sempre entregue na mesma thread de background que fez a
 * chamada HTTP — quem consome (tipicamente um {@code SwingWorker} na
 * camada de UI) e responsavel por devolver as atualizacoes para a EDT.
 */
public sealed interface AiEvent {

    String requestId();

    record Started(String requestId) implements AiEvent {
    }

    /** Um pedaco incremental de texto da resposta do assistente. */
    record Chunk(String requestId, String delta) implements AiEvent {
    }

    /** Resposta concluida com sucesso (mensagem completa em {@code response}). */
    record Completed(String requestId, ChatResponse response) implements AiEvent {
    }

    record Failed(String requestId, ProviderException error) implements AiEvent {
    }

    /** Cancelado a pedido do usuario (ver {@link LLMProvider#cancel}). */
    record Cancelled(String requestId) implements AiEvent {
    }
}
