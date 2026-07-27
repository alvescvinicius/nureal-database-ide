package com.nureal.ide.modulos.metadados.dominio.entidades;

/**
 * Detalhe completo de uma coluna, alem do nome e tipo: se aceita nulo, a chave
 * (PRI/UNI/MUL), o valor default, o "extra" (ex.: auto_increment) e o comentario.
 */
public record ColumnDetail(int position, String name, String type, boolean nullable,
                           String key, String defaultValue, String extra, String comment) {
}
