package com.nureal.ide.core.metadata;

import com.nureal.ide.core.dialect.DatabaseDialect;
import com.nureal.ide.core.metadata.model.ColumnDetail;
import com.nureal.ide.core.metadata.model.ColumnInfo;
import com.nureal.ide.core.metadata.model.ForeignKeyInfo;
import com.nureal.ide.core.metadata.model.IndexInfo;
import com.nureal.ide.core.metadata.model.SchemaInfo;
import com.nureal.ide.core.metadata.model.TableDetails;
import com.nureal.ide.core.metadata.model.TableInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Le a estrutura do banco. A chave da performance: UMA consulta ao information_schema
 * traz todas as colunas de todas as tabelas do schema; agrupamos em memoria.
 * Em seguida, consultas leves listam views, procedures, functions e triggers.
 */
public class MetadataService {

    private final DatabaseDialect dialect;

    public MetadataService(DatabaseDialect dialect) {
        this.dialect = dialect;
    }

    public SchemaInfo loadSchema(Connection conn, String schema) throws SQLException {
        // 1) Colunas de tudo (tabelas e views) em uma unica consulta.
        Map<String, List<ColumnInfo>> columnsByObject = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(dialect.columnsQuery())) {
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

        // 2) Separa tabelas de views pelo TABLE_TYPE.
        List<TableInfo> tables = new ArrayList<>();
        List<TableInfo> views = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(dialect.tablesQuery())) {
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

        // 3) Procedures e functions.
        List<String> procedures = new ArrayList<>();
        List<String> functions = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(dialect.routinesQuery())) {
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

        // 4) Triggers.
        List<String> triggers = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(dialect.triggersQuery())) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    triggers.add(rs.getString("TRIGGER_NAME"));
                }
            }
        }

        return new SchemaInfo(schema, tables, views, procedures, functions, triggers);
    }

    /**
     * Carrega, sob demanda, o detalhe completo de uma tabela/view: colunas
     * (com nulo, chave, default, extra e comentario), indices e chaves
     * estrangeiras. Usado pela tela de propriedades ao abrir um objeto.
     */
    public TableDetails loadTableDetails(Connection conn, String schema, String table)
            throws SQLException {
        return new TableDetails(
                loadColumnDetails(conn, schema, table),
                loadIndexes(conn, schema, table),
                loadForeignKeys(conn, schema, table));
    }

    private List<ColumnDetail> loadColumnDetails(Connection conn, String schema, String table)
            throws SQLException {
        List<ColumnDetail> columns = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(dialect.columnDetailsQuery())) {
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

    private List<IndexInfo> loadIndexes(Connection conn, String schema, String table)
            throws SQLException {
        Map<String, List<String>> cols = new LinkedHashMap<>();
        Map<String, Boolean> unique = new HashMap<>();
        Map<String, String> type = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(dialect.indexesQuery())) {
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

    private List<ForeignKeyInfo> loadForeignKeys(Connection conn, String schema, String table)
            throws SQLException {
        Map<String, List<String>> cols = new LinkedHashMap<>();
        Map<String, List<String>> refCols = new LinkedHashMap<>();
        Map<String, String> refTable = new HashMap<>();
        Map<String, String> onUpdate = new HashMap<>();
        Map<String, String> onDelete = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(dialect.foreignKeysQuery())) {
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
}
