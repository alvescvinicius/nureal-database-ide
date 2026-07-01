package com.nureal.ide.ui;

import com.nureal.ide.core.connection.ConnectionManager;
import com.nureal.ide.core.metadata.model.ColumnDetail;
import com.nureal.ide.core.metadata.model.ForeignKeyInfo;
import com.nureal.ide.core.metadata.model.IndexInfo;
import com.nureal.ide.core.metadata.model.TableDetails;

import java.util.ArrayList;
import java.util.List;

/**
 * Monta um {@link ColumnMetadata} completo para uma coluna da grade,
 * combinando o {@link ResultTableModel} (sempre disponivel, vem do proprio
 * ResultSet) com o {@link TableMetadataCache} (PK/FK/indices/comentario,
 * carregado sob demanda a partir da tabela fisica de origem da coluna).
 *
 * Substitui o antigo par fkCache/populateForeignKeyMap de MainWindow por um
 * unico ponto de resolucao reaproveitado pelo indicador de FK do cabecalho,
 * pelo popup de metadados e pelo menu "Informacoes da coluna".
 */
final class ColumnMetadataResolver {

    private final TableMetadataCache cache;
    private final ConnectionManager connectionManager;
    private final String schema;

    ColumnMetadataResolver(TableMetadataCache cache, ConnectionManager connectionManager, String schema) {
        this.cache = cache;
        this.connectionManager = connectionManager;
        this.schema = schema;
    }

    /**
     * Resolve os metadados da coluna {@code column}. Se a parte de schema
     * (PK/FK/indices/comentario) ainda nao estiver em cache, dispara a carga
     * em segundo plano e chama {@code onSchemaLoaded} quando ela terminar —
     * o chamador deve entao resolver de novo e atualizar a UI (repaint do
     * header, reabrir o popup, etc). O retorno NUNCA e nulo: enquanto o
     * schema nao carrega, {@code schemaLoaded()} vem falso e os campos de
     * schema vem vazios.
     */
    ColumnMetadata resolve(ResultTableModel model, int column, Runnable onSchemaLoaded) {
        String label = model.getColumnName(column);
        String realCol = model.realColumnName(column);
        String sourceTable = model.sourceTable(column);
        String sqlType = model.sqlType(column);
        Class<?> javaType = model.getColumnClass(column);
        ResultTableModel.ColumnJdbcMeta jdbcMeta = model.jdbcMeta(column);

        if (sourceTable == null || sourceTable.isBlank() || realCol == null || realCol.isBlank()) {
            // coluna calculada/expressao/JOIN complexo: nao ha tabela fisica
            // para consultar — devolve so o que o JDBC ja sabe.
            return new ColumnMetadata(label, realCol, sourceTable, schema, sqlType, javaType, jdbcMeta,
                    true, false, null, List.of(), null);
        }

        TableDetails details = cache.get(connectionManager, schema, sourceTable, onSchemaLoaded);
        if (details == null) {
            return new ColumnMetadata(label, realCol, sourceTable, schema, sqlType, javaType, jdbcMeta,
                    false, false, null, List.of(), null);
        }

        ColumnDetail columnDetail = findColumn(details, realCol);
        boolean primaryKey = columnDetail != null && "PRI".equalsIgnoreCase(columnDetail.key());
        String comment = (columnDetail != null) ? columnDetail.comment() : null;
        ForeignKeyInfo fk = findForeignKey(details, realCol);
        List<IndexInfo> indexes = findIndexes(details, realCol);

        return new ColumnMetadata(label, realCol, sourceTable, schema, sqlType, javaType, jdbcMeta,
                true, primaryKey, fk, indexes, comment);
    }

    private static ColumnDetail findColumn(TableDetails details, String realColumnName) {
        for (ColumnDetail c : details.columns()) {
            if (c.name().equalsIgnoreCase(realColumnName)) {
                return c;
            }
        }
        return null;
    }

    private static ForeignKeyInfo findForeignKey(TableDetails details, String realColumnName) {
        for (ForeignKeyInfo fk : details.foreignKeys()) {
            if (containsIgnoreCase(fk.columns(), realColumnName)) {
                return fk;
            }
        }
        return null;
    }

    private static List<IndexInfo> findIndexes(TableDetails details, String realColumnName) {
        List<IndexInfo> found = new ArrayList<>();
        for (IndexInfo idx : details.indexes()) {
            if (containsIgnoreCase(idx.columns(), realColumnName)) {
                found.add(idx);
            }
        }
        return found;
    }

    private static boolean containsIgnoreCase(List<String> values, String target) {
        for (String v : values) {
            if (v.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }
}
