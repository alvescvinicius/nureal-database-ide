package com.nureal.ide.core.ai.provider;

import java.util.List;

/**
 * Evento emitido por {@link LLMProvider#stream} (ou por uma
 * {@link ConversationSession}) durante uma resposta em streaming. Sempre
 * entregue na mesma thread de background que fez a chamada HTTP — quem
 * consome (tipicamente um {@code SwingWorker} na camada de UI, ou o
 * {@code DefaultAgent}) e responsavel por devolver as atualizacoes para a
 * EDT.
 */
public sealed interface AiEvent {

    String requestId();

    record Started(String requestId) implements AiEvent {
    }

    /** Um pedaco incremental de texto da resposta do assistente. */
    record Chunk(String requestId, String delta) implements AiEvent {
    }

    /** Resposta concluida com sucesso, SEM tool pendente (mensagem completa em {@code response}). */
    record Completed(String requestId, ChatResponse response) implements AiEvent {
    }

    /**
     * O modelo pediu uma ou mais tools nesta rodada, em vez de uma resposta
     * final. Emitido so por {@link ConversationSession} (nunca por
     * {@link LLMProvider#stream} diretamente) — quem recebe deve executar
     * cada {@link ToolCall} e devolver o resultado via
     * {@link ConversationSession#submitToolResult}, usando {@link ToolCall#id()}
     * como o token opaco de correlacao (cada provider decide o que esse id
     * significa pro seu proprio protocolo — real na Claude/OpenAI, o proprio
     * nome da function no Gemini).
     */
    record ToolCallsRequested(String requestId, List<ToolCall> calls, String accompanyingText) implements AiEvent {
    }

    record Failed(String requestId, ProviderException error) implements AiEvent {
    }

    /** Cancelado a pedido do usuario (ver {@link LLMProvider#cancel} / {@link ConversationSession#cancel()}). */
    record Cancelled(String requestId) implements AiEvent {
    }
}
