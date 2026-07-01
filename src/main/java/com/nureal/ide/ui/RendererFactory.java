package com.nureal.ide.ui;

import java.time.temporal.Temporal;
import java.util.Locale;

import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

/**
 * Escolhe e instala o {@link TableCellRenderer} de cada coluna da grade de
 * resultados, classificando-a em um dos 6 grupos coloridos (pelo NOME DO
 * TIPO SQL real — {@link ResultTableModel#sqlType(int)} — com fallback pela
 * classe Java quando o tipo nao e conhecido, mais uma heuristica de NOME
 * para identificadores):
 *
 * 1) IDENTIFICADORES (id, *_id, uuid/guid) -&gt; {@link IdentifierCellRenderer}
 * 2) NUMERICOS (INT/DECIMAL/FLOAT/...)     -&gt; {@link NumberCellRenderer}
 * 3) TEMPORAIS (DATE/TIME/TIMESTAMP/...)   -&gt; {@link TemporalCellRenderer}
 * 4) LOGICOS/STATUS (BOOLEAN/BIT/"status") -&gt; {@link BooleanCellRenderer}
 * 5) BINARIOS/COMPLEXOS (BLOB/JSON/XML/...) -&gt; {@link BinaryCellRenderer}
 * 6) TEXTUAIS (VARCHAR/TEXT/...) — tambem o DEFAULT -&gt; {@link TextCellRenderer}
 *
 * A classificacao acontece UMA VEZ POR COLUNA, quando a grade e montada
 * ({@link #installOn}) — nao a cada celula pintada — e os renderers em si
 * sao instancias UNICAS e sem estado (compartilhadas por todas as colunas do
 * mesmo grupo, em todas as grades da aplicacao), nunca recriadas.
 */
final class RendererFactory {

    private static final IdentifierCellRenderer IDENTIFIER = new IdentifierCellRenderer();
    private static final NumberCellRenderer NUMBER = new NumberCellRenderer();
    private static final TemporalCellRenderer TEMPORAL = new TemporalCellRenderer();
    private static final BooleanCellRenderer LOGICAL = new BooleanCellRenderer();
    private static final BinaryCellRenderer BINARY = new BinaryCellRenderer();
    private static final TextCellRenderer TEXTUAL = new TextCellRenderer();

    private static final String[] TEMPORAL_PREFIXES =
            {"TIMESTAMPTZ", "TIMESTAMP", "DATETIME", "DATE", "TIME", "YEAR"};
    private static final String[] BINARY_PREFIXES =
            {"LONGBLOB", "MEDIUMBLOB", "TINYBLOB", "BLOB", "CLOB", "NCLOB", "BYTEA",
                    "JSONB", "JSON", "XML", "VARBINARY", "BINARY", "GEOMETRY"};
    private static final String[] LOGICAL_PREFIXES = {"BOOLEAN", "BOOL", "BIT"};
    private static final String[] NUMERIC_PREFIXES =
            {"INT", "INTEGER", "BIGINT", "SMALLINT", "TINYINT", "MEDIUMINT", "FLOAT", "DOUBLE",
                    "DECIMAL", "NUMERIC", "REAL", "DEC", "FIXED", "NUMBER", "MONEY", "SMALLMONEY"};

    private RendererFactory() {
    }

    enum Group { IDENTIFIER, NUMERIC, TEMPORAL, LOGICAL, BINARY, TEXTUAL }

    /** Classifica e instala o renderer de cada coluna de {@code table}. */
    static void installOn(javax.swing.JTable table, ResultTableModel model) {
        for (int c = 0; c < table.getColumnModel().getColumnCount(); c++) {
            TableColumn column = table.getColumnModel().getColumn(c);
            int modelColumn = column.getModelIndex();
            Group group = classify(model.sqlType(modelColumn), model.getColumnClass(modelColumn),
                    model.getColumnName(modelColumn));
            column.setCellRenderer(rendererFor(group));
        }
    }

    static TableCellRenderer rendererFor(Group group) {
        return switch (group) {
            case IDENTIFIER -> IDENTIFIER;
            case NUMERIC -> NUMBER;
            case TEMPORAL -> TEMPORAL;
            case LOGICAL -> LOGICAL;
            case BINARY -> BINARY;
            case TEXTUAL -> TEXTUAL;
        };
    }

    /**
     * Classifica a coluna em um dos 6 grupos. Prioridade: tipos
     * temporais/binarios/logicos pelo NOME DO TIPO SQL primeiro
     * (inequivocos); depois o nome da coluna para identificadores (id/_id/
     * uuid/guid — vale mesmo para colunas numericas OU texto, ja que UUID
     * costuma vir como CHAR/VARCHAR); por fim numericos pelo tipo SQL, com
     * fallback pela classe Java quando o tipo SQL nao estiver disponivel.
     */
    static Group classify(String sqlType, Class<?> cls, String columnName) {
        String type = normalizeType(sqlType);
        String name = columnName == null ? "" : columnName.toLowerCase(Locale.ROOT);

        if (!type.isEmpty()) {
            if (startsWithAny(type, TEMPORAL_PREFIXES)) {
                return Group.TEMPORAL;
            }
            if (startsWithAny(type, BINARY_PREFIXES)) {
                return Group.BINARY;
            }
            if (startsWithAny(type, LOGICAL_PREFIXES)) {
                return Group.LOGICAL;
            }
        }
        if (name.contains("status")) {
            return Group.LOGICAL;
        }
        if (isIdentifierName(name)) {
            return Group.IDENTIFIER;
        }
        if (!type.isEmpty() && startsWithAny(type, NUMERIC_PREFIXES)) {
            return Group.NUMERIC;
        }
        // Fallback pela classe Java quando o nome do tipo SQL nao ajudou.
        if (Number.class.isAssignableFrom(cls)) {
            return Group.NUMERIC;
        }
        if (isTemporalClass(cls)) {
            return Group.TEMPORAL;
        }
        if (cls == Boolean.class) {
            return Group.LOGICAL;
        }
        return Group.TEXTUAL;
    }

    private static boolean isIdentifierName(String lowerCaseName) {
        return lowerCaseName.equals("id") || lowerCaseName.endsWith("_id")
                || lowerCaseName.contains("uuid") || lowerCaseName.contains("guid");
    }

    private static boolean isTemporalClass(Class<?> cls) {
        return java.util.Date.class.isAssignableFrom(cls) || Temporal.class.isAssignableFrom(cls);
    }

    /** Tipo SQL em maiusculas, sem sufixo de precisao/escala (ex.: "DECIMAL(10,2)" -&gt; "DECIMAL"). */
    private static String normalizeType(String sqlType) {
        if (sqlType == null) {
            return "";
        }
        String t = sqlType.toUpperCase(Locale.ROOT).trim();
        int paren = t.indexOf('(');
        return (paren > 0) ? t.substring(0, paren).trim() : t;
    }

    private static boolean startsWithAny(String type, String[] prefixes) {
        for (String prefix : prefixes) {
            if (type.equals(prefix) || type.startsWith(prefix + " ")) {
                return true;
            }
        }
        return false;
    }
}
