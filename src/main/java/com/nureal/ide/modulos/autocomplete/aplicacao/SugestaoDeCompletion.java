package com.nureal.ide.modulos.autocomplete.aplicacao;

/**
 * Uma sugestao pronta para exibir no popup de autocomplete: rotulo, descricao
 * e (quando aplicavel) o snippet completo a inserir no lugar do rotulo — ver
 * {@code addTablesForJoinContext} em {@link GeradorDeSugestoes} para o unico
 * caso hoje que usa snippet (tabela relacionada por FK).
 */
public record SugestaoDeCompletion(String texto, String descricao, String snippet) {

    public SugestaoDeCompletion(String texto, String descricao) {
        this(texto, descricao, null);
    }
}
