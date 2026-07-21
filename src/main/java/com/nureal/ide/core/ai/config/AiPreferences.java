package com.nureal.ide.core.ai.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Persiste a configuracao do modulo de IA em
 *   ~/.nureal-ide/ai.conf
 *
 * Mesmo formato chave=valor das outras preferencias do projeto (ver
 * {@code UiPreferences}). Por ora so o Ollama e suportado (ver
 * {@code docs/004-Non-Goals.md}), entao nao ha campo de "provider" ainda —
 * so base URL, modelo e parametros de chat.
 */
public class AiPreferences {

    private static final String DIR_NAME = ".nureal-ide";
    private static final String FILE_NAME = "ai.conf";

    public static final String DEFAULT_BASE_URL = "http://localhost:11434";
    public static final double DEFAULT_TEMPERATURE = 0.2;
    public static final int DEFAULT_TIMEOUT_SECONDS = 60;
    /** Baixado automaticamente no 1o uso do Ollama embutido, se nenhum modelo estiver instalado/configurado. */
    public static final String DEFAULT_MODEL = "llama3.2:3b";

    private final Path file;

    public AiPreferences() {
        this(Paths.get(System.getProperty("user.home"), DIR_NAME, FILE_NAME));
    }

    public AiPreferences(Path file) {
        this.file = file;
    }

    public Path location() {
        return file;
    }

    /** Estado imutavel da configuracao de IA. {@code model} vazio = nenhum escolhido ainda. */
    public record State(String baseUrl, String model, double temperature, int timeoutSeconds,
                         boolean streamingEnabled) {

        public static State defaults() {
            return new State(DEFAULT_BASE_URL, "", DEFAULT_TEMPERATURE, DEFAULT_TIMEOUT_SECONDS, true);
        }
    }

    /** Le a configuracao salva; retorna os padroes se o arquivo nao existir. */
    public State load() throws IOException {
        if (!Files.exists(file)) {
            return State.defaults();
        }
        String baseUrl = DEFAULT_BASE_URL;
        String model = "";
        double temperature = DEFAULT_TEMPERATURE;
        int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        boolean streamingEnabled = true;

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
            switch (key) {
                case "baseUrl" -> baseUrl = value.isEmpty() ? DEFAULT_BASE_URL : value;
                case "model" -> model = value;
                case "temperature" -> temperature = parseTemperature(value);
                case "timeoutSeconds" -> timeoutSeconds = parseTimeout(value);
                case "streamingEnabled" -> streamingEnabled = Boolean.parseBoolean(value);
                default -> {
                    // ignora chaves desconhecidas (versoes futuras)
                }
            }
        }
        return new State(baseUrl, model, temperature, timeoutSeconds, streamingEnabled);
    }

    /** Grava a configuracao, criando a pasta se necessario. */
    public void save(State state) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Nureal Database IDE - configuracao de IA (Ollama)\n\n");
        sb.append("baseUrl=").append(state.baseUrl()).append('\n');
        sb.append("model=").append(state.model()).append('\n');
        sb.append("temperature=").append(state.temperature()).append('\n');
        sb.append("timeoutSeconds=").append(state.timeoutSeconds()).append('\n');
        sb.append("streamingEnabled=").append(state.streamingEnabled()).append('\n');
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static double parseTemperature(String s) {
        try {
            double v = Double.parseDouble(s);
            return (v >= 0 && v <= 2) ? v : DEFAULT_TEMPERATURE;
        } catch (NumberFormatException e) {
            return DEFAULT_TEMPERATURE;
        }
    }

    private static int parseTimeout(String s) {
        try {
            int v = Integer.parseInt(s);
            return (v > 0) ? v : DEFAULT_TIMEOUT_SECONDS;
        } catch (NumberFormatException e) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
    }
}
