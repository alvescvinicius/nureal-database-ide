package com.nureal.ide.core.history;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Persiste o HISTORICO de execucoes (log automatico, sem titulo, gravado a
 * cada instrucao rodada) em
 *   ~/.nureal-ide/history.conf
 *
 * Diferente da {@code SavedQueryStore} (biblioteca deliberada, so muda
 * quando o usuario pede): aqui cada execucao de SQL entra sozinha, sem
 * intervencao do usuario — e por isso limitamos o tamanho (ver
 * {@link #MAX_ENTRIES}), descartando as entradas mais antigas, para o
 * arquivo nao crescer sem fim.
 *
 * Mesmo formato/estrategia de leitura-tudo-regrava-tudo da SavedQueryStore:
 * aceitavel pelo mesmo motivo (algumas centenas de linhas, nao milhares).
 */
public class ExecutionHistoryStore {

    /** Numero maximo de entradas guardadas; as mais antigas caem fora. */
    private static final int MAX_ENTRIES = 500;

    private static final String DIR_NAME = ".nureal-ide";
    private static final String FILE_NAME = "history.conf";
    private static final String HEADER = "[exec]";

    private final Path file;

    public ExecutionHistoryStore() {
        this(Paths.get(System.getProperty("user.home"), DIR_NAME, FILE_NAME));
    }

    public ExecutionHistoryStore(Path file) {
        this.file = file;
    }

    public Path location() {
        return file;
    }

    /**
     * Uma execucao registrada. {@code connectionName}: nome da conexao onde
     * rodou (null = workspace "sem conexao"). {@code schema}: esquema ativo
     * da aba no momento da execucao (pode ser null). {@code resultSummary}:
     * texto curto do resultado ("42 linha(s) afetada(s)", mensagem de erro,
     * ou null para resultados em grade).
     */
    public record Entry(String id, String sql, String connectionName, String schema,
                         long executedAt, long durationMs, boolean success, String resultSummary) {
    }

    /** Le todo o historico. Vazio se o arquivo ainda nao existe. */
    public synchronized List<Entry> loadAll() throws IOException {
        List<Entry> out = new ArrayList<>();
        if (!Files.exists(file)) {
            return out;
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

        String id = null;
        String sql = null;
        String conn = null;
        String schema = null;
        long executedAt = 0;
        long durationMs = 0;
        boolean success = true;
        String summary = null;
        boolean open = false;

        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.equals(HEADER)) {
                flush(out, id, sql, conn, schema, executedAt, durationMs, success, summary);
                open = true;
                id = null;
                sql = "";
                conn = null;
                schema = null;
                executedAt = 0;
                durationMs = 0;
                success = true;
                summary = null;
                continue;
            }
            if (!open) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1);
            switch (key) {
                case "id" -> id = value.trim();
                case "sql" -> sql = decode(value.trim());
                case "connection" -> conn = value.trim().isEmpty() ? null : value.trim();
                case "schema" -> schema = value.trim().isEmpty() ? null : value.trim();
                case "executedAt" -> executedAt = parseLong(value.trim());
                case "durationMs" -> durationMs = parseLong(value.trim());
                case "success" -> success = Boolean.parseBoolean(value.trim());
                case "summary" -> summary = value.trim().isEmpty() ? null : decode(value.trim());
                default -> {
                    // ignora chaves desconhecidas (versoes futuras)
                }
            }
        }
        flush(out, id, sql, conn, schema, executedAt, durationMs, success, summary);
        return out;
    }

    private static void flush(List<Entry> out, String id, String sql, String conn, String schema,
            long executedAt, long durationMs, boolean success, String summary) {
        if (id == null) {
            return;
        }
        out.add(new Entry(id, sql == null ? "" : sql, conn, schema, executedAt, durationMs, success, summary));
    }

    /** Grava a lista inteira, criando a pasta se necessario. */
    public synchronized void saveAll(List<Entry> entries) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Nureal Database IDE - historico de execucoes (gerado automaticamente)\n");
        sb.append("# SQL e resumo em Base64 para preservar quebras de linha.\n\n");
        for (Entry e : entries) {
            sb.append(HEADER).append('\n');
            sb.append("id=").append(e.id()).append('\n');
            sb.append("sql=").append(encode(e.sql())).append('\n');
            sb.append("connection=").append(e.connectionName() == null ? "" : e.connectionName()).append('\n');
            sb.append("schema=").append(e.schema() == null ? "" : e.schema()).append('\n');
            sb.append("executedAt=").append(e.executedAt()).append('\n');
            sb.append("durationMs=").append(e.durationMs()).append('\n');
            sb.append("success=").append(e.success()).append('\n');
            sb.append("summary=").append(e.resultSummary() == null ? "" : encode(e.resultSummary())).append('\n');
            sb.append('\n');
        }
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Registra uma execucao (chamado pelo MainWindow logo apos cada
     * instrucao rodar, sucesso ou erro). Descarta as entradas mais antigas
     * se o total passar de {@link #MAX_ENTRIES}.
     */
    public synchronized Entry append(String sql, String connectionName, String schema,
            long durationMs, boolean success, String resultSummary) throws IOException {
        List<Entry> all = loadAll();
        Entry entry = new Entry(UUID.randomUUID().toString(), sql, connectionName, schema,
                System.currentTimeMillis(), durationMs, success, resultSummary);
        all.add(entry);
        if (all.size() > MAX_ENTRIES) {
            all.sort(Comparator.comparingLong(Entry::executedAt));
            all = new ArrayList<>(all.subList(all.size() - MAX_ENTRIES, all.size()));
        }
        saveAll(all);
        return entry;
    }

    public synchronized void delete(String id) throws IOException {
        List<Entry> all = loadAll();
        all.removeIf(e -> e.id().equals(id));
        saveAll(all);
    }

    /** Apaga todo o historico (botao "Limpar historico" no painel). */
    public synchronized void clear() throws IOException {
        saveAll(new ArrayList<>());
    }

    /** Apaga so o historico de uma conexao (null = workspace "sem conexao"). */
    public synchronized void clearForConnection(String connectionName) throws IOException {
        List<Entry> all = loadAll();
        all.removeIf(e -> java.util.Objects.equals(e.connectionName(), connectionName));
        saveAll(all);
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

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
