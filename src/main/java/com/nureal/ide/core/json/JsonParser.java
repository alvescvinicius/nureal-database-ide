package com.nureal.ide.core.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser de JSON minimo, so leitura (sem escrita — ja existe um escritor
 * separado em {@code ui.GridExporter#toJson}, que nunca precisou ler JSON de
 * volta). Existe para decodificar {@code EXPLAIN FORMAT=JSON} (ver
 * {@code ExplainDialog}) sem adicionar uma dependencia nova ao projeto
 * (Jackson/Gson) so para um unico consumidor — mesma filosofia ja aplicada a
 * {@code CsvUtil} (CSV proprio, sem biblioteca externa).
 *
 * Suporta o subconjunto de JSON que o MySQL de fato produz: objetos, arrays,
 * strings (com escapes padrao), numeros, {@code true}/{@code false}/
 * {@code null}. NAO valida agressivamente entradas malformadas (nao e um
 * parser de proposito geral) — lanca {@link JsonParseException} em erros
 * obvios, mas alguns JSONs invalidos "quase certos" podem passar sem erro;
 * suficiente porque a unica fonte real de entrada e a saida bem-formada do
 * proprio servidor MySQL.
 */
public final class JsonParser {

    private JsonParser() {
    }

    /** Erro ao decodificar JSON — mensagem inclui a posicao aproximada (indice de caractere) do problema. */
    public static final class JsonParseException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        JsonParseException(String message) {
            super(message);
        }
    }

    /**
     * Decodifica um texto JSON completo. Devolve, dependendo do valor
     * raiz: {@code LinkedHashMap<String,Object>} (objeto, ordem de insercao
     * preservada), {@code List<Object>} (array), {@code String}, {@code Double},
     * {@code Boolean} ou {@code null}.
     */
    public static Object parse(String text) {
        Cursor c = new Cursor(text);
        c.skipWhitespace();
        Object value = parseValue(c);
        c.skipWhitespace();
        if (!c.atEnd()) {
            throw new JsonParseException("Texto sobrando apos o JSON, na posicao " + c.pos);
        }
        return value;
    }

    private static final class Cursor {
        final String text;
        int pos;

        Cursor(String text) {
            this.text = text;
        }

        boolean atEnd() {
            return pos >= text.length();
        }

        char peek() {
            return text.charAt(pos);
        }

        char next() {
            return text.charAt(pos++);
        }

        void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(peek())) {
                pos++;
            }
        }

        void expect(char ch) {
            if (atEnd() || peek() != ch) {
                throw new JsonParseException("Esperava '" + ch + "' na posicao " + pos);
            }
            pos++;
        }
    }

    private static Object parseValue(Cursor c) {
        if (c.atEnd()) {
            throw new JsonParseException("JSON incompleto na posicao " + c.pos);
        }
        char ch = c.peek();
        return switch (ch) {
            case '{' -> parseObject(c);
            case '[' -> parseArray(c);
            case '"' -> parseString(c);
            case 't', 'f' -> parseBoolean(c);
            case 'n' -> parseNull(c);
            default -> parseNumber(c);
        };
    }

    private static Map<String, Object> parseObject(Cursor c) {
        Map<String, Object> map = new LinkedHashMap<>();
        c.expect('{');
        c.skipWhitespace();
        if (!c.atEnd() && c.peek() == '}') {
            c.next();
            return map;
        }
        while (true) {
            c.skipWhitespace();
            String key = parseString(c);
            c.skipWhitespace();
            c.expect(':');
            c.skipWhitespace();
            Object value = parseValue(c);
            map.put(key, value);
            c.skipWhitespace();
            char sep = c.next();
            if (sep == '}') {
                break;
            }
            if (sep != ',') {
                throw new JsonParseException("Esperava ',' ou '}' na posicao " + (c.pos - 1));
            }
        }
        return map;
    }

    private static List<Object> parseArray(Cursor c) {
        List<Object> list = new ArrayList<>();
        c.expect('[');
        c.skipWhitespace();
        if (!c.atEnd() && c.peek() == ']') {
            c.next();
            return list;
        }
        while (true) {
            c.skipWhitespace();
            list.add(parseValue(c));
            c.skipWhitespace();
            char sep = c.next();
            if (sep == ']') {
                break;
            }
            if (sep != ',') {
                throw new JsonParseException("Esperava ',' ou ']' na posicao " + (c.pos - 1));
            }
        }
        return list;
    }

    private static String parseString(Cursor c) {
        c.expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (c.atEnd()) {
                throw new JsonParseException("String JSON nao terminada");
            }
            char ch = c.next();
            if (ch == '"') {
                break;
            }
            if (ch == '\\') {
                char esc = c.next();
                switch (esc) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        String hex = c.text.substring(c.pos, c.pos + 4);
                        sb.append((char) Integer.parseInt(hex, 16));
                        c.pos += 4;
                    }
                    default -> throw new JsonParseException("Escape invalido '\\" + esc + "'");
                }
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static Boolean parseBoolean(Cursor c) {
        if (c.text.startsWith("true", c.pos)) {
            c.pos += 4;
            return Boolean.TRUE;
        }
        if (c.text.startsWith("false", c.pos)) {
            c.pos += 5;
            return Boolean.FALSE;
        }
        throw new JsonParseException("Token invalido na posicao " + c.pos);
    }

    private static Object parseNull(Cursor c) {
        if (c.text.startsWith("null", c.pos)) {
            c.pos += 4;
            return null;
        }
        throw new JsonParseException("Token invalido na posicao " + c.pos);
    }

    private static Double parseNumber(Cursor c) {
        int start = c.pos;
        if (!c.atEnd() && (c.peek() == '-' || c.peek() == '+')) {
            c.pos++;
        }
        while (!c.atEnd() && (Character.isDigit(c.peek()) || c.peek() == '.' || c.peek() == 'e' || c.peek() == 'E'
                || c.peek() == '+' || c.peek() == '-')) {
            c.pos++;
        }
        String token = c.text.substring(start, c.pos);
        if (token.isEmpty()) {
            throw new JsonParseException("Valor invalido na posicao " + c.pos);
        }
        try {
            return Double.parseDouble(token);
        } catch (NumberFormatException ex) {
            throw new JsonParseException("Numero invalido \"" + token + "\" na posicao " + start);
        }
    }
}
