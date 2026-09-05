package com.nureal.ide.modulos.dialeto.dominio.contratos;

import java.util.List;

/**
 * Primitivas de sintaxe SQL do dialeto — pequenas, mas usadas por TODA
 * parte obrigatoria de um driver (metadados, DDL) e tambem por consumidores
 * externos que so precisam disso (ex.: {@code GridEditController}, que
 * monta UPDATE/INSERT a partir de uma edicao na grade e so quer
 * {@link #quoteIdentifier}, nao o resto do dialeto). Parte OBRIGATORIA de
 * {@link DatabaseDialect}.
 */
public interface SqlSyntaxCapability {

    /** Palavras-chave da linguagem para o autocomplete. */
    List<String> keywords();

    /**
     * Envolve um identificador (tabela ou coluna) nas aspas/crases do
     * dialeto, escapando ocorrencias internas do proprio caractere de aspa —
     * usado para montar UPDATE/INSERT/DELETE com nomes seguros ao aplicar
     * edicoes feitas direto na grade de resultados (ver GridEditController).
     */
    String quoteIdentifier(String ident);
}
