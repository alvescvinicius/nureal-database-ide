package com.nureal.ide.ui;

import javax.swing.RowFilter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Filtro de texto "inteligente" usado pela barra de filtro da grade de
 * resultados: entende operadores de comparacao, intervalos, prefixo/sufixo e
 * NULL/NOT NULL, comparando como DATA ou NUMERO quando possivel (mesmo em
 * colunas de texto), com "contem" (sem diferenciar caixa) como padrao.
 *
 * Extraido de {@code MainWindow} para isolar a regra de negocio do filtro da
 * montagem da interface — reutilizado tanto pela barra de filtro quanto pelo
 * item "Filtrar por este valor" do menu de contexto.
 */
final class SmartCellFilter {

    private SmartCellFilter() {
    }

    /** Monta um {@link RowFilter} para a coluna (indice de MODELO, -1 = todas) com o texto informado. */
    static RowFilter<Object, Object> build(String text, int modelColumn) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            return null;
        }
        Predicate<String> predicate = buildPredicate(t);
        return new RowFilter<>() {
            @Override
            public boolean include(Entry<?, ?> entry) {
                if (modelColumn < 0) {
                    for (int c = 0; c < entry.getValueCount(); c++) {
                        if (predicate.test(entry.getStringValue(c))) {
                            return true;
                        }
                    }
                    return false;
                }
                if (modelColumn >= entry.getValueCount()) {
                    return false;
                }
                return predicate.test(entry.getStringValue(modelColumn));
            }
        };
    }

    /** Constroi o predicado de cada celula a partir do texto digitado no filtro. */
    static Predicate<String> buildPredicate(String raw) {
        String text = raw.trim();

        if (text.equalsIgnoreCase("NULL")) {
            return cell -> cell == null || cell.isEmpty();
        }
        if (text.equalsIgnoreCase("NOT NULL")) {
            return cell -> cell != null && !cell.isEmpty();
        }
        int range = text.indexOf("..");
        if (range > 0 && range < text.length() - 2) {
            String a = text.substring(0, range).trim();
            String b = text.substring(range + 2).trim();
            return cell -> compareCells(cell, a) >= 0 && compareCells(cell, b) <= 0;
        }
        for (String op : new String[] {">=", "<=", "<>", "!=", ">", "<", "="}) {
            if (text.startsWith(op)) {
                String val = text.substring(op.length()).trim();
                return cell -> applyOperator(op, compareCells(cell, val));
            }
        }
        if (text.startsWith("^")) {
            String prefix = text.substring(1).toLowerCase(Locale.ROOT);
            return cell -> cell.toLowerCase(Locale.ROOT).startsWith(prefix);
        }
        if (text.endsWith("$") && text.length() > 1) {
            String suffix = text.substring(0, text.length() - 1).toLowerCase(Locale.ROOT);
            return cell -> cell.toLowerCase(Locale.ROOT).endsWith(suffix);
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return cell -> cell.toLowerCase(Locale.ROOT).contains(lower);
    }

    /** Compara celula x valor: tenta data, depois numero, senao texto (caixa-insensivel). */
    private static int compareCells(String cell, String value) {
        LocalDateTime dc = parseDate(cell);
        LocalDateTime dv = parseDate(value);
        if (dc != null && dv != null) {
            return dc.compareTo(dv);
        }
        Double nc = parseNumber(cell);
        Double nv = parseNumber(value);
        if (nc != null && nv != null) {
            return Double.compare(nc, nv);
        }
        return cell.compareToIgnoreCase(value);
    }

    private static boolean applyOperator(String op, int cmp) {
        return switch (op) {
            case ">=" -> cmp >= 0;
            case "<=" -> cmp <= 0;
            case ">" -> cmp > 0;
            case "<" -> cmp < 0;
            case "=" -> cmp == 0;
            case "<>", "!=" -> cmp != 0;
            default -> true;
        };
    }

    private static final DateTimeFormatter[] DATETIME_FMTS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
    };
    private static final DateTimeFormatter[] DATE_FMTS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
    };

    /** Interpreta data/hora ou data; retorna null se nao for data. */
    private static LocalDateTime parseDate(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().replaceAll("\\.\\d+$", ""); // remove fracao de segundos
        if (s.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter f : DATETIME_FMTS) {
            try {
                return LocalDateTime.parse(s, f);
            } catch (RuntimeException ignore) {
                // tenta o proximo
            }
        }
        for (DateTimeFormatter f : DATE_FMTS) {
            try {
                return LocalDate.parse(s, f).atStartOfDay();
            } catch (RuntimeException ignore) {
                // tenta o proximo
            }
        }
        return null;
    }

    /** Interpreta numero (aceita 1234.56 e 1.234,56); null se nao for numero. */
    private static Double parseNumber(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(s);
        } catch (NumberFormatException ignore) {
            // tenta formato BR
        }
        try {
            return Double.valueOf(s.replace(".", "").replace(",", "."));
        } catch (NumberFormatException ignore) {
            return null;
        }
    }
}
