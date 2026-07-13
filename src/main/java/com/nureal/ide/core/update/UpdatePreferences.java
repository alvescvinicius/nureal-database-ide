package com.nureal.ide.core.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Persiste as preferencias do sistema de atualizacao automatica em
 *   ~/.nureal-ide/update.conf
 *
 * Mesmo formato chave=valor das outras stores do projeto (ver
 * {@code UiPreferences}, {@code ConnectionStore}, {@code SessionStore}).
 * Guarda apenas duas coisas:
 * <ul>
 *   <li>{@code autoCheckEnabled} — se a checagem automatica no startup esta
 *       ligada (padrao: ligada). Item de menu futuro pode desligar pra quem
 *       nao quer ser incomodado.</li>
 *   <li>{@code skippedVersion} — a tag que o usuario mandou "ignorar esta
 *       versao" no banner. Enquanto o ultimo release do GitHub continuar
 *       sendo essa MESMA tag, a checagem automatica do startup nao mostra o
 *       banner de novo (mas a checagem MANUAL, via menu, sempre mostra,
 *       ignorando este campo — o usuario pediu explicitamente).</li>
 * </ul>
 */
public class UpdatePreferences {

    private static final String DIR_NAME = ".nureal-ide";
    private static final String FILE_NAME = "update.conf";

    private final Path file;

    public UpdatePreferences() {
        this(Paths.get(System.getProperty("user.home"), DIR_NAME, FILE_NAME));
    }

    public UpdatePreferences(Path file) {
        this.file = file;
    }

    /** Estado imutavel das preferencias de atualizacao. */
    public record State(boolean autoCheckEnabled, String skippedVersion) {

        public static State defaults() {
            return new State(true, "");
        }

        /** Copia com uma nova versao ignorada. */
        public State withSkippedVersion(String tagName) {
            return new State(autoCheckEnabled, tagName == null ? "" : tagName);
        }

        /** Copia com a checagem automatica ligada/desligada. */
        public State withAutoCheckEnabled(boolean enabled) {
            return new State(enabled, skippedVersion);
        }
    }

    /** Le as preferencias salvas; retorna os padroes se o arquivo nao existir. */
    public State load() throws IOException {
        if (!Files.exists(file)) {
            return State.defaults();
        }
        boolean autoCheckEnabled = true;
        String skippedVersion = "";
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
                case "autoCheckEnabled" -> autoCheckEnabled = Boolean.parseBoolean(value);
                case "skippedVersion" -> skippedVersion = value;
                default -> {
                    // ignora chaves desconhecidas (versoes futuras)
                }
            }
        }
        return new State(autoCheckEnabled, skippedVersion);
    }

    /** Grava as preferencias, criando a pasta se necessario. */
    public void save(State state) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Nureal Database IDE - preferencias de atualizacao automatica\n\n");
        sb.append("autoCheckEnabled=").append(state.autoCheckEnabled()).append('\n');
        sb.append("skippedVersion=").append(state.skippedVersion()).append('\n');
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
