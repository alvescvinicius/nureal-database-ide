package com.nureal.ide.modulos.iachat.infraestrutura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nureal.ide.modulos.iachat.dominio.entidades.ProviderType;
import com.nureal.ide.compartilhado.seguranca.LocalVault;

class AiCredentialsStoreTest {

    private Path tempDir;
    private AiCredentialsStore store;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("nureal-ai-credentials-test");
        LocalVault vault = new LocalVault(tempDir.resolve(".ai-credentials.key"));
        store = new AiCredentialsStore(vault, tempDir.resolve("ai-credentials.conf"));
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

    @Test
    void loadApiKeyVazioQuandoNuncaSalvou() throws GeneralSecurityException, IOException {
        assertTrue(store.loadApiKey(ProviderType.CLAUDE).isEmpty());
        assertFalse(store.hasApiKey(ProviderType.CLAUDE));
    }

    @Test
    void saveEQueRoundtrip() throws GeneralSecurityException, IOException {
        store.saveApiKey(ProviderType.CLAUDE, "sk-ant-teste-123");

        Optional<String> loaded = store.loadApiKey(ProviderType.CLAUDE);
        assertTrue(loaded.isPresent());
        assertEquals("sk-ant-teste-123", loaded.get());
        assertTrue(store.hasApiKey(ProviderType.CLAUDE));
    }

    @Test
    void naoMisturaProvidersDiferentes() throws GeneralSecurityException, IOException {
        store.saveApiKey(ProviderType.CLAUDE, "chave-claude");
        store.saveApiKey(ProviderType.OPENAI, "chave-openai");

        assertEquals("chave-claude", store.loadApiKey(ProviderType.CLAUDE).orElseThrow());
        assertEquals("chave-openai", store.loadApiKey(ProviderType.OPENAI).orElseThrow());
        assertTrue(store.loadApiKey(ProviderType.GEMINI).isEmpty());
    }

    @Test
    void arquivoEmDiscoNaoContemAChaveEmTextoClaro() throws GeneralSecurityException, IOException {
        String secret = "sk-super-secreta-nao-pode-vazar";
        store.saveApiKey(ProviderType.OPENAI, secret);

        String rawFileContent = Files.readString(tempDir.resolve("ai-credentials.conf"), StandardCharsets.UTF_8);
        assertFalse(rawFileContent.contains(secret), "a API key nao deveria aparecer em texto claro no arquivo");
    }

    @Test
    void clearApiKeyRemove() throws GeneralSecurityException, IOException {
        store.saveApiKey(ProviderType.GEMINI, "chave-gemini");
        store.clearApiKey(ProviderType.GEMINI);

        assertTrue(store.loadApiKey(ProviderType.GEMINI).isEmpty());
    }

    @Test
    void saveComValorNuloOuVazioRemove() throws GeneralSecurityException, IOException {
        store.saveApiKey(ProviderType.OPENROUTER, "chave-valida");
        store.saveApiKey(ProviderType.OPENROUTER, "");

        assertTrue(store.loadApiKey(ProviderType.OPENROUTER).isEmpty());
    }

    @Test
    void persisteEntreInstancias() throws GeneralSecurityException, IOException {
        store.saveApiKey(ProviderType.CLAUDE, "chave-persistente");

        LocalVault sameVault = new LocalVault(tempDir.resolve(".ai-credentials.key"));
        AiCredentialsStore reopened = new AiCredentialsStore(sameVault, tempDir.resolve("ai-credentials.conf"));
        assertEquals("chave-persistente", reopened.loadApiKey(ProviderType.CLAUDE).orElseThrow());
    }
}
