package com.nureal.ide.core.format;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Formatador (beautifier) de SQL com 3 presets de estilo:
 *
 *  - RIVER: clausulas alinhadas a direita, formando uma "coluna invisivel"
 *    onde o conteudo comeca (estilo classico de ferramentas como PL/SQL
 *    Developer / Toad).
 *  - STANDARD (o mais usado no mercado — DataGrip, DBeaver, pgAdmin):
 *    clausulas de lista (SELECT, GROUP/ORDER BY, SET, VALUES, RETURNING)
 *    ficam sozinhas na linha com cada item indentado embaixo; clausulas de
 *    valor unico (FROM, JOIN, WHERE, LIMIT...) mantem o 1o valor/condicao
 *    na mesma linha da palavra-chave.
 *  - COMMA_FIRST (virgulas na frente): em listas (SELECT, GROUP/ORDER BY,
 *    SET), a virgula fica no inicio da linha seguinte, antes do campo.
 *
 * Tambem suporta indentacao opcional de chamadas JSON_OBJECT/JSON_ARRAY com
 * varios pares chave/valor (nao quebra chamadas simples de 1 par), e
 * formata operadores JSON (-&gt;, -&gt;&gt;) sem espacos ao redor, como e
 * costume em PostgreSQL/MySQL.
 *
 * Conteudo de strings, identificadores entre crases/aspas e comentarios e
 * preservado integralmente (nunca alteramos a caixa nem o conteudo). A
 * formatacao de quebra so acontece no nivel 0 de parenteses (exceto dentro
 * de chamadas JSON marcadas para quebra); dentro de outros parenteses
 * (funcoes, listas IN, subconsultas) o conteudo fica em linha.
 *
 * Alias de tabela (FROM/JOIN/UPDATE, com ou sem AS) e de coluna (AS, em
 * SELECT/RETURNING) tem a caixa normalizada pela REGRA DA MAIORIA: cada
 * instrucao formatada conta quantos alias o usuario escreveu todo maiusculo
 * vs. todo minusculo e, havendo maioria clara, reescreve TODOS os alias
 * (inclusive os de caixa mista) para essa caixa — alias de tabela tambem
 * propaga a normalizacao para toda referencia qualificada "alias.coluna" no
 * resto da instrucao, mesmo longe de onde foram definidos (protege contra o
 * MySQL enxergar "p" e "P" como alias diferentes dentro da mesma query).
 * Identificadores entre crases/aspas nunca entram nessa conta nem sao
 * alterados — ver {@link #format(String)} / metodo privado normalizeAliasCase.
 */
public final class SqlFormatter {

    public enum KeywordCase { UPPER, LOWER, PRESERVE }

    public enum Style { RIVER, STANDARD, COMMA_FIRST }

    private static final int INDENT_WIDTH = 4;

    private final KeywordCase keywordCase;
    private final Style style;
    private final boolean indentJson;

    public SqlFormatter() {
        this(KeywordCase.UPPER, Style.RIVER, false);
    }

    public SqlFormatter(KeywordCase keywordCase) {
        this(keywordCase, Style.RIVER, false);
    }

    public SqlFormatter(KeywordCase keywordCase, Style style, boolean indentJson) {
        this.keywordCase = keywordCase;
        this.style = (style == null) ? Style.RIVER : style;
        this.indentJson = indentJson;
    }

    public String format(String sql) {
        if (sql == null || sql.isBlank()) {
            return sql == null ? "" : sql;
        }
        List<Tok> tokens = tokenize(sql);
        tokens = normalizeAliasCase(tokens);
        Set<Integer> jsonBreaks = computeJsonBreakPositions(tokens, indentJson);
        return new Run(keywordCase, style, jsonBreaks).run(tokens);
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

    /** Clausulas cujo conteudo e uma lista separada por virgulas (afeta STANDARD/COMMA_FIRST). */
    private static final Set<String> LIST_CLAUSES = Set.of("select", "set", "values", "returning");

    /** Palavras que iniciam (ou continuam) um JOIN. */
    private static final Set<String> JOIN_WORDS = Set.of(
            "join", "inner", "left", "right", "full", "cross", "outer",
            "natural", "straight_join");

    /** Modificadores que ficam na mesma linha do SELECT. */
    private static final Set<String> SELECT_MODIFIERS = Set.of(
            "distinct", "distinctrow", "all", "sql_calc_found_rows", "high_priority");

    /** Funcoes JSON cujas chamadas com varios pares podem ser quebradas em linhas. */
    private static final Set<String> JSON_AGG_FUNCS = Set.of(
            "json_object", "json_array", "json_objectagg", "json_arrayagg");

    /** Operadores que ficam "colados" ao que vem antes/depois (sem espaco), como "." */
    private static final Set<String> TIGHT_OPERATORS = Set.of("->>", "->");

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
            "json_extract", "json_object", "json_array", "json_objectagg", "json_arrayagg",
            "json_contains", "json_search", "json_valid", "json_quote", "json_unquote",
            "greatest", "least", "rand", "uuid", "md5", "sha1", "sha2", "hex", "unhex",
            "if", "isnull"));

    private static boolean isReservedNonFunction(String w) {
        String low = w.toLowerCase(Locale.ROOT);
        return KEYWORDS.contains(low) && !FUNCTIONS.contains(low);
    }

    /**
     * Varre os tokens procurando chamadas JSON_OBJECT(...)/JSON_ARRAY(...) com
     * 2 ou mais virgulas no nivel 0 delas (ou seja, mais de um par chave/valor,
     * ou mais de 2 elementos) e marca o indice do "(" de abertura para quebra
     * em linhas. Chamadas simples (1 par/valor) ficam em linha unica.
     */
    private static Set<Integer> computeJsonBreakPositions(List<Tok> toks, boolean indentJson) {
        Set<Integer> result = new HashSet<>();
        if (!indentJson) {
            return result;
        }
        for (int i = 0; i < toks.size() - 1; i++) {
            Tok t = toks.get(i);
            if (t.type() != T.WORD || !JSON_AGG_FUNCS.contains(t.text().toLowerCase(Locale.ROOT))) {
                continue;
            }
            Tok next = toks.get(i + 1);
            if (next.type() != T.PUNCT || !next.text().equals("(")) {
                continue;
            }
            int parenIdx = i + 1;
            int d = 0;
            int commasAtTop = 0;
            for (int j = parenIdx; j < toks.size(); j++) {
                Tok tj = toks.get(j);
                if (tj.type() != T.PUNCT) {
                    continue;
                }
                if (tj.text().equals("(")) {
                    d++;
                } else if (tj.text().equals(")")) {
                    d--;
                    if (d == 0) {
                        break;
                    }
                } else if (tj.text().equals(",") && d == 1) {
                    commasAtTop++;
                }
            }
            if (commasAtTop >= 2) {
                result.add(parenIdx);
            }
        }
        return result;
    }

    // ================= Regra da maioria para caixa de alias =================

    private enum AliasKind { TABLE, COLUMN }

    private record AliasDef(int tokenIndex, String text, AliasKind kind) {
    }

    /** Contexto de clausula usado apenas para achar aliases (independente do Run/layout). */
    private static final int CLAUSE_OTHER = 0;
    private static final int CLAUSE_SELECT = 1;
    private static final int CLAUSE_FROM = 2;

    private static boolean isReservedWord(String lower) {
        return KEYWORDS.contains(lower);
    }

    /**
     * +1 se o texto e todo maiusculo (e tem letra), -1 se e todo minusculo,
     * 0 se nao tem letra ou e caixa mista (nao vota na maioria).
     */
    private static int aliasCaseVote(String text) {
        boolean hasLetter = false;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetter(text.charAt(i))) {
                hasLetter = true;
                break;
            }
        }
        if (!hasLetter) {
            return 0;
        }
        String upper = text.toUpperCase(Locale.ROOT);
        String lower = text.toLowerCase(Locale.ROOT);
        if (text.equals(upper) && !text.equals(lower)) {
            return 1;
        }
        if (text.equals(lower) && !text.equals(upper)) {
            return -1;
        }
        return 0;
    }

    /**
     * Encontra toda definicao de alias de TABELA (em FROM/JOIN/UPDATE,
     * explicita via AS ou implicita — "tabela t"/"tabela AS t") e de COLUNA
     * (explicita via AS em SELECT/RETURNING), conta quantas foram escritas
     * toda maiuscula vs toda minuscula pelo PROPRIO usuario e, se houver uma
     * maioria clara, reescreve TODOS os alias (definicao + toda referencia
     * "alias.coluna" de alias de tabela) para essa caixa — protege contra o
     * MySQL tratar alias de tabela como case-sensitive de forma inconsistente
     * dentro da mesma instrucao (ex.: "FROM pedidos P ... WHERE p.status=..."
     * vira "FROM pedidos P ... WHERE P.status=..." se a maioria for maiuscula).
     * <p>
     * So identificadores ENTRE CRASES/ASPAS (T.QUOTED) nunca sao tocados —
     * quem usa crase esta pedindo preservacao exata do nome. Sem maioria
     * clara (empate ou nenhum alias encontrado), os tokens voltam inalterados.
     */
    private static List<Tok> normalizeAliasCase(List<Tok> toks) {
        List<AliasDef> defs = new ArrayList<>();
        int depth = 0;
        int clause = CLAUSE_OTHER;
        boolean expectTableName = false;

        for (int i = 0; i < toks.size(); i++) {
            Tok t = toks.get(i);
            if (t.type() == T.PUNCT) {
                switch (t.text()) {
                    case "(" -> depth++;
                    case ")" -> depth = Math.max(0, depth - 1);
                    case "," -> {
                        if (depth == 0 && clause == CLAUSE_FROM) {
                            expectTableName = true;
                        }
                    }
                    case ";" -> {
                        clause = CLAUSE_OTHER;
                        depth = 0;
                        expectTableName = false;
                    }
                    default -> {
                    }
                }
                continue;
            }
            if (t.type() != T.WORD || depth != 0) {
                continue;
            }
            String low = t.text().toLowerCase(Locale.ROOT);

            if (low.equals("select") || low.equals("returning")) {
                clause = CLAUSE_SELECT;
                expectTableName = false;
                continue;
            }
            if (low.equals("from") || low.equals("update")) {
                clause = CLAUSE_FROM;
                expectTableName = true;
                continue;
            }
            if (JOIN_WORDS.contains(low)) {
                clause = CLAUSE_FROM;
                expectTableName = true;
                continue;
            }
            if (low.equals("on") || low.equals("using")) {
                clause = CLAUSE_OTHER;
                expectTableName = false;
                continue;
            }
            if (low.equals("as")) {
                if (i + 1 < toks.size() && toks.get(i + 1).type() == T.WORD) {
                    Tok aliasTok = toks.get(i + 1);
                    if (!isReservedWord(aliasTok.text().toLowerCase(Locale.ROOT))) {
                        AliasKind kind = (clause == CLAUSE_FROM) ? AliasKind.TABLE : AliasKind.COLUMN;
                        defs.add(new AliasDef(i + 1, aliasTok.text(), kind));
                    }
                }
                expectTableName = false;
                continue;
            }
            if (CLAUSE_STARTERS.contains(low)) {
                clause = CLAUSE_OTHER;
                expectTableName = false;
                continue;
            }
            if (clause == CLAUSE_FROM && expectTableName) {
                expectTableName = false;
                // "schema.tabela": pula o "." e o proximo nome, o alias (se
                // houver) vem so depois dele.
                int j = i;
                while (j + 2 < toks.size() && toks.get(j + 1).type() == T.PUNCT
                        && toks.get(j + 1).text().equals(".")
                        && (toks.get(j + 2).type() == T.WORD || toks.get(j + 2).type() == T.QUOTED)) {
                    j += 2;
                }
                if (j + 1 < toks.size() && toks.get(j + 1).type() == T.PUNCT
                        && toks.get(j + 1).text().equals("(")) {
                    continue; // funcao de tabela — nao arriscamos achar alias aqui
                }
                if (j + 1 < toks.size()) {
                    Tok next = toks.get(j + 1);
                    if (next.type() == T.WORD && !isReservedWord(next.text().toLowerCase(Locale.ROOT))) {
                        defs.add(new AliasDef(j + 1, next.text(), AliasKind.TABLE));
                    }
                }
            }
        }

        if (defs.isEmpty()) {
            return toks;
        }
        int upperVotes = 0;
        int lowerVotes = 0;
        for (AliasDef d : defs) {
            int v = aliasCaseVote(d.text());
            if (v > 0) {
                upperVotes++;
            } else if (v < 0) {
                lowerVotes++;
            }
        }
        if (upperVotes == lowerVotes) {
            return toks; // empate (ou so alias de caixa mista) — nao mexe
        }
        boolean targetUpper = upperVotes > lowerVotes;

        Set<String> tableAliasNamesLower = new HashSet<>();
        for (AliasDef d : defs) {
            if (d.kind() == AliasKind.TABLE) {
                tableAliasNamesLower.add(d.text().toLowerCase(Locale.ROOT));
            }
        }

        List<Tok> out = new ArrayList<>(toks);
        for (AliasDef d : defs) {
            Tok orig = out.get(d.tokenIndex());
            out.set(d.tokenIndex(), new Tok(T.WORD, recase(orig.text(), targetUpper)));
        }
        // Propaga a mesma caixa para toda referencia qualificada "alias.coluna"
        // de um alias de TABELA (ex.: normaliza tanto "P.nome" quanto "p.nome"
        // para a mesma caixa escolhida, mesmo longe da clausula FROM/JOIN).
        for (int i = 0; i < out.size() - 1; i++) {
            Tok t = out.get(i);
            if (t.type() != T.WORD) {
                continue;
            }
            String low = t.text().toLowerCase(Locale.ROOT);
            if (!tableAliasNamesLower.contains(low)) {
                continue;
            }
            Tok next = out.get(i + 1);
            if (next.type() == T.PUNCT && next.text().equals(".")) {
                out.set(i, new Tok(T.WORD, recase(t.text(), targetUpper)));
            }
        }
        return out;
    }

    private static String recase(String text, boolean upper) {
        return upper ? text.toUpperCase(Locale.ROOT) : text.toLowerCase(Locale.ROOT);
    }

    // ================= Execucao da formatacao =================

    private enum Mode { NONE, SELECT, FROM, BYLIST, SET }

    private static final class Run {

        private final KeywordCase keywordCase;
        private final Style style;
        private final Set<Integer> jsonBreakParens;
        private final StringBuilder sb = new StringBuilder();

        private int depth = 0;
        private Mode mode = Mode.NONE;
        private boolean conditionCtx = false;
        private boolean pendingListItem = false;
        private boolean pendingContentBreak = false;
        private boolean firstListItemOfClause = true;
        private int betweenPending = 0;
        private boolean atLineStart = true;
        private Tok prev = null;
        private int riverWidth = 6;     // largura do "rio" (RIVER: keywords alinhadas a direita)
        private int content = 7;        // RIVER: coluna onde o conteudo comeca (riverWidth + 1)
        private int currentLineIndent = 0; // indentacao (em espacos) da linha atual

        // Alinhamento das condicoes de um JOIN (ON/AND/OR) — ver #joinConditionLine.
        // Independente do estilo escolhido: sem isto, o AND/OR de uma condicao
        // de JOIN usava o MESMO tratamento do WHERE (alinhado a direita no rio,
        // no RIVER, ou recuo generico de 4 espacos no STANDARD/COMMA_FIRST),
        // que nao tem nenhuma relacao com a coluna onde "ON" realmente ficou
        // (varia com o tamanho do nome da tabela/alias) — o resultado ficava
        // com o AND "puxado" de volta pra esquerda, desalinhado do ON.
        private int joinOnColumn = -1;
        private boolean inJoinCondition = false;

        // pilha de chamadas JSON em quebra: profundidade onde abriram e indentacao base
        private final Deque<Integer> jsonDepths = new ArrayDeque<>();
        private final Deque<Integer> jsonBaseIndents = new ArrayDeque<>();
        private boolean pendingJsonItem = false;

        Run(KeywordCase keywordCase, Style style, Set<Integer> jsonBreakParens) {
            this.keywordCase = keywordCase;
            this.style = style;
            this.jsonBreakParens = jsonBreakParens;
        }

        String run(List<Tok> toks) {
            riverWidth = computeRiverWidth(toks);
            content = riverWidth + 1;
            int idx = 0;
            while (idx < toks.size()) {
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
                        idx++;
                    }
                    case BLOCK_COMMENT, STRING, NUMBER, QUOTED -> {
                        place(t, t.text());
                        idx++;
                    }
                    case WORD -> idx = handleWord(t, next, idx);
                    case PUNCT -> {
                        handlePunct(t, idx);
                        idx++;
                    }
                    default -> {
                        place(t, t.text());
                        idx++;
                    }
                }
            }
            return finish();
        }

        /** Largura do rio (RIVER) = maior keyword de clausula presente. */
        private int computeRiverWidth(List<Tok> toks) {
            int width = 6;
            int d = 0;
            for (int i = 0; i < toks.size(); i++) {
                Tok t = toks.get(i);
                if (t.type() == T.PUNCT) {
                    if (t.text().equals("(")) {
                        d++;
                    } else if (t.text().equals(")")) {
                        d--;
                    }
                    continue;
                }
                if (t.type() == T.WORD && d == 0) {
                    String l = t.text().toLowerCase(Locale.ROOT);
                    if ((l.equals("group") || l.equals("order")) && i + 1 < toks.size()
                            && toks.get(i + 1).text().equalsIgnoreCase("by")) {
                        width = Math.max(width, 8);
                    } else if (l.equals("returning")) {
                        width = Math.max(width, 9);
                    }
                }
            }
            return width;
        }

        private int handleWord(Tok t, Tok next, int idx) {
            String low = t.text().toLowerCase(Locale.ROOT);
            if (depth == 0) {
                boolean nextParen = next != null && next.type() == T.PUNCT
                        && next.text().equals("(");

                // GROUP BY / ORDER BY como uma unica frase (consome o BY)
                if ((low.equals("group") || low.equals("order"))
                        && next != null && next.text().equalsIgnoreCase("by")) {
                    startClause(display(t.text()) + " BY", true);
                    mode = Mode.BYLIST;
                    conditionCtx = false;
                    betweenPending = 0;
                    pendingListItem = false;
                    return idx + 2;
                }
                if (CLAUSE_STARTERS.contains(low)) {
                    startClause(display(t.text()), LIST_CLAUSES.contains(low));
                    conditionCtx = false;
                    inJoinCondition = false;
                    betweenPending = 0;
                    pendingListItem = false;
                    applyClauseMode(low);
                    return idx + 1;
                }
                // JOIN (a menos que seja a funcao LEFT(/RIGHT( etc.)
                if (JOIN_WORDS.contains(low) && !nextParen) {
                    boolean prevJoin = prev != null && prev.type() == T.WORD
                            && JOIN_WORDS.contains(prev.text().toLowerCase(Locale.ROOT));
                    if (!prevJoin) {
                        startJoin();
                        conditionCtx = false;
                        inJoinCondition = false;
                        joinOnColumn = -1;
                        mode = Mode.NONE;
                        betweenPending = 0;
                        pendingListItem = false;
                    }
                    placeWord(t);
                    // O nome da tabela do JOIN fica na mesma linha da keyword
                    // (ex.: "INNER JOIN pedidos p ON ..."), sem quebra — igual
                    // ao FROM. Antes, o STANDARD forcava uma quebra aqui,
                    // jogando o nome da tabela pra uma 3a linha sem necessidade.
                    return idx + 1;
                }
                if (low.equals("on")) {
                    placeWord(t);
                    conditionCtx = true;
                    inJoinCondition = true;
                    joinOnColumn = currentColumn() - 2; // coluna onde o "ON" comecou
                    mode = Mode.NONE;
                    return idx + 1;
                }
                if ((low.equals("and") || low.equals("or"))
                        && conditionCtx && betweenPending == 0) {
                    if (inJoinCondition) {
                        joinConditionLine(display(t.text()));
                    } else {
                        conditionLine(display(t.text()));
                    }
                    return idx + 1;
                }
                if (low.equals("and") && betweenPending > 0) {
                    betweenPending--;
                    placeWord(t);
                    return idx + 1;
                }
                if (low.equals("between")) {
                    betweenPending++;
                    placeWord(t);
                    return idx + 1;
                }
            }
            placeWord(t);
            return idx + 1;
        }

        private void handlePunct(Tok t, int idx) {
            String p = t.text();
            switch (p) {
                case "(" -> {
                    boolean jsonBreakHere = jsonBreakParens.contains(idx);
                    place(t, "(");
                    depth++;
                    if (jsonBreakHere) {
                        jsonDepths.push(depth);
                        jsonBaseIndents.push(currentLineIndent);
                        pendingJsonItem = true;
                    }
                }
                case ")" -> {
                    boolean closingJson = !jsonDepths.isEmpty() && jsonDepths.peek() == depth;
                    int baseIndent = closingJson ? jsonBaseIndents.peek() : 0;
                    if (closingJson) {
                        jsonDepths.pop();
                        jsonBaseIndents.pop();
                        breakToIndent(baseIndent);
                        pendingJsonItem = false;
                    }
                    depth = Math.max(0, depth - 1);
                    place(t, ")");
                }
                case "," -> {
                    boolean jsonListHere = !jsonDepths.isEmpty() && jsonDepths.peek() == depth;
                    if (jsonListHere) {
                        place(t, ",");
                        pendingJsonItem = true;
                    } else if (depth == 0 && style == Style.COMMA_FIRST && isListMode()) {
                        // virgula nao e impressa aqui: vai no inicio da proxima linha
                        pendingListItem = true;
                    } else {
                        place(t, ",");
                        if (depth == 0 && isListMode()) {
                            pendingListItem = true;
                        }
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
                    inJoinCondition = false;
                    joinOnColumn = -1;
                    betweenPending = 0;
                    pendingListItem = false;
                    pendingContentBreak = false;
                    currentLineIndent = 0;
                    jsonDepths.clear();
                    jsonBaseIndents.clear();
                    pendingJsonItem = false;
                }
                default -> place(t, p);
            }
        }

        private boolean isListMode() {
            return mode == Mode.SELECT || mode == Mode.BYLIST || mode == Mode.SET;
        }

        private void applyClauseMode(String low) {
            switch (low) {
                case "select" -> mode = Mode.SELECT;
                case "from" -> mode = Mode.FROM;
                case "set" -> mode = Mode.SET;
                case "where", "having" -> {
                    mode = Mode.NONE;
                    conditionCtx = true;
                }
                default -> mode = Mode.NONE;
            }
        }

        private void placeWord(Tok t) {
            place(t, display(t.text()));
        }

        private void place(Tok tok, String text) {
            if (pendingJsonItem && tok.type() != T.PUNCT) {
                int base = jsonBaseIndents.isEmpty() ? currentLineIndent : jsonBaseIndents.peek();
                breakToIndent(base + INDENT_WIDTH);
                pendingJsonItem = false;
            } else if (pendingListItem && depth == 0) {
                boolean modifier = tok.type() == T.WORD
                        && SELECT_MODIFIERS.contains(tok.text().toLowerCase(Locale.ROOT));
                if (!modifier) {
                    itemLine();
                    pendingListItem = false;
                }
            } else if (pendingContentBreak && depth == 0) {
                pendingContentBreak = false;
                boolean commaFirstList = style == Style.COMMA_FIRST && isListMode();
                breakToIndent(commaFirstList ? INDENT_WIDTH + 2 : INDENT_WIDTH);
                if (commaFirstList) {
                    firstListItemOfClause = false;
                }
            }
            String sep = atLineStart ? "" : separator(prev, tok);
            sb.append(sep).append(text);
            atLineStart = false;
            prev = tok;
        }

        /** Inicia uma clausula (SELECT/FROM/WHERE/...), estilo-dependente. */
        private void startClause(String phrase, boolean listBearing) {
            switch (style) {
                case RIVER -> {
                    riverLine(phrase);
                    currentLineIndent = content;
                }
                case STANDARD -> {
                    plainLine(phrase, 0);
                    // Antes, TODA clausula (inclusive FROM/WHERE/LIMIT, que tem
                    // um unico valor) quebrava a clausula sozinha numa linha e
                    // jogava o conteudo pra linha seguinte — "FROM\n    tabela"
                    // em vez de "FROM tabela". Igual ao COMMA_FIRST: so listas
                    // de verdade (SELECT, GROUP/ORDER BY, SET, VALUES,
                    // RETURNING) quebram; o resto mantem o 1o valor na mesma
                    // linha da clausula, que e o padrao usado pela maioria das
                    // IDEs de banco (DataGrip, DBeaver, pgAdmin).
                    if (listBearing) {
                        pendingContentBreak = true;
                    } else {
                        currentLineIndent = 0;
                    }
                }
                case COMMA_FIRST -> {
                    if (listBearing) {
                        plainLine(phrase, 0);
                        pendingContentBreak = true;
                    } else {
                        plainLine(phrase, 0);
                        currentLineIndent = 0;
                    }
                }
                default -> riverLine(phrase);
            }
            firstListItemOfClause = true;
        }

        /**
         * Inicia um JOIN, estilo-dependente. No STANDARD, a keyword (ex.: "INNER
         * JOIN") fica nesta linha; a quebra para o conteudo (nome da tabela) so
         * e agendada depois, quando a ULTIMA palavra do JOIN for colocada (ver
         * {@link #handleWord}), para nao quebrar entre "INNER" e "JOIN".
         */
        private void startJoin() {
            switch (style) {
                case RIVER -> {
                    joinLine();
                    currentLineIndent = content;
                }
                case STANDARD, COMMA_FIRST -> {
                    plainLine(null, 0);
                    currentLineIndent = 0;
                }
                default -> joinLine();
            }
        }

        /** Continuacao de condicao (AND/OR) do WHERE/HAVING, estilo-dependente. */
        private void conditionLine(String phrase) {
            switch (style) {
                case RIVER -> {
                    riverLine(phrase);
                    currentLineIndent = content;
                }
                default -> {
                    breakToIndent(INDENT_WIDTH);
                    sb.append(phrase);
                    atLineStart = false;
                    prev = new Tok(T.WORD, lastWord(phrase));
                }
            }
        }

        /**
         * Continuacao de condicao (AND/OR) de uma condicao ON de JOIN — IGUAL
         * em qualquer estilo (RIVER/STANDARD/COMMA_FIRST), diferente de
         * {@link #conditionLine}: "ON"/"AND"/"OR" formam seu proprio "rio"
         * local, alinhados a direita entre si (ver {@link #joinOnColumn}, a
         * coluna onde "ON" comecou), de forma que o CONTEUDO de cada condicao
         * (o texto logo apos a palavra-chave) sempre comeca na MESMA coluna,
         * por exemplo:
         * <pre>
         *      INNER JOIN pedidos p
         *              ON p.cliente_id = c.id
         *             AND p.status = 'ATIVO'
         * </pre>
         */
        private void joinConditionLine(String phrase) {
            int startColumn = Math.max(0, joinOnColumn + 2 - phrase.length());
            breakToIndent(startColumn);
            sb.append(phrase);
            atLineStart = false;
            prev = new Tok(T.WORD, lastWord(phrase));
        }

        /** Coluna (0-based) onde o cursor de escrita esta na linha atual. */
        private int currentColumn() {
            int nl = sb.lastIndexOf("\n");
            return sb.length() - nl - 1;
        }

        /** Nova linha em branco (sem texto), so para preparar uma quebra antes do proximo token. */
        private void plainLine(String phrase, int indent) {
            if (sb.length() > 0 && !atLineStart) {
                trimTrailing();
                sb.append('\n');
            }
            if (indent > 0) {
                sb.append(" ".repeat(indent));
            }
            if (phrase != null) {
                sb.append(phrase);
                atLineStart = false;
                prev = new Tok(T.WORD, lastWord(phrase));
            } else {
                atLineStart = true;
                prev = null;
            }
            currentLineIndent = indent;
        }

        /** Linha de clausula no "rio" (RIVER): keyword alinhada a direita + espaco. */
        private void riverLine(String phrase) {
            if (sb.length() > 0 && !atLineStart) {
                trimTrailing();
                sb.append('\n');
            }
            int pad = Math.max(0, riverWidth - phrase.length());
            sb.append(" ".repeat(pad)).append(phrase);
            atLineStart = false;
            prev = new Tok(T.WORD, lastWord(phrase));
        }

        /** Nova linha na coluna de conteudo do RIVER (usada por JOINs). */
        private void joinLine() {
            if (sb.length() > 0 && !atLineStart) {
                trimTrailing();
                sb.append('\n');
            }
            sb.append(" ".repeat(content));
            atLineStart = true;
            prev = null;
        }

        /** Nova linha generica indentada em {@code spaces} espacos. */
        private void breakToIndent(int spaces) {
            trimTrailing();
            sb.append('\n').append(" ".repeat(Math.max(0, spaces)));
            atLineStart = true;
            prev = null;
            currentLineIndent = Math.max(0, spaces);
        }

        /** Nova linha de item de lista (colunas, itens de GROUP/ORDER BY, SET...). */
        private void itemLine() {
            if (style == Style.COMMA_FIRST) {
                trimTrailing();
                int valueColumn = INDENT_WIDTH + 2;
                if (firstListItemOfClause) {
                    sb.append('\n').append(" ".repeat(valueColumn));
                } else {
                    sb.append('\n').append(" ".repeat(INDENT_WIDTH)).append(", ");
                }
                atLineStart = false; // o cursor ja esta posicionado no inicio do valor
                prev = null;
                currentLineIndent = valueColumn;
                firstListItemOfClause = false;
                return;
            }
            int col = (style == Style.RIVER) ? content : INDENT_WIDTH;
            breakToIndent(col);
            firstListItemOfClause = false;
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

        private static String lastWord(String phrase) {
            int sp = phrase.lastIndexOf(' ');
            return phrase.substring(sp + 1);
        }

        private String separator(Tok p, Tok cur) {
            if (p == null) {
                return "";
            }
            String c = cur.text();
            if (c.equals(",") || c.equals(";") || c.equals(")") || c.equals(".")) {
                return "";
            }
            if (TIGHT_OPERATORS.contains(c) || TIGHT_OPERATORS.contains(p.text())) {
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
