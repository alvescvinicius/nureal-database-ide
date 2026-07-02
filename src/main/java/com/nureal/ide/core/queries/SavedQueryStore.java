package com.nureal.ide.core.queries;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Persiste a biblioteca de queries SALVAS (deliberadas, com titulo) em
 *   ~/.nureal-ide/queries.conf
 *
 * Diferente da sessao (ver {@code SessionStore}), que grava sozinha a cada
 * digitacao: aqui nada muda sem o usuario pedir explicitamente (botao
 * Salvar / Ctrl+S / menu de contexto da aba). Cada query tem um {@code id}
 * estavel (nao muda ao renomear) — e o que permite a aba que a abriu saber
 * que deve SOBRESCREVER da proxima vez, em vez de perguntar o titulo de
 * novo (ver {@code SqlEditorPane#savedQueryId}).
 *
 * Prototipo: sem indice em memoria entre chamadas — cada operacao le o
 * arquivo inteiro, altera, e regrava tudo. Aceitavel porque isto e uma
 * biblioteca pessoal de queries salvas DE PROPOSITO (dezenas/centenas, nao
 * milhares de linhas de um log).
 */
public class SavedQueryStore {

    private static final String DIR_NAME = ".nureal-ide";
    private static final String FILE_NAME = "queries.conf";
    private static final String HEADER = "[query]";

    private final Path file;

    public SavedQueryStore() {
        this(Paths.get(System.getProperty("user.home"), DIR_NAME, FILE_NAME));
    }

    public SavedQueryStore(Path file) {
        this.file = file;
    }

    public Path location() {
        return file;
    }

    /**
     * Uma query salva. {@code connectionName}: nome da conexao onde foi
     * criada (null = criada sem conexao ativa, workspace "sem conexao");
     * usado pelo painel de queries salvas para filtrar pela conexao ATIVA.
     * {@code group}: agrupamento livre (string qualquer, pode ser null) —
     * uma "pasta virtual" sem pasta de verdade no disco.
     */
    public record Query(String id, String title, String sql, String connectionName,
                         long createdAt, long updatedAt, boolean favorite, String group) {

        Query withSql(String newSql, long updatedAt) {
            return new Query(id, title, newSql, connectionName, createdAt, updatedAt, favorite, group);
        }

        Query withTitle(String newTitle, long updatedAt) {
            return new Query(id, newTitle, sql, connectionName, createdAt, updatedAt, favorite, group);
        }

        Query withFavorite(boolean fav, long updatedAt) {
            return new Query(id, title, sql, connectionName, createdAt, updatedAt, fav, group);
        }
    }

    /** Le todas as queries salvas. Vazio se o arquivo ainda nao existe. */
    public synchronized List<Query> loadAll() throws IOException {
        List<Query> out = new ArrayList<>();
        if (!Files.exists(file)) {
            return out;
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

        String id = null;
        String title = null;
        String sql = null;
        String conn = null;
        String group = null;
        long created = 0;
        long updated = 0;
        boolean fav = false;
        boolean open = false;

        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.equals(HEADER)) {
                flush(out, id, title, sql, conn, created, updated, fav, group);
                open = true;
                id = null;
                title = null;
                sql = "";
                conn = null;
                group = null;
                created = 0;
                updated = 0;
                fav = false;
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
                case "title" -> title = decode(value.trim());
                case "sql" -> sql = decode(value.trim());
                case "connection" -> conn = value.trim().isEmpty() ? null : value.trim();
                case "created" -> created = parseLong(value.trim());
                case "updated" -> updated = parseLong(value.trim());
                case "favorite" -> fav = Boolean.parseBoolean(value.trim());
                case "group" -> group = value.trim().isEmpty() ? null : decode(value.trim());
                default -> {
                    // ignora chaves desconhecidas (versoes futuras)
                }
            }
        }
        flush(out, id, title, sql, conn, created, updated, fav, group);
        return out;
    }

    private static void flush(List<Query> out, String id, String title, String sql, String conn,
            long created, long updated, boolean fav, String group) {
        if (id == null || title == null) {
            return;
        }
        out.add(new Query(id, title, sql == null ? "" : sql, conn, created, updated, fav, group));
    }

    /** Grava a lista inteira, criando a pasta se necessario. */
    public synchronized void saveAll(List<Query> queries) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Nureal Database IDE - queries salvas\n");
        sb.append("# Titulo e SQL em Base64 para preservar quebras de linha.\n\n");
        for (Query q : queries) {
            sb.append(HEADER).append('\n');
            sb.append("id=").append(q.id()).append('\n');
            sb.append("title=").append(encode(q.title())).append('\n');
            sb.append("sql=").append(encode(q.sql())).append('\n');
            sb.append("connection=").append(q.connectionName() == null ? "" : q.connectionName()).append('\n');
            sb.append("created=").append(q.createdAt()).append('\n');
            sb.append("updated=").append(q.updatedAt()).append('\n');
            sb.append("favorite=").append(q.favorite()).append('\n');
            sb.append("group=").append(q.group() == null ? "" : encode(q.group())).append('\n');
            sb.append('\n');
        }
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    // ---------- Operacoes de alto nivel ----------

    /** Cria e persiste uma nova query salva; retorna o registro criado (com id novo). */
    public synchronized Query create(String title, String sql, String connectionName) throws IOException {
        List<Query> all = loadAll();
        long now = System.currentTimeMillis();
        Query q = new Query(UUID.randomUUID().toString(), title, sql, connectionName, now, now, false, null);
        all.add(q);
        saveAll(all);
        return q;
    }

    /** Sobrescreve o SQL de uma query ja salva (atualiza "updatedAt"). */
    public synchronized Query updateSql(String id, String sql) throws IOException {
        return update(id, q -> q.withSql(sql, System.currentTimeMillis()));
    }

    public synchronized Query rename(String id, String newTitle) throws IOException {
        return update(id, q -> q.withTitle(newTitle, System.currentTimeMillis()));
    }

    public synchronized Query setFavorite(String id, boolean favorite) throws IOException {
        return update(id, q -> q.withFavorite(favorite, q.updatedAt()));
    }

    public synchronized void delete(String id) throws IOException {
        List<Query> all = loadAll();
        all.removeIf(q -> q.id().equals(id));
        saveAll(all);
    }

    private Query update(String id, UnaryOperator<Query> fn) throws IOException {
        List<Query> all = loadAll();
        Query updated = null;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(id)) {
                updated = fn.apply(all.get(i));
                all.set(i, updated);
                break;
            }
        }
        saveAll(all);
        return updated;
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
