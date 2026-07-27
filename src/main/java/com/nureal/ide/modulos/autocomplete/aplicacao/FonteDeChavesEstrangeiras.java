package com.nureal.ide.modulos.autocomplete.aplicacao;

import com.nureal.ide.modulos.metadados.dominio.entidades.ForeignKeyInfo;

import java.util.List;

/**
 * Fonte das chaves estrangeiras JA CONHECIDAS de uma tabela — usada pelo
 * "auxiliar de montagem de queries" (ver {@link GeradorDeSugestoes}) pra
 * sugerir, ao completar o nome da tabela logo apos um JOIN, primeiro as
 * tabelas relacionadas por FK as que ja estao no FROM/JOIN da consulta.
 * Deliberadamente uma interface funcional simples (nao a classe de cache de
 * verdade, {@code com.nureal.ide.ui.TableMetadataCache}): este modulo nao tem
 * dependencia de Swing — quem monta o editor (MainWindow) que liga esta
 * ponte via {@code setForeignKeyLookup}. Pode devolver lista vazia se a
 * tabela ainda nao foi consultada (a implementacao tipica dispara a carga em
 * segundo plano e devolve vazio POR ENQUANTO — a proxima tecla digitada tenta
 * de novo, ja com o cache quente).
 */
@FunctionalInterface
public interface FonteDeChavesEstrangeiras {
    List<ForeignKeyInfo> foreignKeysOf(String tableName);
}
