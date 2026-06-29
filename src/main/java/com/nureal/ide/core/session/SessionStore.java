package com.nureal.ide.core.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Persiste a sessao do editor (as abas de SQL abertas e seu conteudo) em:
 *   ~/.nureal-ide/session.conf
 *
 * Objetivo: o usuario NUNCA perde o trabalho. As abas e os SQLs sao gravados
 * continuamente (a cada digitacao, com debounce) e tambem ao fechar o app, de
 * modo que mesmo um desligamento abrupto do computador preserve as queries.
 *
 * O SQL de cada aba e gravado em Base64 (uma unica linha), entao quebras de
 * linha e caracteres especiais sao mantidos sem precisar de um parser.
 */
public class SessionStore {

    private static final String DIR_NAME = ".nureal-ide";
    private static final String FILE_NAME = "session.conf";
    private static final String RECORD_HEADER = "[tab]";

    private final Path file;

    public SessionStore() {
        this(Paths.get(System.getProperty("user.home"), DIR_NAME, FILE_NAME));
    }

    public SessionStore(Path file) {
        this.file = file;
    }

    public Path location() {
        return file;
    }

    /** Uma aba salva: titulo + SQL completo. */
    public record Tab(String title, String sql) {
    }

    /** A sessao inteira: abas + indice da aba selecionada. */
    public record Session(List<Tab> tabs, int selectedIndex) {
    }

    /** Le a sessao salva. Retorna sessao vazia se nao existir. */
    public Session load() throws IOException {
        List<Tab> tabs = new ArrayList<>();
        int selected = 0;
        if (!Files.exists(file)) {
            return new Session(tabs, 0);
        }

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        String title = null;
        String sql = null;
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.equals(RECORD_HEADER)) {
                if (title != null) {
                    tabs.add(new Tab(title, sql == null ? "" : sql));
                }
                title = "SQL Query";
                sql = "";
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1);
            switch (key) {
                case "selectedIndex" -> selected = parseInt(value.trim());
                case "title" -> title = value.trim();
                case "sql" -> sql = decode(value.trim());
                default -> {
                    // ignora chaves desconhecidas
                }
            }
        }
        if (title != null) {
            tabs.add(new Tab(title, sql == null ? "" : sql));
        }
        if (selected < 0 || selected >= tabs.size()) {
            selected = 0;
        }
        return new Session(tabs, selected);
    }

    /** Grava a sessao, criando a pasta se necessario. */
    public void save(Session session) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Nureal Database IDE - sessao do editor (abas e SQLs)\n");
        sb.append("# O SQL esta em Base64 para preservar quebras de linha.\n\n");
        sb.append("selectedIndex=").append(session.selectedIndex()).append("\n\n");

        for (Tab t : session.tabs()) {
            sb.append(RECORD_HEADER).append('\n');
            sb.append("title=").append(nullToEmpty(t.title())).append('\n');
            sb.append("sql=").append(encode(nullToEmpty(t.sql()))).append('\n');
            sb.append('\n');
        }

        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(String plain) {
        return Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String encoded) {
        try {
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
