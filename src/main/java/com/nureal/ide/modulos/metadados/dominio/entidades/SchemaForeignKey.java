package com.nureal.ide.modulos.metadados.dominio.entidades;
import com.nureal.ide.modulos.metadados.infraestrutura.MetadataService;

import java.util.List;

/**
 * Uma chave estrangeira dentro de um schema INTEIRO — igual a
 * {@link ForeignKeyInfo}, mas com a tabela de ORIGEM explicita ({@code
 * fromTable}), porque aqui nao ha uma tabela "atual" implicita (ver
 * {@code MetadataService#loadSchemaForeignKeys}, usada pelo Diagrama ER para
 * desenhar as relacoes entre TODAS as tabelas do schema de uma vez, ao
 * contrario de {@link ForeignKeyInfo}, carregada uma tabela por vez em
 * {@code MetadataService#loadTableDetails}).
 */
public record SchemaForeignKey(String name, String fromTable, List<String> fromColumns,
                                String toTable, List<String> toColumns,
                                String onUpdate, String onDelete) {
}
