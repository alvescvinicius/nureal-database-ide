package com.nureal.ide.modulos.metadados.dominio.entidades;

import java.util.List;

/** Detalhe completo de uma tabela: colunas, indices e chaves estrangeiras. */
public record TableDetails(List<ColumnDetail> columns,
                           List<IndexInfo> indexes,
                           List<ForeignKeyInfo> foreignKeys) {
}
