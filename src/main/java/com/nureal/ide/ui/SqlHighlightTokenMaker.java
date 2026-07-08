package com.nureal.ide.ui;

import com.nureal.ide.core.sql.SqlStatementLocator;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.fife.ui.rsyntaxtextarea.modes.SQLTokenMaker;

import javax.swing.text.Segment;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * TokenMaker de SQL que GARANTE o reconhecimento de palavras-chave
 * independente de maiusculas/minusculas — pedido explicito do usuario: ele
 * nao deve se preocupar em escrever {@code select}, {@code SELECT} ou
 * {@code Select}, nem {@code left outer join} em vez de
 * {@code LEFT OUTER JOIN}, para o editor reconhecer e destacar do mesmo
 * jeito.
 *
 * O {@link SQLTokenMaker} padrao do RSyntaxTextArea ja e gerado com a
 * diretiva JFlex {@code %ignorecase}, entao a maior parte dos casos ja
 * funciona sem isto. Esta classe existe como uma GARANTIA extra e defensiva
 * (nao depende de detalhes internos da versao exata da biblioteca) e, de
 * quebra, amplia a lista de palavras-chave reconhecidas alem do dialeto
 * (bem antigo, orientado a MS Access) embutido no RSyntaxTextArea — sem
 * isso, coisas comuns em Postgres/MySQL/SQL Server como {@code FULL JOIN},
 * {@code LIMIT}/{@code OFFSET}, {@code WITH} recursivo, {@code COALESCE}
 * etc. nao ficariam destacadas em NENHUMA caixa.
 *
 * TAMBEM negrita nomes de tabela/view/procedure/function/trigger e seus
 * alias (pedido explicito do usuario) — ver {@link #getTokenList} para como
 * isto e calculado ANTES de cada linha ser lexada (pre-varredura da
 * instrucao inteira, nao um "olhar pra tras" token a token, ver javadoc de
 * {@link #getTokenList} para o motivo).
 *
 * Registrada NAO mais via {@code AbstractTokenMakerFactory#putMapping}
 * (aquele mecanismo cria uma instancia nova por REFLEXAO, via construtor sem
 * argumentos, e nao da nenhum jeito de guardar uma referencia de volta pro
 * {@link RSyntaxTextArea} dono) — em vez disso, {@link SqlEditorPane}
 * instancia esta classe diretamente (passando a si mesma) e a instala via
 * {@code RSyntaxDocument#setSyntaxStyle(TokenMaker)}, DEPOIS de
 * {@code textArea.setSyntaxEditingStyle(SYNTAX_STYLE_SQL)} (que continua
 * sendo chamado, pois o code folding busca o parser certo por esse nome de
 * estilo guardado na PROPRIA {@code RSyntaxTextArea}, nao no documento —
 * substituir so o TokenMaker do documento nao afeta isso).
 */
public final class SqlHighlightTokenMaker extends SQLTokenMaker {

    /**
     * Mesma lista do {@code SQLTokenMaker.flex} original (dialeto MS Access
     * do RSyntaxTextArea) + extras de SQL "moderno"/ANSI comuns em
     * Postgres/MySQL/SQL Server/Oracle que faltam na gramatica embutida.
     */
    // Visibilidade de pacote (nao mais private): reaproveitadas por
    // SqlEditorPane#scanReferenceGroups (secao 8.5 — destaque de todas as
    // referencias a um objeto/alias), pra nao duplicar de novo esta mesma
    // lista grande so pra saber quando uma palavra "encerra" a expectativa
    // de alias implicito.
    static final Set<String> KEYWORDS = Set.of(
            "ADD", "ALL", "ALTER", "AND", "ANY", "AS", "ASC", "AUTOINCREMENT", "AVA",
            "BETWEEN", "BINARY", "BIT", "BOOLEAN", "BY", "BYTE",
            "CASE", "CHAR", "CHARACTER", "COLUMN", "CONSTRAINT", "COUNTER", "CREATE", "CURRENCY",
            "DATABASE", "DATE", "DATETIME", "DELETE", "DESC", "DISALLOW", "DISTINCT", "DISTINCTROW", "DOUBLE", "DROP",
            "END", "ELSE", "EXISTS",
            "FLOAT", "FLOAT4", "FLOAT8", "FOREIGN", "FROM",
            "GENERAL", "GROUP", "GUID",
            "HAVING",
            "INNER", "INSERT", "IGNORE", "IMP", "IN", "INDEX", "INT", "INTEGER",
            "INTEGER1", "INTEGER2", "INTEGER4", "INTO", "IS",
            "JOIN",
            "KEY",
            "LEFT", "LEVEL", "LIKE", "LOGICAL", "LONG", "LONGBINARY", "LONGTEXT",
            "MATCHED", "MEMO", "MERGE", "MOD", "MONEY",
            "NOT", "NULL", "NUMBER", "NUMERIC",
            "OLEOBJECT", "ON", "OPTION", "OR", "ORDER", "OUTER", "OWNERACCESS",
            "PARAMETERS", "PASSWORD", "PERCENT", "PIVOT", "PRIMARY",
            "REAL", "REFERENCES", "RIGHT",
            "SELECT", "SET", "SHORT", "SINGLE", "SMALLINT", "SOME", "STDEV", "STDEVP", "STRING",
            "TABLE", "TABLEID", "TEXT", "THEN", "TIME", "TIMESTAMP", "TOP", "TRANSFORM", "TYPE",
            "UNION", "UNIQUE", "UPDATE", "USER", "USING",
            "VALUE", "VALUES", "VAR", "VARBINARY", "VARCHAR", "VARP",
            "WHEN", "WHERE", "WITH",
            "YESNO",
            // Extras (ANSI/Postgres/MySQL/SQL Server) — nao existem na
            // gramatica embutida do RSyntaxTextArea.
            "FULL", "CROSS", "LATERAL", "NATURAL",
            "LIMIT", "OFFSET", "FETCH", "FIRST", "NEXT", "ROWS", "ONLY",
            "OVER", "PARTITION", "WINDOW", "RECURSIVE", "RETURNING",
            "CASCADE", "RESTRICT", "TRUNCATE", "GRANT", "REVOKE",
            "BEGIN", "COMMIT", "ROLLBACK", "TRANSACTION", "SAVEPOINT",
            "PROCEDURE", "FUNCTION", "TRIGGER", "VIEW", "SEQUENCE",
            "IF", "ELSEIF", "LOOP", "WHILE", "RETURN", "DECLARE",
            "EXEC", "EXECUTE", "CALL", "REPLACE", "IFNULL", "COALESCE",
            "ILIKE", "SIMILAR", "ARRAY", "JSON", "JSONB", "TABLESPACE",
            "SCHEMA", "MATERIALIZED", "TEMP", "TEMPORARY", "UNLOGGED"
    );

    /** Idem — agregacoes/funcoes SQL99 do arquivo original + comuns do dia a dia. */
    static final Set<String> FUNCTIONS = Set.of(
            "AVG", "COUNT", "MIN", "MAX", "SUM",
            "CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP", "CURRENT_USER", "SESSION_USER", "SYSTEM_USER",
            "BIT_LENGTH", "CHAR_LENGTH", "EXTRACT", "OCTET_LENGTH", "POSITION",
            "CONCATENATE", "CONVERT", "LOWER", "SUBSTRING", "TRANSLATE", "TRIM", "UPPER",
            "NOW", "CAST", "ROUND", "FLOOR", "CEIL", "CEILING", "ABS", "LENGTH", "CONCAT",
            "GREATEST", "LEAST", "NULLIF", "TO_CHAR", "TO_DATE", "TO_NUMBER", "ROW_NUMBER",
            "RANK", "DENSE_RANK", "LAG", "LEAD", "GROUP_CONCAT", "STRING_AGG"
    );

    /**
     * Palavras que introduzem um nome de OBJETO de banco (tabela, view,
     * procedure, function, trigger) logo em seguida — usadas por
     * {@link #scanKnownNames} pra saber quando o PROXIMO identificador
     * "de verdade" e o nome de um objeto (ou, em seguida, seu alias).
     */
    static final Set<String> OBJECT_INTRODUCERS = Set.of(
            "FROM", "JOIN", "INTO", "UPDATE", "TABLE", "VIEW",
            "PROCEDURE", "FUNCTION", "TRIGGER", "CALL", "EXEC", "EXECUTE", "REFERENCES"
    );

    private static final int STATE_NONE = 0;
    private static final int STATE_EXPECT_OBJECT = 1;
    private static final int STATE_AFTER_OBJECT = 2;
    private static final int STATE_EXPECT_ALIAS = 3;

    /**
     * {@link RSyntaxTextArea} dona desta instancia (ver {@link SqlEditorPane}
     * — uma instancia NOVA por aba, nunca compartilhada). So usada para
     * enxergar o texto INTEIRO do documento em {@link #getTokenList}, que o
     * TokenMaker normalmente NAO recebe (a API so entrega uma linha por vez).
     */
    private final RSyntaxTextArea owner;

    /**
     * Nomes de objeto/alias validos para a PROXIMA chamada de
     * {@link #addToken} — recalculado em {@link #getTokenList} (uma vez por
     * LINHA lexada, nao token a token) via {@link #scanKnownNames}, uma
     * pre-varredura da instrucao SQL inteira que contem a linha atual.
     *
     * Por que pre-varredura e nao "ir acumulando conforme lexa" (jeito
     * antigo desta classe): SQL legitimamente usa um alias ANTES da linha
     * que o define — o proprio SELECT list ("SELECT t.ID_TITULO...") vem
     * ANTES do "FROM tabela t" que diz o que "t" significa. Um TokenMaker so
     * ve token a token, em ordem — ele NUNCA teria como negritar "t" na
     * linha do SELECT usando so o que ja viu ate ali, nao importa quao
     * esperto for o estado interno. A pre-varredura resolve isso: antes de
     * lexar QUALQUER linha da instrucao, ja sabemos TODOS os nomes/alias
     * dela inteira, entao "t" no SELECT e "t" no WHERE ficam negritados
     * igualmente, nao importa a ordem em que aparecem no texto.
     */
    private Set<String> knownNames = Set.of();

    // Cache simples: soh reescaneia quando a linha atual sai da ultima
    // instrucao ja escaneada OU o documento mudou de tamanho (edicao) —
    // evita reprocessar a instrucao inteira a cada linha visivel em todo
    // repaint (rolagem, etc.), que seria o caso comum.
    private int cachedStmtStart = -1;
    private int cachedStmtEnd = -1;
    private int cachedDocLength = -1;

    SqlHighlightTokenMaker(RSyntaxTextArea owner) {
        this.owner = owner;
    }

    @Override
    public Token getTokenList(Segment text, int startTokenType, int startOffset) {
        refreshKnownNames(startOffset);
        return super.getTokenList(text, startTokenType, startOffset);
    }

    /** Atualiza {@link #knownNames} se a linha em {@code offset} nao pertencer mais a instrucao ja escaneada. */
    private void refreshKnownNames(int offset) {
        if (owner == null) {
            return;
        }
        int docLength = owner.getDocument().getLength();
        if (docLength == cachedDocLength && offset >= cachedStmtStart && offset < cachedStmtEnd) {
            return; // ainda na mesma instrucao ja escaneada, documento sem mudar de tamanho
        }
        String full = owner.getText();
        int[] bounds = SqlStatementLocator.boundsAt(full, offset);
        cachedStmtStart = bounds[0];
        cachedStmtEnd = bounds[1];
        cachedDocLength = docLength;
        knownNames = scanKnownNames(full, cachedStmtStart, cachedStmtEnd);
    }

    /**
     * Varre {@code full.substring(start, end)} (uma instrucao inteira) e
     * devolve todo nome de tabela/view/procedure/function/trigger e seus
     * alias — mesma logica de {@code OBJECT_INTRODUCERS}/"AS"/implicito que
     * a versao anterior desta classe fazia token a token, so que agora como
     * uma pre-varredura independente, ignorando strings/comentarios.
     */
    private static Set<String> scanKnownNames(String full, int start, int end) {
        Set<String> names = new HashSet<>();
        int state = STATE_NONE;
        int i = start;
        while (i < end) {
            char c = full.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if ((c == '-' && i + 1 < end && full.charAt(i + 1) == '-') || c == '#') {
                while (i < end && full.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < end && full.charAt(i + 1) == '*') {
                int close = full.indexOf("*/", i + 2);
                i = (close < 0 || close > end) ? end : close + 2;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                char quote = c;
                i++;
                while (i < end) {
                    char d = full.charAt(i);
                    if (d == '\\' && (quote == '\'' || quote == '"') && i + 1 < end) {
                        i += 2;
                        continue;
                    }
                    if (d == quote) {
                        if (i + 1 < end && full.charAt(i + 1) == quote) {
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    i++;
                }
                continue;
            }
            if (c == '.') {
                i++;
                continue; // nome qualificado (schema.tabela): nao muda o estado
            }
            if (c == ',') {
                if (state == STATE_AFTER_OBJECT) {
                    state = STATE_EXPECT_OBJECT;
                }
                i++;
                continue;
            }
            if (!Character.isLetterOrDigit(c) && c != '_') {
                // qualquer outro separador/operador ("(", ")", "=", ";"...):
                // encerra a expectativa, igual um "(" de subquery encerrava
                // no rastreamento antigo token a token.
                state = STATE_NONE;
                i++;
                continue;
            }
            int wordStart = i;
            while (i < end && (Character.isLetterOrDigit(full.charAt(i)) || full.charAt(i) == '_')) {
                i++;
            }
            String word = full.substring(wordStart, i).toUpperCase(Locale.ROOT);
            if (OBJECT_INTRODUCERS.contains(word)) {
                state = STATE_EXPECT_OBJECT;
            } else if (word.equals("AS")) {
                if (state == STATE_AFTER_OBJECT) {
                    state = STATE_EXPECT_ALIAS;
                }
            } else if (KEYWORDS.contains(word) || FUNCTIONS.contains(word)) {
                // qualquer outra palavra-chave (WHERE, ON, SET, GROUP,
                // AND...) encerra a expectativa: sem alias implicito antes
                // de uma clausula nova comecando.
                state = STATE_NONE;
            } else {
                switch (state) {
                    case STATE_EXPECT_OBJECT -> {
                        state = STATE_AFTER_OBJECT;
                        names.add(word);
                    }
                    case STATE_EXPECT_ALIAS -> {
                        state = STATE_NONE;
                        names.add(word);
                    }
                    case STATE_AFTER_OBJECT -> {
                        // identificador logo apos o nome do objeto, sem
                        // "AS" explicito: alias implicito ("FROM tabela t").
                        state = STATE_NONE;
                        names.add(word);
                    }
                    default -> {
                        // identificador solto fora de FROM/JOIN/AS: so
                        // interessa se ja for um nome conhecido, e isso quem
                        // decide e o addToken (consultando o SET inteiro,
                        // ja calculado) — aqui, nesta pre-varredura, nao ha
                        // nada a fazer.
                    }
                }
            }
        }
        return names;
    }

    /**
     * Um "grupo de referencia": o nome de um objeto (tabela/view/...) e o
     * alias que o acompanha na MESMA introducao (FROM/JOIN), quando houver —
     * ver secao 8.5 do pedido "Navegacao Inteligente e Interativa": clicar ou
     * posicionar o cursor sobre a tabela OU seu alias deve destacar TODAS as
     * ocorrencias de AMBOS na instrucao. Sem alias, o grupo tem um so nome.
     *
     * Nao trata auto-join (mesma tabela citada duas vezes com alias
     * diferentes, ex. {@code FROM funcionario f1 JOIN funcionario f2 ON ...})
     * de forma perfeitamente precisa — os dois grupos ficam com nomes
     * diferentes (f1, f2), entao continuam independentes; so o nome da
     * tabela em si (ambigua entre os dois) nao e distinguido, caso raro e
     * aceitavel pra este recurso.
     */
    record ReferenceGroup(Set<String> names) {
    }

    /**
     * Varre {@code full.substring(start, end)} (uma instrucao inteira) e
     * devolve os grupos de referencia (objeto + seu alias) — mesma maquina de
     * estados de {@link #scanKnownNames}, so que agrupando em vez de
     * achatar tudo num unico {@code Set}.
     */
    static List<ReferenceGroup> scanReferenceGroups(String full, int start, int end) {
        List<ReferenceGroup> groups = new ArrayList<>();
        int state = STATE_NONE;
        // Grupo (objeto + alias) sendo montado no momento — simples variavel
        // local mutavel (nao ha lambda aqui, entao nao precisa de array/holder
        // pra "efetivamente final"), reatribuida a cada nova introducao de
        // objeto (FROM/JOIN) e "fechada" em finalizePending.
        Set<String> pending = null;
        int i = start;
        while (i < end) {
            char c = full.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if ((c == '-' && i + 1 < end && full.charAt(i + 1) == '-') || c == '#') {
                while (i < end && full.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < end && full.charAt(i + 1) == '*') {
                int close = full.indexOf("*/", i + 2);
                i = (close < 0 || close > end) ? end : close + 2;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                char quote = c;
                i++;
                while (i < end) {
                    char d = full.charAt(i);
                    if (d == '\\' && (quote == '\'' || quote == '"') && i + 1 < end) {
                        i += 2;
                        continue;
                    }
                    if (d == quote) {
                        if (i + 1 < end && full.charAt(i + 1) == quote) {
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    i++;
                }
                continue;
            }
            if (c == '.') {
                i++;
                continue;
            }
            if (c == ',') {
                if (state == STATE_AFTER_OBJECT) {
                    pending = finalizePending(groups, pending);
                    state = STATE_EXPECT_OBJECT;
                }
                i++;
                continue;
            }
            if (!Character.isLetterOrDigit(c) && c != '_') {
                if (state == STATE_AFTER_OBJECT) {
                    pending = finalizePending(groups, pending);
                }
                state = STATE_NONE;
                i++;
                continue;
            }
            int wordStart = i;
            while (i < end && (Character.isLetterOrDigit(full.charAt(i)) || full.charAt(i) == '_')) {
                i++;
            }
            String word = full.substring(wordStart, i).toUpperCase(Locale.ROOT);
            if (OBJECT_INTRODUCERS.contains(word)) {
                if (state == STATE_AFTER_OBJECT) {
                    pending = finalizePending(groups, pending);
                }
                state = STATE_EXPECT_OBJECT;
            } else if (word.equals("AS")) {
                if (state == STATE_AFTER_OBJECT) {
                    state = STATE_EXPECT_ALIAS;
                }
            } else if (KEYWORDS.contains(word) || FUNCTIONS.contains(word)) {
                if (state == STATE_AFTER_OBJECT) {
                    pending = finalizePending(groups, pending);
                }
                state = STATE_NONE;
            } else {
                switch (state) {
                    case STATE_EXPECT_OBJECT -> {
                        state = STATE_AFTER_OBJECT;
                        pending = new HashSet<>();
                        pending.add(word);
                    }
                    case STATE_EXPECT_ALIAS -> {
                        if (pending != null) {
                            pending.add(word);
                        }
                        pending = finalizePending(groups, pending);
                        state = STATE_NONE;
                    }
                    case STATE_AFTER_OBJECT -> {
                        if (pending != null) {
                            pending.add(word);
                        }
                        pending = finalizePending(groups, pending);
                        state = STATE_NONE;
                    }
                    default -> {
                        // identificador solto fora de FROM/JOIN/AS: nao inicia grupo.
                    }
                }
            }
        }
        if (state == STATE_AFTER_OBJECT) {
            finalizePending(groups, pending);
        }
        return groups;
    }

    /** Fecha o grupo pendente (se nao vazio) em {@code groups} e devolve {@code null} (novo valor de "pending"). */
    private static Set<String> finalizePending(List<ReferenceGroup> groups, Set<String> pending) {
        if (pending != null && !pending.isEmpty()) {
            groups.add(new ReferenceGroup(pending));
        }
        return null;
    }

    /**
     * Todas as ocorrencias (como palavra inteira, nao parte de outro
     * identificador ou dentro de string/comentario) de qualquer nome em
     * {@code targetNames} dentro de {@code full.substring(start, end)} — usa
     * {@code [inicio, fim)} de cada ocorrencia. Usado por
     * {@code SqlEditorPane#installReferenceHighlight} (secao 8.5) para saber
     * ONDE desenhar o destaque de cada nome do grupo de referencia.
     */
    static List<int[]> findWordOffsets(String full, int start, int end, Set<String> targetNames) {
        List<int[]> offsets = new ArrayList<>();
        int i = start;
        while (i < end) {
            char c = full.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if ((c == '-' && i + 1 < end && full.charAt(i + 1) == '-') || c == '#') {
                while (i < end && full.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < end && full.charAt(i + 1) == '*') {
                int close = full.indexOf("*/", i + 2);
                i = (close < 0 || close > end) ? end : close + 2;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                char quote = c;
                i++;
                while (i < end) {
                    char d = full.charAt(i);
                    if (d == '\\' && (quote == '\'' || quote == '"') && i + 1 < end) {
                        i += 2;
                        continue;
                    }
                    if (d == quote) {
                        if (i + 1 < end && full.charAt(i + 1) == quote) {
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    i++;
                }
                continue;
            }
            if (!Character.isLetterOrDigit(c) && c != '_') {
                i++;
                continue;
            }
            int wordStart = i;
            while (i < end && (Character.isLetterOrDigit(full.charAt(i)) || full.charAt(i) == '_')) {
                i++;
            }
            String word = full.substring(wordStart, i).toUpperCase(Locale.ROOT);
            if (targetNames.contains(word)) {
                offsets.add(new int[] {wordStart, i});
            }
        }
        return offsets;
    }

    @Override
    public void addToken(char[] array, int start, int end, int tokenType, int startOffset) {
        if (end >= start && tokenType == TokenTypes.IDENTIFIER) {
            String word = new String(array, start, end - start + 1).toUpperCase(Locale.ROOT);
            if (KEYWORDS.contains(word)) {
                tokenType = TokenTypes.RESERVED_WORD;
            } else if (FUNCTIONS.contains(word)) {
                tokenType = TokenTypes.FUNCTION;
            } else if (knownNames.contains(word)) {
                // Nome de tabela/view/procedure/.../alias, ja identificado
                // pela pre-varredura da instrucao inteira (ver
                // #scanKnownNames) — negrito (TokenTypes.DATA_TYPE, ver
                // SqlEditorPane#SqlHighlightTokenMaker), INDEPENDENTE de
                // aparecer antes ou depois do FROM/JOIN que o introduziu.
                tokenType = TokenTypes.DATA_TYPE;
            }
        }
        super.addToken(array, start, end, tokenType, startOffset);
    }
}
