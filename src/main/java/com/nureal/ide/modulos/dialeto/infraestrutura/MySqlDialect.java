package com.nureal.ide.modulos.dialeto.infraestrutura;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
 * Implementacao para MySQL. Le metadados via information_schema em UMA
 * consulta,
 * que e a base para o autocomplete rapido.
 * <p>
 * Implementa as 3 capacidades OPCIONAIS ({@link SecurityCapability}/
 * {@link AdminCapability}/{@link ReplicationCapability}) alem das 4
 * obrigatorias — MySQL suporta tudo isso hoje, entao {@link #security()}/
 * {@link #admin()}/{@link #replication()} sempre devolvem
 * {@code Optional.of(this)}.
 */
public class MySqlDialect implements DatabaseDialect, SecurityCapability, AdminCapability, ReplicationCapability {

    @Override
    public Optional<SecurityCapability> security() {
        return Optional.of(this);
    }

    @Override
    public Optional<AdminCapability> admin() {
        return Optional.of(this);
    }

    @Override
    public Optional<ReplicationCapability> replication() {
        return Optional.of(this);
    }

    @Override
    public String id() {
        return "mysql";
    }

    @Override
    public String driverClassName() {
        return "com.mysql.cj.jdbc.Driver";
    }

    @Override
    public String buildJdbcUrl(ConnectionProfile profile) {

        StringBuilder url = new StringBuilder();

        url.append("jdbc:mysql://")
                .append(profile.host())
                .append(":")
                .append(profile.port())
                .append("/")
                .append(profile.schema());

        // url.append("?connectionTimeZone=UTC");
        // url.append("&forceConnectionTimeZoneToSession=true");
        url.append("?preserveInstants=true");
        // Sem isto, o driver MySQL Connector/J lanca "Zero date value prohibited"
        // (SQLException) ao LER qualquer linha cuja coluna DATE/DATETIME/TIMESTAMP
        // contenha '0000-00-00' ou '0000-00-00 00:00:00' — mesmo em um SELECT
        // simples, sem nenhum filtro por data. CONVERT_TO_NULL faz o driver
        // devolver NULL para esses valores em vez de abortar a query inteira.
        url.append("&zeroDateTimeBehavior=CONVERT_TO_NULL");

        return url.toString();

    }

    @Override
    public List<String> sessionInitStatements() {
        // Nenhuma configuracao de sessao necessaria por enquanto. Exemplo de
        // uso futuro, caso surja necessidade:
        // return List.of("SET NAMES utf8mb4", "SET SESSION sql_mode='STRICT_TRANS_TABLES'");
        return List.of();
    }

    private static final String SCHEMAS_SQL = "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA "
            + "WHERE SCHEMA_NAME NOT IN "
            + "('information_schema','performance_schema','mysql','sys') "
            + "ORDER BY SCHEMA_NAME";

    private static final String COLUMNS_SQL = "SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, ORDINAL_POSITION "
            + "FROM information_schema.COLUMNS "
            + "WHERE TABLE_SCHEMA = ? "
            + "ORDER BY TABLE_NAME, ORDINAL_POSITION";

    private static final String TABLES_SQL = "SELECT TABLE_NAME, TABLE_TYPE "
            + "FROM information_schema.TABLES "
            + "WHERE TABLE_SCHEMA = ? "
            + "ORDER BY TABLE_NAME";

    private static final String ROUTINES_SQL = "SELECT ROUTINE_NAME, ROUTINE_TYPE "
            + "FROM information_schema.ROUTINES "
            + "WHERE ROUTINE_SCHEMA = ? "
            + "ORDER BY ROUTINE_NAME";

    private static final String TRIGGERS_SQL = "SELECT TRIGGER_NAME "
            + "FROM information_schema.TRIGGERS "
            + "WHERE TRIGGER_SCHEMA = ? "
            + "ORDER BY TRIGGER_NAME";

    private static final String EVENT_NAMES_SQL = "SELECT EVENT_NAME "
            + "FROM information_schema.EVENTS "
            + "WHERE EVENT_SCHEMA = ? "
            + "ORDER BY EVENT_NAME";

    /** Lista os esquemas (databases) acessiveis ao usuario conectado — ver {@link com.nureal.ide.modulos.dialeto.dominio.contratos.MetadataCapability}. */
    @Override
    public List<String> listSchemas(Connection conn) throws SQLException {
        List<String> schemas = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SCHEMAS_SQL);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                schemas.add(rs.getString(1));
            }
        }
        return schemas;
    }

    /**
     * Carrega tabelas, views, procedures, functions, triggers e eventos
     * agendados de um schema numa UNICA consulta de colunas (a chave da
     * performance: informacao_schema.COLUMNS de TODAS as tabelas de uma vez,
     * agrupada em memoria) mais 4 consultas leves — movido de
     * {@code MetadataService.loadSchema} (agora so um delegador, ver
     * {@link com.nureal.ide.modulos.dialeto.dominio.contratos.MetadataCapability}).
     */
    @Override
    public SchemaInfo loadSchema(Connection conn, String schema) throws SQLException {
        Map<String, List<ColumnInfo>> columnsByObject = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(COLUMNS_SQL)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String object = rs.getString("TABLE_NAME");
                    ColumnInfo col = new ColumnInfo(
                            rs.getString("COLUMN_NAME"),
                            rs.getString("COLUMN_TYPE"),
                            rs.getInt("ORDINAL_POSITION"));
                    columnsByObject.computeIfAbsent(object, k -> new ArrayList<>()).add(col);
                }
            }
        }

        List<TableInfo> tables = new ArrayList<>();
        List<TableInfo> views = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(TABLES_SQL)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    String type = rs.getString("TABLE_TYPE");
                    List<ColumnInfo> cols = columnsByObject.getOrDefault(name, List.of());
                    TableInfo info = new TableInfo(name, cols);
                    if (type != null && type.toUpperCase(Locale.ROOT).contains("VIEW")) {
                        views.add(info);
                    } else {
                        tables.add(info);
                    }
                }
            }
        }

        List<String> procedures = new ArrayList<>();
        List<String> functions = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(ROUTINES_SQL)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("ROUTINE_NAME");
                    String type = rs.getString("ROUTINE_TYPE");
                    if (type != null && type.toUpperCase(Locale.ROOT).contains("FUNCTION")) {
                        functions.add(name);
                    } else {
                        procedures.add(name);
                    }
                }
            }
        }

        List<String> triggers = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(TRIGGERS_SQL)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    triggers.add(rs.getString("TRIGGER_NAME"));
                }
            }
        }

        List<String> events = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(EVENT_NAMES_SQL)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(rs.getString("EVENT_NAME"));
                }
            }
        }

        return new SchemaInfo(schema, tables, views, procedures, functions, triggers, events);
    }

    @Override
    public String definitionQuery(String objectKind, String objectName) {
        return "SHOW CREATE " + objectKind + " " + quoteIdentifier(objectName);
    }

    /** Envolve um identificador em crases, dobrando crases internas. */
    @Override
    public String quoteIdentifier(String ident) {
        return "`" + ident.replace("`", "``") + "`";
    }

    /** {@code ORDER BY RAND()}: aceitavel para o tamanho de amostra do Populador (dezenas/centenas), nao usado em tabelas gigantes de producao sem limite. */
    @Override
    public String randomSampleQuery(String table, String column, int limit) {
        return "SELECT " + quoteIdentifier(column) + " FROM " + quoteIdentifier(table)
                + " ORDER BY RAND() LIMIT " + limit;
    }

    @Override
    public String createSchemaStatement(String name) {
        return "CREATE DATABASE " + quoteIdentifier(name);
    }

    @Override
    public String dropSchemaStatement(String name) {
        return "DROP DATABASE " + quoteIdentifier(name);
    }

    /**
     * Monta o CREATE TABLE a partir da especificacao coletada pelo
     * com.nureal.ide.ui.DdlAssistantDialog. Uma unica PRIMARY KEY (composta, se mais de uma
     * coluna marcar {@code primaryKey}) e adicionada ao final, se houver
     * pelo menos uma. AUTO_INCREMENT so e emitido se a propria coluna pedir
     * (nao valida aqui se faz sentido — isso e responsabilidade do MySQL, que
     * rejeita AUTO_INCREMENT fora da chave/indice na hora de executar).
     */
    @Override
    public String createTableStatement(NewTableSpec spec) {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE ").append(quoteIdentifier(spec.name())).append(" (\n");

        List<String> pkColumns = new java.util.ArrayList<>();
        List<NewColumnSpec> cols = spec.columns();
        List<ForeignKeyInfo> fks = spec.foreignKeys() == null ? List.of() : spec.foreignKeys();
        List<IndexInfo> idxs = spec.indexes() == null ? List.of() : spec.indexes();
        int extraClauses = (pkColumnNames(cols).isEmpty() ? 0 : 1) + fks.size() + idxs.size();

        for (int i = 0; i < cols.size(); i++) {
            NewColumnSpec c = cols.get(i);
            sql.append("  ").append(columnDefinition(c));
            if (i < cols.size() - 1 || extraClauses > 0) {
                sql.append(",\n");
            } else {
                sql.append("\n");
            }
            if (c.primaryKey()) {
                pkColumns.add(quoteIdentifier(c.name()));
            }
        }
        int remaining = extraClauses;
        if (!pkColumns.isEmpty()) {
            sql.append("  PRIMARY KEY (").append(String.join(", ", pkColumns)).append(")");
            remaining--;
            sql.append(remaining > 0 ? ",\n" : "\n");
        }
        for (IndexInfo idx : idxs) {
            sql.append("  ").append(indexDefinition(idx));
            remaining--;
            sql.append(remaining > 0 ? ",\n" : "\n");
        }
        for (ForeignKeyInfo fk : fks) {
            sql.append("  ").append(foreignKeyDefinition(spec.name(), fk));
            remaining--;
            sql.append(remaining > 0 ? ",\n" : "\n");
        }
        sql.append(")");
        if (spec.comment() != null && !spec.comment().isBlank()) {
            sql.append(" COMMENT=").append(quoteLiteral(spec.comment()));
        }
        return sql.toString();
    }

    /**
     * Monta um ALTER TABLE unico e aditivo (todas as colunas/FKs/indices
     * novos numa so instrucao, atomica) — ver javadoc da interface
     * {@link DatabaseDialect#alterTableAddStatements}.
     */
    @Override
    public List<String> alterTableAddStatements(String tableName, List<NewColumnSpec> newColumns,
            List<ForeignKeyInfo> newForeignKeys, List<IndexInfo> newIndexes) {
        List<NewColumnSpec> cols = newColumns == null ? List.of() : newColumns;
        List<ForeignKeyInfo> fks = newForeignKeys == null ? List.of() : newForeignKeys;
        List<IndexInfo> idxs = newIndexes == null ? List.of() : newIndexes;
        if (cols.isEmpty() && fks.isEmpty() && idxs.isEmpty()) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        for (NewColumnSpec c : cols) {
            parts.add("ADD COLUMN " + columnDefinition(c));
        }
        for (IndexInfo idx : idxs) {
            parts.add("ADD " + indexDefinition(idx));
        }
        for (ForeignKeyInfo fk : fks) {
            parts.add("ADD " + foreignKeyDefinition(tableName, fk));
        }
        String sql = "ALTER TABLE " + quoteIdentifier(tableName) + "\n  "
                + String.join(",\n  ", parts);
        return List.of(sql);
    }

    /**
     * Monta um ALTER TABLE unico com as remocoes (FK, indice, coluna, nesta
     * ordem) e modificacoes de coluna pedidas — ver javadoc da interface
     * {@link DatabaseDialect#alterTableModifyStatements}. MODIFY COLUMN
     * reaproveita {@link #columnDefinition(NewColumnSpec)}, a MESMA
     * montagem de definicao de coluna usada por CREATE TABLE e ADD COLUMN,
     * para nao ter uma segunda logica de formatacao de tipo/nulo/default/
     * comentario divergente da que ja existe.
     */
    @Override
    public List<String> alterTableModifyStatements(String tableName, List<NewColumnSpec> modifiedColumns,
            List<String> droppedColumns, List<String> droppedForeignKeys, List<String> droppedIndexes) {
        List<NewColumnSpec> mods = modifiedColumns == null ? List.of() : modifiedColumns;
        List<String> dropCols = droppedColumns == null ? List.of() : droppedColumns;
        List<String> dropFks = droppedForeignKeys == null ? List.of() : droppedForeignKeys;
        List<String> dropIdxs = droppedIndexes == null ? List.of() : droppedIndexes;
        if (mods.isEmpty() && dropCols.isEmpty() && dropFks.isEmpty() && dropIdxs.isEmpty()) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        // Ordem importa: solta a FK/indice ANTES de remover a coluna que
        // participava dela, senao o MySQL recusa a remocao da coluna com
        // "needed in a foreign key constraint" / erro equivalente de indice.
        for (String fk : dropFks) {
            parts.add("DROP FOREIGN KEY " + quoteIdentifier(fk));
        }
        for (String idx : dropIdxs) {
            parts.add("DROP INDEX " + quoteIdentifier(idx));
        }
        for (String col : dropCols) {
            parts.add("DROP COLUMN " + quoteIdentifier(col));
        }
        for (NewColumnSpec c : mods) {
            parts.add("MODIFY COLUMN " + columnDefinition(c));
        }
        String sql = "ALTER TABLE " + quoteIdentifier(tableName) + "\n  "
                + String.join(",\n  ", parts);
        return List.of(sql);
    }

    // ====================================================================
    // Views — ver javadoc na interface DatabaseDialect.
    // ====================================================================

    @Override
    public String createViewStatement(String name, String selectSql) {
        return "CREATE VIEW " + quoteIdentifier(name) + " AS\n" + selectSql;
    }

    @Override
    public String replaceViewStatement(String name, String selectSql) {
        return "CREATE OR REPLACE VIEW " + quoteIdentifier(name) + " AS\n" + selectSql;
    }

    @Override
    public String dropViewStatement(String name) {
        return "DROP VIEW " + quoteIdentifier(name);
    }

    // ====================================================================
    // Triggers — ver javadoc na interface DatabaseDialect.
    // ====================================================================

    @Override
    public String createTriggerStatement(String triggerName, String timing, String event, String tableName,
            String body) {
        return "CREATE TRIGGER " + quoteIdentifier(triggerName) + " " + timing.toUpperCase(Locale.ROOT) + " "
                + event.toUpperCase(Locale.ROOT) + " ON " + quoteIdentifier(tableName) + "\nFOR EACH ROW\nBEGIN\n"
                + body + "\nEND";
    }

    @Override
    public String dropTriggerStatement(String triggerName) {
        return "DROP TRIGGER " + quoteIdentifier(triggerName);
    }

    // ====================================================================
    // Procedures e Functions — ver javadoc na interface DatabaseDialect.
    // ====================================================================

    @Override
    public String createProcedureStatement(String name, List<String> parameters, String body) {
        String params = parameters == null ? "" : String.join(", ", parameters);
        return "CREATE PROCEDURE " + quoteIdentifier(name) + " (" + params + ")\nBEGIN\n" + body + "\nEND";
    }

    @Override
    public String createFunctionStatement(String name, List<String> parameters, String returnType,
            boolean deterministic, String body) {
        String params = parameters == null ? "" : String.join(", ", parameters);
        return "CREATE FUNCTION " + quoteIdentifier(name) + " (" + params + ")\nRETURNS "
                + returnType.toUpperCase(Locale.ROOT) + "\n" + (deterministic ? "DETERMINISTIC" : "NOT DETERMINISTIC")
                + "\nBEGIN\n" + body + "\nEND";
    }

    @Override
    public String dropProcedureStatement(String name) {
        return "DROP PROCEDURE " + quoteIdentifier(name);
    }

    @Override
    public String dropFunctionStatement(String name) {
        return "DROP FUNCTION " + quoteIdentifier(name);
    }

    /** Nome padrao (limitado a 64 chars, limite de identificador do MySQL) quando o usuario deixa em branco. */
    private static String autoName(String... parts) {
        String base = String.join("_", parts);
        return base.length() > 64 ? base.substring(0, 64) : base;
    }

    private String indexDefinition(IndexInfo idx) {
        List<String> quotedCols = idx.columns().stream().map(this::quoteIdentifier).toList();
        String name = (idx.name() == null || idx.name().isBlank())
                ? autoName(idx.unique() ? "uq" : "idx", String.join("_", idx.columns()))
                : idx.name();
        String kind = idx.unique() ? "UNIQUE INDEX" : "INDEX";
        return kind + " " + quoteIdentifier(name) + " (" + String.join(", ", quotedCols) + ")";
    }

    private String foreignKeyDefinition(String tableName, ForeignKeyInfo fk) {
        List<String> localCols = fk.columns().stream().map(this::quoteIdentifier).toList();
        List<String> refCols = fk.referencedColumns().stream().map(this::quoteIdentifier).toList();
        String name = (fk.name() == null || fk.name().isBlank())
                ? autoName("fk", tableName, String.join("_", fk.columns()))
                : fk.name();
        StringBuilder def = new StringBuilder();
        def.append("CONSTRAINT ").append(quoteIdentifier(name))
                .append(" FOREIGN KEY (").append(String.join(", ", localCols)).append(")")
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

    private static List<String> pkColumnNames(List<NewColumnSpec> cols) {
        List<String> names = new java.util.ArrayList<>();
        for (NewColumnSpec c : cols) {
            if (c.primaryKey()) {
                names.add(c.name());
            }
        }
        return names;
    }

    private String columnDefinition(NewColumnSpec c) {
        StringBuilder def = new StringBuilder();
        def.append(quoteIdentifier(c.name())).append(" ").append(c.sqlType().toUpperCase(Locale.ROOT));
        if (c.length() != null && !c.length().isBlank()) {
            def.append("(").append(c.length().trim()).append(")");
        }
        def.append(c.nullable() ? " NULL" : " NOT NULL");
        if (c.autoIncrement()) {
            def.append(" AUTO_INCREMENT");
        }
        if (c.defaultValue() != null && !c.defaultValue().isBlank()) {
            def.append(" DEFAULT ").append(defaultLiteral(c.defaultValue().trim()));
        }
        if (c.comment() != null && !c.comment().isBlank()) {
            def.append(" COMMENT ").append(quoteLiteral(c.comment()));
        }
        return def.toString();
    }

    /**
     * Decide se um DEFAULT deve ir literal (palavras-chave/numeros, ex.:
     * {@code CURRENT_TIMESTAMP}, {@code NULL}, {@code 0}) ou entre aspas
     * simples (qualquer outra coisa, ex.: um texto). Escapa aspas simples
     * internas para evitar quebrar o DDL.
     */
    private static String defaultLiteral(String value) {
        String upper = value.toUpperCase(Locale.ROOT);
        if (upper.equals("NULL") || upper.equals("TRUE") || upper.equals("FALSE")
                || upper.startsWith("CURRENT_TIMESTAMP") || value.matches("-?\\d+(\\.\\d+)?")) {
            return value;
        }
        return quoteLiteral(value);
    }

    /** Envolve um valor literal em aspas simples, escapando aspas internas. */
    private static String quoteLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static final String COLUMN_DETAILS_SQL = "SELECT ORDINAL_POSITION, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, "
            + "COLUMN_KEY, COLUMN_DEFAULT, EXTRA, COLUMN_COMMENT "
            + "FROM information_schema.COLUMNS "
            + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? "
            + "ORDER BY ORDINAL_POSITION";

    private static final String INDEXES_SQL = "SELECT INDEX_NAME, NON_UNIQUE, INDEX_TYPE, SEQ_IN_INDEX, COLUMN_NAME "
            + "FROM information_schema.STATISTICS "
            + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? "
            + "ORDER BY INDEX_NAME, SEQ_IN_INDEX";

    private static final String FOREIGN_KEYS_SQL = "SELECT k.CONSTRAINT_NAME, k.COLUMN_NAME, k.REFERENCED_TABLE_NAME, "
            + "k.REFERENCED_COLUMN_NAME, r.UPDATE_RULE, r.DELETE_RULE "
            + "FROM information_schema.KEY_COLUMN_USAGE k "
            + "JOIN information_schema.REFERENTIAL_CONSTRAINTS r "
            + "  ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA "
            + "  AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME "
            + "WHERE k.TABLE_SCHEMA = ? AND k.TABLE_NAME = ? "
            + "  AND k.REFERENCED_TABLE_NAME IS NOT NULL "
            + "ORDER BY k.CONSTRAINT_NAME, k.ORDINAL_POSITION";

    private static final String FOREIGN_KEYS_FOR_SCHEMA_SQL = "SELECT k.CONSTRAINT_NAME, k.TABLE_NAME, k.COLUMN_NAME, k.REFERENCED_TABLE_NAME, "
            + "k.REFERENCED_COLUMN_NAME, r.UPDATE_RULE, r.DELETE_RULE "
            + "FROM information_schema.KEY_COLUMN_USAGE k "
            + "JOIN information_schema.REFERENTIAL_CONSTRAINTS r "
            + "  ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA "
            + "  AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME "
            + "WHERE k.TABLE_SCHEMA = ? "
            + "  AND k.REFERENCED_TABLE_NAME IS NOT NULL "
            + "ORDER BY k.TABLE_NAME, k.CONSTRAINT_NAME, k.ORDINAL_POSITION";

    // No MySQL/InnoDB o nome da constraint de chave primaria e SEMPRE
    // literalmente "PRIMARY" — nao ha necessidade de olhar
    // TABLE_CONSTRAINTS.CONSTRAINT_TYPE para confirmar.
    private static final String PRIMARY_KEYS_FOR_SCHEMA_SQL = "SELECT TABLE_NAME, COLUMN_NAME "
            + "FROM information_schema.KEY_COLUMN_USAGE "
            + "WHERE TABLE_SCHEMA = ? AND CONSTRAINT_NAME = 'PRIMARY' "
            + "ORDER BY TABLE_NAME, ORDINAL_POSITION";

    /**
     * Detalhe completo de UMA tabela/view — movido de
     * {@code MetadataService.loadTableDetails} (agora so um delegador, ver
     * {@link com.nureal.ide.modulos.dialeto.dominio.contratos.MetadataCapability}).
     */
    @Override
    public TableDetails loadTableDetails(Connection conn, String schema, String table) throws SQLException {
        return new TableDetails(
                loadColumnDetails(conn, schema, table),
                loadIndexes(conn, schema, table),
                loadForeignKeys(conn, schema, table));
    }

    private List<ColumnDetail> loadColumnDetails(Connection conn, String schema, String table) throws SQLException {
        List<ColumnDetail> columns = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(COLUMN_DETAILS_SQL)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.add(new ColumnDetail(
                            rs.getInt("ORDINAL_POSITION"),
                            rs.getString("COLUMN_NAME"),
                            rs.getString("COLUMN_TYPE"),
                            "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")),
                            rs.getString("COLUMN_KEY"),
                            rs.getString("COLUMN_DEFAULT"),
                            rs.getString("EXTRA"),
                            rs.getString("COLUMN_COMMENT")));
                }
            }
        }
        return columns;
    }

    private List<IndexInfo> loadIndexes(Connection conn, String schema, String table) throws SQLException {
        Map<String, List<String>> cols = new LinkedHashMap<>();
        Map<String, Boolean> unique = new HashMap<>();
        Map<String, String> type = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(INDEXES_SQL)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("INDEX_NAME");
                    cols.computeIfAbsent(name, k -> new ArrayList<>())
                            .add(rs.getString("COLUMN_NAME"));
                    unique.put(name, rs.getInt("NON_UNIQUE") == 0);
                    type.put(name, rs.getString("INDEX_TYPE"));
                }
            }
        }
        List<IndexInfo> indexes = new ArrayList<>();
        cols.forEach((name, columns) -> indexes.add(
                new IndexInfo(name, unique.get(name), type.get(name), columns)));
        return indexes;
    }

    private List<ForeignKeyInfo> loadForeignKeys(Connection conn, String schema, String table) throws SQLException {
        Map<String, List<String>> cols = new LinkedHashMap<>();
        Map<String, List<String>> refCols = new LinkedHashMap<>();
        Map<String, String> refTable = new HashMap<>();
        Map<String, String> onUpdate = new HashMap<>();
        Map<String, String> onDelete = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(FOREIGN_KEYS_SQL)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("CONSTRAINT_NAME");
                    cols.computeIfAbsent(name, k -> new ArrayList<>())
                            .add(rs.getString("COLUMN_NAME"));
                    refCols.computeIfAbsent(name, k -> new ArrayList<>())
                            .add(rs.getString("REFERENCED_COLUMN_NAME"));
                    refTable.put(name, rs.getString("REFERENCED_TABLE_NAME"));
                    onUpdate.put(name, rs.getString("UPDATE_RULE"));
                    onDelete.put(name, rs.getString("DELETE_RULE"));
                }
            }
        }
        List<ForeignKeyInfo> fks = new ArrayList<>();
        cols.forEach((name, columns) -> fks.add(new ForeignKeyInfo(
                name, columns, refTable.get(name), refCols.get(name),
                onUpdate.get(name), onDelete.get(name))));
        return fks;
    }

    /**
     * Todas as chaves estrangeiras do schema INTEIRO, numa unica consulta —
     * movido de {@code MetadataService.loadSchemaForeignKeys} (usada pelo
     * Diagrama ER, ver {@link com.nureal.ide.modulos.dialeto.dominio.contratos.MetadataCapability}).
     */
    @Override
    public List<SchemaForeignKey> loadSchemaForeignKeys(Connection conn, String schema) throws SQLException {
        Map<String, List<String>> cols = new LinkedHashMap<>();
        Map<String, List<String>> refCols = new LinkedHashMap<>();
        Map<String, String> fromTable = new HashMap<>();
        Map<String, String> refTable = new HashMap<>();
        Map<String, String> onUpdate = new HashMap<>();
        Map<String, String> onDelete = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(FOREIGN_KEYS_FOR_SCHEMA_SQL)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("CONSTRAINT_NAME");
                    cols.computeIfAbsent(name, k -> new ArrayList<>())
                            .add(rs.getString("COLUMN_NAME"));
                    refCols.computeIfAbsent(name, k -> new ArrayList<>())
                            .add(rs.getString("REFERENCED_COLUMN_NAME"));
                    fromTable.put(name, rs.getString("TABLE_NAME"));
                    refTable.put(name, rs.getString("REFERENCED_TABLE_NAME"));
                    onUpdate.put(name, rs.getString("UPDATE_RULE"));
                    onDelete.put(name, rs.getString("DELETE_RULE"));
                }
            }
        }
        List<SchemaForeignKey> fks = new ArrayList<>();
        cols.forEach((name, columns) -> fks.add(new SchemaForeignKey(
                name, fromTable.get(name), columns, refTable.get(name), refCols.get(name),
                onUpdate.get(name), onDelete.get(name))));
        return fks;
    }

    /**
     * Colunas de chave primaria de TODO o schema, agrupadas por tabela —
     * movido de {@code MetadataService.loadSchemaPrimaryKeys} (usada pelo
     * Diagrama ER, ver {@link com.nureal.ide.modulos.dialeto.dominio.contratos.MetadataCapability}).
     */
    @Override
    public Map<String, Set<String>> loadSchemaPrimaryKeys(Connection conn, String schema) throws SQLException {
        Map<String, Set<String>> byTable = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(PRIMARY_KEYS_FOR_SCHEMA_SQL)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    byTable.computeIfAbsent(rs.getString("TABLE_NAME"), k -> new LinkedHashSet<>())
                            .add(rs.getString("COLUMN_NAME"));
                }
            }
        }
        return byTable;
    }

    @Override
    public List<String> keywords() {
        return List.of(
                "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "INTO", "VALUES",
                "SET", "JOIN", "INNER", "LEFT", "RIGHT", "OUTER", "ON", "GROUP", "BY",
                "ORDER", "HAVING", "LIMIT", "OFFSET", "DISTINCT", "AS", "AND", "OR", "NOT",
                "NULL", "IS", "IN", "LIKE", "BETWEEN", "EXISTS", "CREATE", "ALTER", "DROP",
                "TABLE", "INDEX", "VIEW", "PRIMARY", "KEY", "FOREIGN", "REFERENCES",
                "COUNT", "SUM", "AVG", "MIN", "MAX", "CASE", "WHEN", "THEN", "ELSE", "END");
    }

    // ====================================================================
    // Usuarios e privilegios — ver javadoc de cada metodo na interface
    // DatabaseDialect. Sintaxe MySQL 8 (CREATE USER/ALTER USER modernos);
    // roles exigem 8.0+, o proprio servidor recusa com mensagem clara em
    // versoes anteriores.
    // ====================================================================

    @Override
    public String listUsersQuery() {
        return "SELECT User, Host, account_locked, password_expired FROM mysql.user ORDER BY User, Host";
    }

    @Override
    public String showGrantsQuery(String user, String host) {
        return "SHOW GRANTS FOR " + quoteUserHost(user, host);
    }

    @Override
    public String createUserStatement(String user, String host, String password, boolean expireNow) {
        StringBuilder sql = new StringBuilder("CREATE USER ").append(quoteUserHost(user, host))
                .append(" IDENTIFIED BY ").append(quoteLiteral(password));
        if (expireNow) {
            sql.append(" PASSWORD EXPIRE");
        }
        return sql.toString();
    }

    @Override
    public String dropUserStatement(String user, String host) {
        return "DROP USER " + quoteUserHost(user, host);
    }

    @Override
    public String setPasswordStatement(String user, String host, String newPassword) {
        return "ALTER USER " + quoteUserHost(user, host) + " IDENTIFIED BY " + quoteLiteral(newPassword);
    }

    @Override
    public String lockUserStatement(String user, String host, boolean lock) {
        return "ALTER USER " + quoteUserHost(user, host) + (lock ? " ACCOUNT LOCK" : " ACCOUNT UNLOCK");
    }

    @Override
    public String expirePasswordStatement(String user, String host) {
        return "ALTER USER " + quoteUserHost(user, host) + " PASSWORD EXPIRE";
    }

    @Override
    public String privilegeTarget(String schema, String table, List<String> columns) {
        if (schema == null || schema.isBlank()) {
            return "*.*";
        }
        if (table == null || table.isBlank()) {
            return quoteIdentifier(schema) + ".*";
        }
        String base = quoteIdentifier(schema) + "." + quoteIdentifier(table);
        if (columns == null || columns.isEmpty()) {
            return base;
        }
        List<String> quotedCols = columns.stream().map(this::quoteIdentifier).toList();
        return base + " (" + String.join(", ", quotedCols) + ")";
    }

    @Override
    public String grantStatement(List<String> privileges, String target, String user, String host,
            boolean withGrantOption) {
        String sql = "GRANT " + String.join(", ", privileges) + " ON " + target + " TO " + quoteUserHost(user, host);
        return withGrantOption ? sql + " WITH GRANT OPTION" : sql;
    }

    @Override
    public String revokeStatement(List<String> privileges, String target, String user, String host) {
        return "REVOKE " + String.join(", ", privileges) + " ON " + target + " FROM " + quoteUserHost(user, host);
    }

    /**
     * Lista curada dos privilegios GLOBAIS mais relevantes para
     * administracao do dia a dia — o MySQL aceita mais alguns bem raros
     * (ex.: {@code CREATE TABLESPACE}, {@code ENCRYPTION_KEY_ADMIN}), de
     * proposito fora daqui para nao poluir a UI com opcoes que praticamente
     * ninguem usa.
     */
    @Override
    public List<String> globalPrivilegeNames() {
        return List.of(
                "CREATE USER", "RELOAD", "PROCESS", "SUPER", "SHUTDOWN", "FILE",
                "REPLICATION CLIENT", "REPLICATION SLAVE", "SHOW DATABASES",
                "LOCK TABLES", "CREATE TABLESPACE", "ALL PRIVILEGES");
    }

    @Override
    public List<String> objectPrivilegeNames() {
        return List.of(
                "SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP", "ALTER",
                "INDEX", "REFERENCES", "CREATE VIEW", "SHOW VIEW", "CREATE ROUTINE",
                "ALTER ROUTINE", "EXECUTE", "TRIGGER", "EVENT", "CREATE TEMPORARY TABLES",
                "GRANT OPTION", "ALL PRIVILEGES");
    }

    /** MySQL so aceita GRANT por coluna para estes 4 (ver documentacao de GRANT do MySQL). */
    @Override
    public List<String> columnPrivilegeNames() {
        return List.of("SELECT", "INSERT", "UPDATE", "REFERENCES");
    }

    @Override
    public String createRoleStatement(String role) {
        return "CREATE ROLE " + quoteIdentifier(role);
    }

    @Override
    public String dropRoleStatement(String role) {
        return "DROP ROLE " + quoteIdentifier(role);
    }

    @Override
    public String grantRoleStatement(String role, String user, String host) {
        return "GRANT " + quoteIdentifier(role) + " TO " + quoteUserHost(user, host);
    }

    @Override
    public String revokeRoleStatement(String role, String user, String host) {
        return "REVOKE " + quoteIdentifier(role) + " FROM " + quoteUserHost(user, host);
    }

    @Override
    public String setDefaultRoleStatement(String role, String user, String host) {
        return "SET DEFAULT ROLE " + quoteIdentifier(role) + " TO " + quoteUserHost(user, host);
    }

    @Override
    public String knownRolesQuery() {
        return "SELECT DISTINCT FROM_USER FROM mysql.role_edges ORDER BY FROM_USER";
    }

    // ====================================================================
    // Monitoramento de sessoes e manutencao — ver javadoc na interface.
    // ====================================================================

    @Override
    public String processListQuery() {
        return "SELECT ID, USER, HOST, DB, COMMAND, TIME, STATE, INFO "
                + "FROM information_schema.PROCESSLIST ORDER BY TIME DESC";
    }

    @Override
    public String killStatement(long processId) {
        return "KILL " + processId;
    }

    @Override
    public String optimizeTableStatement(String schema, String table) {
        return "OPTIMIZE TABLE " + quoteIdentifier(schema) + "." + quoteIdentifier(table);
    }

    @Override
    public String analyzeTableStatement(String schema, String table) {
        return "ANALYZE TABLE " + quoteIdentifier(schema) + "." + quoteIdentifier(table);
    }

    @Override
    public String checkTableStatement(String schema, String table) {
        return "CHECK TABLE " + quoteIdentifier(schema) + "." + quoteIdentifier(table);
    }

    @Override
    public String globalVariablesQuery() {
        // SHOW (nao performance_schema.global_variables): funciona em
        // qualquer versao/config do MySQL, mesmo com performance_schema
        // desligado (nao e o padrao, mas alguns servidores desligam por
        // overhead) — SHOW so exige a conexao estar aberta, sem privilegio
        // extra.
        return "SHOW GLOBAL VARIABLES";
    }

    @Override
    public String globalStatusQuery() {
        return "SHOW GLOBAL STATUS";
    }

    @Override
    public String eventsQuery(String schema) {
        return "SELECT EVENT_NAME, STATUS, EVENT_TYPE, EXECUTE_AT, INTERVAL_VALUE, INTERVAL_FIELD, "
                + "STARTS, ENDS, ON_COMPLETION, LAST_EXECUTED, DEFINER "
                + "FROM information_schema.EVENTS "
                + "WHERE EVENT_SCHEMA = " + quoteLiteral(schema) + " "
                + "ORDER BY EVENT_NAME";
    }

    @Override
    public String replicaStatusQuery() {
        // "SHOW SLAVE STATUS", nao "SHOW REPLICA STATUS" (so existe a partir
        // do 8.0.22) — mesmo criterio de compatibilidade ampla ja usado em
        // globalVariablesQuery()/globalStatusQuery(): continua funcionando
        // (so como sinonimo/deprecated) em qualquer versao suportada por
        // esta IDE, sem precisar detectar a versao do servidor primeiro.
        return "SHOW SLAVE STATUS";
    }

    @Override
    public String sourceStatusQuery() {
        return "SHOW MASTER STATUS";
    }

    /** {@code 'user'@'host'}, escapando aspas simples internas de cada parte. */
    private static String quoteUserHost(String user, String host) {
        return quoteLiteral(user) + "@" + quoteLiteral(host);
    }
}
