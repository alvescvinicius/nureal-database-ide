package com.nureal.ide.core.json;

import java.util.List;
import java.util.Map;

/**
 * Escritor de JSON minimo, contraparte do {@link JsonParser} (que so le).
 * Existe para montar o corpo das requisicoes HTTP ao Ollama (ver
 * {@code modulos.iachat.infraestrutura.provider.OllamaProvider}) sem adicionar uma dependencia
 * nova ao projeto (Jackson/Gson) — mesma filosofia do {@code JsonParser}.
 *
 * Aceita apenas os tipos que {@link JsonParser#parse} devolve: {@code Map},
 * {@code List}, {@code String}, {@code Number}, {@code Boolean} e
 * {@code null}. Nao e um serializador de objetos Java de proposito geral.
 */
public final class JsonWriter {

    private JsonWriter() {
    }

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(sb, s);
        } else if (value instanceof Boolean b) {
            sb.append(b.booleanValue());
        } else if (value instanceof Number n) {
            writeNumber(sb, n);
        } else if (value instanceof Map<?, ?> map) {
            writeObject(sb, map);
        } else if (value instanceof List<?> list) {
            writeArray(sb, list);
        } else {
            throw new IllegalArgumentException("Tipo nao suportado para escrita em JSON: " + value.getClass());
        }
    }

    private static void writeObject(StringBuilder sb, Map<?, ?> map) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeString(sb, String.valueOf(entry.getKey()));
            sb.append(':');
            writeValue(sb, entry.getValue());
        }
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, List<?> list) {
        sb.append('[');
        boolean first = true;
        for (Object item : list) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeValue(sb, item);
        }
        sb.append(']');
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static void writeNumber(StringBuilder sb, Number n) {
        if (n instanceof Double || n instanceof Float) {
            double d = n.doubleValue();
            if (!Double.isFinite(d)) {
                throw new IllegalArgumentException("Numero nao finito nao pode ser serializado em JSON: " + d);
            }
            if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                sb.append((long) d);
            } else {
                sb.append(d);
            }
        } else {
            sb.append(n);
        }
    }
}
