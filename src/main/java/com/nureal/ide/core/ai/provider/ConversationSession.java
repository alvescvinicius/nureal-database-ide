package com.nureal.ide.core.ai.provider;

/**
 * Uma conversa em andamento com um {@link LLMProvider}, incluindo qualquer
 * rodada de tool-calling que ela precise. Existe pra que o {@code Agent}
 * nunca precise conhecer o protocolo nativo de tool-calling de cada provider
 * ({@code tool_use}/{@code tool_use_id} da Claude, {@code tool_calls}/
 * {@code tool_call_id} da OpenAI, {@code functionCall}/{@code functionResponse}
 * por nome do Gemini): o Agent so ve {@link AiEvent.ToolCallsRequested},
 * executa a tool e devolve o resultado aqui usando o {@code callToken} opaco
 * que veio no evento (ver {@link ToolCall#id()}) — cada implementacao decide
 * sozinha como manter seu proprio historico nativo coerente entre rodadas.
 *
 * Uma sessao e de uso unico: cobre exatamente um turno (uma mensagem do
 * usuario, mais quantas rodadas de tool-calling forem necessarias ate a
 * resposta final). Eventos chegam via o {@code Consumer<AiEvent>} passado em
 * {@link LLMProvider#createSession}, sempre fora da thread chamadora.
 */
public interface ConversationSession {

    /** Envia a mensagem do usuario e dispara a primeira rodada. Chamado uma unica vez por sessao. */
    void send(String userMessage);

    /**
     * Devolve o resultado de uma tool pedida via {@link AiEvent.ToolCallsRequested}.
     * Quando todas as tools daquela rodada tiverem resposta, a sessao dispara
     * automaticamente a proxima rodada — quem chama nao precisa (nem deve)
     * disparar nada explicitamente depois disso.
     */
    void submitToolResult(String callToken, String content);

    /** Cancela a rodada em andamento, se houver. Sem efeito se a sessao ja tiver terminado. */
    void cancel();
}
