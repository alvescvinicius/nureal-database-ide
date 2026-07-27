package com.nureal.ide.modulos.metadados.dominio.entidades;

import java.util.List;

/** Um indice de uma tabela: nome, se e unico, o tipo (BTREE/HASH) e suas colunas. */
public record IndexInfo(String name, boolean unique, String type, List<String> columns) {
}
