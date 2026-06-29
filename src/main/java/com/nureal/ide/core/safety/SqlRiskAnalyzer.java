package com.nureal.ide.core.safety;

import java.util.Locale;

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

    /** Primeira palavra (minuscula) da instrucao, ignorando pontuacao inicial. */
    private static String firstWord(String s) {
        int i = 0;
        int n = s.length();
        while (i < n && !isWordChar(s.charAt(i))) {
            i++;
        }
        int j = i;
        while (j < n && isWordChar(s.charAt(j))) {
            j++;
        }
        return s.substring(i, j).toLowerCase(Locale.ROOT);
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
