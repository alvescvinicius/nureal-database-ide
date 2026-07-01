package com.nureal.ide.ui;

import java.awt.Color;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.SQLException;

import javax.swing.SwingConstants;

/**
 * Colunas binarias/complexas (BLOB/JSON/XML/...): rosa choque, alinhado a
 * esquerda.
 *
 * BLOB e CLOB nao tem um {@code toString()} util (um {@code byte[]} imprime
 * algo como "[B@1a2b3c", e um driver de CLOB costuma imprimir o nome interno
 * da classe) — aqui eles ganham uma representacao legivel (tamanho em
 * bytes/KB para BLOB, previa de texto para CLOB). JSON/XML normalmente ja
 * chegam como String comum do driver, entao usam o texto direto (truncado
 * pela base, ver {@link CellText}).
 */
final class BinaryCellRenderer extends AbstractTypedCellRenderer {

    private static final long serialVersionUID = 1L;

    @Override
    int alignment(Object value) {
        return SwingConstants.LEFT;
    }

    @Override
    Color colorFor(Object value) {
        return GridTheme.COLOR_BINARY;
    }

    @Override
    String formatValue(Object value) {
        if (value instanceof byte[] bytes) {
            return "BLOB (" + humanSize(bytes.length) + ")";
        }
        if (value instanceof Blob blob) {
            return formatBlob(blob);
        }
        if (value instanceof Clob clob) {
            return formatClob(clob);
        }
        return value.toString();
    }

    private static String formatBlob(Blob blob) {
        try {
            return "BLOB (" + humanSize(blob.length()) + ")";
        } catch (SQLException ex) {
            return "BLOB";
        }
    }

    private static String formatClob(Clob clob) {
        try {
            long length = clob.length();
            // Busca 1 caractere a mais que o limite de exibicao: se o CLOB for
            // maior, isso garante que CellText.forDisplay() detecte e corte
            // com reticencias — sem precisar ler o CLOB inteiro do banco.
            int fetchLen = (int) Math.min(length, CellText.MAX_DISPLAY_CHARS + 1L);
            return clob.getSubString(1, fetchLen);
        } catch (SQLException ex) {
            return "CLOB";
        }
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " bytes";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
