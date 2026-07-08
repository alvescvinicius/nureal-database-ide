package com.nureal.ide.core.sql;

/**
 * Localiza os limites (offsets) da instrucao SQL que contem um determinado
 * ponto do texto — usado tanto pelo destacador de sintaxe (para saber quais
 * nomes de tabela/alias pertencem "a mesma instrucao" que a linha sendo
 * lexada) quanto pelo editor (para o destaque de fundo da instrucao atual,
 * ver secao 8.1 do pedido do usuario "Navegacao Inteligente e Interativa").
 *
 * Consciente de strings ({@code '...'}, {@code "..."}), identificadores
 * entre crases ({@code `...`}) e comentarios ({@code --}, {@code #},
 * {@code /* ... * /}) — um {@code ;} dentro deles nunca conta como fim de
 * instrucao. Ponto UNICO desta logica: antes, uma variacao dela estava
 * duplicada em {@code SqlFoldParser}, {@code UnquotedDateGuard} e
 * {@code SqlHighlightTokenMaker}; esta classe nao os substitui (cada um tem
 * uma necessidade ligeiramente diferente — ranges completos vs. so limites),
 * mas qualquer NOVO uso de "onde comeca/termina a instrucao atual" deve vir
 * daqui.
 */
public final class SqlStatementLocator {

    private SqlStatementLocator() {
    }

    /**
     * Limites {@code [inicio, fim)} da instrucao que contem {@code offset}
     * dentro de {@code full}. {@code inicio} e o offset logo apos o {@code ;}
     * anterior mais proximo (ou 0, se nao houver um antes de {@code offset});
     * {@code fim} e o offset do proximo {@code ;} (ou {@code full.length()},
     * se a instrucao nao terminar em {@code ;} — ex.: a ultima do arquivo).
     */
    public static int[] boundsAt(String full, int offset) {
        int n = full.length();
        int safeOffset = Math.max(0, Math.min(offset, n));
        int start = 0;
        int end = n;
        int i = 0;
        while (i < n) {
            char c = full.charAt(i);
            if ((c == '-' && i + 1 < n && full.charAt(i + 1) == '-') || c == '#') {
                while (i < n && full.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && full.charAt(i + 1) == '*') {
                int close = full.indexOf("*/", i + 2);
                i = (close < 0) ? n : close + 2;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                char quote = c;
                i++;
                while (i < n) {
                    char d = full.charAt(i);
                    if (d == '\\' && (quote == '\'' || quote == '"') && i + 1 < n) {
                        i += 2;
                        continue;
                    }
                    if (d == quote) {
                        if (i + 1 < n && full.charAt(i + 1) == quote) {
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    i++;
                }
                continue;
            }
            if (c == ';') {
                if (i < safeOffset) {
                    start = i + 1;
                } else {
                    end = i;
                    break;
                }
            }
            i++;
        }
        // Aparar espacos/quebras de linha nas pontas: o destaque de fundo
        // (ver SqlEditorPane) nao deve cobrir a linha em branco entre duas
        // instrucoes, so o texto de verdade da instrucao.
        while (start < end && Character.isWhitespace(full.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(full.charAt(end - 1))) {
            end--;
        }
        return new int[] {start, end};
    }
}
