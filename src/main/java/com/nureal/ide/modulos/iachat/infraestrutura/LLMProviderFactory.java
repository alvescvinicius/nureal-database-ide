package com.nureal.ide.modulos.iachat.infraestrutura;
import com.nureal.ide.modulos.iachat.dominio.entidades.ProviderException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;

import com.nureal.ide.modulos.iachat.infraestrutura.provider.ClaudeProvider;
import com.nureal.ide.modulos.iachat.infraestrutura.provider.GeminiProvider;
import com.nureal.ide.modulos.iachat.dominio.contratos.LLMProvider;
import com.nureal.ide.modulos.iachat.infraestrutura.provider.OllamaProvider;
import com.nureal.ide.modulos.iachat.infraestrutura.provider.OpenAiProvider;
import com.nureal.ide.modulos.iachat.infraestrutura.provider.OpenRouterProvider;
import com.nureal.ide.modulos.iachat.dominio.entidades.ProviderType;
import com.nureal.ide.core.log.AppLogger;

/**
 * Constroi o {@link LLMProvider} certo a partir da configuracao atual. Vive em
 * {@code modulos.iachat.config} (nao em {@code modulos.iachat.provider}) porque
 * {@link AiCredentialsStore} ja depende de {@link ProviderType} — colocar a factory
 * aqui mantem a dependencia numa unica direcao ({@code config -&gt; provider}) em vez
 * de criar um ciclo entre os dois pacotes.
 *
 * Nunca lanca por falta de API key: constroi o provider com uma key vazia se
 * nenhuma estiver salva, e deixa a PROPRIA chamada real (health/chat/stream) falhar
 * com {@code ProviderException.MissingCredential} — mesmo caminho de erro amigavel
 * que uma key invalida ja teria, sem precisar de tratamento especial aqui.
 */
public final class LLMProviderFactory {

    private LLMProviderFactory() {
    }

    public static LLMProvider create(AiPreferences.State prefs, AiCredentialsStore credentials) {
        Duration timeout = Duration.ofSeconds(prefs.timeoutSeconds());
        return switch (prefs.provider()) {
            case OLLAMA -> new OllamaProvider(prefs.baseUrl(), timeout);
            case CLAUDE -> new ClaudeProvider(apiKeyOrEmpty(credentials, ProviderType.CLAUDE), timeout);
            case OPENAI -> new OpenAiProvider(apiKeyOrEmpty(credentials, ProviderType.OPENAI), timeout);
            case GEMINI -> new GeminiProvider(apiKeyOrEmpty(credentials, ProviderType.GEMINI), timeout);
            case OPENROUTER -> new OpenRouterProvider(apiKeyOrEmpty(credentials, ProviderType.OPENROUTER), timeout);
        };
    }

    private static String apiKeyOrEmpty(AiCredentialsStore credentials, ProviderType provider) {
        try {
            return credentials.loadApiKey(provider).orElse("");
        } catch (GeneralSecurityException | IOException e) {
            AppLogger.warning("Falha ao carregar API key de " + provider.displayName(), e);
            return "";
        }
    }
}
