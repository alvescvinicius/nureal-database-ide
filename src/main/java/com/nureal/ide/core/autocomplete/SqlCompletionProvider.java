package com.nureal.ide.core.autocomplete;

import com.nureal.ide.core.autocomplete.CaretContextResolver.CaretContext;
import com.nureal.ide.core.autocomplete.CaretContextResolver.TableRef;
import com.nureal.ide.core.metadata.model.ColumnInfo;
import com.nureal.ide.core.metadata.model.SchemaInfo;
import com.nureal.ide.core.metadata.model.TableInfo;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;

import javax.swing.text.JTextComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
            case TABLE -> addTables(candidates);
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
            BasicCompletion completion = new BasicCompletion(this, e.getKey(), e.getValue());
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
