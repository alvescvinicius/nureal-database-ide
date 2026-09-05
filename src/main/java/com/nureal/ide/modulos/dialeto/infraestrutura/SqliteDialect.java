package com.nureal.ide.modulos.dialeto.infraestrutura;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.nureal.ide.modulos.dialeto.dominio.contratos.AdminCapability;
import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;
import com.nureal.ide.modulos.dialeto.dominio.contratos.ReplicationCapability;
import com.nureal.ide.modulos.dialeto.dominio.contratos.SecurityCapability;
import com.nureal.ide.modulos.conexoes.dominio.entidades.ConnectionProfile;
import com.nureal.ide.modulos.metadados.dominio.entidades.ColumnDetail;
import com.nureal.ide.modulos.metadados.dominio.entidades.ColumnInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.ForeignKeyInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.IndexInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.NewColumnSpec;
import com.nureal.ide.modulos.metadados.dominio.entidades.NewTableSpec;
import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaForeignKey;
import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.TableDetails;
import com.nureal.ide.modulos.metadados.dominio.entidades.TableInfo;

/**
 * Implementacao para SQLite — terceiro driver de verdade da IDE (depois de
 * {@link MySqlDialect} e {@link PostgresDialect}). SQLite e um banco
 * embutido de ARQUIVO UNICO, sem servidor/porta/usuario/senha e sem o
 * conceito de multiplos schemas por conexao — por isso varias peças da
 * IDE (pensadas em torno do modelo host/porta/schema do MySQL) sao
 * reinterpretadas aqui:
 * <ul>
 * <li>{@link ConnectionProfile#host()} e o CAMINHO do arquivo {@code .db}
 * (ou {@code :memory:} para um banco temporario em memoria) — porta,
 * usuario e senha sao ignorados.</li>
 * <li>SQLite so tem UM schema por conexao, sempre chamado {@code main}
 * (ver {@link #listSchemas}) — nao ha "trocar de banco" numa conexao
 * SQLite aberta.</li>
 * <li>{@link #createSchemaStatement}/{@link #dropSchemaStatement} nao tem
 * equivalente (um "schema" novo exigiria {@code ATTACH DATABASE} com outro
 * arquivo, um fluxo totalmente diferente de "criar/apagar banco") — lancam
 * {@link UnsupportedOperationException} com mensagem clara em vez de
 * mandar SQL invalido pro banco.</li>
 * <li>SQLite nao tem PROCEDURE/FUNCTION via SQL (so via extensao na
 * linguagem hospedeira, fora do alcance de um {@code CREATE} qualquer) —
 * os 4 metodos de procedure/function tambem lancam {@link
 * UnsupportedOperationException}.</li>
 * <li>{@code ALTER TABLE} do SQLite e limitado: sem {@code ADD
 * CONSTRAINT}/{@code DROP CONSTRAINT} (chave estrangeira so pode ser
 * definida no {@code CREATE TABLE} original) e sem mudar tipo/nulo/default
 * de uma coluna existente in-place — ver {@link #alterTableAddStatements}/
 * {@link #alterTableModifyStatements} pra exatamente o que e suportado
 * (colunas e indices) e o que lanca excecao (chaves estrangeiras e
 * modificacao de coluna existente).</li>
 * </ul>
 * <p>
 * So implementa as 4 capacidades OBRIGATORIAS — {@link #security()},
 * {@link #admin()} e {@link #replication()} devolvem {@code
 * Optional.empty()} (SQLite nao tem usuarios/roles, processos concorrentes
 * nem replicacao — sao conceitos de banco CLIENTE/SERVIDOR, que um arquivo
 * local nao tem).
 */
public class SqliteDialect implements DatabaseDialect {

    /** Unico "schema" que uma conexao SQLite enxerga — ver javadoc da classe. */
    public static final String MAIN_SCHEMA = "main";

    @Override
    public Optional<SecurityCapability> security() {
        return Optional.empty();
    }

    @Override
    public Optional<AdminCapability> admin() {
        return Optional.empty();
    }

    @Override
    public Optional<ReplicationCapability> replication() {
        return Optional.empty();
    }

    @Override
    public String id() {
        return "sqlite";
    }

