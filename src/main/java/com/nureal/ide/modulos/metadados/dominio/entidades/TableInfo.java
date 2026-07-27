package com.nureal.ide.modulos.metadados.dominio.entidades;

import java.util.List;

/** Uma tabela e suas colunas. */
public record TableInfo(String name, List<ColumnInfo> columns) {
}
