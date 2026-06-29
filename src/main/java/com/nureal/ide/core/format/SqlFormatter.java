package com.nureal.ide.core.format;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Formatador (beautifier) de SQL no estilo do PL/SQL Developer.
 *
 * Estilo aplicado:
 *  - palavras-chave em MAIUSCULAS (configuravel);
 *  - cada coluna do SELECT em sua propria linha, com a virgula ao final;
 *  - clausulas principais (SELECT/FROM/WHERE/GROUP BY/ORDER BY/...) e JOINs
 *    iniciando uma nova linha;
 *  - AND/OR de condicoes (WHERE/ON/HAVING) recuados numa nova linha;
 *  - conteudo de strings, identificadores entre crases/aspas e comentarios
 *    e preservado integralmente (nunca alteramos a caixa nem o conteudo).
 *
 * A formatacao de quebra so acontece no nivel 0 de parenteses; dentro de
 * parenteses (funcoes, listas IN, subconsultas) o conteudo fica em linha,
 * apenas normalizando espacos. Isso mantem o resultado previsivel e seguro.
 */
public final class SqlFormatter {

    public enum KeywordCase { UPPER, LOWER, PRESERVE }

    private static final String ITEM_INDENT = "    ";
    private static final String COND_INDENT = "  ";

    private final KeywordCase keywordCase;

    public SqlFormatter() {
        this(KeywordCase.UPPER);
    }

    public SqlFormatter(KeywordCase keywordCase) {
        this.keywordCase = keywordCase;
    }

    public String format(String sql) {
        if (sql == null || sql.isBlank()) {
            return sql == null ? "" : sql;
        }
        List<Tok> tokens = tokenize(sql);
        return new Run(keywordCase).run(tokens);
    }

    // ================= Tokenizer =================

    private enum T { WORD, STRING, QUOTED, NUMBER, LINE_COMMENT, BLOCK_COMMENT, PUNCT }

    private record Tok(T type, String text) {
    }

