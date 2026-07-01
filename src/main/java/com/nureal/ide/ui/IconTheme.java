package com.nureal.ide.ui;

import java.awt.BasicStroke;
import java.awt.Color;

/**
 * Design system unico para todos os icones da Nureal Database IDE.
 *
 * Grid de referencia: 20x20, stroke 2px, raio de canto 3px — guardados aqui
 * como RAZOES (2/20, 3/20), nao pixels fixos, para que todo icone escale
 * para qualquer tamanho/DPI pedido sem perder a proporcao.
 *
 * Regra de cor da marca (estrita):
 *  - Verde  -> SOMENTE acoes positivas (executar, conectar, sucesso).
 *  - Amarelo -> SOMENTE estados/avisos.
 *  - Vermelho -> SOMENTE erro/exclusao/desconexao.
 *  - Tudo o mais (a grande maioria: estrutura, navegacao, metadados) usa o
 *    "preto" da marca (INK) ou, em contextos de baixo peso visual (barra de
 *    ferramentas, paineis), o cinza neutro (MUTED).
 */
final class IconTheme {

    private IconTheme() {
    }

    /** Grid de desenho (icones sao compostos em coordenadas 0..size, nao em 0..20 literal). */
    static final double GRID = 20.0;
    private static final double STROKE_RATIO = 2.0 / GRID;
    private static final double RADIUS_RATIO = 3.0 / GRID;

    static final Color GREEN = new Color(0x13961F);
    static final Color YELLOW = new Color(0xF3C300);
    static final Color RED = new Color(0xC62828);
    static final Color INK = new Color(0x1A1A1A);
    static final Color MUTED = new Color(0x6B7280);
    static final Color DISABLED = new Color(0xC1C7CD);

    static final int DEFAULT_SIZE = 16;

    /** Espessura de traco proporcional ao tamanho pedido (2px num grid de 20px). */
    static float strokeWidth(double size) {
        return (float) Math.max(1.0, size * STROKE_RATIO);
    }

    /** Raio de canto proporcional ao tamanho pedido (3px num grid de 20px). */
    static double cornerRadius(double size) {
        return size * RADIUS_RATIO;
    }

    /** Traco padrao (cantos e pontas arredondados) para o tamanho pedido. */
    static BasicStroke stroke(double size) {
        return new BasicStroke(strokeWidth(size), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    /** Cor padrao de um icone, de acordo com o papel semantico do seu IconType. */
    static Color colorFor(IconType type) {
        return switch (type.role()) {
            case POSITIVE -> GREEN;
            case WARNING -> YELLOW;
            case NEGATIVE -> RED;
            case NEUTRAL -> INK;
        };
    }
}
