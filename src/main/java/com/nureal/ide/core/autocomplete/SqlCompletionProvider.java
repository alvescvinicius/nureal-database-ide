package com.nureal.ide.core.autocomplete;

import com.nureal.ide.core.autocomplete.CaretContextResolver.CaretContext;
import com.nureal.ide.core.autocomplete.CaretContextResolver.TableRef;
import com.nureal.ide.core.metadata.model.ColumnInfo;
import com.nureal.ide.core.metadata.model.ForeignKeyInfo;
import com.nureal.ide.core.metadata.model.SchemaInfo;
import com.nureal.ide.core.metadata.model.TableInfo;
import com.nureal.ide.core.sql.TableAliasGenerator;
import org.fife.ui.autocomplete.AbstractCompletion;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.autocomplete.ShorthandCompletion;

import javax.swing.text.JTextComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Gera as sugestoes a partir do cache de metadados, sensiveis ao contexto do cursor.
 * Nunca consulta o banco ao digitar: tudo vem do schema ja carregado em memoria.
 *
 * Em contexto de coluna o provider retorna SOMENTE colunas (sem palavras-chave),
 * escopadas as tabelas em uso no statement.
 *
 * ORDEM DE EXIBICAO (base): usamos LinkedHashMap (ordem de insercao). As colunas
 * sao inseridas na ordem de criacao na base (ORDINAL_POSITION), que e a mesma
 * ordem do seletor de objetos. Por isso NAO ordenamos as sugestoes por nome.
 *
 * ORDEM DE EXIBICAO (com prefixo digitado): pedido explicito do usuario — entre
 * os candidatos que batem com o prefixo, o "mais proximo" (menos caracteres
 * sobrando alem do que foi digitado) deve aparecer primeiro, pra nao precisar
 * rolar a lista. Ver {@link #getCompletionsImpl}: so reordena (por tamanho da
 * string, estavel) quando ha prefixo; sem prefixo (ex.: Ctrl+Espaco em branco),
 * mantem a ordem ordinal/insercao de sempre.
 */
public class SqlCompletionProvider extends DefaultCompletionProvider {

    private static final int MAX_RESULTS = 300;

    private final List<String> keywords;

    private volatile SchemaInfo schema;
    /** Indice tabela(lowercase) -> TableInfo, para resolver "alias." rapido. */
    private volatile Map<String, TableInfo> tablesByName = new LinkedHashMap<>();

    /**
     * Fonte das chaves estrangeiras JA CONHECIDAS de uma tabela — usada pelo
     * "auxiliar de montagem de queries" (ver {@link #addTablesForJoinContext})
     * pra sugerir, ao completar o nome da tabela logo apos um JOIN, primeiro
     * as tabelas relacionadas por FK as que ja estao no FROM/JOIN da consulta
     * (ver {@link CaretContextResolver}). Deliberadamente um {@code interface}
     * funcional simples (nao a classe de cache de verdade, {@code
     * com.nureal.ide.ui.TableMetadataCache}): este pacote e {@code core}, sem
     * dependencia de {@code ui}/Swing — quem monta o editor (MainWindow) que
     * liga esta ponte via {@link #setForeignKeyLookup}. Pode devolver lista
     * vazia se a tabela ainda nao foi consultada (a implementacao tipica
     * dispara a carga em segundo plano e devolve vazio POR ENQUANTO — a
     * proxima tecla digitada tenta de novo, ja com o cache quente).
     */
    @FunctionalInterface
    public interface ForeignKeyLookup {
        List<ForeignKeyInfo> foreignKeysOf(String tableName);
    }

    private volatile ForeignKeyLookup fkLookup = tableName -> List.of();

    /**
     * Tabela(nome original, PRESERVANDO caixa) -> snippet completo pronto pra
     * inserir no lugar do nome (ver {@link #addTablesForJoinContext}) —
     * recalculado do zero a cada chamada de {@link #getCompletionsImpl},
     * nunca acumula entre chamadas diferentes.
     */
    private final Map<String, String> fkSnippets = new LinkedHashMap<>();

    public SqlCompletionProvider(List<String> keywords) {
        this.keywords = keywords;
        // Auto-ativa o popup ao digitar letras E logo apos um ponto.
        setAutoActivationRules(true, ".");
    }

    /** Atualiza o cache apos a estrutura do banco ser lida. */
    public void refresh(SchemaInfo schema) {
        this.schema = schema;
        Map<String, TableInfo> index = new LinkedHashMap<>();
        if (schema != null) {
            for (TableInfo t : schema.tables()) {
                index.put(t.name().toLowerCase(Locale.ROOT), t);
            }
        }
        this.tablesByName = index;
    }