    private static List<Tok> tokenize(String s) {
        List<Tok> out = new ArrayList<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            // comentario de linha: -- ...  ou  # ...
            if ((c == '-' && i + 1 < n && s.charAt(i + 1) == '-') || c == '#') {
                int j = i;
                while (j < n && s.charAt(j) != '\n') {
                    j++;
                }
                out.add(new Tok(T.LINE_COMMENT, stripTrailing(s.substring(i, j))));
                i = j;
                continue;
            }
            // comentario de bloco
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                int j = i + 2;
                while (j + 1 < n && !(s.charAt(j) == '*' && s.charAt(j + 1) == '/')) {
                    j++;
                }
                j = (j + 1 < n) ? j + 2 : n;
                out.add(new Tok(T.BLOCK_COMMENT, s.substring(i, j)));
                i = j;
                continue;
            }
            // string literal '...'
            if (c == '\'') {
                int j = i + 1;
                while (j < n) {
                    char d = s.charAt(j);
                    if (d == '\\') {
                        j += 2;
                        continue;
                    }
                    if (d == '\'') {
                        if (j + 1 < n && s.charAt(j + 1) == '\'') {
                            j += 2;
                            continue;
                        }
                        j++;
                        break;
                    }
                    j++;
                }
                out.add(new Tok(T.STRING, s.substring(i, Math.min(j, n))));
                i = j;
                continue;
            }
            // identificador entre crases ou aspas
            if (c == '`' || c == '"') {
                char q = c;
                int j = i + 1;
                while (j < n) {
                    char d = s.charAt(j);
                    if (d == q) {
                        if (j + 1 < n && s.charAt(j + 1) == q) {
                            j += 2;
                            continue;
                        }
                        j++;
                        break;
                    }
                    j++;
                }
                out.add(new Tok(T.QUOTED, s.substring(i, Math.min(j, n))));
                i = j;
                continue;
            }
            // numero
            if (Character.isDigit(c)
                    || (c == '.' && i + 1 < n && Character.isDigit(s.charAt(i + 1)))) {
                int j = i;
                while (j < n && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '.')) {
                    j++;
                }
                out.add(new Tok(T.NUMBER, s.substring(i, j)));
                i = j;
                continue;
            }
            // identificador / palavra-chave
            if (isIdentStart(c)) {
                int j = i;
                while (j < n && isIdentPart(s.charAt(j))) {
                    j++;
                }
                out.add(new Tok(T.WORD, s.substring(i, j)));
                i = j;
                continue;
            }
            // operadores de dois/tres caracteres
            if (i + 1 < n) {
                String two = s.substring(i, i + 2);
                if (two.equals("->") && i + 2 < n && s.charAt(i + 2) == '>') {
                    out.add(new Tok(T.PUNCT, "->>"));
                    i += 3;
                    continue;
                }
                if (Set.of("<=", ">=", "<>", "!=", ":=", "||", "->", "&&").contains(two)) {
                    out.add(new Tok(T.PUNCT, two));
                    i += 2;
                    continue;
                }
            }
            out.add(new Tok(T.PUNCT, String.valueOf(c)));
            i++;
        }
        return out;
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '@' || c == '$';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '@' || c == '$';
    }

    private static String stripTrailing(String s) {
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(0, end);
    }

    // ================= Conjuntos de palavras =================

    /** Clausulas que iniciam uma nova linha (nivel 0). */
    private static final Set<String> CLAUSE_STARTERS = Set.of(
            "select", "from", "where", "group", "order", "having", "limit",
            "offset", "union", "intersect", "except", "insert", "update",
            "delete", "set", "values", "returning");

    /** Palavras que iniciam (ou continuam) um JOIN. */
    private static final Set<String> JOIN_WORDS = Set.of(
            "join", "inner", "left", "right", "full", "cross", "outer",
            "natural", "straight_join");

    /** Modificadores que ficam na mesma linha do SELECT. */
    private static final Set<String> SELECT_MODIFIERS = Set.of(
            "distinct", "distinctrow", "all", "sql_calc_found_rows", "high_priority");

    private static final Set<String> KEYWORDS = Set.copyOf(List.of(
            // clausulas e estrutura
            "select", "from", "where", "group", "by", "order", "having", "limit",
            "offset", "union", "intersect", "except", "all", "distinct", "distinctrow",
            "insert", "into", "update", "delete", "set", "values", "returning", "as",
            "on", "using", "join", "inner", "left", "right", "full", "cross", "outer",
            "natural", "straight_join", "and", "or", "not", "in", "is", "null", "like",
            "rlike", "regexp", "between", "exists", "case", "when", "then", "else",
            "end", "asc", "desc", "with", "recursive", "default", "primary", "key",
            "foreign", "references", "unique", "index", "constraint", "create", "alter",
            "drop", "table", "view", "database", "schema", "add", "column", "modify",
            "change", "rename", "to", "if", "replace", "ignore", "duplicate", "cascade",
            "restrict", "begin", "commit", "rollback", "start", "transaction", "truncate",
            "grant", "revoke", "escape", "true", "false", "unknown", "interval",
            "current_date", "current_time", "current_timestamp", "separator", "partition",
            "over", "window", "rows", "range", "unbounded", "preceding", "following",
            "current", "row", "collate", "character", "charset", "engine", "auto_increment",
            "comment", "unsigned", "zerofill", "binary", "high_priority", "low_priority",
            "sql_calc_found_rows", "primary",
            // tipos
            "int", "integer", "smallint", "tinyint", "mediumint", "bigint", "decimal",
            "numeric", "float", "double", "real", "bit", "boolean", "bool", "date",
            "datetime", "timestamp", "time", "year", "char", "varchar", "text",
            "tinytext", "mediumtext", "longtext", "blob", "tinyblob", "mediumblob",
            "longblob", "enum", "json", "geometry"));

    private static final Set<String> FUNCTIONS = Set.copyOf(List.of(
            "count", "sum", "avg", "min", "max", "coalesce", "ifnull", "nullif", "cast",
            "convert", "round", "floor", "ceil", "ceiling", "abs", "mod", "power", "sqrt",
            "length", "char_length", "character_length", "substring", "substr", "left",
            "right", "concat", "concat_ws", "lower", "upper", "ucase", "lcase", "trim",
            "ltrim", "rtrim", "replace", "lpad", "rpad", "locate", "instr", "position",
            "format", "now", "curdate", "curtime", "sysdate", "date_format", "str_to_date",
            "datediff", "timestampdiff", "date_add", "date_sub", "adddate", "subdate",
            "year", "month", "day", "hour", "minute", "second", "dayofweek", "dayname",
            "monthname", "week", "weekday", "last_day", "group_concat", "row_number",
            "rank", "dense_rank", "ntile", "lead", "lag", "first_value", "last_value",
            "json_extract", "json_object", "json_array", "greatest", "least", "rand",
            "uuid", "md5", "sha1", "sha2", "hex", "unhex", "if", "isnull"));

    private static boolean isReservedNonFunction(String w) {
        String low = w.toLowerCase(Locale.ROOT);
        return KEYWORDS.contains(low) && !FUNCTIONS.contains(low);
    }

    // ================= Execucao da formatacao =================

    private enum Mode { NONE, SELECT, FROM, BYLIST, SET }

    private static final class Run {

        private final KeywordCase keywordCase;
        private final StringBuilder sb = new StringBuilder();

        private int depth = 0;
        private Mode mode = Mode.NONE;
        private boolean conditionCtx = false;
        private boolean expectBy = false;
        private boolean pendingListItem = false;
        private int betweenPending = 0;
        private boolean atLineStart = true;
        private Tok prev = null;

        Run(KeywordCase keywordCase) {
            this.keywordCase = keywordCase;
        }

        String run(List<Tok> toks) {
            for (int idx = 0; idx < toks.size(); idx++) {
                Tok t = toks.get(idx);
                Tok next = (idx + 1 < toks.size()) ? toks.get(idx + 1) : null;
                switch (t.type()) {
                    case LINE_COMMENT -> {
                        if (!atLineStart) {
                            sb.append(' ');
                        }
                        sb.append(t.text());
                        trimTrailing();
                        sb.append('\n');
                        atLineStart = true;
                        prev = null;
                    }
                    case BLOCK_COMMENT, STRING, NUMBER, QUOTED -> place(t, t.text());
                    case WORD -> handleWord(t, next);
                    case PUNCT -> handlePunct(t);
                    default -> place(t, t.text());
                }
            }
            return finish();
        }

        private void handleWord(Tok t, Tok next) {
            String low = t.text().toLowerCase(Locale.ROOT);
            if (depth == 0) {
                boolean nextParen = next != null && next.type() == T.PUNCT
                        && next.text().equals("(");

                if (CLAUSE_STARTERS.contains(low)) {
                    startClauseLine();
                    conditionCtx = false;
                    betweenPending = 0;
                    pendingListItem = false;
                    expectBy = false;
                    placeWord(t);
                    applyClauseMode(low);
                    return;
                }
                // JOIN (a menos que seja a funcao LEFT(/RIGHT( etc.)
                if (JOIN_WORDS.contains(low) && !nextParen) {
                    boolean prevJoin = prev != null && prev.type() == T.WORD
                            && JOIN_WORDS.contains(prev.text().toLowerCase(Locale.ROOT));
                    if (!prevJoin) {
                        startClauseLine();
                        conditionCtx = false;
                        mode = Mode.NONE;
                        betweenPending = 0;
                        pendingListItem = false;
                    }
                    placeWord(t);
                    return;
                }
                if (low.equals("on")) {
                    placeWord(t);
                    conditionCtx = true;
                    mode = Mode.NONE;
                    return;
                }
                if (low.equals("by") && expectBy) {
                    placeWord(t);
                    expectBy = false;
                    mode = Mode.BYLIST;
                    return;
                }
                if ((low.equals("and") || low.equals("or"))
                        && conditionCtx && betweenPending == 0) {
                    condLine();
                    placeWord(t);
                    return;
                }
                if (low.equals("and") && betweenPending > 0) {
                    betweenPending--;
                    placeWord(t);
                    return;
                }
                if (low.equals("between")) {
                    betweenPending++;
                    placeWord(t);
                    return;
                }
            }
            placeWord(t);
        }

        private void handlePunct(Tok t) {
            String p = t.text();
            switch (p) {
                case "(" -> {
                    place(t, "(");
                    depth++;
                }
                case ")" -> {
                    depth = Math.max(0, depth - 1);
                    place(t, ")");
                }
                case "," -> {
                    place(t, ",");
                    if (depth == 0 && (mode == Mode.SELECT || mode == Mode.FROM
                            || mode == Mode.BYLIST || mode == Mode.SET)) {
                        pendingListItem = true;
                    }
                }
                case ";" -> {
                    place(t, ";");
                    trimTrailing();
                    sb.append("\n\n");
                    atLineStart = true;
                    prev = null;
                    depth = 0;
                    mode = Mode.NONE;
                    conditionCtx = false;
                    betweenPending = 0;
                    pendingListItem = false;
                    expectBy = false;
                }
                default -> place(t, p);
            }
        }

        private void applyClauseMode(String low) {
            switch (low) {
                case "select" -> {
                    // A primeira coluna fica na MESMA linha do SELECT; apenas as
                    // colunas seguintes (apos virgula) quebram para novas linhas.
                    mode = Mode.SELECT;
                }
                case "from" -> mode = Mode.FROM;
                case "set" -> mode = Mode.SET;
                case "where", "having" -> {
                    mode = Mode.NONE;
                    conditionCtx = true;
                }
                case "group", "order" -> {
                    mode = Mode.NONE;
                    expectBy = true;
                }
                default -> mode = Mode.NONE;
            }
        }

        private void placeWord(Tok t) {
            place(t, display(t.text()));
        }

        private void place(Tok tok, String text) {
            if (pendingListItem && depth == 0) {
                boolean modifier = tok.type() == T.WORD
                        && SELECT_MODIFIERS.contains(tok.text().toLowerCase(Locale.ROOT));
                if (!modifier) {
                    itemLine();
                    pendingListItem = false;
                }
            }
            String sep = atLineStart ? "" : separator(prev, tok);
            sb.append(sep).append(text);
            atLineStart = false;
            prev = tok;
        }

        private void startClauseLine() {
            if (sb.length() > 0 && !atLineStart) {
                trimTrailing();
                sb.append('\n');
            }
            atLineStart = true;
            prev = null;
        }

        private void itemLine() {
            trimTrailing();
            sb.append('\n').append(ITEM_INDENT);
            atLineStart = true;
        }

        private void condLine() {
            trimTrailing();
            sb.append('\n').append(COND_INDENT);
            atLineStart = true;
        }

        private void trimTrailing() {
            int end = sb.length();
            while (end > 0 && (sb.charAt(end - 1) == ' ' || sb.charAt(end - 1) == '\t')) {
                end--;
            }
            sb.setLength(end);
        }

        private String display(String text) {
            if (keywordCase == KeywordCase.PRESERVE) {
                return text;
            }
            String low = text.toLowerCase(Locale.ROOT);
            if (KEYWORDS.contains(low) || FUNCTIONS.contains(low)) {
                return keywordCase == KeywordCase.UPPER
                        ? text.toUpperCase(Locale.ROOT)
                        : low;
            }
            return text;
        }

        private String separator(Tok p, Tok cur) {
            if (p == null) {
                return "";
            }
            String c = cur.text();
            if (c.equals(",") || c.equals(";") || c.equals(")") || c.equals(".")) {
                return "";
            }
            if (p.text().equals("(") || p.text().equals(".")) {
                return "";
            }
            if (c.equals("(")) {
                if (p.type() == T.WORD) {
                    return isReservedNonFunction(p.text()) ? " " : "";
                }
                if (p.type() == T.QUOTED) {
                    return "";
                }
                return " ";
            }
            return " ";
        }

        private String finish() {
            // remove espacos/linhas em branco no final
            int end = sb.length();
            while (end > 0 && Character.isWhitespace(sb.charAt(end - 1))) {
                end--;
            }
            sb.setLength(end);
            return sb.toString();
        }
    }
}
