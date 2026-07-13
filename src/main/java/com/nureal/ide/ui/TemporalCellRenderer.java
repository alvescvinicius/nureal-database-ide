package com.nureal.ide.ui;

import java.awt.Color;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

import javax.swing.SwingConstants;

/**
 * Colunas temporais, centralizadas — uma instancia para cada categoria do
 * "Sistema Semantico de Cores por Tipo de Dado" (ver DESIGN_SYSTEM.md):
 * {@link RendererFactory.Group#DATE} (laranja), {@link RendererFactory.Group#TIME}
 * (amarelo) e {@link RendererFactory.Group#DATETIME} (laranja intenso) — cada
 * uma com sua PROPRIA formatacao (so data, so hora, ou os dois), decidida
 * pelo {@code kind} passado no construtor (o tipo SQL REAL da coluna, nao o
 * valor) em vez de adivinhar pela classe Java do valor recebido.
 */
final class TemporalCellRenderer extends AbstractTypedCellRenderer {

    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    // Duas variantes: COM milissegundos (para nao esconder valores que so
    // diferem nesses digitos, ex.: linhas geradas em lote no mesmo segundo) e
    // SEM (quando o milissegundo e exatamente zero, mostrar ".000" em toda
    // linha e so poluicao visual sem informacao nova — pedido explicito do
    // usuario). {@link #formatDateTime} escolhe qual usar por LINHA, olhando
    // o nanossegundo real do valor, nunca por coluna inteira.
    private static final DateTimeFormatter DATETIME_FMT_MS = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss.SSS");
    private static final DateTimeFormatter DATETIME_FMT_NO_MS = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /** Qual formatacao usar — vem do TIPO SQL real da coluna (ver RendererFactory), nao do valor. */
    enum Kind { DATE, TIME, DATETIME }

    private final Supplier<Color> colorSupplier;
    private final Kind kind;

    TemporalCellRenderer(Supplier<Color> colorSupplier, Kind kind) {
        this.colorSupplier = colorSupplier;
        this.kind = kind;
    }

    @Override
    int alignment(Object value) {
        return SwingConstants.CENTER;
    }

    @Override
    Color colorFor(Object value) {
        return colorSupplier.get();
    }

    @Override
    String formatValue(Object value) {
        return switch (kind) {
            case DATE -> formatDate(value);
            case TIME -> formatTime(value);
            case DATETIME -> formatDateTime(value);
        };
    }

    private static String formatDate(Object value) {
        if (value instanceof LocalDate ld) {
            return ld.format(DATE_FMT);
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate().format(DATE_FMT);
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt.toLocalDate().format(DATE_FMT);
        }
        return value.toString();
    }

    private static String formatTime(Object value) {
        if (value instanceof LocalTime lt) {
            return lt.format(TIME_FMT);
        }
        if (value instanceof Time t) {
            return t.toLocalTime().format(TIME_FMT);
        }
        return value.toString();
    }

    private static String formatDateTime(Object value) {
        LocalDateTime ldt = null;
        if (value instanceof Timestamp ts) {
            ldt = ts.toLocalDateTime();
        } else if (value instanceof LocalDateTime v) {
            ldt = v;
        }
        if (ldt != null) {
            // getNano() == 0 cobre tanto um valor sem fracao de segundo no
            // banco quanto um com fracao mas exatamente ".000" — nos dois
            // casos nao ha milissegundo diferente de zero pra mostrar.
            return ldt.format(ldt.getNano() == 0 ? DATETIME_FMT_NO_MS : DATETIME_FMT_MS);
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate().format(DATE_FMT);
        }
        if (value instanceof LocalDate ld) {
            return ld.format(DATE_FMT);
        }
        return value.toString();
    }
}
