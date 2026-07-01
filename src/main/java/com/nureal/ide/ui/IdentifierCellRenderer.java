package com.nureal.ide.ui;

import java.awt.Color;

import javax.swing.SwingConstants;

/**
 * Colunas identificadoras (id, *_id, uuid/guid): laranja forte.
 *
 * Alinhamento depende do FORMATO do valor, nao so do grupo: um ID numerico
 * autoincrementado (1, 2, 42...) alinha a direita, como qualquer numero —
 * mas um UUID/GUID (texto opaco de 32+ caracteres) alinha a ESQUERDA, porque
 * nao e um numero comparavel e right-align so confundiria a leitura.
 */
final class IdentifierCellRenderer extends AbstractTypedCellRenderer {

    private static final long serialVersionUID = 1L;

    /** Textos com esta quantidade de caracteres ou mais sao tratados como UUID/hash opaco, nao numero. */
    private static final int OPAQUE_ID_MIN_LENGTH = 20;

    @Override
    int alignment(Object value) {
        return isOpaqueIdentifier(value) ? SwingConstants.LEFT : SwingConstants.RIGHT;
    }

    @Override
    Color colorFor(Object value) {
        return GridTheme.COLOR_IDENTIFIER;
    }

    private static boolean isOpaqueIdentifier(Object value) {
        if (value instanceof java.util.UUID) {
            return true;
        }
        return value instanceof String s && s.length() >= OPAQUE_ID_MIN_LENGTH;
    }
}
