package com.nureal.ide.ui;

import java.awt.Color;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.swing.SwingConstants;

/** Colunas temporais (DATE/TIME/TIMESTAMP/...): roxo vibrante, centralizado. */
final class TemporalCellRenderer extends AbstractTypedCellRenderer {

    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Override
    int alignment(Object value) {
        return SwingConstants.CENTER;
    }

    @Override
    Color colorFor(Object value) {
        return GridTheme.COLOR_TEMPORAL;
    }

    @Override
    String formatValue(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime().format(DATETIME_FMT);
        }
        if (value instanceof Date d) {
            return d.toLocalDate().format(DATE_FMT);
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt.format(DATETIME_FMT);
        }
        if (value instanceof LocalDate ld) {
            return ld.format(DATE_FMT);
        }
        return value.toString();
    }
}