    @Override
    public String driverClassName() {
        return "org.sqlite.JDBC";
    }

    /** {@code profile.host()} e o caminho do arquivo (ou {@code :memory:}) — ver javadoc da classe. */
    @Override
    public String buildJdbcUrl(ConnectionProfile profile) {
        return "jdbc:sqlite:" + profile.host();
    }

    @Override
    public List<String> sessionInitStatements() {
        // Sem isto, o proprio SQLite ignora silenciosamente qualquer
        // "REFERENCES" declarado no CREATE TABLE (enforcement de FK e
        // desligado por padrao, por compatibilidade historica).
        return List.of("PRAGMA foreign_keys = ON");
    }

    /** Sempre {@code ["main"]} — SQLite so tem um schema por conexao (ver javadoc da classe). */
    @Override
    public List<String> listSchemas(Connection conn) throws SQLException {
        return List.of(MAIN_SCHEMA);
    }

    private static final String TABLES_SQL = "SELECT name FROM sqlite_master "
            + "WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name";

    private static final String VIEWS_SQL = "SELECT name FROM sqlite_master WHERE type = 'view' ORDER BY name";

    private static final String TRIGGERS_SQL = "SELECT name FROM sqlite_master WHERE type = 'trigger' ORDER BY name";

    // pragma_table_info(nome) como funcao de tabela (SQLite 3.16+, presente
    // em qualquer versao recente do driver xerial): permite trazer as
    // colunas de TODAS as tabelas/views de uma vez, juntando com
    // sqlite_master — mesma ideia da UNICA consulta que MySQL/Postgres usam
    // aqui, so que o SQLite nao tem um catalogo "COLUMNS" pronto, so a
    // PRAGMA por tabela.
    private static final String ALL_COLUMNS_SQL = "SELECT m.name AS table_name, p.name AS column_name, "
            + "p.type AS column_type, p.cid AS position "
            + "FROM sqlite_master m, pragma_table_info(m.name) p "
            + "WHERE m.type IN ('table', 'view') AND m.name NOT LIKE 'sqlite_%' "
            + "ORDER BY m.name, p.cid";

    /** {@code schema} ignorado (SQLite so tem "main") — ver javadoc da classe. Sem procedures/functions/eventos (conceitos que o SQLite nao tem). */
    @Override
    public SchemaInfo loadSchema(Connection conn, String schema) throws SQLException {
        Map<String, List<ColumnInfo>> columnsByObject = new LinkedHashMap<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(ALL_COLUMNS_SQL)) {
            while (rs.next()) {
                String object = rs.getString("table_name");
                ColumnInfo col = new ColumnInfo(
                        rs.getString("column_name"),
                        rs.getString("column_type"),
                        rs.getInt("position") + 1);
                columnsByObject.computeIfAbsent(object, k -> new ArrayList<>()).add(col);
            }
        }

