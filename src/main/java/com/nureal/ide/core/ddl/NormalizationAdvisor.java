package com.nureal.ide.core.ddl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.nureal.ide.core.metadata.model.ForeignKeyInfo;
import com.nureal.ide.core.metadata.model.IndexInfo;
import com.nureal.ide.core.metadata.model.NewColumnSpec;

/**
 * Motor de sugestoes do assistente de DDL ({@code com.nureal.ide.ui.DdlAssistantDialog}):
 * regras HEURISTICAS (nao um provador de teoremas nem IA) que apontam
 * desvios comuns de normalizacao e boas praticas de tipagem/integridade a
 * partir so dos NOMES e TIPOS das colunas declaradas — sem dados de
 * exemplo nem dependencias funcionais explicitas, e o maximo que da pra
 * inferir com seguranca. Cada regra retorna uma mensagem pronta, acionavel,
 * em portugues; nenhuma regra bloqueia a criacao/alteracao — sao so avisos.
 * <p>
 * Publica e sem estado (metodos estaticos) de proposito: nao depende de
 * Swing nem de JDBC, entao e testavel isoladamente (ver
 * {@code NormalizationAdvisorTest}).
 */
public final class NormalizationAdvisor {

    private NormalizationAdvisor() {
    }

    private static final Pattern TRAILING_NUMBER = Pattern.compile("^(.*?)_?([0-9]+)$");

    private static final Set<String> MONEY_HINTS = Set.of(
            "preco", "price", "valor", "total", "salario", "salary", "custo", "cost",
            "montante", "saldo", "desconto", "discount", "subtotal");

    private static final Set<String> BOOLEAN_HINTS_PREFIX = Set.of("is_", "has_", "possui_", "tem_");
    private static final Set<String> BOOLEAN_HINTS_EXACT = Set.of(
            "ativo", "ativa", "inativo", "inativa", "flag", "habilitado", "habilitada",
            "aprovado", "aprovada", "excluido", "excluida", "deletado", "deletada");

    private static final Set<String> NATURAL_KEY_HINTS = Set.of(
            "email", "e_mail", "cpf", "cnpj", "login", "username", "codigo", "code", "slug");

    /**
     * Roda todas as regras sobre a especificacao atual (colunas + FKs +
     * indices ja declarados no assistente, incluindo colunas EXISTENTES no
     * modo "alterar tabela") e devolve as sugestoes, na ordem em que fazem
     * sentido revisar (chave primaria e tipos primeiro, relacoes depois).
     */
    public static List<String> analyze(String tableName, List<NewColumnSpec> allColumns,
            List<ForeignKeyInfo> foreignKeys, List<IndexInfo> indexes) {
        List<String> out = new ArrayList<>();
        if (allColumns == null || allColumns.isEmpty()) {
            return out;
        }
        checkTableName(tableName, out);
        checkPrimaryKey(allColumns, out);
        checkDataTypes(allColumns, out);
        checkNaturalKeysWithoutUnique(allColumns, indexes, out);
        checkRepeatingGroups(allColumns, out);
        checkPartialDependency(allColumns, out);
        checkDenormalizedDescriptiveColumns(allColumns, out);
        checkForeignKeysWithoutIndex(foreignKeys, indexes, out);
        return out;
    }

    private static void checkTableName(String tableName, List<String> out) {
        if (tableName == null || tableName.isBlank()) {
            return;
        }
        if (!tableName.equals(tableName.toLowerCase(Locale.ROOT)) || tableName.contains(" ")) {
            out.add("Nome de tabela \"" + tableName + "\": prefira snake_case totalmente minusculo "
                    + "(ex.: \"" + tableName.trim().toLowerCase(Locale.ROOT).replace(' ', '_')
                    + "\") para manter o padrao do resto do banco e evitar precisar de aspas/crases nas queries.");
        }
    }

    private static void checkPrimaryKey(List<NewColumnSpec> cols, List<String> out) {
        boolean hasPk = cols.stream().anyMatch(NewColumnSpec::primaryKey);
        if (!hasPk) {
            out.add("Nenhuma PRIMARY KEY definida: toda tabela deveria ter uma chave primaria "
                    + "(o mais simples costuma ser um \"id\" INT/BIGINT AUTO_INCREMENT) para permitir "
                    + "referencias (FK), replicacao e evitar linhas duplicadas.");
        }
    }