    /** Liga a fonte de FKs (ver {@link ForeignKeyLookup}) — chamado uma vez por MainWindow ao construir o provider. */
    public void setForeignKeyLookup(ForeignKeyLookup lookup) {
        this.fkLookup = (lookup != null) ? lookup : (tableName -> List.of());
    }

    @Override
    protected List<Completion> getCompletionsImpl(JTextComponent comp) {
        String entered = getAlreadyEnteredText(comp);
        String prefix = (entered == null) ? "" : entered.toLowerCase(Locale.ROOT);

        CaretContext ctx = CaretContextResolver.resolve(comp.getText(), comp.getCaretPosition());

        // nome -> descricao, preservando a ordem de insercao (ver nota no topo da classe)
        Map<String, String> candidates = new LinkedHashMap<>();

        switch (ctx.kind()) {
            case COLUMN -> {
                // Numa clausula de coluna oferecemos colunas qualificadas
                // (alias.coluna) e tambem as nao qualificadas; NAO oferecemos
                // nomes de tabela nem aliases "crus".
                addQualifiedColumns(candidates, ctx.refs());
                addScopedColumns(candidates, ctx.tables());
            }
            case TABLE -> addTablesForJoinContext(candidates, ctx.tables());
            case GENERAL -> {
                addKeywords(candidates);
                addTables(candidates);
                addScopedColumns(candidates, List.of());
            }
        }

        List<Map.Entry<String, String>> matched = new ArrayList<>();
        for (Map.Entry<String, String> e : candidates.entrySet()) {
            if (prefix.isEmpty() || e.getKey().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matched.add(e);
            }
        }
        if (!prefix.isEmpty()) {
            // Pedido explicito do usuario: o resultado mais "proximo" do que foi
            // digitado (ou seja, com MENOS caracteres sobrando alem do prefixo)
            // deve ficar sempre no topo — digitando "tit_ti", quer ver
            // "tit_titulo_tb" antes de "tit_titulo_venda_tb", sem precisar rolar
            // a lista pra achar o que procura. Como todo candidato aqui ja
            // comeca com o MESMO prefixo, ordenar por tamanho total da string
            // e equivalente a ordenar pelo tamanho do "sobra" depois do prefixo.
            // Comparator.comparingInt() com Collections.sort (estavel) preserva
            // a ordem original (ordinal/alfabetica) entre candidatos do MESMO
            // tamanho, entao nao embaralha o que ja estava certo.
            matched.sort(Comparator.comparingInt(e -> e.getKey().length()));
        }

        List<Completion> result = new ArrayList<>();
        // Relevancia decrescente segue a ordem de "matched" (ja com o mais
        // proximo primeiro, quando ha prefixo) — maior relevancia aparece
        // antes no popup, mesmo que ele reordene por conta propria.
        int relevance = matched.size();
        for (Map.Entry<String, String> e : matched) {
            if (result.size() >= MAX_RESULTS) {
                break;
            }
            // Tabela relacionada por FK (ver addTablesForJoinContext): o
            // ROTULO exibido no popup continua so o nome da tabela (chave do
            // mapa), mas o texto REALMENTE inserido ao escolher e o snippet
            // inteiro "tabela alias ON ..." — ShorthandCompletion e feito
            // exatamente pra isso (rotulo curto, insercao mais longa).
            // Tipo declarado como AbstractCompletion (nao a interface
            // Completion): setRelevance(int) e um metodo de AbstractCompletion,
            // nao da interface — BasicCompletion e ShorthandCompletion
            // (via BasicCompletion) sempre estendem AbstractCompletion.
            String snippet = fkSnippets.get(e.getKey());
            AbstractCompletion completion = (snippet != null)
                    ? new ShorthandCompletion(this, e.getKey(), snippet, e.getValue())
                    : new BasicCompletion(this, e.getKey(), e.getValue());
            completion.setRelevance(relevance--);
            result.add(completion);
        }
        return result;
    }

    private void addKeywords(Map<String, String> out) {
        for (String kw : keywords) {
            out.putIfAbsent(kw, "palavra-chave");
        }
    }

    private void addTables(Map<String, String> out) {
        SchemaInfo s = this.schema;
        if (s == null) {
            return;
        }
        for (TableInfo t : s.tables()) {
            out.putIfAbsent(t.name(), "tabela");
        }
    }

