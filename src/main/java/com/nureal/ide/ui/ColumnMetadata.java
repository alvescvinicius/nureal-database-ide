package com.nureal.ide.ui;

import com.nureal.ide.core.metadata.model.ForeignKeyInfo;
import com.nureal.ide.core.metadata.model.IndexInfo;

import java.util.List;

/**
 * Visao completa e "achatada" dos metadados de uma coluna da grade de
 * resultados, pronta para exibicao (popup do cabecalho, menu "Informacoes da
 * coluna"). Combina:
 *
 *  - o que o JDBC entrega de graca junto do proprio ResultSet ({@code label},
 *    {@code sqlType}, {@code javaType}, {@code jdbcMeta}) — sempre disponivel,
 *    sem custo extra;
 *  - o que exige consulta ao catalogo do schema ({@code primaryKey},
 *    {@code foreignKey}, {@code indexes}, {@code comment}) — carregado sob
 *    demanda por {@link ColumnMetadataResolver} e pode chegar vazio/"ainda
 *    carregando" na primeira exibicao.
 *
 * {@code schema} e o schema/catalogo em uso na conexao no momento da consulta
 * (o mesmo repassado ao {@link ColumnMetadataResolver}) — pode ser
 * {@code null} quando a conexao nao tem schema selecionado.
 *
 * Ver {@link ColumnMetadataResolver#resolve} para como isto e montado.
 */
record ColumnMetadata(
        String label,
        String realColumnName,
        String sourceTable,
        String schema,
        String sqlType,
        Class<?> javaType,
        ResultTableModel.ColumnJdbcMeta jdbcMeta,
        boolean schemaLoaded,
        boolean primaryKey,
        ForeignKeyInfo foreignKey,
        List<IndexInfo> indexes,
        String comment) {

    boolean hasForeignKey() {
        return foreignKey != null;
    }

    boolean hasIndexes() {
        return indexes != null && !indexes.isEmpty();
    }

    boolean hasComment() {
        return comment != null && !comment.isBlank();
    }
}
