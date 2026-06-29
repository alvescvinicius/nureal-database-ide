package com.nureal.ide.core.metadata.model;

import java.util.List;

/**
 * Uma chave estrangeira: nome da constraint, colunas locais, tabela e colunas
 * referenciadas e as regras ON UPDATE / ON DELETE.
 */
public record ForeignKeyInfo(String name, List<String> columns,
                             String referencedTable, List<String> referencedColumns,
                             String onUpdate, String onDelete) {
}
