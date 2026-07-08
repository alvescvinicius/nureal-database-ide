package com.nureal.ide.core.metadata.model;

/**
 * Especificacao de UMA coluna a ser criada por um CREATE TABLE (ver
 * {@link NewTableSpec}). Vem direto do formulario do usuario (ver
 * {@code CreateTableDialog} em com.nureal.ide.ui) — nenhuma validacao de SQL
 * acontece aqui, so a montagem do DDL em si (ver
 * {@code DatabaseDialect#createTableStatement}).
 *
 * @param name           nome da coluna (sem aspas/crases).
 * @param sqlType        tipo base (ex.: "VARCHAR", "INT", "DECIMAL").
 * @param length         tamanho/precisao como digitado (ex.: "255" ou
 *                       "10,2"); vazio/nulo quando o tipo nao usa parametro.
 * @param nullable       aceita NULL.
 * @param primaryKey     participa da PRIMARY KEY da tabela.
 * @param autoIncrement  AUTO_INCREMENT (so faz sentido em coluna inteira e
 *                       parte da chave primaria; nao e validado aqui).
 * @param defaultValue   valor DEFAULT como digitado; vazio/nulo = sem default.
 * @param comment        comentario da coluna; vazio/nulo = sem comentario.
 */
public record NewColumnSpec(String name, String sqlType, String length, boolean nullable,
                            boolean primaryKey, boolean autoIncrement, String defaultValue, String comment) {
}
