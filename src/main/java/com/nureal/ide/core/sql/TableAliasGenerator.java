package com.nureal.ide.core.sql;

import java.util.Locale;

/**
 * Deriva um alias curto e convencional a partir do nome de uma tabela:
 * primeira letra de cada "palavra" separada por underscore (ex.:
 * {@code "operation" -> "o"}, {@code "operation_order" -> "oo"},
 * {@code "trade_asset" -> "ta"}) — a mesma convencao pedida explicitamente
 * pelo usuario ao corrigir a primeira versao do gerador de JOIN
 * ({@code MainWindow#insertJoinStatement}), reaproveitada aqui porque o
 * autocomplete inteligente (ver {@code SqlCompletionProviderRSyntax}) precisa da
 * MESMA logica ao montar o snippet "tabela alias ON ..." — mas
 * {@code SqlCompletionProviderRSyntax} vive em {@code modulos.autocomplete} (sem
 * dependencia de {@code ui}), entao a logica compartilhada mora aqui em vez
 * de em {@code MainWindow}.
 */
public final class TableAliasGenerator {

    private TableAliasGenerator() {
    }

    /** Alias curto para {@code tableName}; nunca vazio (cai no proprio nome, em minusculas, se nao houver letras). */
    public static String deriveAlias(String tableName) {
        StringBuilder alias = new StringBuilder();
        for (String part : tableName.split("_")) {
            if (!part.isEmpty()) {
                alias.append(Character.toLowerCase(part.charAt(0)));
            }
        }
        return (alias.length() > 0) ? alias.toString() : tableName.toLowerCase(Locale.ROOT);
    }

    /**
     * Alias para {@code candidateTable} garantido DIFERENTE de {@code otherAlias}
     * quando {@code candidateTable} e {@code otherTable} sao tabelas
     * diferentes — evita gerar um {@code ON} ambiguo/invalido quando duas
     * tabelas distintas calham de derivar o MESMO alias (ex.: nomes que
     * comecam com as mesmas iniciais). So desempata o lado CANDIDATO,
     * acrescentando "2"; nunca muda o alias que o chamador ja fixou pro
     * outro lado.
     */
    public static String deriveDistinctAlias(String candidateTable, String otherTable, String otherAlias) {
        String alias = deriveAlias(candidateTable);
        if (alias.equalsIgnoreCase(otherAlias) && !candidateTable.equalsIgnoreCase(otherTable)) {
            return alias + "2";
        }
        return alias;
    }
}
