package com.nureal.ide.ui;

import java.awt.Color;
import java.util.Locale;

import javax.swing.SwingConstants;

/**
 * Colunas logicas/status (BOOLEAN/BIT ou coluna chamada "status"): verde para
 * valores "verdadeiros"/ativos, vermelho para "falsos"/inativos, centralizado.
 * Valores de status nao-binarios (ex.: "PENDENTE") mantem a cor padrao do
 * tema — so o texto muda, o layout (centralizado) e sempre aplicado.
 */
final class BooleanCellRenderer extends AbstractTypedCellRenderer {

    private static final long serialVersionUID = 1L;

    @Override
    int alignment(Object value) {
        return SwingConstants.CENTER;
    }

    @Override
    Color colorFor(Object value) {
        String v = value.toString().trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "TRUE", "ATIVO", "SIM", "YES", "Y", "1" -> GridTheme.COLOR_LOGIC_TRUE;
            case "FALSE", "INATIVO", "NAO", "NO", "N", "0" -> GridTheme.COLOR_LOGIC_FALSE;
            default -> null; // valor de status nao-binario: cor padrao do tema
        };
    }
}
