package com.nureal.ide.ui;

import java.util.HashSet;
import java.util.Set;

/**
 * Decide se uma coluna TEXTUAL (grupo {@link RendererFactory.Group#TEXTUAL})
 * tem "cara de enum" — poucos valores distintos, curtos, que SE REPETEM ao
 * longo das linhas ja carregadas (ex.: RECEITA/DESPESA, ATIVO/INATIVO,
 * PENDENTE/PAGO/CANCELADO) — caso em que {@link RendererFactory} instala
 * {@link BadgeCellRenderer} (pill colorido) em vez do {@link TextCellRenderer}
 * padrao (texto plano).
 *
 * Nao ha sinal de SCHEMA para isso (o tipo SQL e so VARCHAR/TEXT, igual
 * qualquer outra coluna de texto livre) — a unica pista disponivel e o
 * CONTEUDO ja carregado na pagina atual de resultados (ver
 * {@link ResultTableModel}). Por isso e so uma heuristica "melhor esforco"
 * sobre as linhas JA BUSCADAS, nao a tabela inteira: pode errar (falso
 * negativo se a pagina atual, por acaso, so tiver valores unicos de uma
 * coluna que na tabela inteira se repete; falso positivo bem mais raro,
 * dado o limite de valores distintos e tamanho abaixo) — aceitavel para um
 * recurso cosmetico, sem impacto nos dados/edicao.
 */
final class EnumColumnDetector {

    /** Acima disto, a coluna parece livre demais (categorias tem poucos rotulos). */
    private static final int MAX_DISTINCT = 8;
    /** Acima disto, o valor parece frase/descricao, nao rotulo curto. */
    private static final int MAX_LENGTH = 24;
    /** Poucas linhas demais pra qualquer inferencia ser confiavel. */
    private static final int MIN_ROWS = 2;

    private EnumColumnDetector() {
    }

    /**
     * @param model       dados da grade ja carregados (pagina atual)
     * @param modelColumn indice de COLUNA no modelo (nao na view)
     */
    static boolean isEnumLike(ResultTableModel model, int modelColumn) {
        int rows = model.getRowCount();
        if (rows < MIN_ROWS) {
            return false;
        }
        Set<String> distinct = new HashSet<>();
        int nonNull = 0;
        for (int r = 0; r < rows; r++) {
            Object raw = model.getValueAt(r, modelColumn);
            if (raw == null) {
                continue;
            }
            String s = raw.toString().trim();
            if (s.isEmpty() || s.length() > MAX_LENGTH || s.indexOf('\n') >= 0) {
                // Vazio, texto longo ou multi-linha: nao e um rotulo curto de categoria.
                return false;
            }
            nonNull++;
            distinct.add(s);
            if (distinct.size() > MAX_DISTINCT) {
                return false;
            }
        }
        if (nonNull == 0) {
            return false;
        }
        // Precisa de repeticao de verdade (pelo menos um valor aparece 2x na
        // pagina atual) OU um conjunto ja pequeno o bastante (<=3 rotulos)
        // pra parecer fechado mesmo sem repetir ainda (ex.: pagina com so 3
        // linhas, uma de cada categoria).
        return distinct.size() < nonNull || distinct.size() <= 3;
    }
}
