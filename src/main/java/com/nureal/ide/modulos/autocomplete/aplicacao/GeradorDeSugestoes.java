package com.nureal.ide.modulos.autocomplete.aplicacao;

import com.nureal.ide.core.sql.TableAliasGenerator;
import com.nureal.ide.modulos.autocomplete.dominio.CaretContextResolver;
import com.nureal.ide.modulos.autocomplete.dominio.CaretContextResolver.CaretContext;
import com.nureal.ide.modulos.autocomplete.dominio.CaretContextResolver.TableRef;
import com.nureal.ide.modulos.metadados.dominio.entidades.ColumnInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.ForeignKeyInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.TableInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Gera as sugestoes de autocomplete a partir do cache de metadados, sensiveis
 * ao contexto do cursor — mesma logica que antes vivia dentro de
 * {@code SqlCompletionProvider} (que estendia diretamente
 * {@code org.fife.ui.autocomplete.DefaultCompletionProvider}). Extraida para
 * ca sem herdar de nenhum tipo da biblioteca de UI: quem faz a ponte com o
 * RSyntaxTextArea e o adaptador de infraestrutura
 * {@code SqlCompletionProviderRSyntax}, que so converte
 * {@link SugestaoDeCompletion} em {@code Completion} do fife.
 *
 * Nunca consulta o banco ao digitar: tudo vem do schema ja carregado em
 * memoria.
 *
 * Em contexto de coluna o gerador retorna SOMENTE colunas (sem
 * palavras-chave), escopadas as tabelas em uso no statement.
 *
 * ORDEM DE EXIBICAO (base): usamos LinkedHashMap (ordem de insercao). As
 * colunas sao inseridas na ordem de criacao na base (ORDINAL_POSITION), que e
 * a mesma ordem do seletor de objetos. Por isso NAO ordenamos as sugestoes
 * por nome.
 *
 * ORDEM DE EXIBICAO (com prefixo digitado): pedido explicito do usuario —
 * entre os candidatos que batem com o prefixo, o "mais proximo" (menos
 * caracteres sobrando alem do que foi digitado) deve aparecer primeiro, pra
 * nao precisar rolar a lista. Ver {@link #gerar}: so reordena (por tamanho da
 * string, estavel) quando ha prefixo; sem prefixo (ex.: Ctrl+Espaco em
 * branco), mantem a ordem ordinal/insercao de sempre.
 */
public final class GeradorDeSugestoes {

    private static final int MAX_RESULTS = 300;

    private final List<String> keywords;

    /**
     * TODOS os esquemas conhecidos da conexao ativa (nao so um) — pedido
     * explicito do usuario: rodar/completar uma consulta cruzando schemas
     * (ex.: {@code db1.tabela1} junto de {@code db2.tabela2}) nao deveria
     * exigir "selecionar um esquema" primeiro. Populado por {@link #refreshAll},
     * chamado com TODOS os esquemas ja carregados da conexao ATIVA (ver
     * {@code Conexao#loadedSchemas} em {@code com.nureal.ide.ui}) — nunca
     * mistura esquemas de conexoes DIFERENTES, porque quem chama sempre
     * passa a colecao inteira da conexao ativa no momento, substituindo (nao
     * acumulando) o que havia antes.
     */
    private volatile List<SchemaInfo> schemas = List.of();
    /** Nome do esquema "corrente" (ver {@link #refreshAll}) — usado so para decidir QUANDO uma sugestao de tabela precisa vir qualificada ("schema.tabela") ou pode ficar so o nome, como sempre foi. */
    private volatile String currentSchemaName;
    /**
     * Indice tabela(lowercase, SEM schema) -> TableInfo, para resolver
     * "alias." rapido (ver {@link #addQualifiedColumns}) — o resolvedor de
     * contexto do cursor ({@code CaretContextResolver}) ainda nao entende
     * {@code schema.tabela} num FROM/JOIN (fora do escopo desta rodada, ver
     * {@code CaretContextResolver}), entao esta busca continua so por nome
     * de tabela; em caso de colisao (mesmo nome em 2+ schemas), prefere a
     * tabela do {@link #currentSchemaName}, senao a primeira encontrada —
     * melhor resolver ALGUMA coisa do que nenhuma.
     */
    private volatile Map<String, TableInfo> tablesByName = new LinkedHashMap<>();

    private volatile FonteDeChavesEstrangeiras fkLookup = tableName -> List.of();

    /**
     * Tabela(nome original, PRESERVANDO caixa) -> snippet completo pronto pra
     * inserir no lugar do nome (ver {@link #addTablesForJoinContext}) —
     * recalculado do zero a cada chamada de {@link #gerar}, nunca acumula
     * entre chamadas diferentes.
     */
    private final Map<String, String> fkSnippets = new LinkedHashMap<>();

    public GeradorDeSugestoes(List<String> keywords) {
        this.keywords = keywords;
    }

