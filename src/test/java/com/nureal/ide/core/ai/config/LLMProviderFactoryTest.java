package com.nureal.ide.core.ai.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nureal.ide.core.ai.provider.ClaudeProvider;
import com.nureal.ide.core.ai.provider.GeminiProvider;
import com.nureal.ide.core.ai.provider.LLMProvider;
import com.nureal.ide.core.ai.provider.OllamaProvider;
import com.nureal.ide.core.ai.provider.OpenAiProvider;
import com.nureal.ide.core.ai.provider.OpenRouterProvider;
import com.nureal.ide.core.ai.provider.ProviderType;
import com.nureal.ide.core.security.LocalVault;

class LLMProviderFactoryTest {

    private Path tempDir;
    private AiCredentialsStore credentials;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("nureal-provider-factory-test");
        LocalVault vault = new LocalVault(tempDir.resolve(".ai-credentials.key"));
        credentials = new AiCredentialsStore(vault, tempDir.resolve("ai-credentials.conf"));
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempDir)
                .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // limpeza de teste
                    }
                });
    }

    private static AiPreferences.State prefsFor(ProviderType provider) {
        return new AiPreferences.State(provider, AiPreferences.DEFAULT_BASE_URL, "algum-modelo",
                AiPreferences.DEFAULT_TEMPERATURE, AiPreferences.DEFAULT_TIMEOUT_SECONDS, true);
    }

    @Test
    void criaOllamaProvider() {
        LLMProvider provider = LLMProviderFactory.create(prefsFor(ProviderType.OLLAMA), credentials);
        assertInstanceOf(OllamaProvider.class, provider);
    }

    @Test
    void criaClaudeProvider() {
        LLMProvider provider = LLMProviderFactory.create(prefsFor(ProviderType.CLAUDE), credentials);
        assertInstanceOf(ClaudeProvider.class, provider);
    }

    @Test
    void criaOpenAiProvider() {
        LLMProvider provider = LLMProviderFactory.create(prefsFor(ProviderType.OPENAI), credentials);
        assertInstanceOf(OpenAiProvider.class, provider);
    }

    @Test
    void criaGeminiProvider() {
        LLMProvider provider = LLMProviderFactory.create(prefsFor(ProviderType.GEMINI), credentials);
        assertInstanceOf(GeminiProvider.class, provider);
    }

    @Test
    void criaOpenRouterProvider() {
        LLMProvider provider = LLMProviderFactory.create(prefsFor(ProviderType.OPENROUTER), credentials);
        assertInstanceOf(OpenRouterProvider.class, provider);
    }

    @Test
    void semApiKeySalvaAFactoryNaoLancaNaConstrucao() throws GeneralSecurityException, IOException {
        // Sem key nenhuma salva -- a factory nao lanca (constroi com key vazia); o
        // erro amigavel (MissingCredential) so aparece quando o provider e USADO de
        // verdade (health/chat/stream, ver ClaudeProviderTest/OpenAiProviderTest/...),
        // nao na construcao -- por isso nao chamamos health() aqui (bateria na API
        // real do provider, indesejavel num teste unitario).
        assertFalse(credentials.hasApiKey(ProviderType.CLAUDE));
        LLMProvider provider = LLMProviderFactory.create(prefsFor(ProviderType.CLAUDE), credentials);
        assertInstanceOf(ClaudeProvider.class, provider);
    }
}
