package com.nureal.ide.modulos.metadados.dominio.entidades;

import java.util.List;

/**
 * Um schema (database) e seus objetos: tabelas, visualizacoes, procedures,
 * functions, triggers e eventos agendados. Tabelas e views carregam suas
 * colunas; os demais objetos sao listados pelo nome.
 */
public record SchemaInfo(String name,
                         List<TableInfo> tables,
                         List<TableInfo> views,
                         List<String> procedures,
                         List<String> functions,
                         List<String> triggers,
                         List<String> events) {

    /**
     * Compatibilidade: schema com os objetos "classicos" (sem eventos) —
     * usado por chamadores que ainda nao carregam eventos (ver
     * {@code MetadataService#loadSchema}, unico que preenche a lista de
     * verdade hoje).
     */
    public SchemaInfo(String name, List<TableInfo> tables, List<TableInfo> views, List<String> procedures,
            List<String> functions, List<String> triggers) {
        this(name, tables, views, procedures, functions, triggers, List.of());
    }

    /** Compatibilidade: schema apenas com tabelas (sem demais objetos). */
    public SchemaInfo(String name, List<TableInfo> tables) {
        this(name, tables, List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
