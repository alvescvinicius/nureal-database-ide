package com.nureal.ide.core.sql;

import java.util.Locale;

/**
 * Classificacao UNICA de um tipo SQL numa "categoria semantica" de dado —
 * fonte unica de verdade para QUAL categoria um tipo pertence (INTEIRO,
 * DECIMAL, TEXTO, ENUM, DATA, HORA, DATA/HORA, BOOLEANO, UUID, JSON, XML,
 * BINARIO), reaproveitada por todo componente que precisa colorir um tipo de
 * dado de forma consistente em toda a aplicacao: editor SQL (syntax
 * highlight), grade de resultados, autocomplete, tooltips/quick info,
 * visualizador de celula, DDL e metadados — ver DESIGN_SYSTEM.md, secao
 * "Sistema semantico de cores por tipo de dado".
 *
 * Pedido explicito do usuario: "um mesmo tipo de dado seja representado
 * SEMPRE pela mesma cor, independentemente de onde ele esteja sendo
 * exibido... nenhum componente podera definir cores proprias para tipos de
 * dados". Esta classe decide a CATEGORIA (o "o que e"); a COR de cada
 * categoria e responsabilidade unica de {@code com.nureal.ide.ui.GridTheme}
 * (camada UI) — este pacote ({@code core}) nao depende de Swing/AWT de
 * proposito (mesma razao documentada em {@code SqlCompletionProvider}), para
 * poder ser usado tanto pela grade quanto pelo autocomplete sem acoplar um ao
 * outro.
 *
 * A classificacao e SEMPRE pelo TIPO REAL declarado no banco — nunca pela
 * aparencia do valor (ex.: uma coluna VARCHAR contendo so digitos continua
 * {@link #TEXTUAL}; um INT com valores 0/1 continua {@link #INTEGER}, nao
 * {@link #BOOLEAN}).
 */
public enum SqlTypeKind {
    INTEGER, DECIMAL, DATE, TIME, DATETIME, BOOLEAN, UUID, JSON, XML, BINARY, ENUM, TEXTUAL;

    private static final String[] ENUM_TYPES = {"ENUM", "SET"};
    private static final String[] DATE_TYPES = {"DATE"};
    private static final String[] TIME_TYPES = {"TIME"};
    private static final String[] DATETIME_TYPES = {"DATETIME", "TIMESTAMP", "TIMESTAMPTZ", "YEAR"};
    private static final String[] JSON_TYPES = {"JSON", "JSONB"};
    private static final String[] XML_TYPES = {"XML"};
    private static final String[] BINARY_TYPES =
            {"LONGBLOB", "MEDIUMBLOB", "TINYBLOB", "BLOB", "CLOB", "NCLOB", "BYTEA",
                    "VARBINARY", "BINARY", "GEOMETRY"};
    private static final String[] BOOLEAN_TYPES = {"BOOLEAN", "BOOL", "BIT"};
    private static final String[] UUID_TYPES = {"UUID", "UNIQUEIDENTIFIER"};
    private static final String[] INTEGER_TYPES =
            {"TINYINT", "SMALLINT", "MEDIUMINT", "INT", "INTEGER", "BIGINT"};
    private static final String[] DECIMAL_TYPES =
            {"DECIMAL", "NUMERIC", "FLOAT", "DOUBLE", "REAL", "DEC", "FIXED", "NUMBER", "MONEY", "SMALLMONEY"};

    /**
     * Categoria do tipo SQL informado (ex.: {@code "DECIMAL(10,2)"},
     * {@code "varchar(255)"}, {@code "int"}) — {@link #TEXTUAL} e o padrao de
     * qualquer tipo vazio/nao reconhecido (mesma logica "textual e o grupo
     * default" que a grade ja usava antes desta classe existir).
     */
    public static SqlTypeKind classify(String sqlType) {
        String type = normalize(sqlType);
        if (type.isEmpty()) {
            return TEXTUAL;
        }
        if (startsWithAny(type, ENUM_TYPES)) {
            return ENUM;
        }
        if (startsWithAny(type, DATE_TYPES)) {
            return DATE;
        }
        if (startsWithAny(type, TIME_TYPES)) {
            return TIME;
        }
        if (startsWithAny(type, DATETIME_TYPES)) {
            return DATETIME;
        }
        if (startsWithAny(type, JSON_TYPES)) {
            return JSON;
        }
        if (startsWithAny(type, XML_TYPES)) {
            return XML;
        }
        if (startsWithAny(type, BINARY_TYPES)) {
            return BINARY;
        }
        if (startsWithAny(type, BOOLEAN_TYPES)) {
            return BOOLEAN;
        }
        if (startsWithAny(type, UUID_TYPES)) {
            return UUID;
        }
        if (startsWithAny(type, INTEGER_TYPES)) {
            return INTEGER;
        }
        if (startsWithAny(type, DECIMAL_TYPES)) {
            return DECIMAL;
        }
        return TEXTUAL;
    }

    /** Tipo SQL em maiusculas, sem sufixo de precisao/escala (ex.: "DECIMAL(10,2)" -&gt; "DECIMAL"). */
    private static String normalize(String sqlType) {
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
