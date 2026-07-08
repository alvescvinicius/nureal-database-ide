package com.nureal.ide.core.metadata.model;

import java.util.List;

/**
 * Especificacao completa de uma tabela nova, coletada pelo
 * {@code CreateTableDialog} (com.nureal.ide.ui) e traduzida em DDL por
 * {@code DatabaseDialect#createTableStatement}. {@code name} e
 * {@code comment} ja vem "limpos" (trim) do dialogo; {@code columns} tem
 * sempre pelo menos um elemento.
 */
public record NewTableSpec(String name, List<NewColumnSpec> columns, String comment) {
}