    /**
     * Atualiza o cache apos a estrutura do banco ser lida — recebe TODOS os
     * esquemas ja carregados da conexao ATIVA de uma vez (substitui, nunca
     * acumula sozinho: quem chama decide o escopo, ver {@link #schemas}) e
     * qual deles e o "corrente" ({@code currentSchemaName}, pode ser
     * {@code null} — conexao sem nenhum esquema aberto ainda, so com
     * schemas de outras abas, ou nenhum mesmo).
     */
    public void refreshAll(Collection<SchemaInfo> schemas, String currentSchemaName) {
        this.schemas = (schemas == null) ? List.of() : List.copyOf(schemas);
        this.currentSchemaName = currentSchemaName;
        Map<String, TableInfo> index = new LinkedHashMap<>();
        // Esquema corrente primeiro: em caso de colisao de nome entre
        // schemas, a tabela do corrente "vence" no indice bare-name (ver
        // javadoc de #tablesByName).
        for (SchemaInfo s : this.schemas) {
            if (s != null && s.name().equals(currentSchemaName)) {
                for (TableInfo t : s.tables()) {
                    index.putIfAbsent(t.name().toLowerCase(Locale.ROOT), t);
                }
            }
        }
        for (SchemaInfo s : this.schemas) {
            if (s == null) {
                continue;
            }
            for (TableInfo t : s.tables()) {
                index.putIfAbsent(t.name().toLowerCase(Locale.ROOT), t);
            }
        }
        this.tablesByName = index;
    }

    /** Liga a fonte de FKs (ver {@link FonteDeChavesEstrangeiras}) — chamado uma vez por MainWindow ao construir o provider. */
    public void setForeignKeyLookup(FonteDeChavesEstrangeiras lookup) {
        this.fkLookup = (lookup != null) ? lookup : (tableName -> List.of());
    }

    /** Sugestoes para o texto/posicao de cursor atuais, ja filtradas pelo prefixo digitado. */
    public List<SugestaoDeCompletion> gerar(String texto, int caretPosition, String prefixoDigitado) {
        String prefix = (prefixoDigitado == null) ? "" : prefixoDigitado.toLowerCase(Locale.ROOT);

        CaretContext ctx = CaretContextResolver.resolve(texto, caretPosition);

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

        List<SugestaoDeCompletion> result = new ArrayList<>();
        for (Map.Entry<String, String> e : matched) {
            if (result.size() >= MAX_RESULTS) {
                break;
            }
            // Rotulo exibido no popup continua so o nome da tabela (chave do
            // mapa), mas o texto REALMENTE inserido ao escolher e o snippet
            // inteiro "tabela alias ON ..." quando ha um (ver
            // addTablesForJoinContext).
            String snippet = fkSnippets.get(e.getKey());
            result.add(new SugestaoDeCompletion(e.getKey(), e.getValue(), snippet));
        }
        return result;
    }

    private void addKeywords(Map<String, String> out) {
        for (String kw : keywords) {
            out.putIfAbsent(kw, "palavra-chave");
        }
    }

    private void addTables(Map<String, String> out) {
        for (SchemaInfo s : schemas) {
            for (TableInfo t : s.tables()) {
                out.putIfAbsent(qualifiedTableName(s, t), "tabela" + schemaSuffix(s));
            }
        }
    }

    /**
     * Nome pronto pra inserir no editor: SO o nome da tabela quando ela e do
     * esquema CORRENTE (ou so ha um esquema conhecido — comportamento
     * identico ao de antes desta funcionalidade, o caso comum) — qualificado
     * como {@code "schema.tabela"} quando e de outro esquema, senao a
     * instrucao gerada nao rodaria (nome sozinho pode nao existir ou
     * apontar pra tabela ERRADA no esquema corrente).
     */
    private String qualifiedTableName(SchemaInfo s, TableInfo t) {
        boolean sameAsCurrent = s.name().equals(currentSchemaName) || currentSchemaName == null;
        return sameAsCurrent ? t.name() : s.name() + "." + t.name();
    }

    /** " (outro-esquema)" na descricao quando a tabela nao e do esquema corrente — deixa claro de onde ela vem no popup. */
    private String schemaSuffix(SchemaInfo s) {
        return (s.name().equals(currentSchemaName) || currentSchemaName == null) ? "" : " (" + s.name() + ")";
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
        for (SchemaInfo s : schemas) {
            for (TableInfo t : s.tables()) {
                out.putIfAbsent(qualifiedTableName(s, t), "tabela" + schemaSuffix(s));
            }
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
        // fallback: todas as colunas de TODOS os esquemas conhecidos (cada tabela em sua ordem de criacao)
        for (SchemaInfo s : schemas) {
            for (TableInfo t : s.tables()) {
                for (ColumnInfo col : t.columns()) {
                    out.putIfAbsent(col.name(), "coluna (" + qualifiedTableName(s, t) + ")");
                }
            }
        }
    }
}
