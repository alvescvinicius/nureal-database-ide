package com.nureal.ide.core.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persiste a sessao do editor por CONEXAO (workspace): cada conexao guarda suas
 * proprias abas de SQL. Arquivo:  ~/.nureal-ide/session.conf
 *
 * Objetivo: o usuario NUNCA perde o trabalho. As abas e os SQLs sao gravados
 * continuamente (a cada digitacao, com debounce) e ao fechar o app.
 *
 * O SQL de cada aba e gravado em Base64 (uma unica linha), preservando quebras
 * de linha e caracteres especiais sem precisar de um parser.
 */
public class SessionStore {

    private static final String DIR_NAME = ".nureal-ide";
    private static final String FILE_NAME = "session.conf";
    private static final String CONN_HEADER = "[conn]";
    private static final String TAB_HEADER = "[tab]";

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

    /** A sessao de uma conexao: abas + indice da aba selecionada. */
    public record Session(List<Tab> tabs, int selectedIndex) {
    }

    /** Le todas as sessoes (por nome de conexao). Vazio se nao existir. */
    public Map<String, Session> load() throws IOException {
        Map<String, Session> result = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return result;
        }

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        String connName = null;
        int selected = 0;
        List<Tab> tabs = new ArrayList<>();
        String title = null;
        String sql = null;

        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.equals(CONN_HEADER)) {
                flush(result, connName, tabs, title, sql, selected);
                connName = "";
                selected = 0;
                tabs = new ArrayList<>();
                title = null;
                sql = null;
                continue;
            }
            if (line.equals(TAB_HEADER)) {
                addTab(tabs, title, sql);
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
                case "name" -> connName = value;
                case "selected" -> selected = parseInt(value.trim());
                case "title" -> title = value.trim();
                case "sql" -> sql = decode(value.trim());
                default -> {
                    // ignora chaves desconhecidas
                }
            }
        }
        flush(result, connName, tabs, title, sql, selected);
        return result;
    }

    private static void flush(Map<String, Session> result, String connName,
            List<Tab> tabs, String title, String sql, int selected) {
        if (connName == null) {
            return;
        }
        addTab(tabs, title, sql);
        int sel = (selected < 0 || selected >= tabs.size()) ? 0 : selected;
        result.put(connName, new Session(new ArrayList<>(tabs), sel));
    }

    private static void addTab(List<Tab> tabs, String title, String sql) {
        if (title != null) {
            tabs.add(new Tab(title, sql == null ? "" : sql));
        }
    }

    /** Grava todas as sessoes, criando a pasta se necessario. */
    public void save(Map<String, Session> sessions) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Nureal Database IDE - sessao do editor por conexao\n");
        sb.append("# O SQL esta em Base64 para preservar quebras de linha.\n\n");

        for (Map.Entry<String, Session> e : sessions.entrySet()) {
            Session s = e.getValue();
            sb.append(CONN_HEADER).append('\n');
            sb.append("name=").append(nullToEmpty(e.getKey())).append('\n');
            sb.append("selected=").append(s.selectedIndex()).append('\n');
            for (Tab t : s.tabs()) {
                sb.append(TAB_HEADER).append('\n');
                sb.append("title=").append(nullToEmpty(t.title())).append('\n');
                sb.append("sql=").append(encode(nullToEmpty(t.sql()))).append('\n');
            }
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
