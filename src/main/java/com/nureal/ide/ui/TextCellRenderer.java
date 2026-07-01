package com.nureal.ide.ui;

import java.awt.Color;

import javax.swing.SwingConstants;

/** Colunas textuais (VARCHAR/TEXT/...) — tambem o grupo padrao: grafite escuro, esquerda. */
final class TextCellRenderer extends AbstractTypedCellRenderer {

    private static final long serialVersionUID = 1L;

    @Override
    int alignment(Object value) {
        return SwingConstants.LEFT;
    }

    @Override
    Color colorFor(Object value) {
        return GridTheme.COLOR_TEXTUAL;
    }
}
