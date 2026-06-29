package com.nureal.ide.core.sql;

import java.util.ArrayList;
import java.util.List;

/**
 * Divide um texto SQL em varios statements pelo ';', ignorando ';' que estejam
 * dentro de strings ('...', "..."), identificadores com crase (`...`) ou
 * comentarios (-- linha, # linha, /* bloco *&#47;).
 *
 * Suficiente para o caso comum de executar varios SELECT/DML de uma vez.
 */
public final class SqlStatementSplitter {

    private SqlStatementSplitter() {
    }

    public static List<String> split(String sql) {
        List<String> out = new ArrayList<>();
        if (sql == null) {
            return out;
        }
        StringBuilder cur = new StringBuilder();
        int i = 0;
        int n = sql.length();

        while (i < n) {
            char c = sql.charAt(i);

            // comentario de linha: -- ... ou # ...
            if ((c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') || c == '#') {
                while (i < n && sql.charAt(i) != '\n') {
                    cur.append(sql.charAt(i));
                    i++;
                }
                continue;
            }

            // comentario de bloco /* ... */
            if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                cur.append("/*");
                i += 2;
                while (i < n && !(sql.charAt(i) == '*' && i + 1 < n && sql.charAt(i + 1) == '/')) {
                    cur.append(sql.charAt(i));
                    i++;
                }
                if (i < n) {
                    cur.append("*/");
                    i += 2;
                }
                continue;
            }

            // strings e identificadores entre aspas/crase
            if (c == '\'' || c == '"' || c == '`') {
                char quote = c;
                cur.append(c);
                i++;
                while (i < n) {
                    char d = sql.charAt(i);
                    // escape com barra invertida (apenas em '...' e "...")
                    if (d == '\\' && (quote == '\'' || quote == '"') && i + 1 < n) {
                        cur.append(d);
                        cur.append(sql.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    if (d == quote) {
                        // aspas duplicadas ('' "" ``) = aspas escapada, continua dentro
                        if (i + 1 < n && sql.charAt(i + 1) == quote) {
                            cur.append(quote);
                            cur.append(quote);
                            i += 2;
                            continue;
                        }
                        cur.append(d);
                        i++;
                        break;
                    }
                    cur.append(d);
                    i++;
                }
                continue;
            }

            // separador de statements
            if (c == ';') {
                addTrimmed(out, cur);
                cur.setLength(0);
                i++;
                continue;
            }

            cur.append(c);
            i++;
        }

        addTrimmed(out, cur);
        return out;
    }

    private static void addTrimmed(List<String> out, StringBuilder sb) {
        String s = sb.toString().trim();
        if (!s.isEmpty()) {
            out.add(s);
        }
    }
}
