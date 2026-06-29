package com.nureal.ide.core.dialect;

import com.nureal.ide.core.connection.ConnectionProfile;

import java.util.List;

/**
 * Implementacao para MySQL. Le metadados via information_schema em UMA consulta,
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
    public String buildJdbcUrl(ConnectionProfile p) {
        return String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                p.host(), p.port(), p.schema());
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
        return "SHOW CREATE " + objectKind + " " + quoteIdent(objectName);
    }

    /** Envolve um identificador em crases, dobrando crases internas. */
    private static String quoteIdent(String ident) {
        return "`" + ident.replace("`", "``") + "`";
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
