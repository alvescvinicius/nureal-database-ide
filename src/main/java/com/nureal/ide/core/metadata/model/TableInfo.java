package com.nureal.ide.core.metadata.model;

import java.util.List;

/** Uma tabela e suas colunas. */
public record TableInfo(String name, List<ColumnInfo> columns) {
}
