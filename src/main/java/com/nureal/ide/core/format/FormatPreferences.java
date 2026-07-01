package com.nureal.ide.core.format;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Persiste as preferencias de formatacao de SQL e a fonte do editor em
 *   ~/.nureal-ide/format.conf
 *
 * Guarda: preset de formatacao (RIVER/STANDARD/COMMA_FIRST), se as
 * palavras-chave saem em CAIXA ALTA, se chamadas JSON_OBJECT/JSON_ARRAY com
 * varios pares sao indentadas em linhas, e a familia de fonte do editor
 * (vazio = escolha automatica do sistema).
 */
public class FormatPreferences {

    private static final String DIR_NAME = ".nureal-ide";
    private static final String FILE_NAME = "format.conf";

    public static final SqlFormatter.Style DEFAULT_STYLE = SqlFormatter.Style.RIVER;

    private final Path file;

    public FormatPreferences() {
        this(Paths.get(System.getProperty("user.home"), DIR_NAME, FILE_NAME));
    }

    public FormatPreferences(Path file) {
        this.file = file;
    }

    public Path location() {
        return file;
    }

    /** Estado imutavel das preferencias de formatacao. */
    public record State(SqlFormatter.Style style, boolean upperKeywords,
                         boolean indentJson, String editorFontFamily) {

        public static State defaults() {
            return new State(DEFAULT_STYLE, true, true, "");
        }

        /** Constroi o SqlFormatter correspondente a este estado. */
        public SqlFormatter buildFormatter() {
            SqlFormatter.KeywordCase kc = upperKeywords
                    ? SqlFormatter.KeywordCase.UPPER : SqlFormatter.KeywordCase.PRESERVE;
            return new SqlFormatter(kc, style, indentJson);
        }
    }

    public State load() throws IOException {
        if (!Files.exists(file)) {
            return State.defaults();
        }
        SqlFormatter.Style style = DEFAULT_STYLE;
        boolean upperKeywords = true;
        boolean indentJson = true;
        String fontFamily = "";

        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
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
                case "style" -> style = parseStyle(value);
                case "upperKeywords" -> upperKeywords = Boolean.parseBoolean(value);
                case "indentJson" -> indentJson = Boolean.parseBoolean(value);
                case "editorFontFamily" -> fontFamily = value;
                default -> {
                    // ignora chaves desconhecidas (versoes futuras)
                }
            }
        }
        return new State(style, upperKeywords, indentJson, fontFamily);
    }

    public void save(State state) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Nureal Database IDE - formatacao de SQL e fonte do editor\n\n");
        sb.append("style=").append(state.style().name()).append('\n');
        sb.append("upperKeywords=").append(state.upperKeywords()).append('\n');
        sb.append("indentJson=").append(state.indentJson()).append('\n');
        sb.append("editorFontFamily=").append(
                state.editorFontFamily() == null ? "" : state.editorFontFamily()).append('\n');
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static SqlFormatter.Style parseStyle(String s) {
        try {
            return SqlFormatter.Style.valueOf(s.trim());
        } catch (Exception ex) {
            return DEFAULT_STYLE;
        }
    }
}
