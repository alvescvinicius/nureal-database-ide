package com.nureal.ide.core.sql;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detecta uma "data" no formato AAAA-MM-DD usada SEM aspas num statement SQL
 * — ex.: {@code WHERE data_inclusao >= 2026-07-08}. Sem aspas, isso NAO e uma
 * data pro banco: e uma subtracao numerica normal (2026 menos 7 menos 8 =
 * 2011), um bug silencioso e perigoso — o valor comparado muda por completo
 * e nenhum erro e lançado. Pior: se a instrucao for copiada e rodada fora da
 * IDE (outro cliente SQL, um terminal), o mesmo bug se repete la, sem
 * nenhum aviso — exatamente o cenario que o usuario pediu para evitar.
 *
 * So verifica FORA de strings/comentarios: uma data de verdade, entre aspas
 * ({@code '2026-07-08'}), nunca aciona isto.
 */
public final class UnquotedDateGuard {

    private UnquotedDateGuard() {
    }

    // 4 digitos (ano), hifen, 1-2 digitos (mes), hifen, 1-2 digitos (dia) —
    // com ou sem espacos ao redor dos hifens: o formatador (ver SqlFormatter)
    // pode ja ter inserido espacos ao redor do que ele achou que eram
    // operadores de subtracao, entao o guard precisa pegar os dois formatos.
    private static final Pattern BARE_DATE = Pattern.compile("\\b(\\d{4})\\s*-\\s*(\\d{1,2})\\s*-\\s*(\\d{1,2})\\b");

    /**
     * Primeiro trecho de {@code sql} que parece uma data sem aspas (fora de
     * strings/comentarios), ou {@code null} se nao houver nenhum.
     */
    public static String findUnquotedDate(String sql) {
        if (sql == null || sql.isEmpty()) {
            return null;
        }
        String codeOnly = stripStringsAndComments(sql);
        Matcher m = BARE_DATE.matcher(codeOnly);
        return m.find() ? m.group() : null;
    }

    /**
     * Substitui o conteudo de strings/comentarios por espacos (preserva o
     * comprimento e as quebras de linha, so apaga o CONTEUDO) — evita que um
     * digito ou hifen dentro de um comentario ou de uma string ja
     * corretamente citada dispare um falso positivo.
     */
    private static String stripStringsAndComments(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);

            // comentario de linha: -- ... ou # ...
            if ((c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') || c == '#') {
                while (i < n && sql.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
                continue;
            }
            // comentario de bloco /* ... */
            if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                out.append("  ");
                i += 2;
                while (i + 1 < n && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) {
                    out.append(sql.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < n) {
                    out.append("  ");
                    i = Math.min(n, i + 2);
                }
                continue;
            }
            // strings e identificadores entre aspas/crase
            if (c == '\'' || c == '"' || c == '`') {
                char quote = c;
                out.append(' ');
                i++;
                while (i < n) {
                    char d = sql.charAt(i);
                    if (d == '\\' && (quote == '\'' || quote == '"') && i + 1 < n) {
                        out.append("  ");
                        i += 2;
                        continue;
                    }
                    if (d == quote) {
                        if (i + 1 < n && sql.charAt(i + 1) == quote) {
                            out.append("  ");
                            i += 2;
                            continue;
                        }
                        out.append(' ');
                        i++;
                        break;
                    }
                    out.append(d == '\n' ? '\n' : ' ');
                    i++;
                }
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }
}
