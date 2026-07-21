package com.nureal.ide.core.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Persiste posicao/tamanho da janela do Chat com IA em
 *   ~/.nureal-ide/chat-window.conf
 *
 * Mesmo formato chave=valor de {@link UiPreferences}. So guarda bounds — a
 * janela do chat e um {@code JDialog} MODELESS simples (ver {@code ChatWindow}),
 * nao tem os outros toggles de {@link UiPreferences}.
 */
public final class ChatWindowPreferences {

    private static final String DIR_NAME = ".nureal-ide";
    private static final String FILE_NAME = "chat-window.conf";

    private final Path file;

    public ChatWindowPreferences() {
        this(Paths.get(System.getProperty("user.home"), DIR_NAME, FILE_NAME));
    }

    public ChatWindowPreferences(Path file) {
        this.file = file;
    }

    public Path location() {
        return file;
    }

    /** Bounds salvos, ou {@link #UNSET} se a janela nunca foi redimensionada/movida pelo usuario. */
    public record State(int x, int y, int width, int height) {

        public static final State UNSET = new State(0, 0, -1, -1);

        public boolean isSet() {
            return width > 0 && height > 0;
        }
    }

    /** Le os bounds salvos; {@link State#UNSET} se o arquivo nao existir ou estiver incompleto. */
    public State load() throws IOException {
        if (!Files.exists(file)) {
            return State.UNSET;
        }
        int x = 0;
        int y = 0;
        int width = -1;
        int height = -1;

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
                case "x" -> x = parseInt(value, x);
                case "y" -> y = parseInt(value, y);
                case "width" -> width = parseInt(value, width);
                case "height" -> height = parseInt(value, height);
                default -> {
                    // ignora chaves desconhecidas (versoes futuras)
                }
            }
        }
        return (width > 0 && height > 0) ? new State(x, y, width, height) : State.UNSET;
    }

    /** Grava os bounds atuais, criando a pasta se necessario. */
    public void save(State state) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Nureal Database IDE - posicao/tamanho da janela do Chat com IA\n\n");
        sb.append("x=").append(state.x()).append('\n');
        sb.append("y=").append(state.y()).append('\n');
        sb.append("width=").append(state.width()).append('\n');
        sb.append("height=").append(state.height()).append('\n');
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