        List<TableInfo> tables = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(TABLES_SQL)) {
            while (rs.next()) {
                String name = rs.getString(1);
                tables.add(new TableInfo(name, columnsByObject.getOrDefault(name, List.of())));
            }
        }

        List<TableInfo> views = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(VIEWS_SQL)) {
            while (rs.next()) {
                String name = rs.getString(1);
                views.add(new TableInfo(name, columnsByObject.getOrDefault(name, List.of())));
            }
        }

        List<String> triggers = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(TRIGGERS_SQL)) {
            while (rs.next()) {
                triggers.add(rs.getString(1));
            }
        }

        return new SchemaInfo(MAIN_SCHEMA, tables, views, List.of(), List.of(), triggers, List.of());
    }

    /** {@code schema} ignorado — ver javadoc da classe. */
    @Override
    public TableDetails loadTableDetails(Connection conn, String schema, String table) throws SQLException {
        return new TableDetails(
                loadColumnDetails(conn, table),
                loadIndexes(conn, table),
                loadForeignKeys(conn, table));
    }

    /**
     * PRAGMA nao aceita bind parameter (?) pro nome da tabela — o nome
     * precisa ir INLINE na string SQL. Seguro aqui porque {@code table} vem
     * sempre de metadados do PROPRIO banco (ja listado por {@link
     * #loadSchema}), nunca de entrada livre do usuario (mesma premissa que
     * {@link MetadataCapability#randomSampleQuery} ja documenta pro
     * MySQL/Postgres); ainda assim {@link #quoteIdentifier} escapa aspas
     * internas por seguranca.
     */
    private List<ColumnDetail> loadColumnDetails(Connection conn, String table) throws SQLException {
        List<ColumnDetail> columns = new ArrayList<>();
        String sql = "PRAGMA table_info(" + quoteIdentifier(table) + ")";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            List<Object[]> rows = new ArrayList<>();
            int pkColumnCount = 0;
            while (rs.next()) {
                int pk = rs.getInt("pk");
                if (pk > 0) {
                    pkColumnCount++;
                }
                rows.add(new Object[] {
                        rs.getInt("cid"), rs.getString("name"), rs.getString("type"),
                        rs.getInt("notnull"), rs.getString("dflt_value"), pk
                });
            }
            for (Object[] row : rows) {
                String type = (String) row[2];
                int pk = (Integer) row[5];
                // INTEGER PRIMARY KEY (sem composicao com outra coluna) e
                // sempre um alias do rowid: o SQLite atribui um valor novo
                // sozinho quando omitido na insercao, mesmo sem a palavra
                // AUTOINCREMENT — equivalente pratico ao AUTO_INCREMENT do
                // MySQL pro proposito de quem consome ColumnDetail#extra()
                // (Populador de Tabelas, assistente de DDL).
                boolean autoIncrement = pk > 0 && pkColumnCount == 1
                        && type != null && type.toUpperCase(Locale.ROOT).contains("INT");
                columns.add(new ColumnDetail(
                        (Integer) row[0] + 1,
                        (String) row[1],
                        type,
                        (Integer) row[3] == 0,
                        pk > 0 ? "PRI" : "",
                        (String) row[4],
                        autoIncrement ? "auto_increment" : "",
                        null));
            }
        }
        return columns;
    }

    private List<IndexInfo> loadIndexes(Connection conn, String table) throws SQLException {
        List<IndexInfo> indexes = new ArrayList<>();
        String listSql = "PRAGMA index_list(" + quoteIdentifier(table) + ")";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(listSql)) {
            while (rs.next()) {
                String name = rs.getString("name");
                boolean unique = rs.getInt("unique") != 0;
                String origin = rs.getString("origin"); // 'c' = CREATE INDEX, 'u' = UNIQUE constraint, 'pk' = PRIMARY KEY
                List<String> columns = new ArrayList<>();
                String infoSql = "PRAGMA index_info(" + quoteIdentifier(name) + ")";
                try (Statement st2 = conn.createStatement(); ResultSet rs2 = st2.executeQuery(infoSql)) {
                    while (rs2.next()) {
                        columns.add(rs2.getString("name"));
                    }
                }
                indexes.add(new IndexInfo(name, unique, origin, columns));
            }
        }
        return indexes;
    }

    private List<ForeignKeyInfo> loadForeignKeys(Connection conn, String table) throws SQLException {
        // Agrupa por "id" (PRAGMA foreign_key_list numera cada FK; varias
        // linhas com o mesmo id = uma FK COMPOSTA, uma por coluna, na ordem
        // de "seq"). SQLite nao da NOME as chaves estrangeiras — sintetiza
        // um a partir da tabela e do id.
        Map<Integer, List<String>> cols = new LinkedHashMap<>();
        Map<Integer, List<String>> refCols = new LinkedHashMap<>();
        Map<Integer, String> refTable = new HashMap<>();
        Map<Integer, String> onUpdate = new HashMap<>();
        Map<Integer, String> onDelete = new HashMap<>();
        String sql = "PRAGMA foreign_key_list(" + quoteIdentifier(table) + ")";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                int id = rs.getInt("id");
                cols.computeIfAbsent(id, k -> new ArrayList<>()).add(rs.getString("from"));
                refCols.computeIfAbsent(id, k -> new ArrayList<>()).add(rs.getString("to"));
                refTable.put(id, rs.getString("table"));
                onUpdate.put(id, rs.getString("on_update"));
                onDelete.put(id, rs.getString("on_delete"));
            }
        }
        List<ForeignKeyInfo> fks = new ArrayList<>();
        cols.forEach((id, columns) -> fks.add(new ForeignKeyInfo(
                "fk_" + table + "_" + id, columns, refTable.get(id), refCols.get(id),
                onUpdate.get(id), onDelete.get(id))));
        return fks;
    }

    /**
     * SQLite nao tem um catalogo unico com as FKs de TODAS as tabelas (ao
     * contrario de MySQL/Postgres, que tem uma view/tabela de sistema pra
     * isso) — resolve iterando {@link #loadForeignKeys} tabela por tabela
     * (lista de tabelas do proprio schema), transparente pra quem chama
     * (mesma assinatura, so a implementacao interna que precisa de N
     * consultas em vez de uma so).
     */
    @Override
    public List<SchemaForeignKey> loadSchemaForeignKeys(Connection conn, String schema) throws SQLException {
        List<SchemaForeignKey> all = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(TABLES_SQL)) {
            while (rs.next()) {
                String table = rs.getString(1);
                for (ForeignKeyInfo fk : loadForeignKeys(conn, table)) {
                    all.add(new SchemaForeignKey(fk.name(), table, fk.columns(), fk.referencedTable(),
                            fk.referencedColumns(), fk.onUpdate(), fk.onDelete()));
                }
            }
        }
        return all;
    }

    /** Mesma limitacao de catalogo de {@link #loadSchemaForeignKeys}: itera tabela por tabela via {@code PRAGMA table_info}. */
    @Override
    public Map<String, Set<String>> loadSchemaPrimaryKeys(Connection conn, String schema) throws SQLException {
        Map<String, Set<String>> byTable = new HashMap<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(TABLES_SQL)) {
            while (rs.next()) {
                String table = rs.getString(1);
                Set<String> pkCols = new LinkedHashSet<>();
                String infoSql = "PRAGMA table_info(" + quoteIdentifier(table) + ")";
                try (Statement st2 = conn.createStatement(); ResultSet rs2 = st2.executeQuery(infoSql)) {
                    while (rs2.next()) {
                        if (rs2.getInt("pk") > 0) {
                            pkCols.add(rs2.getString("name"));
                        }
                    }
                }
                if (!pkCols.isEmpty()) {
                    byTable.put(table, pkCols);
                }
            }
        }
        return byTable;
    }

    /**
     * {@code sqlite_master.sql} guarda o texto EXATO do {@code CREATE}
     * original — ao contrario do MySQL ({@code SHOW CREATE TABLE}, uma
     * reconstrucao) e do Postgres (sem equivalente builtin pra TABLE, ver
     * {@link PostgresDialect}), aqui a definicao e sempre EXATA pra
     * qualquer tipo de objeto, sem aproximacao nenhuma.
     */
    @Override
    public String definitionQuery(String objectKind, String objectName) {
        String type = objectKind.toLowerCase(Locale.ROOT);
        return "SELECT sql FROM sqlite_master WHERE type = " + quoteLiteral(type)
                + " AND name = " + quoteLiteral(objectName);
    }

    @Override
    public String quoteIdentifier(String ident) {
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }

    @Override
    public String randomSampleQuery(String table, String column, int limit) {
        return "SELECT " + quoteIdentifier(column) + " FROM " + quoteIdentifier(table)
                + " ORDER BY RANDOM() LIMIT " + limit;
    }

    /** Ver javadoc da classe: SQLite nao tem equivalente a "criar um banco novo" dentro de uma conexao ja aberta. */
    @Override
    public String createSchemaStatement(String name) {
        throw new UnsupportedOperationException(
                "SQLite nao suporta criar um schema/banco novo dentro de uma conexao existente "
                        + "(cada arquivo .db e uma conexao separada) — crie uma nova conexao apontando "
                        + "para um arquivo .db diferente.");
    }

    @Override
    public String dropSchemaStatement(String name) {
        throw new UnsupportedOperationException(
                "SQLite nao suporta apagar um schema/banco pela IDE — para descartar um banco SQLite, "
                        + "apague o proprio arquivo .db pelo sistema operacional.");
    }

    /**
     * Igual ao MySQL na estrutura (colunas, PK, FK inline), com a
     * diferenca do AUTOINCREMENT: no SQLite ele so e valido numa unica
     * coluna do tipo {@code INTEGER PRIMARY KEY} declarada assim SOZINHA
     * (nao dentro de uma clausula {@code PRIMARY KEY(...)} separada) — a
     * primeira coluna marcada {@code autoIncrement} vira {@code "col"
     * INTEGER PRIMARY KEY AUTOINCREMENT} inline e sai da lista de colunas
     * da PRIMARY KEY composta no final (mesmo que outras colunas tambem
     * estejam marcadas como chave primaria).
     */
    @Override
    public String createTableStatement(NewTableSpec spec) {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE ").append(quoteIdentifier(spec.name())).append(" (\n");

        List<NewColumnSpec> cols = spec.columns();
        List<ForeignKeyInfo> fks = spec.foreignKeys() == null ? List.of() : spec.foreignKeys();
        List<IndexInfo> idxs = spec.indexes() == null ? List.of() : spec.indexes();

        String autoIncrementColumn = cols.stream()
                .filter(NewColumnSpec::autoIncrement)
                .map(NewColumnSpec::name)
                .findFirst()
                .orElse(null);

        List<String> pkColumns = new ArrayList<>();
        for (NewColumnSpec c : cols) {
            if (c.primaryKey() && !c.name().equals(autoIncrementColumn)) {
                pkColumns.add(quoteIdentifier(c.name()));
            }
        }
        int extraClauses = (pkColumns.isEmpty() ? 0 : 1) + fks.size();

        for (int i = 0; i < cols.size(); i++) {
            NewColumnSpec c = cols.get(i);
            sql.append("  ").append(columnDefinition(c, c.name().equals(autoIncrementColumn)));
            if (i < cols.size() - 1 || extraClauses > 0) {
                sql.append(",\n");
            } else {
                sql.append("\n");
            }
        }
        int remaining = extraClauses;
        if (!pkColumns.isEmpty()) {
            sql.append("  PRIMARY KEY (").append(String.join(", ", pkColumns)).append(")");
            remaining--;
            sql.append(remaining > 0 ? ",\n" : "\n");
        }
        for (ForeignKeyInfo fk : fks) {
            sql.append("  ").append(foreignKeyDefinition(spec.name(), fk));
            remaining--;
            sql.append(remaining > 0 ? ",\n" : "\n");
        }
        sql.append(")");

        for (IndexInfo idx : idxs) {
            sql.append(";\n").append(indexStatement(spec.name(), idx));
        }
        return sql.toString();
    }

    /**
     * ADD COLUMN e CREATE INDEX (separado) sao suportados normalmente; ADD
     * COLUMN com FK NAO E — o SQLite so aceita FOREIGN KEY declarada no
     * CREATE TABLE original, nunca via ALTER TABLE. Lanca {@link
     * UnsupportedOperationException} se {@code newForeignKeys} nao vier
     * vazia, em vez de gerar um {@code ALTER TABLE ... ADD CONSTRAINT} que
     * o SQLite simplesmente rejeitaria na hora de executar.
     */
    @Override
    public List<String> alterTableAddStatements(String tableName, List<NewColumnSpec> newColumns,
            List<ForeignKeyInfo> newForeignKeys, List<IndexInfo> newIndexes) {
        List<NewColumnSpec> cols = newColumns == null ? List.of() : newColumns;
        List<ForeignKeyInfo> fks = newForeignKeys == null ? List.of() : newForeignKeys;
        List<IndexInfo> idxs = newIndexes == null ? List.of() : newIndexes;
        if (!fks.isEmpty()) {
            throw new UnsupportedOperationException(
                    "SQLite nao suporta adicionar chave estrangeira a uma tabela existente "
                            + "(so pode ser declarada no CREATE TABLE original) — recrie a tabela para adicionar "
                            + "esta chave estrangeira.");
        }
        if (cols.isEmpty() && idxs.isEmpty()) {
            return List.of();
        }
        List<String> statements = new ArrayList<>();
        for (NewColumnSpec c : cols) {
            statements.add("ALTER TABLE " + quoteIdentifier(tableName) + " ADD COLUMN " + columnDefinition(c, false));
        }
        for (IndexInfo idx : idxs) {
            statements.add(indexStatement(tableName, idx));
        }
        return statements;
    }

    /**
     * So DROP COLUMN e DROP INDEX sao suportados (SQLite 3.35+) — DROP de
     * chave estrangeira (constraints do SQLite nao tem nome/nao sao
     * dropaveis individualmente) e qualquer modificacao de coluna EXISTENTE
     * (tipo/nulo/default: sem {@code MODIFY}/{@code ALTER COLUMN TYPE})
     * lancam {@link UnsupportedOperationException} — a unica forma real do
     * SQLite fazer isso e recriar a tabela inteira (o classico "12-step
     * ALTER TABLE"), fora do escopo deste assistente guiado.
     */
    @Override
    public List<String> alterTableModifyStatements(String tableName, List<NewColumnSpec> modifiedColumns,
            List<String> droppedColumns, List<String> droppedForeignKeys, List<String> droppedIndexes) {
        List<NewColumnSpec> mods = modifiedColumns == null ? List.of() : modifiedColumns;
        List<String> dropCols = droppedColumns == null ? List.of() : droppedColumns;
        List<String> dropFks = droppedForeignKeys == null ? List.of() : droppedForeignKeys;
        List<String> dropIdxs = droppedIndexes == null ? List.of() : droppedIndexes;
        if (!mods.isEmpty() || !dropFks.isEmpty()) {
            throw new UnsupportedOperationException(
                    "SQLite nao suporta modificar uma coluna existente (tipo, nulo ou default) nem remover "
                            + "uma chave estrangeira — a unica forma e recriar a tabela inteira com a definicao "
                            + "nova, fora do que este assistente guiado faz.");
        }
        if (dropCols.isEmpty() && dropIdxs.isEmpty()) {
            return List.of();
        }
        List<String> statements = new ArrayList<>();
        for (String idx : dropIdxs) {
            statements.add("DROP INDEX " + quoteIdentifier(idx));
        }
        for (String col : dropCols) {
            statements.add("ALTER TABLE " + quoteIdentifier(tableName) + " DROP COLUMN " + quoteIdentifier(col));
        }
        return statements;
    }

    @Override
    public String createViewStatement(String name, String selectSql) {
        return "CREATE VIEW " + quoteIdentifier(name) + " AS\n" + selectSql;
    }

    /** SQLite nao tem {@code CREATE OR REPLACE VIEW} — vira DROP + CREATE numa unica string (ver {@link #createTriggerStatement} pro mesmo padrao multi-comando). */
    @Override
    public String replaceViewStatement(String name, String selectSql) {
        return "DROP VIEW IF EXISTS " + quoteIdentifier(name) + ";\n" + createViewStatement(name, selectSql);
    }

    @Override
    public String dropViewStatement(String name) {
        return "DROP VIEW " + quoteIdentifier(name);
    }

    /** Corpo inline entre {@code BEGIN...END}, igual ao MySQL (bem mais simples que o Postgres, que exige uma FUNCTION separada). */
    @Override
    public String createTriggerStatement(String triggerName, String timing, String event, String tableName,
            String body) {
        return "CREATE TRIGGER " + quoteIdentifier(triggerName) + " " + timing.toUpperCase(Locale.ROOT) + " "
                + event.toUpperCase(Locale.ROOT) + " ON " + quoteIdentifier(tableName) + "\nBEGIN\n" + body
                + "\nEND";
    }

    @Override
    public String dropTriggerStatement(String triggerName) {
        return "DROP TRIGGER " + quoteIdentifier(triggerName);
    }

    @Override
    public String createProcedureStatement(String name, List<String> parameters, String body) {
        throw new UnsupportedOperationException("SQLite nao suporta procedures (nao existe CREATE PROCEDURE).");
    }

    @Override
    public String createFunctionStatement(String name, List<String> parameters, String returnType,
            boolean deterministic, String body) {
        throw new UnsupportedOperationException(
                "SQLite nao suporta functions via SQL (so via extensao na linguagem hospedeira).");
    }

    @Override
    public String dropProcedureStatement(String name) {
        throw new UnsupportedOperationException("SQLite nao suporta procedures (nao existe DROP PROCEDURE).");
    }

    @Override
    public String dropFunctionStatement(String name) {
        throw new UnsupportedOperationException("SQLite nao suporta functions via SQL.");
    }

    private String indexStatement(String tableName, IndexInfo idx) {
        List<String> quotedCols = idx.columns().stream().map(this::quoteIdentifier).toList();
        String name = (idx.name() == null || idx.name().isBlank())
                ? autoName(idx.unique() ? "uq" : "idx", tableName, String.join("_", idx.columns()))
                : idx.name();
        String kind = idx.unique() ? "CREATE UNIQUE INDEX" : "CREATE INDEX";
        return kind + " " + quoteIdentifier(name) + " ON " + quoteIdentifier(tableName)
                + " (" + String.join(", ", quotedCols) + ")";
    }

    private static String autoName(String... parts) {
        return String.join("_", parts);
    }

    private String foreignKeyDefinition(String tableName, ForeignKeyInfo fk) {
        List<String> localCols = fk.columns().stream().map(this::quoteIdentifier).toList();
        List<String> refCols = fk.referencedColumns().stream().map(this::quoteIdentifier).toList();
        StringBuilder def = new StringBuilder();
        def.append("FOREIGN KEY (").append(String.join(", ", localCols)).append(")")
                .append(" REFERENCES ").append(quoteIdentifier(fk.referencedTable()))
                .append(" (").append(String.join(", ", refCols)).append(")");
        if (fk.onUpdate() != null && !fk.onUpdate().isBlank() && !"NO ACTION".equalsIgnoreCase(fk.onUpdate())) {
            def.append(" ON UPDATE ").append(fk.onUpdate().toUpperCase(Locale.ROOT));
        }
        if (fk.onDelete() != null && !fk.onDelete().isBlank() && !"NO ACTION".equalsIgnoreCase(fk.onDelete())) {
            def.append(" ON DELETE ").append(fk.onDelete().toUpperCase(Locale.ROOT));
        }
        return def.toString();
    }

    private String columnDefinition(NewColumnSpec c, boolean asIntegerPrimaryKeyAutoincrement) {
        StringBuilder def = new StringBuilder();
        def.append(quoteIdentifier(c.name())).append(" ");
        if (asIntegerPrimaryKeyAutoincrement) {
            def.append("INTEGER PRIMARY KEY AUTOINCREMENT");
            return def.toString();
        }
        def.append(c.sqlType().toUpperCase(Locale.ROOT));
        if (c.length() != null && !c.length().isBlank()) {
            def.append("(").append(c.length().trim()).append(")");
        }
        def.append(c.nullable() ? " NULL" : " NOT NULL");
        if (c.defaultValue() != null && !c.defaultValue().isBlank()) {
            def.append(" DEFAULT ").append(defaultLiteral(c.defaultValue().trim()));
        }
        return def.toString();
    }

    private static String defaultLiteral(String value) {
        String upper = value.toUpperCase(Locale.ROOT);
        if (upper.equals("NULL") || upper.equals("TRUE") || upper.equals("FALSE")
                || upper.startsWith("CURRENT_TIMESTAMP") || value.matches("-?\\d+(\\.\\d+)?")) {
            return value;
        }
        return quoteLiteral(value);
    }

    private static String quoteLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    @Override
    public List<String> keywords() {
        return List.of(
                "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "INTO", "VALUES",
                "SET", "JOIN", "INNER", "LEFT", "OUTER", "ON", "GROUP", "BY",
                "ORDER", "HAVING", "LIMIT", "OFFSET", "DISTINCT", "AS", "AND", "OR", "NOT",
                "NULL", "IS", "IN", "LIKE", "GLOB", "BETWEEN", "EXISTS", "CREATE", "ALTER", "DROP",
                "TABLE", "INDEX", "VIEW", "PRIMARY", "KEY", "FOREIGN", "REFERENCES",
                "COUNT", "SUM", "AVG", "MIN", "MAX", "CASE", "WHEN", "THEN", "ELSE", "END",
                "AUTOINCREMENT", "PRAGMA", "WITHOUT", "ROWID", "ATTACH", "DETACH");
    }
}
