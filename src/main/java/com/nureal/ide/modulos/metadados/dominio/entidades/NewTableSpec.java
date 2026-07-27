package com.nureal.ide.modulos.metadados.dominio.entidades;
import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;

import java.util.List;

/**
 * Especificacao completa de uma tabela nova, coletada pelo assistente de DDL
 * ({@code com.nureal.ide.ui.DdlAssistantDialog}) e traduzida em DDL por
 * {@code DatabaseDialect#createTableStatement}. {@code name} e
 * {@code comment} ja vem "limpos" (trim) do dialogo; {@code columns} tem
 * sempre pelo menos um elemento.
 *
 * @param foreignKeys chaves estrangeiras da tabela (pode ser vazia — nunca
 *                     nula); {@code ForeignKeyInfo#name} pode vir em branco,
 *                     nesse caso o dialeto gera um nome de constraint.
 * @param indexes      indices adicionais (fora da PRIMARY KEY) a criar junto
 *                     com a tabela (pode ser vazia — nunca nula);
 *                     {@code IndexInfo#name} pode vir em branco.
 */
public record NewTableSpec(String name, List<NewColumnSpec> columns, String comment,
                           List<ForeignKeyInfo> foreignKeys, List<IndexInfo> indexes) {

    /** Compatibilidade: tabela sem chaves estrangeiras/indices extras. */
    public NewTableSpec(String name, List<NewColumnSpec> columns, String comment) {
        this(name, columns, comment, List.of(), List.of());
    }
}
