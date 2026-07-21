package com.nureal.ide.core.ai.provider;

/** Contagem de tokens de uma resposta, quando o provider informa (Ollama informa em {@code done=true}). */
public record ChatUsage(long promptTokens, long completionTokens) {

    public static final ChatUsage EMPTY = new ChatUsage(0, 0);
}
