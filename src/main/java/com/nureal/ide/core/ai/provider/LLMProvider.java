package com.nureal.ide.core.ai.provider;

import java.util.List;
import java.util.function.Consumer;

/**
 * Contrato unico para qualquer provedor de LLM (Ollama primeiro; OpenAI/
 * Gemini/Claude no futuro, sem alterar Agent nem UI — ver
 * {@code docs/015-Provider-Abstraction.md}).
 *
 * Nenhum metodo aqui deve ser chamado na EDT: {@link #chat} bloqueia ate a
 * resposta completa, e {@link #stream} dispara trabalho em background e
 * retorna imediatamente.
 */
public interface LLMProvider {

    /** Verifica se o provider esta acessivel agora. Nunca lanca excecao. */
    boolean health();

    /** Lista os modelos instalados/disponiveis no provider. */
    List<String> listModels();

    /** Chat sem streaming: bloqueia a thread chamadora ate a resposta completa. */
    ChatResponse chat(ChatRequest request);

    /**
     * Chat em streaming: dispara a chamada em background e retorna
     * imediatamente um id de requisicao (usavel em {@link #cancel}).
     * {@code onEvent} e chamado a cada evento (ver {@link AiEvent}), sempre
     * fora da thread chamadora.
     */
    String stream(ChatRequest request, Consumer<AiEvent> onEvent);

    /** Cancela uma requisicao em andamento (streaming ou nao). Sem efeito se ja tiver terminado. */
    void cancel(String requestId);
}
