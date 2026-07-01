package com.nureal.ide.ui;

import javax.swing.JTable;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Exportacao de CSV e JSON da grade de resultados — sem nenhuma dependencia
 * externa nova (Excel ja tem seu proprio exportador em
 * {@code core.export.ExcelExporter}; aqui e so texto puro).
 *
 * Sempre le atraves do {@link JTable} (indices de VIEW), portanto respeita a
 * ordenacao e o filtro aplicados no momento — o que o usuario ve na tela e
 * exatamente o que sai no arquivo/clipboard.
 */
final class GridExporter {

    private GridExporter() {
    }

    /** Exporta TODAS as linhas/colunas visiveis (view) para um arquivo CSV. */
    static void exportCsv(JTable table, Path file) throws IOException {
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write(toCsv(table, allRows(table), allColumns(table), true));
        }
    }

    /** Exporta TODAS as linhas/colunas visiveis (view) para um arquivo JSON (array de objetos). */
    static void exportJson(JTable table, Path file) throws IOException {
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write(toJson(table, allRows(table), allColumns(table)));
        }
    }

    /**
     * Texto separado por TAB (sem nenhum escape — e o formato que a area de
     * transferencia do Windows/Excel entende para colar em celulas) das
     * linhas/colunas informadas (indices de VIEW). Usado pelas copias
     * "linha"/"selecao com cabecalho" — deliberadamente SEPARADO de
     * {@link #toCsv}, que usa virgula e aspas: substituir virgulas por TAB
     * num CSV ja escapado corromperia valores que contenham virgula.
     */
    static String toTabSeparated(JTable table, int[] rows, int[] cols, boolean withHeader) {
        StringBuilder sb = new StringBuilder();
        if (withHeader) {
            for (int c = 0; c < cols.length; c++) {
                if (c > 0) {
                    sb.append('\t');
                }
                sb.append(table.getColumnName(cols[c]));
            }
            sb.append('\n');
        }
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < cols.length; c++) {
                if (c > 0) {
                    sb.append('\t');
                }
                Object v = table.getValueAt(rows[r], cols[c]);
                sb.append(v == null ? "" : v.toString());
            }
            if (r < rows.length - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /** CSV (separador virgula, aspas quando necessario) das linhas/colunas informadas (indices de VIEW). */
    static String toCsv(JTable table, int[] rows, int[] cols, boolean withHeader) {
        StringBuilder sb = new StringBuilder();
        if (withHeader) {
            for (int c = 0; c < cols.length; c++) {
                if (c > 0) {
                    sb.append(',');
                }
                sb.append(csvEscape(table.getColumnName(cols[c])));
            }
            sb.append('\n');
        }
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < cols.length; c++) {
                if (c > 0) {
                    sb.append(',');
                }
                Object v = table.getValueAt(rows[r], cols[c]);
                sb.append(csvEscape(v == null ? "" : v.toString()));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** JSON (array de objetos {@code {"coluna": valor}}) das linhas/colunas informadas (indices de VIEW). */
    static String toJson(JTable table, int[] rows, int[] cols) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int r = 0; r < rows.length; r++) {
            sb.append("  {");
            for (int c = 0; c < cols.length; c++) {
                if (c > 0) {
                    sb.append(", ");
                }
                sb.append('"').append(jsonEscape(table.getColumnName(cols[c]))).append("\": ");
                sb.append(jsonValue(table.getValueAt(rows[r], cols[c])));
            }
            sb.append('}');
            sb.append(r < rows.length - 1 ? ",\n" : "\n");
        }
        sb.append(']');
        return sb.toString();
    }

    private static int[] allRows(JTable table) {
        int[] rows = new int[table.getRowCount()];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = i;
        }
        return rows;
    }

    private static int[] allColumns(JTable table) {
        int[] cols = new int[table.getColumnCount()];
        for (int i = 0; i < cols.length; i++) {
            cols[i] = i;
        }
        return cols;
    }

    private static String csvEscape(String s) {
        boolean needsQuotes = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        String escaped = s.replace("\"", "\"\"");
        return needsQuotes ? "\"" + escaped + "\"" : escaped;
    }

    private static String jsonValue(Object raw) {
        if (raw == null) {
            return "null";
        }
        if (raw instanceof Number || raw instanceof Boolean) {
            return raw.toString();
        }
        return '"' + jsonEscape(raw.toString()) + '"';
    }

    private static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(ch);
            }
        }
        return sb.toString();
    }
}
