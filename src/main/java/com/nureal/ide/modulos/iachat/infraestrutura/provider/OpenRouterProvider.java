package com.nureal.ide.modulos.iachat.infraestrutura.provider;

import java.time.Duration;
import java.util.Map;

/**
 * OpenRouter — roteador multi-modelo 100% compativel com o formato OpenAI Chat
 * Completions (ver {@link OpenAiCompatibleProvider}); so muda base URL e headers.
 */
public final class OpenRouterProvider extends OpenAiCompatibleProvider {

    private static final String BASE_URL = "https://openrouter.ai/api/v1";

    public OpenRouterProvider(String apiKey, Duration timeout) {
        super(apiKey, BASE_URL, timeout);
    }

    @Override
    protected String providerName() {
        return "OpenRouter";
    }

    @Override
    protected Map<String, String> extraHeaders() {
        // Recomendado (nao obrigatorio) pelo OpenRouter para identificar a app nos
        // rankings/creditos de uso: https://openrouter.ai/docs
        return Map.of(
                "HTTP-Referer", "https://github.com/alvescvinicius/nureal-database-ide",
                "X-Title", "Nureal Database IDE");
    }
}
