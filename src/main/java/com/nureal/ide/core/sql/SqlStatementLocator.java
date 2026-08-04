package com.nureal.ide.core.sql;

import java.util.ArrayList;
import java.util.List;

/**
 * Localiza os limites (offsets) da instrucao SQL que contem um determinado
 * ponto do texto — usado tanto pelo destacador de sintaxe (para saber quais
 * nomes de tabela/alias pertencem "a mesma instrucao" que a linha sendo
 * lexada) quanto pelo editor (para o destaque de fundo da instrucao atual,
 * ver secao 8.1 do pedido do usuario "Navegacao Inteligente e Interativa",
 * e para "selecionar/executar a instrucao sob o cursor" no duplo-clique e no
 * menu de contexto, ver {@code SqlEditorPane}).
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
     * dentro de {@code full}, ja aparados de espacos/quebras de linha nas
     * pontas.
     *
     * Um {@code offset} que cai no "vao" entre duas instrucoes (o ";" em si,
     * ou qualquer espaco/linha em branco DEPOIS dele e ANTES do primeiro
     * caractere de verdade da PROXIMA instrucao) pertence a instrucao
     * ANTERIOR, nunca a seguinte — mesmo criterio de qualquer editor SQL
     * (DBeaver, SSMS etc.): terminar de digitar uma instrucao e posicionar o
     * cursor logo apos o ";" continua "dentro" dela para efeitos de
     * selecionar/executar. Sem isto, um duplo-clique bem em cima do ";" ou
     * logo depois dele (posicao MUITO comum: e exatamente onde o cursor fica
     * apos terminar de digitar a instrucao) selecionava a instrucao de BAIXO
     * em vez da que o usuario estava olhando — bug relatado pelo usuario.
     */
    public static int[] boundsAt(String full, int offset) {
        int n = full.length();
        int safeOffset = Math.max(0, Math.min(offset, n));
        List<int[]> segments = rawSegments(full);

        for (int idx = 0; idx < segments.size(); idx++) {
            int[] trimmed = trim(full, segments.get(idx));
            boolean isLast = idx == segments.size() - 1;
            if (isLast) {
                return trimmed;
            }
            int nextTrimmedStart = trim(full, segments.get(idx + 1))[0];
            if (safeOffset < nextTrimmedStart) {
                return trimmed;
            }
        }
        // Texto vazio (rawSegments sempre devolve pelo menos 1 elemento) —
        // nunca deveria chegar aqui, mas devolve um range vazio seguro.
        return new int[] {0, 0};
    }

    /**
     * Divide {@code full} em instrucoes NAO aparadas (cada uma
     * {@code [inicio, fim)}, onde {@code fim} e o offset do ";" que a termina,
     * ou {@code full.length()} para a ultima, sem ";" final) — consciente de
     * comentarios/strings, mesma varredura que {@link #boundsAt} usava antes,
     * agora coletando TODAS as instrucoes de uma vez (nao so a que contem um
     * offset especifico) para permitir a comparacao com a PROXIMA instrucao.
     */
    private static List<int[]> rawSegments(String full) {
        List<int[]> segments = new ArrayList<>();
        int n = full.length();
        int segStart = 0;
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
                segments.add(new int[] {segStart, i});
                segStart = i + 1;
            }
            i++;
        }
        segments.add(new int[] {segStart, n});
        return segments;
    }

    /** Apara espacos/quebras de linha nas pontas de {@code [seg[0], seg[1])} — a instrucao de verdade, sem a "moldura" em branco ao redor. */
    private static int[] trim(String full, int[] seg) {
        int start = seg[0];
        int end = seg[1];
        while (start < end && Character.isWhitespace(full.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(full.charAt(end - 1))) {
            end--;
        }
        return new int[] {start, end};
    }
}
