package com.nureal.ide.modulos.autocomplete.infraestrutura;

import com.nureal.ide.modulos.autocomplete.aplicacao.FonteDeChavesEstrangeiras;
import com.nureal.ide.modulos.autocomplete.aplicacao.GeradorDeSugestoes;
import com.nureal.ide.modulos.autocomplete.aplicacao.SugestaoDeCompletion;
import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaInfo;
import org.fife.ui.autocomplete.AbstractCompletion;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.autocomplete.ShorthandCompletion;

import javax.swing.text.JTextComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * Unica classe do projeto autorizada a {@code extends DefaultCompletionProvider}
 * — qualquer outro ponto de autocomplete futuro reusa esta mesma classe em
 * vez de herdar novamente da biblioteca (ver
 * .specs/05-modulo-autocomplete-e-editor-sql.md). Toda a logica de "quais
 * sugestoes fazem sentido aqui" vive em {@link GeradorDeSugestoes}
 * (aplicacao, sem depender de nenhum tipo do fife); este adaptador so
 * traduz {@link SugestaoDeCompletion} para {@code Completion} do fife.
 */
public class SqlCompletionProviderRSyntax extends DefaultCompletionProvider {

    private final GeradorDeSugestoes gerador;

    public SqlCompletionProviderRSyntax(List<String> keywords) {
        this.gerador = new GeradorDeSugestoes(keywords);
        // Auto-ativa o popup ao digitar letras E logo apos um ponto.
        setAutoActivationRules(true, ".");
    }

    /** Atualiza o cache apos a estrutura do banco ser lida. */
    public void refresh(SchemaInfo schema) {
        gerador.refresh(schema);
    }

    /** Liga a fonte de FKs — chamado uma vez por MainWindow ao construir o provider. */
    public void setForeignKeyLookup(FonteDeChavesEstrangeiras lookup) {
        gerador.setForeignKeyLookup(lookup);
    }

    @Override
    protected List<Completion> getCompletionsImpl(JTextComponent comp) {
        String entered = getAlreadyEnteredText(comp);
        List<SugestaoDeCompletion> sugestoes = gerador.gerar(comp.getText(), comp.getCaretPosition(), entered);

        List<Completion> result = new ArrayList<>();
        // Relevancia decrescente segue a ordem de "sugestoes" (ja com o mais
        // proximo primeiro, quando ha prefixo, resolvido por GeradorDeSugestoes)
        // — maior relevancia aparece antes no popup, mesmo que ele reordene
        // por conta propria.
        int relevance = sugestoes.size();
        for (SugestaoDeCompletion s : sugestoes) {
            // Tipo declarado como AbstractCompletion (nao a interface
            // Completion): setRelevance(int) e um metodo de AbstractCompletion,
            // nao da interface — BasicCompletion e ShorthandCompletion
            // (via BasicCompletion) sempre estendem AbstractCompletion.
            AbstractCompletion completion = (s.snippet() != null)
                    ? new ShorthandCompletion(this, s.texto(), s.snippet(), s.descricao())
                    : new BasicCompletion(this, s.texto(), s.descricao());
            completion.setRelevance(relevance--);
            result.add(completion);
        }
        return result;
    }
}
