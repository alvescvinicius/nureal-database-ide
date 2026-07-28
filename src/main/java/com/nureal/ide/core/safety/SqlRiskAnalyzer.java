package com.nureal.ide.core.safety;

import java.util.Locale;
import java.util.Set;

/**
 * Avalia se uma instrucao SQL e "de risco" e merece confirmacao antes de rodar:
 *  - DELETE sem WHERE  -> apaga TODAS as linhas
 *  - UPDATE sem WHERE  -> altera TODAS as linhas
 *  - DROP / TRUNCATE   -> destrutivo e irreversivel
 *  - ALTER / RENAME / CREATE -> altera a estrutura (DDL)
 *
 * O WHERE so "protege" quando esta no nivel principal da instrucao (fora de
 * parenteses): um WHERE dentro de uma subconsulta NAO conta como filtro do
 * DELETE/UPDATE externo. Strings e comentarios sao ignorados na analise.
 */
public final class SqlRiskAnalyzer {

    private SqlRiskAnalyzer() {
    }

    /** Retorna a razao do risco, ou {@code null} se a instrucao for segura. */
    public static String riskReason(String sql) {
        if (sql == null || sql.isBlank()) {
            return null;
        }
        String clean = stripCommentsAndStrings(sql);
        String first = firstWord(clean);
        switch (first) {
            case "delete":
                return hasTopLevelWhere(clean) ? null
                        : "DELETE sem WHERE — apaga TODAS as linhas da tabela.";
            case "update":
                return hasTopLevelWhere(clean) ? null
                        : "UPDATE sem WHERE — altera TODAS as linhas da tabela.";
            case "truncate":
                return "TRUNCATE — apaga TODAS as linhas (irreversivel).";
            case "drop":
                return "DROP — remove o objeto do banco (irreversivel).";
            case "alter":
                return "ALTER — altera a estrutura do objeto (DDL).";
            case "rename":
                return "RENAME — renomeia objeto (DDL).";
            case "create":
                return "CREATE — comando de definicao de estrutura (DDL).";
            default:
                return null;
        }
    }

    public static boolean isRisky(String sql) {
        return riskReason(sql) != null;
    }

    private static final Set<String> WRITE_OR_DDL_VERBS = Set.of(
            "insert", "update", "delete", "replace", "truncate", "drop", "alter", "rename", "create");

    /**
     * Verdadeiro se a instrucao escreve dados ou altera estrutura — usado por
     * {@code ExecuteSqlTool} (chat com IA) para bloquear QUALQUER escrita/DDL,
     * nao so os casos "arriscados" de {@link #riskReason} (que so cobre
     * DELETE/UPDATE SEM WHERE, nao um INSERT ou um UPDATE/DELETE COM WHERE —
     * a IA nao deve rodar nenhum desses sozinha, arriscado ou nao).
     */
    public static boolean isWriteOrDdl(String sql) {
        if (sql == null || sql.isBlank()) {
            return false;
        }
        return WRITE_OR_DDL_VERBS.contains(firstWord(stripCommentsAndStrings(sql)));
    }

    /**
     * Verdadeiro se a instrucao e DDL (CREATE/ALTER/DROP/RENAME) — ou seja,
     * pode ter criado, removido ou alterado tabelas, views, procedures,
     * functions ou triggers, exigindo recarregar o navegador de objetos.
     */
    public static boolean isStructuralChange(String sql) {
        if (sql == null || sql.isBlank()) {
            return false;
        }
        String first = firstWord(stripCommentsAndStrings(sql));
        return switch (first) {
            case "create", "alter", "drop", "rename" -> true;
            default -> false;
        };
    }

    /**
     * Verbos que podem seguir um preambulo {@code WITH ... AS (...)} (CTE) —
     * usados por {@link #firstWord} para achar o verbo REAL da instrucao
     * quando ela comeca com WITH, em vez de parar em "with" e considerar
     * tudo seguro.
     */
    private static final Set<String> STATEMENT_VERBS = Set.of("select", "insert", "update", "delete", "replace");

    /**
     * Primeira palavra (minuscula) da instrucao, ignorando pontuacao inicial —
     * exceto quando a instrucao comeca com uma CTE ({@code WITH nome AS
     * (...) DELETE ...}): nesse caso, "with" nao e um verbo de instrucao
     * nenhum, entao continua procurando (pulando nomes de CTE, "recursive",
     * "as" e os corpos entre parenteses, que sao ignorados pelo controle de
     * profundidade abaixo) ate achar o verbo real (SELECT/INSERT/UPDATE/
     * DELETE/REPLACE) no nivel 0 de parenteses. Sem isto, QUALQUER DELETE/
     * UPDATE sem WHERE (ou DROP/TRUNCATE/ALTER) prefixado por uma CTE
     * escapava do analisador de risco por inteiro — MySQL 8+ aceita WITH
     * antes de DELETE/UPDATE, entao nao e um caso hipotetico.
     */
    private static String firstWord(String s) {
        int n = s.length();
        int depth = 0;
        boolean sawWith = false;
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
                i++;
                continue;
            }
            if (c == ')') {
                depth--;
                i++;
                continue;
            }
            if (depth > 0 || !isWordChar(c)) {
                i++;
                continue;
            }
            int j = i;
            while (j < n && isWordChar(s.charAt(j))) {
                j++;
            }
            String word = s.substring(i, j).toLowerCase(Locale.ROOT);
            i = j;
            if (!sawWith) {
                if (word.equals("with")) {
                    sawWith = true;
                    continue;
                }
                return word;
            }
            if (STATEMENT_VERBS.contains(word)) {
                return word;
            }
            // nome de CTE, "recursive", "as": ainda no preambulo, continua procurando
        }
        return sawWith ? "with" : "";
    }

    /** Verdadeiro se existe um WHERE no nivel 0 de parenteses. */
    private static boolean hasTopLevelWhere(String s) {
        int depth = 0;
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
                i++;
            } else if (c == ')') {
                depth--;
                i++;
            } else if (isWordChar(c)) {
                int j = i;
                while (j < n && isWordChar(s.charAt(j))) {
                    j++;
                }
                if (depth == 0 && s.substring(i, j).equalsIgnoreCase("where")) {
                    return true;
                }
                i = j;
            } else {
                i++;
            }
        }
        return false;
    }

    /** Substitui strings e comentarios por espacos, preservando o resto. */
    private static String stripCommentsAndStrings(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            // comentario de linha -- ... ou # ...
            if ((c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') || c == '#') {
                while (i < n && sql.charAt(i) != '\n') {
                    i++;
                }
                out.append(' ');
                continue;
            }
            // comentario de bloco
            if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) {
                    i++;
                }
                i = Math.min(n, i + 2);
                out.append(' ');
                continue;
            }
            // strings / identificadores citados
            if (c == '\'' || c == '"' || c == '`') {
                char q = c;
                i++;
                while (i < n) {
                    char d = sql.charAt(i);
                    if (d == '\\') {
                        i += 2;
                        continue;
                    }
                    if (d == q) {
                        if (i + 1 < n && sql.charAt(i + 1) == q) {
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    i++;
                }
                out.append(' ');
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
