package com.nureal.ide.modulos.iachat.infraestrutura;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.nureal.ide.modulos.iachat.dominio.entidades.ProviderType;
import com.nureal.ide.compartilhado.seguranca.LocalVault;

/**
 * Guarda as API keys dos providers de IA em nuvem (Claude/OpenAI/Gemini/OpenRouter),
 * cifradas via {@link LocalVault} — mesma criptografia (AES-256/GCM) ja usada pra
 * senha de conexao, mas com uma chave PROPRIA em
 *   ~/.nureal-ide/.ai-credentials.key
 * (isolamento: comprometer uma API key de IA nao expoe senhas de banco, e vice-versa).
 *
 * Formato do arquivo ({@code ~/.nureal-ide/ai-credentials.conf}): uma linha por
 * provider, {@code nome=<base64 cifrado>} — cada VALOR e cifrado individualmente (nao
 * o arquivo inteiro), mais simples que o esquema whole-blob do {@code ConnectionStore}
 * ja que aqui nao ha outros campos nao-sensiveis pra misturar no mesmo arquivo.
 */
public class AiCredentialsStore {

    private static final String DIR_NAME = ".nureal-ide";
    private static final String KEY_FILE_NAME = ".ai-credentials.key";
    private static final String STORE_FILE_NAME = "ai-credentials.conf";

    private final LocalVault vault;
    private final Path file;

    public AiCredentialsStore() {
        this(new LocalVault(Paths.get(System.getProperty("user.home"), DIR_NAME, KEY_FILE_NAME)),
                Paths.get(System.getProperty("user.home"), DIR_NAME, STORE_FILE_NAME));
    }

    public AiCredentialsStore(LocalVault vault, Path file) {
        this.vault = vault;
        this.file = file;
    }

    /** Salva (ou, se {@code apiKey} for nulo/vazio, remove) a API key deste provider. */
    public synchronized void saveApiKey(ProviderType provider, String apiKey)
            throws GeneralSecurityException, IOException {
        Map<ProviderType, String> encrypted = loadEncrypted();
        if (apiKey == null || apiKey.isBlank()) {
            encrypted.remove(provider);
        } else {
            encrypted.put(provider, vault.encrypt(apiKey));
        }
        saveEncrypted(encrypted);
    }

    public synchronized Optional<String> loadApiKey(ProviderType provider)
            throws GeneralSecurityException, IOException {
        String ciphertext = loadEncrypted().get(provider);
        if (ciphertext == null) {
            return Optional.empty();
        }
        return Optional.of(vault.decrypt(ciphertext));
    }

    public synchronized void clearApiKey(ProviderType provider) throws IOException {
        Map<ProviderType, String> encrypted = loadEncrypted();
        encrypted.remove(provider);
        saveEncrypted(encrypted);
    }

    public synchronized boolean hasApiKey(ProviderType provider) throws IOException {
        return loadEncrypted().containsKey(provider);
    }

    private Map<ProviderType, String> loadEncrypted() throws IOException {
        Map<ProviderType, String> map = new EnumMap<>(ProviderType.class);
        if (!Files.exists(file)) {
            return map;
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                map.put(ProviderType.valueOf(key.toUpperCase(Locale.ROOT)), value);
            } catch (IllegalArgumentException ignored) {
                // provider desconhecido (versoes futuras) — ignora a linha
            }
        }
        return map;
    }

    private void saveEncrypted(Map<ProviderType, String> encrypted) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Nureal Database IDE - API keys de IA (cifradas, ver LocalVault)\n\n");
        for (Map.Entry<ProviderType, String> entry : encrypted.entrySet()) {
            sb.append(entry.getKey().name().toLowerCase(Locale.ROOT)).append('=').append(entry.getValue())
                    .append('\n');
        }
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
