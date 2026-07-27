package com.nureal.ide.modulos.assistenteddl.aplicacao;

import com.nureal.ide.modulos.metadados.dominio.entidades.ForeignKeyInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.IndexInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.NewColumnSpec;

import java.util.List;

/**
 * Entrada do caso de uso "construir DDL de tabela" — ja com o formulario do
 * assistente (colunas/FKs/indices novos, e no modo alterar tambem os
 * modificados/removidos) coletado e validado LINHA A LINHA por quem monta o
 * formulario ({@code DdlAssistantDialog}); este Input so carrega o resultado
 * ja limpo dessa coleta, nunca os componentes Swing em si.
 */
public record ConstruirDdlDeTabelaInput(
        boolean alterMode,
        String alterTableName,
        String tableName,
        boolean tableNameJaExiste,
        String comment,
        List<NewColumnSpec> newColumns,
        List<ForeignKeyInfo> foreignKeys,
        List<IndexInfo> indexes,
        List<NewColumnSpec> modifiedColumns,
        List<String> droppedColumns,
        List<String> droppedForeignKeys,
        List<String> droppedIndexes) {
}
