package com.nureal.ide.ui;

import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.fife.ui.rsyntaxtextarea.modes.SQLTokenMaker;

import java.util.HashSet;
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
 * Estrategia: deixa o {@link SQLTokenMaker} original fazer TODO o trabalho
 * de lexing (strings, comentarios, numeros, operadores...) e so intercepta,
 * via {@link #addToken(char[], int, int, int, int)} — o unico ponto por
 * onde TODO token gerado pela gramatica passa — os tokens que vieram
 * classificados como {@code IDENTIFIER} (palavra generica, sem correspondencia
 * exata na gramatica original) para reclassifica-los como palavra-chave ou
 * funcao quando (em CAIXA ALTA) batem com a lista abaixo. Nunca reclassifica
 * o que a gramatica original ja acertou (RESERVED_WORD/FUNCTION continuam
 * como vieram), entao nao ha risco de "desfazer" nenhum destaque correto.
 *
 * Registrada no lugar do {@link SQLTokenMaker} padrao em
 * {@link SqlEditorPane} (bloco {@code static}), via
 * {@code AbstractTokenMakerFactory#putMapping}.
 */
public final class SqlHighlightTokenMaker extends SQLTokenMaker {

    /**
     * Mesma lista do {@code SQLTokenMaker.flex} original (dialeto MS Access
     * do RSyntaxTextArea) + extras de SQL "moderno"/ANSI comuns em
     * Postgres/MySQL/SQL Server/Oracle que faltam na gramatica embutida.
     */
    private static final Set<String> KEYWORDS = Set.of(
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
    private static final Set<String> FUNCTIONS = Set.of(
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
     * {@link #applyObjectNameHighlight} para saber quando o PROXIMO
     * identificador "de verdade" deve ganhar negrito (ver
     * {@link #expectState}). "JOIN" sozinho basta (LEFT/INNER/OUTER/FULL/
     * CROSS antes dele ja sao palavras-chave normais, tratadas a parte).
     */
    private static final Set<String> OBJECT_INTRODUCERS = Set.of(
            "FROM", "JOIN", "INTO", "UPDATE", "TABLE", "VIEW",
            "PROCEDURE", "FUNCTION", "TRIGGER", "CALL", "EXEC", "EXECUTE", "REFERENCES"
    );

    private static final int STATE_NONE = 0;
    /** Acabou de ver um introdutor (FROM/JOIN/INTO/...): o PROXIMO identificador e o nome do objeto. */
    private static final int STATE_EXPECT_OBJECT = 1;
    /** Acabou de destacar o nome do objeto: o que vier a seguir PODE ser um alias implicito, "AS alias", "." (nome qualificado) ou "," (mais um objeto). */
    private static final int STATE_AFTER_OBJECT = 2;
    /** Viu "AS" logo apos o nome do objeto: o PROXIMO identificador e o alias, com certeza. */
    private static final int STATE_EXPECT_ALIAS = 3;

    /**
     * Estado do "olhar para tras" usado para negritar nomes de
     * tabela/view/procedure/function/trigger e seus alias (pedido explicito
     * do usuario: "os nomes de tabelas e seus alias devem ser em negrito
     * para destacar, assim como se fosse nome de uma procedure, view etc").
     * Cada {@link org.fife.ui.rsyntaxtextarea.RSyntaxTextArea} tem sua
     * PROPRIA instancia deste TokenMaker (ver o bloco {@code static} em
     * {@link SqlEditorPane}, que registra a CLASSE, nao uma instancia — o
     * {@code TokenMakerFactory} cria uma nova via reflexao por editor), entao
     * nao ha risco deste campo vazar de uma aba pra outra.
     *
     * LIMITACAO CONHECIDA E ACEITA: isto rastreia contexto DENTRO da mesma
     * chamada de lexing (tipicamente uma linha inteira, processada token a
     * token em ordem — cobre o caso comum de "FROM tabela alias" na MESMA
     * linha com 100% de confiabilidade). Entre linhas DIFERENTES, funciona
     * bem na pratica (o RSyntaxTextArea relexa de cima para baixo em uso
     * normal), mas nao ha garantia formal como ha para strings/comentarios
     * multi-linha (que o RSyntaxTextArea rastreia nativamente via
     * {@code initialTokenType}) — na pior hipotese (repaint fora de ordem
     * apos scroll/edicao), o pior que acontece e um nome/alias raro nao
     * ficar em negrito ou uma palavra ganhar negrito por engano; nunca afeta
     * a formatacao/execucao do SQL, so a aparencia.
     */
    private int expectState = STATE_NONE;

    /**
     * Nomes de objeto/alias ja vistos NESTA INSTRUCAO (desde o ultimo
     * {@code ;}, ver {@link #applyObjectNameHighlight}) — pedido explicito
     * do usuario: o alias deve ficar em negrito toda vez que aparecer de
     * novo, nao so no ponto onde foi definido no FROM/JOIN (ex.: "WHERE
     * O.ID > 0" deve negritar o "O" mesmo estando bem depois do FROM). Ao
     * contrario de {@link #expectState} (que so importa token a token,
     * "olhando pra tras"), isto precisa ACUMULAR — por isso e limpo so ao
     * ver um {@code ;} (fim de instrucao), nao a cada token neutro.
     */
    private final Set<String> knownNames = new HashSet<>();

    @Override
    public void addToken(char[] array, int start, int end, int tokenType, int startOffset) {
        // IMPORTANTE: "FROM"/"JOIN"/"INTO"/"UPDATE"/"TABLE"/"REFERENCES"/"AS"
        // ja vem RESERVED_WORD direto da gramatica NATIVA do SQLTokenMaker
        // (estao no dialeto embutido) — so "VIEW"/"PROCEDURE"/"FUNCTION"/
        // "TRIGGER"/"CALL"/"EXEC"/"EXECUTE" (nossos EXTRAS) passam pelo
        // reclassificador acima e chegam aqui como RESERVED_WORD DEPOIS de
        // ja terem sido IDENTIFIER. Por isso o rastreamento de objeto/alias
        // (applyObjectNameHighlight) tem que rodar para os DOIS casos —
        // RESERVED_WORD/FUNCTION (ja classificados, de um jeito ou de
        // outro) E IDENTIFIER (nomes de tabela/alias de verdade) — nunca
        // so IDENTIFIER, senao "FROM"/"JOIN" nativos nunca disparariam a
        // expectativa e nada seria negritado.
        if (end >= start && (tokenType == TokenTypes.IDENTIFIER
                || tokenType == TokenTypes.RESERVED_WORD || tokenType == TokenTypes.FUNCTION)) {
            String word = new String(array, start, end - start + 1).toUpperCase(Locale.ROOT);
            if (tokenType == TokenTypes.IDENTIFIER) {
                if (KEYWORDS.contains(word)) {
                    tokenType = TokenTypes.RESERVED_WORD;
                } else if (FUNCTIONS.contains(word)) {
                    tokenType = TokenTypes.FUNCTION;
                }
            }
            tokenType = applyObjectNameHighlight(word, tokenType);
        } else if (tokenType != TokenTypes.WHITESPACE
                && tokenType != TokenTypes.COMMENT_EOL
                && tokenType != TokenTypes.COMMENT_MULTILINE) {
            // Separador ("(", ")"), operador, numero, string etc: nao faz
            // parte de um nome/alias de objeto, e tambem encerra qualquer
            // expectativa em andamento — ex.: "FROM (subquery)" cai aqui no
            // "(" e desiste de tentar negritar algo dentro da subquery
            // (limitacao aceita, ver javadoc de expectState).
            expectState = STATE_NONE;
        }
        super.addToken(array, start, end, tokenType, startOffset);
    }

    /**
     * Decide se {@code word} (identificador, ja em CAIXA ALTA — inclui casos
     * especiais como {@code "."} e {@code ","}, que a gramatica do
     * {@link SQLTokenMaker} emite como IDENTIFIER de 1 caractere, ver
     * {@code SQLTokenMaker.flex}) deve virar {@link TokenTypes#DATA_TYPE}
     * (negrito, ja por padrao do RSyntaxTextArea — ver
     * {@code SyntaxScheme#restoreDefaults}) por ser um nome de
     * tabela/view/procedure/function/trigger ou o alias de um deles,
     * avancando {@link #expectState} conforme o caso.
     */
    private int applyObjectNameHighlight(String word, int tokenType) {
        if (word.equals(";")) {
            // Fim de instrucao: os alias/nomes vistos ate aqui nao valem
            // mais para a PROXIMA instrucao (podem ate colidir de proposito
            // com nomes de coluna dela, ex.: "o" de "orders o" numa consulta
            // nao deveria negritar uma coluna "o" de outra consulta bem
            // diferente logo depois, na mesma aba).
            knownNames.clear();
            expectState = STATE_NONE;
            return tokenType;
        }
        if (word.equals(".")) {
            return tokenType; // nome qualificado (schema.tabela): nao muda a expectativa
        }
        if (word.equals(",")) {
            // "FROM a, b" (lista antiga de tabelas): uma virgula logo apos
            // um nome/alias de objeto reabre a expectativa para mais um.
            // Em qualquer outro contexto (ex.: lista de colunas do SELECT)
            // nao ha expectativa ativa mesmo, entao e inofensivo.
            if (expectState == STATE_AFTER_OBJECT) {
                expectState = STATE_EXPECT_OBJECT;
            }
            return tokenType;
        }
        if (OBJECT_INTRODUCERS.contains(word)) {
            expectState = STATE_EXPECT_OBJECT;
            return tokenType;
        }
        if (word.equals("AS")) {
            if (expectState == STATE_AFTER_OBJECT) {
                expectState = STATE_EXPECT_ALIAS;
            }
            return tokenType;
        }
        if (tokenType == TokenTypes.RESERVED_WORD || tokenType == TokenTypes.FUNCTION) {
            // Qualquer outra palavra-chave (WHERE, ON, SET, GROUP, AND...)
            // encerra a expectativa: nao ha alias implicito antes de uma
            // clausula nova comecando.
            expectState = STATE_NONE;
            return tokenType;
        }
        // Identificador "normal" (nao e palavra reservada nem pontuacao
        // especial) — o que fazer com ele depende de quem veio antes.
        switch (expectState) {
            case STATE_EXPECT_OBJECT:
                expectState = STATE_AFTER_OBJECT;
                knownNames.add(word);
                return TokenTypes.DATA_TYPE;
            case STATE_EXPECT_ALIAS:
                expectState = STATE_NONE;
                knownNames.add(word);
                return TokenTypes.DATA_TYPE;
            case STATE_AFTER_OBJECT:
                // Identificador logo apos o nome do objeto, sem "AS"
                // explicito: alias implicito ("FROM tabela t") — tambem em
                // negrito, pedido explicito do usuario.
                expectState = STATE_NONE;
                knownNames.add(word);
                return TokenTypes.DATA_TYPE;
            default:
                // Fora de qualquer FROM/JOIN/... : ainda assim, se esta
                // palavra JA foi vista como nome de objeto/alias antes
                // NESTA instrucao, continua em negrito — pedido explicito
                // do usuario: o alias deve "acompanhar" em qualquer lugar
                // que reaparecer (WHERE, ON, SELECT, GROUP BY...), nao so
                // no ponto onde foi definido no FROM/JOIN.
                return knownNames.contains(word) ? TokenTypes.DATA_TYPE : tokenType;
        }
    }
}
