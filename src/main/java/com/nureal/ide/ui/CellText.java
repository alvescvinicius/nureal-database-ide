package com.nureal.ide.ui;

/**
 * Truncamento de texto exibido nas celulas da grade — usado por
 * {@link AbstractTypedCellRenderer} (exibicao) e {@link ColumnAutoFit}
 * (medida de largura), para que um unico valor gigante (um JSON, um CLOB, um
 * BLOB em base64) nunca:
 *
 *  1) force uma coluna a ficar absurdamente larga (ColumnAutoFit mede so os
 *     primeiros {@link #MAX_MEASURED_CHARS} caracteres);
 *  2) custe caro para pintar a cada repaint (o JLabel do renderer recebe so
 *     os primeiros {@link #MAX_DISPLAY_CHARS} caracteres, nunca a string
 *     inteira).
 *
 * O valor ORIGINAL, completo, continua disponivel via {@code getValueAt} —
 * usado pelo tooltip da celula ({@link ResultGrid}) e pelo visualizador de
 * celula ({@link CellContentViewer}); truncar so a APARENCIA nunca perde
 * dado, so evita que ele quebre o layout ou pese na renderizacao.
 */
final class CellText {

    /** Caracteres exibidos de fato na celula antes de cortar com reticencias. */
    static final int MAX_DISPLAY_CHARS = 300;

    /** Caracteres considerados pelo autofit ao medir a largura de uma celula. */
    static final int MAX_MEASURED_CHARS = 200;

    private CellText() {
    }

    /** Verdadeiro se o texto formatado excede o limite de exibicao (precisa de "..." e tooltip). */
    static boolean isTruncated(String text) {
        return text != null && text.length() > MAX_DISPLAY_CHARS;
    }

    /** Corta para exibicao na celula, com reticencias quando necessario. */
    static String forDisplay(String text) {
        if (!isTruncated(text)) {
            return text;
        }
        return text.substring(0, MAX_DISPLAY_CHARS) + "…";
    }

    /** Corta para o calculo de largura do AutoFit (mais curto — so precisa ser "largo o bastante"). */
    static String forWidthMeasurement(String text) {
        if (text == null || text.length() <= MAX_MEASURED_CHARS) {
            return text;
        }
        return text.substring(0, MAX_MEASURED_CHARS);
    }
}
