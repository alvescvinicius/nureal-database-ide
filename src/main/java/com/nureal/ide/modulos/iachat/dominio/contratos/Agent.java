package com.nureal.ide.modulos.iachat.dominio.contratos;

import java.util.function.Consumer;

import com.nureal.ide.modulos.iachat.dominio.entidades.AiEvent;

/**
 * Orquestrador do chat de IA: recebe a mensagem do usuario, coordena
 * contexto e tools, invoca o {@code LLMProvider} (ver
 * {@code docs/012-Agent-Architecture.md}). Nunca acessa Swing.
 */
public interface Agent {

    /**
     * Envia uma mensagem do usuario numa conversa. Retorna um id de turno
     * estavel (nao o id de streaming do provider, que pode mudar entre
     * rounds de tool-calling) usavel em {@link #cancel}.
     */
    String chat(String conversationId, String userMessage, Consumer<AiEvent> onEvent);

    /** Cancela um turno em andamento (streaming ou execucao de tool). Sem efeito se ja tiver terminado. */
    void cancel(String turnId);
}
