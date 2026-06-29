package com.nureal.ide.core.metadata.model;

import java.util.List;

/**
 * Um schema (database) e seus objetos: tabelas, visualizacoes, procedures,
 * functions e triggers. Tabelas e views carregam suas colunas; os demais
 * objetos sao listados pelo nome.
 */
public record SchemaInfo(String name,
                         List<TableInfo> tables,
                         List<TableInfo> views,
                         List<String> procedures,
                         List<String> functions,
                         List<String> triggers) {

    /** Compatibilidade: schema apenas com tabelas (sem demais objetos). */
    public SchemaInfo(String name, List<TableInfo> tables) {
        this(name, tables, List.of(), List.of(), List.of(), List.of());
    }
}
