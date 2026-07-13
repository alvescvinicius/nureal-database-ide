package com.nureal.ide.core.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Persiste as preferencias de layout/aparencia da janela em
 *   ~/.nureal-ide/ui.conf
 *
 * Guarda: lado do painel lateral (esquerda/direita), orientacao do split de
 * resultados (horizontal/vertical), nivel de zoom da interface, modo
 * compacto (densidade), se o keep-alive de conexao esta ligado e o intervalo
 * (em segundos) usado por ele. Formato simples chave=valor, igual ao das
 * outras stores do projeto (ConnectionStore, SessionStore).
 */
public class UiPreferences {

    private static final String DIR_NAME = ".nureal-ide";
    private static final String FILE_NAME = "ui.conf";

    /** Indice padrao em {@code MainWindow.ZOOM_LEVELS} (100%). */
    public static final int DEFAULT_ZOOM_INDEX = 2;

    /**
     * Indice padrao em {@code MainWindow.ROW_SPACING_LEVELS} ("Padrao", 22px
     * de altura de linha) — o MESMO valor que a grade de resultados sempre
     * usou antes deste controle existir, para quem atualiza a IDE nao ver
     * nenhuma mudanca visual ate escolher um espacamento diferente.
     */
    public static final int DEFAULT_ROW_SPACING_INDEX = 1;

    private final Path file;

    public UiPreferences() {
        this(Paths.get(System.getProperty("user.home"), DIR_NAME, FILE_NAME));
    }

    public UiPreferences(Path file) {
        this.file = file;
    }

    public Path location() {
        return file;
    }

    /** Intervalo padrao do keep-alive, em segundos, quando nunca configurado. */
    public static final int DEFAULT_KEEP_ALIVE_SECONDS = 60;

    /** Estado imutavel das preferencias de UI. */
    public record State(boolean sidebarOnRight, boolean resultsVertical,
                         int zoomIndex, boolean compactMode, boolean keepAliveEnabled,
                         int keepAliveIntervalSeconds, int rowSpacingIndex) {

        public static State defaults() {
            return new State(false, false, DEFAULT_ZOOM_INDEX, false, false, DEFAULT_KEEP_ALIVE_SECONDS,
                    DEFAULT_ROW_SPACING_INDEX);
        }
    }

    /** Le as preferencias salvas; retorna os padroes se o arquivo nao existir. */
    public State load() throws IOException {
        if (!Files.exists(file)) {
            return State.defaults();
        }
        boolean sidebarOnRight = false;
        boolean resultsVertical = false;
        int zoomIndex = DEFAULT_ZOOM_INDEX;
        boolean compactMode = false;
        boolean keepAliveEnabled = false;
        int keepAliveIntervalSeconds = DEFAULT_KEEP_ALIVE_SECONDS;
        int rowSpacingIndex = DEFAULT_ROW_SPACING_INDEX;

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
                case "sidebarOnRight" -> sidebarOnRight = Boolean.parseBoolean(value);
                case "resultsVertical" -> resultsVertical = Boolean.parseBoolean(value);
                case "zoomIndex" -> zoomIndex = parseIndex(value);
                case "compactMode" -> compactMode = Boolean.parseBoolean(value);
                case "keepAliveEnabled" -> keepAliveEnabled = Boolean.parseBoolean(value);
                case "keepAliveIntervalSeconds" -> keepAliveIntervalSeconds = parseSeconds(value);
                case "rowSpacingIndex" -> rowSpacingIndex = parseRowSpacingIndex(value);
                default -> {
                    // ignora chaves desconhecidas (versoes futuras)
                }
            }
        }
        return new State(sidebarOnRight, resultsVertical, zoomIndex, compactMode, keepAliveEnabled,
                keepAliveIntervalSeconds, rowSpacingIndex);
    }

    /** Grava as preferencias, criando a pasta se necessario. */
    public void save(State state) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Nureal Database IDE - preferencias de layout/aparencia\n\n");
        sb.append("sidebarOnRight=").append(state.sidebarOnRight()).append('\n');
        sb.append("resultsVertical=").append(state.resultsVertical()).append('\n');
        sb.append("zoomIndex=").append(state.zoomIndex()).append('\n');
        sb.append("compactMode=").append(state.compactMode()).append('\n');
        sb.append("keepAliveEnabled=").append(state.keepAliveEnabled()).append('\n');
        sb.append("keepAliveIntervalSeconds=").append(state.keepAliveIntervalSeconds()).append('\n');
        sb.append("rowSpacingIndex=").append(state.rowSpacingIndex()).append('\n');
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static int parseIndex(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return DEFAULT_ZOOM_INDEX;
        }
    }

    /**
     * Assim como {@link #parseIndex}, nao valida contra o tamanho real de
     * {@code MainWindow.ROW_SPACING_LEVELS} — este pacote ({@code core}) nao
     * conhece a UI de proposito (mesma razao documentada em
     * {@code SqlCompletionProvider}). {@code MainWindow} e quem faz o
     * "clamp" final contra o array de verdade, do mesmo jeito que ja faz
     * hoje para {@code zoomIndex} (ver {@code clampZoomIndex}).
     */
    private static int parseRowSpacingIndex(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return DEFAULT_ROW_SPACING_INDEX;
        }
    }

    private static int parseSeconds(String s) {
        try {
            int v = Integer.parseInt(s);
            return (v > 0) ? v : DEFAULT_KEEP_ALIVE_SECONDS;
        } catch (NumberFormatException e) {
            return DEFAULT_KEEP_ALIVE_SECONDS;
        }
    }
}
