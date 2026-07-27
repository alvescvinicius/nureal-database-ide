package com.nureal.ide.modulos.iachat.dominio.entidades;

/** Providers de LLM suportados (ver {@code docs/015-Provider-Abstraction.md}). */
public enum ProviderType {

    OLLAMA("Ollama (local/externo)", false),
    CLAUDE("Claude (Anthropic)", true),
    OPENAI("OpenAI", true),
    GEMINI("Gemini (Google)", true),
    OPENROUTER("OpenRouter", true);

    private final String displayName;
    private final boolean requiresApiKey;

    ProviderType(String displayName, boolean requiresApiKey) {
        this.displayName = displayName;
        this.requiresApiKey = requiresApiKey;
    }

    public String displayName() {
        return displayName;
    }

    /** {@code false} so pro Ollama, que autentica via rede local/Base URL, nunca via API key. */
    public boolean requiresApiKey() {
        return requiresApiKey;
    }
}