    private static void checkDataTypes(List<NewColumnSpec> cols, List<String> out) {
        for (NewColumnSpec c : cols) {
            String name = low(c.name());
            String type = low(c.sqlType());
            if (containsHint(name, MONEY_HINTS) && (type.equals("float") || type.equals("double"))) {
                out.add("Coluna \"" + c.name() + "\" parece guardar dinheiro mas usa " + c.sqlType().toUpperCase(Locale.ROOT)
                        + ": prefira DECIMAL(10,2) (ou precisao equivalente) — FLOAT/DOUBLE podem arredondar "
                        + "valores monetarios de forma imprevisivel.");
            }
            boolean looksBoolean = BOOLEAN_HINTS_EXACT.contains(name)
                    || BOOLEAN_HINTS_PREFIX.stream().anyMatch(name::startsWith);
            if (looksBoolean && !type.equals("boolean") && !type.equals("bool")
                    && !(type.equals("tinyint") && "1".equals(c.length()))) {
                out.add("Coluna \"" + c.name() + "\" parece um sinalizador (sim/nao) mas usa "
                        + c.sqlType().toUpperCase(Locale.ROOT) + ": prefira BOOLEAN (ou TINYINT(1)) — "
                        + "deixa a intencao clara e evita valores fora de 0/1.");
            }
        }
    }

    private static void checkNaturalKeysWithoutUnique(List<NewColumnSpec> cols, List<IndexInfo> indexes,
            List<String> out) {
        Set<String> uniqueColumns = new java.util.HashSet<>();
        if (indexes != null) {
            for (IndexInfo idx : indexes) {
                if (idx.unique() && idx.columns().size() == 1) {
                    uniqueColumns.add(low(idx.columns().get(0)));
                }
            }
        }
        for (NewColumnSpec c : cols) {
            String name = low(c.name());
            if (c.primaryKey() || uniqueColumns.contains(name)) {
                continue;
            }
            if (containsHint(name, NATURAL_KEY_HINTS)) {
                out.add("Coluna \"" + c.name() + "\" parece uma chave natural (deveria ser unica na tabela) "
                        + "mas nao tem indice UNIQUE: considere adicionar um indice unico para essa coluna, "
                        + "alem da PRIMARY KEY tecnica.");
            }
        }
    }

    /**
     * 1FN: colunas com o MESMO prefixo e um numero sequencial no final (ex.:
     * telefone1/telefone2/telefone3) sao um sinal classico de "grupo
     * repetitivo" — deveriam virar uma tabela filha (1:N) em vez de colunas
     * fixas na mesma linha.
     */
    private static void checkRepeatingGroups(List<NewColumnSpec> cols, List<String> out) {
        Map<String, List<String>> byPrefix = new LinkedHashMap<>();
        for (NewColumnSpec c : cols) {
            Matcher m = TRAILING_NUMBER.matcher(low(c.name()));
            if (m.matches() && !m.group(1).isBlank()) {
                byPrefix.computeIfAbsent(m.group(1), k -> new ArrayList<>()).add(c.name());
            }
        }
        for (Map.Entry<String, List<String>> e : byPrefix.entrySet()) {
            if (e.getValue().size() >= 2) {
                out.add("Colunas " + e.getValue() + " parecem um grupo repetitivo (mesmo dado, varias vezes "
                        + "na mesma linha): para respeitar a 1a Forma Normal, considere extrair para uma "
                        + "tabela filha relacionada por chave estrangeira (1 linha por valor, em vez de 1 "
                        + "coluna por valor).");
            }
        }
    }