    /**
     * Igual a {@link #addTables}, so que para o contexto TABLE de verdade
     * (FROM/JOIN/UPDATE/INTO — ver {@link CaretContextResolver}): quando
     * {@code tablesInScope} ja tem alguma tabela (tipicamente o caso de estar
     * completando o alvo de um JOIN, com a primeira tabela ja no FROM), as
     * tabelas RELACIONADAS por FK a alguma delas entram PRIMEIRO — via
     * {@link #fkLookup} — cada uma com o snippet completo ja pronto (ver
     * {@link #fkSnippets}); o resto do schema entra depois, na ordem de
     * sempre. Sem nada em {@code tablesInScope} (ex.: completando a
     * PRIMEIRA tabela de um FROM, nada foi referenciado ainda), o
     * comportamento e IDENTICO ao de antes desta funcionalidade.
     * <p>
     * So considera o sentido "para fora" (FK que a tabela em uso DECLARA,
     * apontando pra outra) — o mesmo escopo documentado em
     * {@code MainWindow#insertJoinStatement}; o sentido inverso exigiria
     * varrer o schema inteiro, fora do escopo desta primeira versao.
     */
    private void addTablesForJoinContext(Map<String, String> out, List<String> tablesInScope) {
        fkSnippets.clear();
        SchemaInfo s = this.schema;
        if (s == null) {
            return;
        }
        Set<String> already = new LinkedHashSet<>();
        for (String t : tablesInScope) {
            already.add(t.toLowerCase(Locale.ROOT));
        }
        for (String baseTable : tablesInScope) {
            String baseAlias = TableAliasGenerator.deriveAlias(baseTable);
            for (ForeignKeyInfo fk : fkLookup.foreignKeysOf(baseTable)) {
                String refTable = fk.referencedTable();
                if (refTable == null || already.contains(refTable.toLowerCase(Locale.ROOT))
                        || out.containsKey(refTable)) {
                    // null: FK sem tabela referenciada valida (nao deveria
                    // acontecer, mas por seguranca); ja em "already": tabela
                    // que ja esta na consulta, juntar de novo nao faz
                    // sentido; ja em "out": outra FK ja ofereceu a mesma
                    // tabela referenciada antes (ex.: duas colunas apontando
                    // pra ela).
                    continue;
                }
                String refAlias = TableAliasGenerator.deriveDistinctAlias(refTable, baseTable, baseAlias);
                fkSnippets.put(refTable, joinSnippet(refTable, refAlias, baseAlias, fk));
                out.put(refTable, "relacionado por FK a " + baseTable);
            }
        }
        for (TableInfo t : s.tables()) {
            out.putIfAbsent(t.name(), "tabela");
        }
    }

    /** Monta {@code "<tabela> <alias> ON <baseAlias>.<col> = <alias>.<col referenciada>"} (com AND para FK composta). */
    private static String joinSnippet(String refTable, String refAlias, String baseAlias, ForeignKeyInfo fk) {
        StringBuilder sql = new StringBuilder(refTable).append(' ').append(refAlias).append(" ON ");
        List<String> cols = fk.columns();
        List<String> refCols = fk.referencedColumns();
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) {
                sql.append(" AND ");
            }
            sql.append(baseAlias).append('.').append(cols.get(i))
                    .append(" = ")
                    .append(refAlias).append('.').append(refCols.get(i));
        }
        return sql.toString();
    }

    /**
     * Colunas qualificadas pelo qualificador preferido de cada tabela em uso,
     * ex.: "c.id_categoria". Permite que digitar o alias ja proponha as colunas.
     */
    private void addQualifiedColumns(Map<String, String> out, List<TableRef> refs) {
        for (TableRef ref : refs) {
            TableInfo t = tablesByName.get(ref.table().toLowerCase(Locale.ROOT));
            if (t != null) {
                for (ColumnInfo col : t.columns()) {
                    out.putIfAbsent(ref.qualifier() + "." + col.name(),
                            col.type() + " (" + ref.table() + ")");
                }
            }
        }
    }

    /**
     * Colunas escopadas: se 'tables' tem nomes, mostra so as colunas dessas tabelas;
     * se vier vazio (ou nenhuma resolvida), cai no fallback de todas as colunas.
     * Em ambos os casos as colunas entram na ordem de ORDINAL_POSITION.
     */
    private void addScopedColumns(Map<String, String> out, List<String> tables) {
        SchemaInfo s = this.schema;
        if (s == null) {
            return;
        }
        boolean added = false;
        for (String name : tables) {
            TableInfo t = tablesByName.get(name.toLowerCase(Locale.ROOT));
            if (t != null) {
                for (ColumnInfo col : t.columns()) {
                    out.putIfAbsent(col.name(), col.type() + " (" + t.name() + ")");
                }
                added = true;
            }
        }
        if (added) {
            return;
        }
        // fallback: todas as colunas do schema (cada tabela em sua ordem de criacao)
        for (TableInfo t : s.tables()) {
            for (ColumnInfo col : t.columns()) {
                out.putIfAbsent(col.name(), "coluna (" + t.name() + ")");
            }
        }
    }
}
