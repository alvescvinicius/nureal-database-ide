package com.nureal.ide.ui;

import java.awt.Color;

import javax.swing.SwingConstants;

/** Colunas numericas (INT/DECIMAL/FLOAT/...): turquesa, alinhado a direita. */
final class NumberCellRenderer extends AbstractTypedCellRenderer {

    private static final long serialVersionUID = 1L;

    @Override
    int alignment(Object value) {
        return SwingConstants.RIGHT;
    }

    @Override
    Color colorFor(Object value) {
        return GridTheme.COLOR_NUMERIC;
    }
}
