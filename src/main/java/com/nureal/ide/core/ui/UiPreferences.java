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
 * resultados (horizontal/vertical), nivel de zoom da interface e modo
 * compacto (densidade). Formato simples chave=valor, igual ao das outras
 * stores do projeto (ConnectionStore, SessionStore).
 */
public class UiPreferences {

    private static final String DIR_NAME = ".nureal-ide";
    private static final String FILE_NAME = "ui.conf";

    /** Indice padrao em {@code MainWindow.ZOOM_LEVELS} (100%). */
    public static final int DEFAULT_ZOOM_INDEX = 2;

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

    /** Estado imutavel das preferencias de UI. */
    public record State(boolean sidebarOnRight, boolean resultsVertical,
                         int zoomIndex, boolean compactMode) {

        public static State defaults() {
            return new State(false, false, DEFAULT_ZOOM_INDEX, false);
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
                default -> {
                    // ignora chaves desconhecidas (versoes futuras)
                }
            }
        }
        return new State(sidebarOnRight, resultsVertical, zoomIndex, compactMode);
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
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static int parseIndex(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return DEFAULT_ZOOM_INDEX;
        }
    }
}