    /**
     * 2FN: com PK composta, uma coluna cujo nome sugere depender de SO UMA
     * das colunas da chave (ex.: PK = (pedido_id, produto_id) e existe
     * "produto_descricao") e um sinal de dependencia parcial — deveria estar
     * na tabela referenciada por aquela coluna, nao repetida aqui.
     */
    private static void checkPartialDependency(List<NewColumnSpec> cols, List<String> out) {
        List<String> pkNames = cols.stream().filter(NewColumnSpec::primaryKey)
                .map(c -> low(c.name())).toList();
        if (pkNames.size() < 2) {
            return;
        }
        for (String pkCol : pkNames) {
            String entityPrefix = pkCol.endsWith("_id") ? pkCol.substring(0, pkCol.length() - 3) : null;
            if (entityPrefix == null || entityPrefix.isBlank()) {
                continue;
            }
            for (NewColumnSpec c : cols) {
                String name = low(c.name());
                if (c.primaryKey() || name.equals(pkCol)) {
                    continue;
                }
                if (name.startsWith(entityPrefix + "_")) {
                    out.add("Coluna \"" + c.name() + "\" parece depender so de \"" + pkCol + "\" (parte da "
                            + "chave primaria composta), nao da chave inteira: possivel dependencia parcial "
                            + "(2a Forma Normal) — considere mover para a tabela \"" + entityPrefix
                            + "\" referenciada por essa FK.");
                }
            }
        }
    }

    /**
     * 3FN: uma coluna "*_id" (parece FK) acompanhada de uma coluna irma com
     * o MESMO prefixo mas descritiva (ex.: cliente_id + cliente_nome) sugere
     * um dado derivado/duplicado que deveria vir de um JOIN com a tabela
     * referenciada, nao guardado de novo aqui (evita anomalias de
     * atualizacao quando o dado original mudar).
     */
    private static void checkDenormalizedDescriptiveColumns(List<NewColumnSpec> cols, List<String> out) {
        // Uma coluna "*_id" que e a UNICA da PRIMARY KEY costuma ser a
        // identidade da PROPRIA tabela (ex.: "pedido_id" na tabela
        // "pedidos"), nao uma chave estrangeira — nao conta como prefixo de
        // FK, senao qualquer outra coluna com o mesmo prefixo (ex.:
        // "pedido_data") seria apontada como "duplicada" por engano.
        long pkCount = cols.stream().filter(NewColumnSpec::primaryKey).count();
        Set<String> fkPrefixes = new java.util.HashSet<>();
        for (NewColumnSpec c : cols) {
            String name = low(c.name());
            boolean looksLikeOwnIdentity = c.primaryKey() && pkCount == 1;
            if (name.endsWith("_id") && !looksLikeOwnIdentity) {
                fkPrefixes.add(name.substring(0, name.length() - 3));
            }
        }
        for (NewColumnSpec c : cols) {
            String name = low(c.name());
            for (String prefix : fkPrefixes) {
                if (name.startsWith(prefix + "_") && !name.equals(prefix + "_id")) {
                    out.add("Colunas \"" + prefix + "_id\" e \"" + c.name() + "\" juntas sugerem dado "
                            + "duplicado da tabela referenciada por \"" + prefix + "_id\": possivel "
                            + "dependencia transitiva (3a Forma Normal) — considere obter \"" + c.name()
                            + "\" via JOIN em vez de guardar aqui (evita ficar desatualizado se o original mudar).");
                }
            }
        }
    }

    private static void checkForeignKeysWithoutIndex(List<ForeignKeyInfo> foreignKeys, List<IndexInfo> indexes,
            List<String> out) {
        if (foreignKeys == null || foreignKeys.isEmpty()) {
            return;
        }
        for (ForeignKeyInfo fk : foreignKeys) {
            boolean covered = indexes != null && indexes.stream().anyMatch(idx ->
                    !idx.columns().isEmpty() && low(idx.columns().get(0)).equals(low(fk.columns().get(0))));
            if (!covered) {
                out.add("Chave estrangeira em " + fk.columns() + " (-> " + fk.referencedTable()
                        + "): o MySQL/InnoDB costuma criar um indice automatico para ela, mas vale conferir "
                        + "(ou adicionar um indice explicito na aba Indices) para garantir joins rapidos.");
            }
        }
    }

    private static boolean containsHint(String lowerName, Set<String> hints) {
        for (String h : hints) {
            if (lowerName.contains(h)) {
                return true;
            }
        }
        return false;
    }

    private static String low(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
