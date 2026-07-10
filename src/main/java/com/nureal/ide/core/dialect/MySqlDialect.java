package com.nureal.ide.core.dialect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.nureal.ide.core.connection.ConnectionProfile;
import com.nureal.ide.core.metadata.model.ForeignKeyInfo;
import com.nureal.ide.core.metadata.model.IndexInfo;
import com.nureal.ide.core.metadata.model.NewColumnSpec;
import com.nureal.ide.core.metadata.model.NewTableSpec;

/**
 * Implementacao para MySQL. Le metadados via information_schema em UMA
 * consulta,
 * que e a base para o autocomplete rapido.
 */
public class MySqlDialect implements DatabaseDialect {

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
    public String schemasQuery() {
        return "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA "
                + "WHERE SCHEMA_NAME NOT IN "
                + "('information_schema','performance_schema','mysql','sys') "
                + "ORDER BY SCHEMA_NAME";
    }

    @Override
    public String columnsQuery() {
        return "SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, ORDINAL_POSITION "
                + "FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = ? "
                + "ORDER BY TABLE_NAME, ORDINAL_POSITION";
    }

    @Override
    public String tablesQuery() {
        return "SELECT TABLE_NAME, TABLE_TYPE "
                + "FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA = ? "
                + "ORDER BY TABLE_NAME";
    }

    @Override
    public String routinesQuery() {
        return "SELECT ROUTINE_NAME, ROUTINE_TYPE "
                + "FROM information_schema.ROUTINES "
                + "WHERE ROUTINE_SCHEMA = ? "
                + "ORDER BY ROUTINE_NAME";
    }

    @Override
    public String triggersQuery() {
        return "SELECT TRIGGER_NAME "
                + "FROM information_schema.TRIGGERS "
                + "WHERE TRIGGER_SCHEMA = ? "
                + "ORDER BY TRIGGER_NAME";
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

    @Override
    public String createSchemaStatement(String name) {
        return "CREATE DATABASE " + quoteIdentifier(name);
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

    @Override
    public String columnDetailsQuery() {
        return "SELECT ORDINAL_POSITION, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, "
                + "COLUMN_KEY, COLUMN_DEFAULT, EXTRA, COLUMN_COMMENT "
                + "FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? "
                + "ORDER BY ORDINAL_POSITION";
    }

    @Override
    public String indexesQuery() {
        return "SELECT INDEX_NAME, NON_UNIQUE, INDEX_TYPE, SEQ_IN_INDEX, COLUMN_NAME "
                + "FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? "
                + "ORDER BY INDEX_NAME, SEQ_IN_INDEX";
    }

    @Override
    public String foreignKeysQuery() {
        return "SELECT k.CONSTRAINT_NAME, k.COLUMN_NAME, k.REFERENCED_TABLE_NAME, "
                + "k.REFERENCED_COLUMN_NAME, r.UPDATE_RULE, r.DELETE_RULE "
                + "FROM information_schema.KEY_COLUMN_USAGE k "
                + "JOIN information_schema.REFERENTIAL_CONSTRAINTS r "
                + "  ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA "
                + "  AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME "
                + "WHERE k.TABLE_SCHEMA = ? AND k.TABLE_NAME = ? "
                + "  AND k.REFERENCED_TABLE_NAME IS NOT NULL "
                + "ORDER BY k.CONSTRAINT_NAME, k.ORDINAL_POSITION";
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
}
