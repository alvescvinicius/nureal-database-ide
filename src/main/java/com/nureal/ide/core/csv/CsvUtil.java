package com.nureal.ide.core.csv;

import java.util.ArrayList;
import java.util.List;

/**
 * Leitura/escrita minima de CSV (RFC4180-ish): aspas duplas delimitam um
 * campo com o proprio delimitador ou aspas dentro dele; {@code ""} dentro de
 * um campo entre aspas escapa uma aspa literal.
 * <p>
 * LIMITACAO ACEITA conscientemente: {@link #parseLine} trabalha LINHA A
 * LINHA — um campo entre aspas que contenha uma quebra de linha de verdade
 * (bem incomum, mas valido em CSV "de verdade") nao e suportado; um parser
 * completo precisaria ser um STREAM sobre o arquivo inteiro, nao uma funcao
 * por linha. Suficiente para o caso de uso real desta IDE (importar/exportar
 * dados tabulares de teste/planilha, texto sem quebras de linha internas).
 */
public final class CsvUtil {

    private CsvUtil() {
    }

    /** Separa uma linha de CSV em campos, respeitando aspas duplas. */
    public static List<String> parseLine(String line, char delimiter) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++; // pula a segunda aspa do par de escape
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == delimiter) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    /** Envolve {@code value} em aspas duplas SE necessario (contem delimitador, aspas ou quebra de linha); escapa aspas internas. */
    public static String escapeField(String value, char delimiter) {
        if (value == null) {
            return "";
        }
        boolean needsQuoting = value.indexOf(delimiter) >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        if (!needsQuoting) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /** Monta uma linha de CSV completa a partir dos valores (ja escapando cada campo), terminada em {@code \r\n} (padrao CSV). */
    public static String joinLine(List<String> values, char delimiter) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(delimiter);
            }
            sb.append(escapeField(values.get(i), delimiter));
        }
        sb.append("\r\n");
        return sb.toString();
    }
}
