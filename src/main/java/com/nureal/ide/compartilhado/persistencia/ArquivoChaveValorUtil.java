package com.nureal.ide.compartilhado.persistencia;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utilitarios repetidos em todas as stores de arquivo plano do projeto
 * ({@code ConnectionStore}, {@code ExecutionHistoryStore},
 * {@code SavedQueryStore}, {@code SessionStore}, {@code ChatHistoryStore}):
 * cada uma reimplementava, de forma identica, a mesma cifragem Base64 de
 * campo (para preservar quebras de linha dentro do formato {@code chave=valor})
 * e o mesmo parse tolerante a erro de numeros. Extraido para
 * {@code compartilhado} por atender com folga a regra dos tres usos (ver
 * .specs/08-modulo-historico-consultas-sessao.md, regra 1) — nenhuma mudanca
 * de formato de arquivo, os bytes gerados sao identicos aos de antes.
 */
public final class ArquivoChaveValorUtil {

    private ArquivoChaveValorUtil() {
    }

    /** Codifica um campo em Base64 (preserva quebras de linha/caracteres especiais no formato flat). */
    public static String encode(String plain) {
        return Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    /** Decodifica um campo gravado por {@link #encode(String)}; string vazia se malformado. */
    public static String decode(String encoded) {
        try {
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    /** Interpreta como {@code long}; 0 se ausente/malformado (nunca lanca excecao). */
    public static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** Interpreta como {@code int}; 0 se ausente/malformado (nunca lanca excecao). */
    public static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
